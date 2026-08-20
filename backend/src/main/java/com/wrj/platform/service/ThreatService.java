package com.wrj.platform.service;

import com.wrj.platform.entity.Alert;
import com.wrj.platform.entity.Device;
import com.wrj.platform.entity.FlightTask;
import com.wrj.platform.entity.GeoFence;
import com.wrj.platform.repository.DeviceRepository;
import com.wrj.platform.repository.GeoFenceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 低空威胁感知(P1):传统算法实时判断,LLM 不进环路。
 * 1) 轨迹外推 → 预测闯入禁飞区(PREDICTED_BREACH);
 * 2) 多机两两 CPA 最近点 → 接近冲突(CONFLICT_ALERT);
 * 3) 遥测异常:电量骤降/高度突变/卫星信号弱。
 * 数据源:FlightSimulator(虚拟机)与 DeviceEventService(真实设备)的遥测统一喂入 onTelemetry。
 */
@Service
public class ThreatService {

    private static final Logger log = LoggerFactory.getLogger(ThreatService.class);
    private static final int TRACK_CAP = 40;
    private static final long TRACK_STALE_MS = 120_000;

    private final AlertService alertService;
    private final GeoFenceRepository fenceRepository;
    private final DeviceRepository deviceRepository;

    @Value("${threat.enabled:true}")
    private boolean enabled;
    @Value("${threat.horizon-seconds:60}")
    private int horizonSeconds;
    @Value("${threat.predict-cooldown-ms:300000}")
    private long predictCooldownMs;
    @Value("${threat.conflict-dcpa-meters:100}")
    private double conflictDcpa;
    @Value("${threat.conflict-tcpa-seconds:60}")
    private double conflictTcpa;
    @Value("${threat.conflict-altitude-diff:30}")
    private double conflictAltDiff;
    @Value("${threat.battery-drop-percent:15}")
    private double batteryDropPercent;
    @Value("${threat.battery-window-seconds:300}")
    private int batteryWindowSeconds;
    @Value("${threat.altitude-jump-meters:40}")
    private double altitudeJumpMeters;
    @Value("${threat.min-satellites:6}")
    private int minSatellites;

    /** 每机遥测环形缓冲(BD-09 坐标,与地图/遥测一致) */
    private final Map<Long, ArrayDeque<Sample>> tracks = new ConcurrentHashMap<>();
    /** 告警冷却:key -> 上次触发 ms */
    private final Map<String, Long> cooldowns = new ConcurrentHashMap<>();
    /** 每机预测/冲突检查节流 */
    private final Map<Long, Long> lastPredictCheck = new ConcurrentHashMap<>();
    private volatile long lastConflictCheck = 0;

    static class Sample {
        final long t;
        final double lng, lat, alt, speed, heading, battery;
        final int sats;
        Sample(long t, double lng, double lat, double alt, double speed,
               double heading, double battery, int sats) {
            this.t = t; this.lng = lng; this.lat = lat; this.alt = alt;
            this.speed = speed; this.heading = heading; this.battery = battery; this.sats = sats;
        }
    }

    public ThreatService(AlertService alertService, GeoFenceRepository fenceRepository,
                         DeviceRepository deviceRepository) {
        this.alertService = alertService;
        this.fenceRepository = fenceRepository;
        this.deviceRepository = deviceRepository;
    }

    /** 遥测喂入:虚拟/真实无人机统一入口 */
    public void onTelemetry(Device device, FlightTask task, Map<String, Object> fields) {
        if (!enabled || device.getCategory() != Device.Category.DRONE) return;
        Double lng = d(fields, "lng"), lat = d(fields, "lat");
        if (lng == null || lat == null) return;
        Sample s = new Sample(System.currentTimeMillis(), lng, lat,
                nz(d(fields, "altitude")), nz(d(fields, "speed")),
                nz(d(fields, "heading")), nz(d(fields, "battery")),
                (int) nz(d(fields, "satellites")));
        ArrayDeque<Sample> q = tracks.computeIfAbsent(device.getId(), k -> new ArrayDeque<>());
        synchronized (q) {
            q.addLast(s);
            while (q.size() > TRACK_CAP) q.pollFirst();
        }
        try {
            anomalies(device, task, q, s);
            long now = s.t;
            Long last = lastPredictCheck.get(device.getId());
            if (last == null || now - last >= 3000) {   // 预测检查 3s 节流
                lastPredictCheck.put(device.getId(), now);
                predictFence(device, task, q, s);
                conflictCheck();
            }
        } catch (Exception e) {
            log.warn("Threat check error ({}): {}", device.getCode(), e.getMessage());
        }
    }

    // ==================== 轨迹预测 → 预测闯入 ====================

    private void predictFence(Device device, FlightTask task, ArrayDeque<Sample> q, Sample now) {
        double[] v = velocity(q, now);
        if (v[0] == 0 && v[1] == 0) return;   // 静止不预测
        final int steps = 8;
        for (int i = 1; i <= steps; i++) {
            double eta = horizonSeconds * i / (double) steps;
            double plng = now.lng + v[0] * eta;
            double plat = now.lat + v[1] * eta;
            double[] wgs = CoordUtils.bd09ToWgs84(plng, plat);
            List<Long> ids = fenceRepository.findContainingFenceIds(wgs[0], wgs[1]);
            for (GeoFence fence : fenceRepository.findAllById(ids)) {
                if (fence.getType() != GeoFence.Type.NO_FLY) continue;
                if (!Boolean.TRUE.equals(fence.getEnabled())) continue;
                String key = "pb:" + device.getId() + ":" + fence.getId();
                if (cooldown(key, predictCooldownMs)) {
                    alertService.raise(Alert.Type.PREDICTED_BREACH, Alert.Level.WARNING, device, task,
                            String.format("[%s] 按当前航迹预计 %.0f 秒后将进入禁飞区「%s」,建议立即调整航线",
                                    device.getCode(), eta, fence.getName()),
                            now.lng, now.lat, now.alt);
                }
                return;   // 一个围栏命中即止
            }
        }
    }

    // ==================== 多机接近冲突(CPA) ====================

    private synchronized void conflictCheck() {
        long now = System.currentTimeMillis();
        if (now - lastConflictCheck < 3000) return;   // 全局 3s 节流
        lastConflictCheck = now;

        List<Map.Entry<Long, Sample[]>> active = new ArrayList<>();
        for (Map.Entry<Long, ArrayDeque<Sample>> e : tracks.entrySet()) {
            Sample[] pair = latestTwo(e.getValue());
            if (pair != null && now - pair[0].t < 10_000) active.add(Map.entry(e.getKey(), pair));
        }
        for (int i = 0; i < active.size(); i++) {
            for (int j = i + 1; j < active.size(); j++) {
                checkPair(active.get(i), active.get(j), now);
            }
        }
        // 顺带清理失联轨迹
        tracks.values().removeIf(q -> {
            synchronized (q) {
                Sample last = q.peekLast();
                return last == null || System.currentTimeMillis() - last.t > TRACK_STALE_MS;
            }
        });
    }

    private void checkPair(Map.Entry<Long, Sample[]> a, Map.Entry<Long, Sample[]> b, long now) {
        Sample sa = a.getValue()[0], pa = a.getValue()[1];
        Sample sb = b.getValue()[0], pb = b.getValue()[1];
        if (Math.abs(sa.alt - sb.alt) > conflictAltDiff) return;

        double latMid = (sa.lat + sb.lat) / 2;
        double mx = 111320 * Math.cos(Math.toRadians(latMid));
        double my = 111320;
        // 相对位置/速度(米)
        double rx = (sb.lng - sa.lng) * mx, ry = (sb.lat - sa.lat) * my;
        double vax = (sa.lng - pa.lng) * mx / ((sa.t - pa.t) / 1000.0);
        double vay = (sa.lat - pa.lat) * my / ((sa.t - pa.t) / 1000.0);
        double vbx = (sb.lng - pb.lng) * mx / ((sb.t - pb.t) / 1000.0);
        double vby = (sb.lat - pb.lat) * my / ((sb.t - pb.t) / 1000.0);
        double rvx = vbx - vax, rvy = vby - vay;
        double vv = rvx * rvx + rvy * rvy;
        if (vv < 1e-6) return;   // 相对静止
        double tcpa = -(rx * rvx + ry * rvy) / vv;
        if (tcpa <= 0 || tcpa > conflictTcpa) return;
        double cx = rx + rvx * tcpa, cy = ry + rvy * tcpa;
        double dcpa = Math.sqrt(cx * cx + cy * cy);
        if (dcpa > conflictDcpa) return;

        String key = "cf:" + Math.min(a.getKey(), b.getKey()) + ":" + Math.max(a.getKey(), b.getKey());
        if (cooldown(key, 180_000)) {
            Device da = device(a.getKey()), db = device(b.getKey());
            if (da == null || db == null) return;
            alertService.raise(Alert.Type.CONFLICT_ALERT, Alert.Level.WARNING, da, null,
                    String.format("[%s] 与 [%s] 预计 %.0f 秒后最近距离 %.0f m(高度差 %.0f m),注意避让",
                            da.getCode(), db.getCode(), tcpa, dcpa, Math.abs(sa.alt - sb.alt)),
                    sa.lng, sa.lat, sa.alt);
        }
    }

    // ==================== 遥测异常检测 ====================

    private void anomalies(Device device, FlightTask task, ArrayDeque<Sample> q, Sample now) {
        Sample[] pair = latestTwo(q);
        // 电量骤降:窗口内掉电超过阈值
        Sample old = windowHead(q, batteryWindowSeconds * 1000L);
        if (old != null && old.t < now.t && old.battery - now.battery >= batteryDropPercent
                && now.battery > 20) {   // 低电量已有专门告警,>20 时骤降才算异常
            if (cooldown("ba:" + device.getId(), 600_000)) {
                alertService.raise(Alert.Type.BATTERY_ANOMALY, Alert.Level.WARNING, device, task,
                        String.format("[%s] 电量 %.0f%%→%.0f%%,%d 分钟内骤降,疑似异常耗电,建议检查动力/返航",
                                device.getCode(), old.battery, now.battery, batteryWindowSeconds / 60),
                        now.lng, now.lat, now.alt);
            }
        }
        // 高度突变
        if (pair != null && Math.abs(now.alt - pair[0].alt) >= altitudeJumpMeters) {
            if (cooldown("aj:" + device.getId(), 300_000)) {
                alertService.raise(Alert.Type.ALTITUDE_JUMP, Alert.Level.WARNING, device, task,
                        String.format("[%s] 高度 %.0fm→%.0fm 突变,疑似失控或强扰动",
                                device.getCode(), pair[0].alt, now.alt),
                        now.lng, now.lat, now.alt);
            }
        }
        // 卫星信号弱:连续 3 个采样低于阈值
        if (pair != null && minSatellites > 0 && now.sats < minSatellites
                && pair[0].sats < minSatellites && pair[1].sats < minSatellites) {
            if (cooldown("sw:" + device.getId(), 600_000)) {
                alertService.raise(Alert.Type.SIGNAL_WEAK, Alert.Level.WARNING, device, task,
                        String.format("[%s] 卫星数连续 %d 颗(阈值 %d),定位不可靠,建议悬停或返航",
                                device.getCode(), now.sats, minSatellites),
                        now.lng, now.lat, now.alt);
            }
        }
    }

    // ==================== 工具 ====================

    /** 速度向量(度/秒):用当前点与 ≥3s 前的采样差分,避开单帧抖动 */
    private double[] velocity(ArrayDeque<Sample> q, Sample now) {
        Sample ref = null;
        synchronized (q) {
            for (Sample s : q) {
                if (now.t - s.t >= 3000) ref = s;   // 取满足间隔的最后一个
            }
        }
        if (ref == null || ref.t >= now.t) return new double[]{0, 0};
        double dt = (now.t - ref.t) / 1000.0;
        return new double[]{(now.lng - ref.lng) / dt, (now.lat - ref.lat) / dt};
    }

    private Sample[] latestTwo(ArrayDeque<Sample> q) {
        synchronized (q) {
            if (q.size() < 2) return null;
            Sample last = q.pollLast();
            Sample prev = q.peekLast();
            q.addLast(last);
            return new Sample[]{last, prev};
        }
    }

    /** 窗口起点:缓冲按时间升序迭代,首个落在窗口内的即窗口内最早采样 */
    private Sample windowHead(ArrayDeque<Sample> q, long windowMs) {
        long now = System.currentTimeMillis();
        synchronized (q) {
            for (Sample s : q) {
                if (now - s.t <= windowMs) return s;
            }
            return q.peekLast();   // 整个缓冲都早于窗口时退化为最新值(偏保守)
        }
    }

    private boolean cooldown(String key, long ms) {
        long now = System.currentTimeMillis();
        Long last = cooldowns.get(key);
        if (last != null && now - last < ms) return false;
        cooldowns.put(key, now);
        if (cooldowns.size() > 500) cooldowns.clear();   // 兜底防膨胀
        return true;
    }

    private final Map<Long, Device> deviceCache = new ConcurrentHashMap<>();

    private Device device(Long id) {
        return deviceCache.computeIfAbsent(id, k -> {
            try {
                return deviceRepository.findById(k).orElse(null);
            } catch (Exception e) {
                return null;
            }
        });
    }

    private static Double d(Map<String, Object> f, String key) {
        Object v = f == null ? null : f.get(key);
        return v instanceof Number n ? n.doubleValue() : null;
    }

    private static double nz(Double v) {
        return v == null ? 0 : v;
    }
}
