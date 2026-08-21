package com.wrj.platform.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wrj.platform.config.TelemetryWebSocketHandler;
import com.wrj.platform.entity.Device;
import com.wrj.platform.entity.DrillRun;
import com.wrj.platform.repository.DeviceRepository;
import com.wrj.platform.repository.DrillRunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 攻防演练模拟引擎(内存单演练实例,与 FlightSimulator 独立):
 * - 布防反制设备(警戒雷达/无线电探测/光电跟踪/无线电压制/激光处置/网捕无人机)
 * - 投放入侵敌机:沿航线逼近核心防护区,进入探测范围产生演练告警事件
 * - 反制处置:压制驱离 / 激光击落(需光电锁定)/ 网捕捕获
 * - AI 自动守候:目标被探测后由 LLM 决策选用装备(不可用时规则兜底:就近可用装备)
 * - 每秒 tick 推进并经 WS(type=drill)广播全量快照;结束后落库 DrillRun 汇总
 * 坐标口径:内存态全部 BD-09,与前端地图一致;GeoUtils 球面距离/方位。
 */
@Service
public class DrillService {

    private static final Logger log = LoggerFactory.getLogger(DrillService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 探测类分类 */
    private static final Set<Device.Category> DETECT_CATS = Set.of(
            Device.Category.RADAR, Device.Category.RADIO_DETECT, Device.Category.EO_TRACK);
    /** 反制类分类 → 动作 */
    private static final Map<Device.Category, String> COUNTER_ACTIONS = Map.of(
            Device.Category.RADIO_JAM, "JAM",
            Device.Category.LASER, "DESTROY",
            Device.Category.NET_CAPTURE, "CAPTURE");
    private static final Map<String, String> ACTION_TEXT = Map.of(
            "JAM", "电磁压制", "DESTROY", "激光打击", "CAPTURE", "网捕");

    private static final double JAM_SUCCESS = 0.85;
    private static final double LASER_SUCCESS = 0.90;
    private static final double NET_SUCCESS = 0.90;
    private static final long DEVICE_COOLDOWN_MS = 8_000;   // 反制装备动作冷却(墙钟)
    private static final long ENEMY_TIMEOUT_MS = 240_000;   // 敌机超时自行离场
    private static final long JAM_DRIFT_MS = 6_000;         // 压制生效至撤离的失控漂移时长
    private static final double NET_DRONE_SPEED = 12.0;     // 网捕无人机飞行速度 m/s

    private final DeviceRepository deviceRepository;
    private final DrillRunRepository runRepository;
    private final TelemetryWebSocketHandler wsHandler;
    private final LlmService llmService;

    public DrillService(DeviceRepository deviceRepository, DrillRunRepository runRepository,
                        TelemetryWebSocketHandler wsHandler, LlmService llmService) {
        this.deviceRepository = deviceRepository;
        this.runRepository = runRepository;
        this.wsHandler = wsHandler;
        this.llmService = llmService;
    }

    @Value("${ai.enabled:true}")
    private boolean aiEnabled;

    /** 核心防护区(与种子禁飞区同心) */
    @Value("${drill.core-lng:116.397}")
    private double coreLng;
    @Value("${drill.core-lat:39.910}")
    private double coreLat;
    @Value("${drill.core-radius:800}")
    private double coreRadius;
    /** 敌机生成半径(距核心区中心) */
    @Value("${drill.spawn-radius:6000}")
    private double spawnRadius;
    @Value("${drill.target-radius:1200}")
    private double targetRadius;

    private final ExecutorService aiExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "drill-ai");
        t.setDaemon(true);
        return t;
    });

    private final Object lock = new Object();
    private RunState run;                     // null = 尚未开始
    private final AtomicLong enemySeq = new AtomicLong();

    /* ============================== 运行态模型 ============================== */

    private static class RunState {
        String phase = "RUNNING";             // RUNNING / ENDED
        boolean autoguard;
        int speed = 1;
        long startedAt = System.currentTimeMillis();
        Long runId;
        final List<Placement> placements = new ArrayList<>();
        final List<Enemy> enemies = new ArrayList<>();          // CopyOnWrite 语义:tick 内快照遍历
        final List<ObjectNode> events = new ArrayList<>();      // 新事件追加在尾部
        long eventId = 0;
        // 统计
        int detected, neutralized, escaped;
        long responseMsTotal;
        int responseCount;
    }

    /** 布防的反制装备(位置由演练页拖放指定,能力取设备档案) */
    private static class Placement {
        final long deviceId;
        final String code;
        final String name;
        final Device.Category category;
        final double lng;
        final double lat;
        final double scanRange;
        long lastEngageAt = 0;

        Placement(long deviceId, String code, String name, Device.Category category,
                  double lng, double lat, double scanRange) {
            this.deviceId = deviceId;
            this.code = code;
            this.name = name;
            this.category = category;
            this.lng = lng;
            this.lat = lat;
            this.scanRange = scanRange;
        }

        boolean isDetect() { return DETECT_CATS.contains(category); }

        boolean isCounter() { return COUNTER_ACTIONS.containsKey(category); }
    }

    /** 入侵敌机 */
    private static class Enemy {
        final String id;
        final String kind;          // SCOUT 侦察型 / FAST 快速穿越
        final double speed;         // m/s
        final double[] target;      // 意图抵达点(BD-09)
        double lng, lat, alt, heading;
        String status = "FLYING";   // FLYING / CAPTURING / JAMMED / NEUTRALIZED / ESCAPED
        String outcome;             // 击落 / 捕获 / 驱离 / 出界撤离 / 超时撤离 / 中止
        final long spawnAt = System.currentTimeMillis();
        long detectedAt;            // 0=未被发现
        long engageAt;              // 首次处置时间
        boolean tracked;            // 已被光电锁定跟踪
        boolean intruded;           // 已闯入核心防护区
        boolean aiHandled;          // AI 已对该目标做出处置决策(防重复调用)
        long jamEndAt;              // 压制失控截止时刻
        long netEtaAt;              // 网捕到达时刻
        long engageDeviceId;
        final List<Long> detectedBy = new ArrayList<>();   // 已探测到该目标的设备 id
        final List<double[]> track = new ArrayList<>();     // 最近轨迹

        Enemy(String id, String kind, double speed, double[] target) {
            this.id = id;
            this.kind = kind;
            this.speed = speed;
            this.target = target;
        }

        boolean terminal() { return "NEUTRALIZED".equals(status) || "ESCAPED".equals(status); }
    }

    /* ============================== 对外接口 ============================== */

    /** 开始演练:placements=[{deviceId,lng,lat,scanRange?}],enemies=投放数量,autoguard=自动守候 */
    public Map<String, Object> start(Map<String, Object> req) {
        List<Map<String, Object>> ps = castList(req.get("placements"));
        if (ps == null || ps.isEmpty()) {
            throw new IllegalArgumentException("请先在地图上拖入反制设备布防");
        }
        RunState st = new RunState();
        for (Map<String, Object> pm : ps) {
            long deviceId = ((Number) pm.get("deviceId")).longValue();
            Device d = deviceRepository.findById(deviceId)
                    .orElseThrow(() -> new IllegalArgumentException("布防设备不存在: " + deviceId));
            double lng = ((Number) pm.get("lng")).doubleValue();
            double lat = ((Number) pm.get("lat")).doubleValue();
            double scan = pm.get("scanRange") != null ? ((Number) pm.get("scanRange")).doubleValue()
                    : (d.getScanRange() != null ? d.getScanRange() : defaultScan(d.getCategory()));
            st.placements.add(new Placement(deviceId, d.getCode(), d.getName(), d.getCategory(), lng, lat, scan));
        }
        if (st.placements.stream().noneMatch(Placement::isDetect)) {
            throw new IllegalArgumentException("布防至少需要 1 台探测类装备(警戒雷达/无线电探测/光电跟踪)");
        }
        st.autoguard = Boolean.TRUE.equals(req.get("autoguard"));
        int enemies = req.get("enemies") instanceof Number n ? Math.max(1, Math.min(8, n.intValue())) : 3;

        DrillRun rec = new DrillRun();
        rec.setStartedAt(LocalDateTime.now());
        rec.setStatus("RUNNING");
        rec.setAutoguard(st.autoguard);
        runRepository.save(rec);
        st.runId = rec.getId();

        spawnWave(st, enemies, null);
        event(st, "INFO", "START", String.format("演练开始:布防 %d 处反制装备,投放 %d 架入侵无人机%s",
                st.placements.size(), enemies, st.autoguard ? ",AI 自动守候已开启" : ""));
        synchronized (lock) {
            run = st;
        }
        broadcast(st);
        return snapshot(st);
    }

    /** 中止演练(未处置敌机按逃脱计,落库 ABORTED) */
    public Map<String, Object> stop() {
        RunState st = requireRun();
        synchronized (lock) {
            for (Enemy e : st.enemies) {
                if (!e.terminal()) {
                    e.status = "ESCAPED";
                    e.outcome = "中止";
                    st.escaped++;
                }
            }
            event(st, "WARNING", "STOP", "演练被手动中止");
            endRun(st, "ABORTED");
        }
        broadcast(st);
        return snapshot(st);
    }

    /** 清空演练(回到布防阶段);进行中的记录按中止落库,避免残留 RUNNING */
    public Map<String, Object> reset() {
        synchronized (lock) {
            if (run != null && !"ENDED".equals(run.phase)) {
                for (Enemy e : run.enemies) {
                    if (!e.terminal()) {
                        e.status = "ESCAPED";
                        e.outcome = "中止";
                        run.escaped++;
                    }
                }
                endRun(run, "ABORTED");
            }
            run = null;
        }
        Map<String, Object> idle = new HashMap<>();
        idle.put("phase", "IDLE");
        wsHandler.broadcast("drill", idle);
        return idle;
    }

    /** 增派敌机(波次) */
    public Map<String, Object> wave(int count, String kind) {
        RunState st = requireRun();
        if (!"RUNNING".equals(st.phase)) {
            throw new IllegalArgumentException("演练已结束,请重置后再增派");
        }
        synchronized (lock) {
            spawnWave(st, Math.max(1, Math.min(8, count)), kind);
        }
        broadcast(st);
        return snapshot(st);
    }

    public Map<String, Object> setAutoguard(boolean on) {
        RunState st = requireRun();
        synchronized (lock) {
            st.autoguard = on;
            event(st, "INFO", "AUTOGUARD", on
                    ? "AI 自动守候已开启:LLM 将对锁定目标自动决策处置"
                    : "AI 自动守候已关闭,请人工处置");
        }
        broadcast(st);
        return snapshot(st);
    }

    public Map<String, Object> setSpeed(int speed) {
        RunState st = requireRun();
        synchronized (lock) {
            st.speed = Math.max(1, Math.min(4, speed));
        }
        broadcast(st);
        return snapshot(st);
    }

    /** 人工反制处置(地图/光电视窗按钮触发) */
    public Map<String, Object> engage(long deviceId, String enemyId) {
        RunState st = requireRun();
        synchronized (lock) {
            engageInternal(st, deviceId, enemyId, "MANUAL", null);
        }
        broadcast(st);
        return snapshot(st);
    }

    public Map<String, Object> state() {
        synchronized (lock) {
            return snapshot(run);
        }
    }

    public List<DrillRun> runs() {
        return runRepository.findTop20ByOrderByStartedAtDesc();
    }

    /* ============================== 模拟 tick ============================== */

    @Scheduled(fixedDelay = 1000)
    public void tick() {
        RunState st;
        synchronized (lock) {
            st = run;
            if (st == null || !"RUNNING".equals(st.phase)) {
                return;
            }
            double dt = st.speed;   // 1 tick = speed 秒(模拟时间)
            long now = System.currentTimeMillis();

            for (Enemy e : new ArrayList<>(st.enemies)) {
                if (e.terminal()) {
                    continue;
                }
                moveEnemy(e, dt);

                // 敌机超时离场
                if (now - e.spawnAt > ENEMY_TIMEOUT_MS) {
                    e.status = "ESCAPED";
                    e.outcome = "超时撤离";
                    st.escaped++;
                    event(st, "WARNING", "ESCAPE", String.format("%s 未被处置,已滞空超时撤离", e.id));
                    continue;
                }
                // 出界撤离(远离核心区 1.6 倍生成半径)
                if (distance(e, coreLng, coreLat) > spawnRadius * 1.6) {
                    e.status = "ESCAPED";
                    e.outcome = "出界撤离";
                    st.escaped++;
                    event(st, "WARNING", "ESCAPE", String.format("%s 已飞离演练空域", e.id));
                    continue;
                }
                // 压制失控漂移结束 → 撤离(驱离成功)
                if ("JAMMED".equals(e.status) && now >= e.jamEndAt) {
                    e.status = "ESCAPED";
                    e.outcome = "驱离";
                    st.neutralized++;
                    event(st, "SUCCESS", "NEUTRALIZED", String.format("%s 遭电磁压制失控,已被驱离出核心空域", e.id));
                    continue;
                }
                // 网捕到达判定
                if ("CAPTURING".equals(e.status) && now >= e.netEtaAt) {
                    if (random().nextDouble() < NET_SUCCESS) {
                        e.status = "NEUTRALIZED";
                        e.outcome = "捕获";
                        st.neutralized++;
                        event(st, "SUCCESS", "NEUTRALIZED", String.format("%s 被网捕无人机成功捕获并押离", e.id));
                    } else {
                        e.status = "FLYING";
                        event(st, "WARNING", "COUNTER_FAIL", String.format("网捕无人机对 %s 投网未中,目标继续逼近", e.id));
                    }
                    continue;
                }
                if (!"FLYING".equals(e.status)) {
                    continue;   // JAMMED/CAPTURING 只做漂移/减速移动
                }

                // 探测判定(每台探测装备一次性发现 + 光电锁定)
                detect(st, e, now);

                // 核心区闯入告警
                if (!e.intruded && distance(e, coreLng, coreLat) <= coreRadius) {
                    e.intruded = true;
                    event(st, "CRITICAL", "INTRUSION", String.format(
                            "入侵告警:%s(快速穿越)已进入核心防护区(半径 %.0fm),威胁等级极高!", e.id, coreRadius));
                }

                // AI 自动守候:光电跟踪/探测到 + 有可用反制装备 → 提交 LLM 决策
                if (st.autoguard && e.detectedAt > 0 && !e.aiHandled && capableIn(st, e) != null) {
                    e.aiHandled = true;
                    final String enemyId = e.id;
                    aiExecutor.submit(() -> aiEngage(enemyId));
                }
            }

            // 全部敌机终态 → 演练结束
            if (st.enemies.stream().allMatch(Enemy::terminal)) {
                event(st, "SUCCESS", "END", String.format(
                        "演练结束:投放 %d 架,探测发现 %d,处置成功 %d,逃脱 %d,平均响应 %.1fs,综合评分 %d",
                        st.enemies.size(), st.detected, st.neutralized, st.escaped,
                        st.responseCount > 0 ? st.responseMsTotal / 1000.0 / st.responseCount : 0,
                        score(st)));
                endRun(st, "COMPLETED");
            }
        }
        broadcast(st);
    }

    /** 敌机移动:朝意图点直线逼近;抵达后盘旋;压制失控/网捕接近阶段减速漂移 */
    private void moveEnemy(Enemy e, double dt) {
        double effectiveSpeed = e.speed;
        if ("CAPTURING".equals(e.status)) {
            effectiveSpeed = e.speed * 0.5;
        } else if ("JAMMED".equals(e.status)) {
            effectiveSpeed = e.speed * 0.35;
            e.heading += (random().nextDouble() - 0.5) * 60;   // 失控乱向
            e.alt = Math.max(20, e.alt - 4 * dt);
        }
        double dist = GeoUtils.distance(e.lng, e.lat, e.target[0], e.target[1]);
        if (dist < 80 && "FLYING".equals(e.status)) {
            e.heading += 25;                                    // 抵达意图点后盘旋待机
        } else if (!"JAMMED".equals(e.status)) {
            e.heading = GeoUtils.bearing(e.lng, e.lat, e.target[0], e.target[1]);
        }
        double rad = Math.toRadians(e.heading);
        double lngM = 111320 * Math.max(0.1, Math.cos(Math.toRadians(e.lat)));
        e.lng += effectiveSpeed * dt * Math.sin(rad) / lngM;
        e.lat += effectiveSpeed * dt * Math.cos(rad) / 111320;
        e.alt = Math.min(500, e.alt + 1.5 * dt);
        e.track.add(new double[]{e.lng, e.lat});
        if (e.track.size() > 60) {
            e.track.remove(0);
        }
    }

    /** 探测判定:进入探测范围首次发现 → 告警事件;光电在锁程内 → tracked */
    private void detect(RunState st, Enemy e, long now) {
        for (Placement p : st.placements) {
            if (!p.isDetect() || e.detectedBy.contains(p.deviceId)) {
                continue;
            }
            double d = distance(e, p.lng, p.lat);
            if (d > p.scanRange) {
                continue;
            }
            e.detectedBy.add(p.deviceId);
            if (e.detectedAt == 0) {
                e.detectedAt = now;
                st.detected++;
                String kindText = "FAST".equals(e.kind) ? "快速穿越" : "侦察巡飞";
                event(st, "WARNING", "DETECT", String.format(
                        "探测告警:%s 发现不明目标 %s(%s) 方位 %.0f° 距离 %.0fm 高度 %.0fm,速度 %.0fm/s",
                        p.name, e.id, kindText, GeoUtils.bearing(p.lng, p.lat, e.lng, e.lat),
                        d, e.alt, e.speed));
            }
            if (p.category == Device.Category.EO_TRACK && !e.tracked) {
                e.tracked = true;
                event(st, "INFO", "TRACK", String.format(
                        "光电跟踪:%s 已锁定 %s(双光谱转塔跟踪,可供激光/网捕引导)", p.name, e.id));
            }
        }
    }

    /* ============================== 反制处置 ============================== */

    /** 处置入口(锁内调用):source=AI/MANUAL,reason 为 AI 决策理由(可空) */
    private void engageInternal(RunState st, long deviceId, String enemyId, String source, String reason) {
        Placement p = st.placements.stream().filter(x -> x.deviceId == deviceId).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("该装备未在本次演练布防"));
        if (!p.isCounter()) {
            throw new IllegalArgumentException(p.name + " 是探测类装备,不具备反制能力");
        }
        Enemy e = st.enemies.stream().filter(x -> x.id.equals(enemyId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("目标不存在: " + enemyId));
        if (!"FLYING".equals(e.status)) {
            throw new IllegalArgumentException(e.id + " 当前状态不可处置(" + e.status + ")");
        }
        long now = System.currentTimeMillis();
        if (now - p.lastEngageAt < DEVICE_COOLDOWN_MS) {
            throw new IllegalArgumentException(p.name + " 冷却中(" + (DEVICE_COOLDOWN_MS - (now - p.lastEngageAt)) / 1000 + "s)");
        }
        double d = distance(e, p.lng, p.lat);
        if (d > p.scanRange) {
            throw new IllegalArgumentException(String.format("%s 距 %s %.0fm,超出作用范围 %.0fm", e.id, p.name, d, p.scanRange));
        }
        String action = COUNTER_ACTIONS.get(p.category);

        // 响应耗时:发现 → 首次处置(仅统计一次)
        if (e.engageAt == 0 && e.detectedAt > 0) {
            e.engageAt = now;
            st.responseMsTotal += now - e.detectedAt;
            st.responseCount++;
        }
        p.lastEngageAt = now;
        e.engageDeviceId = deviceId;
        String src = "AI".equals(source) ? "AI 守候" : "人工处置";
        String why = reason == null || reason.isBlank() ? "" : "(" + reason + ")";

        switch (action) {
            case "JAM" -> {
                if (random().nextDouble() < JAM_SUCCESS) {
                    e.status = "JAMMED";
                    e.jamEndAt = now + JAM_DRIFT_MS / st.speed;
                    event(st, "SUCCESS", "COUNTER", String.format(
                            "%s:%s 对 %s 实施%s,目标遥控/图传链路被切断,失控漂移%s", src, p.name, e.id, ACTION_TEXT.get(action), why));
                } else {
                    event(st, "WARNING", "COUNTER_FAIL", String.format(
                            "%s:%s 对 %s 实施电磁压制未生效(频段规避)%s", src, p.name, e.id, why));
                }
            }
            case "DESTROY" -> {
                if (!e.tracked) {
                    throw new IllegalArgumentException("激光处置需要光电跟踪仪先锁定目标(开启光电视窗锁定)");
                }
                if (random().nextDouble() < LASER_SUCCESS) {
                    e.status = "NEUTRALIZED";
                    e.outcome = "击落";
                    st.neutralized++;
                    event(st, "SUCCESS", "COUNTER", String.format(
                            "%s:%s 对 %s 实施%s,目标空中解体坠落%s", src, p.name, e.id, ACTION_TEXT.get(action), why));
                } else {
                    event(st, "WARNING", "COUNTER_FAIL", String.format(
                            "%s:%s 对 %s 激光照射未命中,目标机动规避%s", src, p.name, e.id, why));
                }
            }
            case "CAPTURE" -> {
                // 网捕无人机起飞拦截:按距离估算到达时间,期间目标减速
                e.status = "CAPTURING";
                e.netEtaAt = now + (long) (d / NET_DRONE_SPEED * 1000 / st.speed);
                event(st, "INFO", "COUNTER", String.format(
                        "%s:网捕无人机已起飞,预计 %.0fs 后对 %s 实施投网拦截%s",
                        src, d / NET_DRONE_SPEED, e.id, why));
            }
            default -> throw new IllegalArgumentException("未知反制动作: " + action);
        }
    }

    /** 就近可用反制装备(在目标作用范围内且不在冷却) */
    private Placement capableIn(RunState st, Enemy e) {
        long now = System.currentTimeMillis();
        Placement best = null;
        double bestD = Double.MAX_VALUE;
        for (Placement p : st.placements) {
            if (!p.isCounter() || now - p.lastEngageAt < DEVICE_COOLDOWN_MS) {
                continue;
            }
            if ("LASER".equals(p.category.name()) && !e.tracked) {
                continue;   // 激光需光电锁定
            }
            double d = distance(e, p.lng, p.lat);
            if (d <= p.scanRange && d < bestD) {
                best = p;
                bestD = d;
            }
        }
        return best;
    }

    /* ============================== AI 自动守候 ============================== */

    /** LLM 决策处置:上下文含来袭目标与可用装备,返回严格 JSON;失败/无模型走规则兜底 */
    private void aiEngage(String enemyId) {
        try {
            Thread.sleep(600);   // 略作延迟,模拟决策耗时(同时等光电锁定事件先广播)
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return;
        }
        synchronized (lock) {
            RunState st = run;
            if (st == null || !"RUNNING".equals(st.phase) || !st.autoguard) {
                return;
            }
            Enemy e = st.enemies.stream().filter(x -> x.id.equals(enemyId)).findFirst().orElse(null);
            if (e == null || !"FLYING".equals(e.status)) {
                return;
            }
            Placement fallback = capableIn(st, e);
            if (fallback == null) {
                return;
            }
            Placement chosen = fallback;
            String reason = null;
            boolean byLlm = false;
            if (aiEnabled) {
                try {
                    String reply = askLlm(st, e);
                    JsonNode node = MAPPER.readTree(extractJson(reply));
                    long devId = node.path("deviceId").asLong(-1);
                    String action = node.path("action").asText("");
                    reason = node.path("reason").asText("");
                    Placement cand = st.placements.stream().filter(x -> x.deviceId == devId).findFirst().orElse(null);
                    if (cand != null && action.equals(COUNTER_ACTIONS.get(cand.category))
                            && distance(e, cand.lng, cand.lat) <= cand.scanRange
                            && System.currentTimeMillis() - cand.lastEngageAt >= DEVICE_COOLDOWN_MS
                            && (!"DESTROY".equals(action) || e.tracked)) {
                        chosen = cand;
                        byLlm = true;
                    }
                } catch (Exception ex) {
                    log.debug("Drill LLM decision failed, fallback to rule: {}", ex.getMessage());
                }
            }
            try {
                engageInternal(st, chosen.deviceId, e.id, "AI",
                        (byLlm ? reason : "规则决策:就近可用 " + chosen.name));
            } catch (Exception ex) {
                event(st, "WARNING", "AI_FAIL", "AI 守候处置失败:" + ex.getMessage());
            }
        }
        synchronized (lock) {
            if (run != null) {
                broadcast(run);
            }
        }
    }

    /** 组装态势上下文并调用 LLM(OpenAI 兼容 chat,场景 DRILL_AUTOGUARD) */
    private String askLlm(RunState st, Enemy e) {
        ObjectNode ctx = MAPPER.createObjectNode();
        ObjectNode tgt = ctx.putObject("target");
        tgt.put("id", e.id);
        tgt.put("type", "FAST".equals(e.kind) ? "快速穿越机(高速逼近)" : "侦察巡飞机(慢速盘旋)");
        tgt.put("distanceToCore", Math.round(distance(e, coreLng, coreLat)));
        tgt.put("altitude", Math.round(e.alt));
        tgt.put("speed", Math.round(e.speed));
        tgt.put("intrudedCore", e.intruded);
        tgt.put("eoTracked", e.tracked);
        ArrayNode tools = ctx.putArray("counterDevices");
        long now = System.currentTimeMillis();
        for (Placement p : st.placements) {
            if (!p.isCounter()) {
                continue;
            }
            ObjectNode d = tools.addObject();
            d.put("deviceId", p.deviceId);
            d.put("name", p.name);
            d.put("action", COUNTER_ACTIONS.get(p.category));
            d.put("actionMean", ACTION_TEXT.get(COUNTER_ACTIONS.get(p.category)));
            d.put("range", p.scanRange);
            d.put("targetDistance", Math.round(distance(e, p.lng, p.lat)));
            d.put("inRange", distance(e, p.lng, p.lat) <= p.scanRange);
            d.put("cooling", now - p.lastEngageAt < DEVICE_COOLDOWN_MS);
        }
        List<Map<String, String>> messages = List.of(
                Map.of("role", "system", "content",
                        "你是低空防御指挥AI,在攻防演练中对入侵无人机做自动处置决策。" +
                        "决策原则:目标已闯入或即将闯入核心区时优先硬摧毁(DESTROY),边缘目标可先电磁压制(JAM)驱离;" +
                        "激光(DESTROY)必须 eoTracked=true 才可选;只能选 inRange=true 且 cooling=false 的装备。" +
                        "只输出一个 JSON 对象,不要输出任何其他文字。"),
                Map.of("role", "user", "content",
                        "当前态势:" + ctx.toString() +
                        "\n请选择最优处置装备,严格输出 {\"deviceId\":数字,\"action\":\"JAM|DESTROY|CAPTURE\",\"reason\":\"20字内中文理由\"}"));
        Map<String, Object> resp = llmService.chat(null, messages, "DRILL_AUTOGUARD");
        return String.valueOf(resp.get("content"));
    }

    /** 从 LLM 回复中截取首个 JSON 对象(容忍前后缀说明文字/代码围栏) */
    private static String extractJson(String s) {
        if (s == null) {
            return "{}";
        }
        int a = s.indexOf('{');
        int b = s.lastIndexOf('}');
        return (a >= 0 && b > a) ? s.substring(a, b + 1) : "{}";
    }

    /* ============================== 演练生成与收尾 ============================== */

    private void spawnWave(RunState st, int count, String kind) {
        Random rnd = random();
        for (int i = 0; i < count; i++) {
            String k = kind != null ? kind : (rnd.nextDouble() < 0.35 ? "FAST" : "SCOUT");
            double speed = "FAST".equals(k) ? 20 + rnd.nextDouble() * 6 : 9 + rnd.nextDouble() * 4;
            double bearingDeg = rnd.nextDouble() * 360;
            double[] spawn = offset(coreLng, coreLat, bearingDeg, spawnRadius * (0.85 + rnd.nextDouble() * 0.3));
            double[] target = offset(coreLng, coreLat, rnd.nextDouble() * 360, rnd.nextDouble() * targetRadius);
            Enemy e = new Enemy("E-" + enemySeq.incrementAndGet(), k, speed, target);
            e.lng = spawn[0];
            e.lat = spawn[1];
            e.alt = 60 + rnd.nextDouble() * 140;
            e.heading = GeoUtils.bearing(spawn[0], spawn[1], target[0], target[1]);
            st.enemies.add(e);
            event(st, "INFO", "ENEMY", String.format("敌情通报:%s(%s)自方位 %.0f° 进入演练空域,正朝核心区逼近",
                    e.id, "FAST".equals(k) ? "快速穿越机" : "侦察巡飞机", bearingDeg));
        }
    }

    private void endRun(RunState st, String status) {
        if (!"ENDED".equals(st.phase)) {
            st.phase = "ENDED";
        }
        DrillRun rec = runRepository.findById(st.runId).orElse(null);
        if (rec == null) {
            return;
        }
        rec.setEndedAt(LocalDateTime.now());
        rec.setStatus(status);
        rec.setEnemiesTotal(st.enemies.size());
        rec.setDetected(st.detected);
        rec.setNeutralized(st.neutralized);
        rec.setEscaped(st.escaped);
        rec.setAvgResponseMs(st.responseCount > 0 ? st.responseMsTotal / st.responseCount : 0);
        rec.setScore(score(st));
        try {
            ArrayNode detail = MAPPER.createArrayNode();
            for (Enemy e : st.enemies) {
                ObjectNode d = detail.addObject();
                d.put("id", e.id);
                d.put("kind", "FAST".equals(e.kind) ? "快速穿越" : "侦察巡飞");
                d.put("outcome", e.outcome == null ? "未处置" : e.outcome);
                d.put("intruded", e.intruded);
                d.put("detectedBy", e.detectedBy.size());
                d.put("responseMs", e.engageAt > 0 && e.detectedAt > 0 ? e.engageAt - e.detectedAt : 0);
            }
            rec.setDetailJson(MAPPER.writeValueAsString(detail));
        } catch (Exception ex) {
            log.warn("Drill detail json failed: {}", ex.getMessage());
        }
        runRepository.save(rec);
    }

    private int score(RunState st) {
        int total = st.enemies.size();
        if (total == 0) {
            return 0;
        }
        double detectRate = st.detected * 1.0 / total;
        double killRate = st.neutralized * 1.0 / total;
        return (int) Math.round(100 * (0.45 * detectRate + 0.55 * killRate));
    }

    /* ============================== 快照与广播 ============================== */

    private void event(RunState st, String level, String type, String text) {
        ObjectNode ev = MAPPER.createObjectNode();
        ev.put("id", ++st.eventId);
        ev.put("ts", System.currentTimeMillis());
        ev.put("level", level);
        ev.put("type", type);
        ev.put("text", text);
        st.events.add(ev);
        if (st.events.size() > 120) {
            st.events.remove(0);
        }
    }

    /** 全量快照(锁内调用;广播与 REST 返回共用) */
    private Map<String, Object> snapshot(RunState st) {
        if (st == null) {
            return Map.of("phase", "IDLE");
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("phase", st.phase);
        m.put("autoguard", st.autoguard);
        m.put("speed", st.speed);
        m.put("runId", st.runId);
        m.put("startedAt", st.startedAt);
        m.put("center", Map.of("lng", coreLng, "lat", coreLat));
        m.put("coreRadius", coreRadius);
        m.put("placements", placementSnapshots(st));
        m.put("enemies", enemySnapshots(st));
        List<ObjectNode> evs = st.events.size() > 60
                ? new ArrayList<>(st.events.subList(st.events.size() - 60, st.events.size()))
                : new ArrayList<>(st.events);
        m.put("events", evs);
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", st.enemies.size());
        stats.put("flying", st.enemies.stream().filter(e -> !e.terminal()).count());
        stats.put("detected", st.detected);
        stats.put("neutralized", st.neutralized);
        stats.put("escaped", st.escaped);
        stats.put("avgResponseMs", st.responseCount > 0 ? st.responseMsTotal / st.responseCount : 0);
        stats.put("elapsedMs", System.currentTimeMillis() - st.startedAt);
        stats.put("score", score(st));
        m.put("stats", stats);
        return m;
    }

    /** 广播演练快照(与 REST 修改/tick 推进同锁,避免快照遍历与状态变更并发) */
    private void broadcast(RunState st) {
        synchronized (lock) {
            wsHandler.broadcast("drill", snapshot(st));
        }
    }

    private List<Map<String, Object>> placementSnapshots(RunState st) {
        List<Map<String, Object>> ps = new ArrayList<>();
        for (Placement p : st.placements) {
            Map<String, Object> pm = new LinkedHashMap<>();
            pm.put("deviceId", p.deviceId);
            pm.put("code", p.code);
            pm.put("name", p.name);
            pm.put("category", p.category.name());
            pm.put("lng", p.lng);
            pm.put("lat", p.lat);
            pm.put("scanRange", p.scanRange);
            pm.put("role", p.isDetect() ? "detect" : "counter");
            pm.put("action", COUNTER_ACTIONS.get(p.category));
            pm.put("cooling", System.currentTimeMillis() - p.lastEngageAt < DEVICE_COOLDOWN_MS);
            ps.add(pm);
        }
        return ps;
    }

    private List<Map<String, Object>> enemySnapshots(RunState st) {
        List<Map<String, Object>> es = new ArrayList<>();
        for (Enemy e : st.enemies) {
            Map<String, Object> em = new LinkedHashMap<>();
            em.put("id", e.id);
            em.put("kind", e.kind);
            em.put("lng", round6(e.lng));
            em.put("lat", round6(e.lat));
            em.put("alt", Math.round(e.alt));
            em.put("heading", Math.round(e.heading));
            em.put("speed", Math.round(e.speed));
            em.put("status", e.status);
            em.put("outcome", e.outcome);
            em.put("tracked", e.tracked);
            em.put("intruded", e.intruded);
            em.put("detected", e.detectedAt > 0);
            em.put("engageBy", e.engageDeviceId);
            em.put("track", new ArrayList<>(e.track));
            es.add(em);
        }
        return es;
    }

    /* ============================== 工具 ============================== */

    private RunState requireRun() {
        RunState st = run;
        if (st == null) {
            throw new IllegalArgumentException("尚未开始演练");
        }
        return st;
    }

    private double distance(Enemy e, double lng, double lat) {
        return GeoUtils.distance(e.lng, e.lat, lng, lat);
    }

    /** 以 (lng,lat) 为原点,沿方位角(北为 0 顺时针)偏移 d 米 */
    private double[] offset(double lng, double lat, double bearingDeg, double d) {
        double rad = Math.toRadians(bearingDeg);
        double lngM = 111320 * Math.max(0.1, Math.cos(Math.toRadians(lat)));
        return new double[]{lng + d * Math.sin(rad) / lngM, lat + d * Math.cos(rad) / 111320};
    }

    private static double round6(double v) {
        return Math.round(v * 1e6) / 1e6;
    }

    private static Random random() {
        return ThreadLocalRandom.current();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> castList(Object o) {
        return o instanceof List<?> l ? (List<Map<String, Object>>) (List<?>) l : null;
    }

    /** 类型默认扫描范围(设备档案未填 scanRange 时兜底) */
    private static double defaultScan(Device.Category c) {
        return switch (c) {
            case RADAR -> 5000;
            case RADIO_DETECT -> 3500;
            case EO_TRACK -> 2500;
            case RADIO_JAM -> 1800;
            case LASER -> 1000;
            case NET_CAPTURE -> 1200;
            default -> 1000;
        };
    }
}
