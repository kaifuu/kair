package com.wrj.platform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wrj.platform.config.TelemetryWebSocketHandler;
import com.wrj.platform.dto.TelemetryDto;
import com.wrj.platform.entity.Alert;
import com.wrj.platform.entity.Device;
import com.wrj.platform.entity.DeviceDataHistory;
import com.wrj.platform.entity.FlightTask;
import com.wrj.platform.entity.GeoFence;
import com.wrj.platform.repository.DeviceDataHistoryRepository;
import com.wrj.platform.repository.DeviceRepository;
import com.wrj.platform.repository.FlightTaskRepository;
import com.wrj.platform.repository.GeoFenceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 飞行模拟引擎(仅驱动 virtual=true 的虚拟设备,真机遥测走 Netty 网关):
 * - 每 2s 一个 tick,推进所有 FLYING 任务沿航线飞行
 * - 电量消耗、卫星数抖动
 * - 围栏检测(闯入禁飞区/限飞区超高)→ 生成告警
 * - 通过 WebSocket 广播遥测
 */
@Component
public class FlightSimulator {

    private static final Logger log = LoggerFactory.getLogger(FlightSimulator.class);
    private static final double TICK_SECONDS = 2.0;

    private final FlightTaskRepository taskRepository;
    private final DeviceRepository deviceRepository;
    private final GeoFenceRepository fenceRepository;
    private final DeviceDataHistoryRepository historyRepository;
    private final AlertService alertService;
    private final ThreatService threatService;
    private final TelemetryWebSocketHandler wsHandler;
    private final ObjectMapper objectMapper;

    /** 每个任务的飞行进度:taskId -> SimState */
    private final Map<Long, SimState> states = new ConcurrentHashMap<>();

    /** 告警去重:taskId -> 最近告警时间戳 */
    private final Map<String, Long> alarmCooldown = new ConcurrentHashMap<>();

    /** 遥测入库节流计数(每 100 次落库触发一次每设备裁剪) */
    private long historyInserts = 0;
    private static final int HISTORY_KEEP = 2000;
    private static final int TRIM_EVERY = 100;

    public FlightSimulator(FlightTaskRepository taskRepository,
                           DeviceRepository deviceRepository,
                           GeoFenceRepository fenceRepository,
                           DeviceDataHistoryRepository historyRepository,
                           AlertService alertService,
                           ThreatService threatService,
                           TelemetryWebSocketHandler wsHandler,
                           ObjectMapper objectMapper) {
        this.taskRepository = taskRepository;
        this.deviceRepository = deviceRepository;
        this.fenceRepository = fenceRepository;
        this.historyRepository = historyRepository;
        this.alertService = alertService;
        this.threatService = threatService;
        this.wsHandler = wsHandler;
        this.objectMapper = objectMapper;
    }

    /** 任务开始飞行时注册初始状态 */
    public void startTask(FlightTask task) {
        List<double[]> route = parseRoute(task.getRouteJson());
        if (route.isEmpty() && task.getDevice() != null) {
            route = defaultRoute(task.getDevice().getHomeLng(), task.getDevice().getHomeLat());
        }
        SimState st = new SimState();
        st.taskId = task.getId();
        st.route = route;
        if (!route.isEmpty()) {
            st.lng = route.get(0)[0];
            st.lat = route.get(0)[1];
        }
        st.altitude = task.getPlannedAltitude() == null ? 120 : task.getPlannedAltitude();
        st.battery = 95 + Math.random() * 5;
        st.speed = 8 + Math.random() * 4;
        st.progress = 0;
        states.put(task.getId(), st);
    }

    /** 任务结束/中止时清理 */
    public void stopTask(Long taskId) {
        states.remove(taskId);
    }

    /** 模拟 tick:2s 一次 */
    @Scheduled(fixedDelay = 2000)
    public void tick() {
        List<FlightTask> flyingTasks = taskRepository.findByStatus(FlightTask.Status.FLYING);
        if (flyingTasks.isEmpty()) {
            return;
        }
        List<TelemetryDto> batch = new ArrayList<>();

        for (FlightTask task : flyingTasks) {
            try {
                TelemetryDto dto = advance(task);
                if (dto != null) {
                    batch.add(dto);
                }
            } catch (Exception e) {
                log.error("Sim error on task {}: {}", task.getId(), e.getMessage());
            }
        }
        if (!batch.isEmpty()) {
            wsHandler.broadcast("telemetry", batch);
        }
    }

    /** 推进单架无人机,返回本 tick 遥测 */
    private TelemetryDto advance(FlightTask task) {
        SimState st = states.get(task.getId());
        Device device = task.getDevice();
        if (device == null) {
            return null;
        }
        // 真机(virtual=false)遥测由 Netty 网关上报,模拟器不推进
        if (!Boolean.TRUE.equals(device.getVirtual())) {
            return null;
        }
        if (st == null) {
            startTask(task);
            st = states.get(task.getId());
            if (st == null) {
                return null;
            }
        }

        // ---- 沿航线推进 ----
        if (st.route.size() >= 2) {
            double[] from = st.route.get(st.progress);
            double[] to = st.route.get(st.progress + 1);
            double segLen = GeoUtils.distance(from[0], from[1], to[0], to[1]);
            double stepMeters = st.speed * TICK_SECONDS;
            st.segDone += stepMeters;

            if (st.segDone >= segLen && st.progress < st.route.size() - 2) {
                st.progress++;
                st.segDone = 0;
                from = st.route.get(st.progress);
                to = st.route.get(st.progress + 1);
                segLen = GeoUtils.distance(from[0], from[1], to[0], to[1]);
            }

            double t = segLen <= 0 ? 1.0 : Math.min(1.0, st.segDone / segLen);
            // 目标点为路线终点时,到位即完成
            if (st.progress >= st.route.size() - 2 && t >= 1.0) {
                completeTask(task, st, device);
                return null;
            }
            double[] pos = GeoUtils.interpolate(from[0], from[1], to[0], to[1], t);
            st.lng = pos[0];
            st.lat = pos[1];
            st.heading = GeoUtils.bearing(from[0], from[1], to[0], to[1]);
            st.altitude = (st.route.get(st.progress + 1)[2] > 0)
                    ? st.route.get(st.progress + 1)[2]
                    : task.getPlannedAltitude();
        }

        // ---- 电量/卫星模拟 ----
        st.battery = Math.max(5, st.battery - 0.18 - Math.random() * 0.1);
        st.satellites = 12 + (int) (Math.random() * 9);

        // ---- 围栏检测(PostGIS 空间查询,BD-09→WGS-84) ----
        checkFences(task, device, st);
        checkBattery(task, device, st);

        // ---- 轨迹点(最多保留 60 个) ----
        Map<String, Double> point = new HashMap<>();
        point.put("lng", st.lng);
        point.put("lat", st.lat);
        st.track.add(point);
        if (st.track.size() > 60) {
            st.track.remove(0);
        }

        // ---- 遥测入库(每 2 tick 一帧 ≈4s,供轨迹回放/历史曲线) ----
        if (++st.persistTicks % 2 == 0) {
            persistTelemetry(device, st);
        }

        // ---- 威胁感知喂入(轨迹预测/多机冲突/遥测异常,内部自带节流) ----
        threatService.onTelemetry(device, task, Map.of(
                "lng", st.lng, "lat", st.lat,
                "altitude", st.altitude, "speed", st.speed,
                "heading", st.heading, "battery", st.battery,
                "satellites", (double) st.satellites));

        return buildDto(task, device, st);
    }

    /** 遥测留痕(失败不影响飞行模拟) */
    private void persistTelemetry(Device device, SimState st) {
        try {
            Map<String, Object> fields = new HashMap<>();
            fields.put("lng", round(st.lng, 6));
            fields.put("lat", round(st.lat, 6));
            fields.put("altitude", round(st.altitude, 1));
            fields.put("speed", round(st.speed, 1));
            fields.put("heading", round(st.heading, 1));
            fields.put("battery", round(st.battery, 1));
            fields.put("satellites", st.satellites);
            historyRepository.save(new DeviceDataHistory(device.getId(), device.getCode(),
                    device.getCategory(), objectMapper.writeValueAsString(fields)));
            if (historyInserts++ % TRIM_EVERY == 0) {
                historyRepository.trimPerDevice(device.getId(), HISTORY_KEEP);
            }
        } catch (Exception e) {
            log.warn("Persist telemetry error ({}): {}", device.getCode(), e.getMessage());
        }
    }

    /** 围栏碰撞:无人机 BD-09 位置转 WGS-84 后走 PostGIS ST_Contains/ST_DWithin */
    private void checkFences(FlightTask task, Device device, SimState st) {
        double[] wgs = CoordUtils.bd09ToWgs84(st.lng, st.lat);
        List<Long> ids = fenceRepository.findContainingFenceIds(wgs[0], wgs[1]);
        if (ids.isEmpty()) {
            return;
        }
        for (GeoFence fence : fenceRepository.findAllById(ids)) {
            if (fence.getType() == GeoFence.Type.NO_FLY) {
                alarm(task, device, st, Alert.Type.GEOFENCE_BREACH, Alert.Level.CRITICAL,
                        String.format("[%s] 闯入禁飞区「%s」!", device.getCode(), fence.getName()), 60_000);
            } else if (fence.getType() == GeoFence.Type.LIMIT && fence.getMaxAltitude() != null
                    && st.altitude > fence.getMaxAltitude()) {
                alarm(task, device, st, Alert.Type.ALTITUDE_EXCEED, Alert.Level.WARNING,
                        String.format("[%s] 在限飞区「%s」内超高: %.0fm > 限高%.0fm",
                                device.getCode(), fence.getName(), st.altitude, fence.getMaxAltitude()),
                        60_000);
            }
        }
    }

    private void checkBattery(FlightTask task, Device device, SimState st) {
        if (st.battery <= 20 && st.battery > 15) {
            alarm(task, device, st, Alert.Type.LOW_BATTERY, Alert.Level.WARNING,
                    String.format("[%s] 电量不足 %.0f%%,建议返航", device.getCode(), st.battery), 120_000);
        } else if (st.battery <= 15) {
            alarm(task, device, st, Alert.Type.LOW_BATTERY, Alert.Level.CRITICAL,
                    String.format("[%s] 电量危急 %.0f%%,立即返航!", device.getCode(), st.battery), 120_000);
        }
    }

    /** 带冷却的告警(同任务同类告警 60/120s 内不重复) */
    private void alarm(FlightTask task, Device device, SimState st,
                       Alert.Type type, Alert.Level level, String msg, long cooldownMs) {
        String key = task.getId() + ":" + type;
        long now = System.currentTimeMillis();
        Long last = alarmCooldown.get(key);
        if (last != null && now - last < cooldownMs) {
            return;
        }
        alarmCooldown.put(key, now);
        alertService.raise(type, level, device, task, msg, st.lng, st.lat, st.altitude);
    }

    private void completeTask(FlightTask task, SimState st, Device device) {
        task.setStatus(FlightTask.Status.COMPLETED);
        task.setEndTime(java.time.LocalDateTime.now());
        taskRepository.save(task);

        device.setStatus(Device.Status.IDLE);
        double minutes = tickCount(task) * TICK_SECONDS / 60.0;
        device.setTotalFlightHours(device.getTotalFlightHours() == null ? 0 : device.getTotalFlightHours() + minutes / 60.0);
        deviceRepository.save(device);

        states.remove(task.getId());
        log.info("Task {} completed, device {} back to IDLE", task.getId(), device.getCode());
    }

    private double tickCount(FlightTask task) {
        if (task.getStartTime() == null) {
            return 0;
        }
        return java.time.Duration.between(task.getStartTime(), java.time.LocalDateTime.now()).getSeconds() / TICK_SECONDS;
    }

    private TelemetryDto buildDto(FlightTask task, Device device, SimState st) {
        TelemetryDto dto = new TelemetryDto();
        dto.setDroneId(device.getId());
        dto.setDroneCode(device.getCode());
        dto.setModel(device.getModel());
        dto.setStatus("FLYING");
        dto.setTaskId(task.getId());
        dto.setTaskName(task.getName());
        dto.setPilotName(task.getPilot() == null ? null : task.getPilot().getName());
        dto.setLng(round(st.lng, 6));
        dto.setLat(round(st.lat, 6));
        dto.setAltitude(round(st.altitude, 1));
        dto.setSpeed(round(st.speed, 1));
        dto.setHeading(round(st.heading, 1));
        dto.setBattery(round(st.battery, 1));
        dto.setSatellites(st.satellites);
        dto.setTrack(new ArrayList<>(st.track));
        return dto;
    }

    /** 解析航线 JSON:[{"lng":..,"lat":..,"alt":..}, ...] */
    private List<double[]> parseRoute(String json) {
        List<double[]> result = new ArrayList<>();
        if (json == null || json.isBlank()) {
            return result;
        }
        try {
            List<Map<String, Object>> raw = objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class));
            for (Map<String, Object> p : raw) {
                result.add(new double[]{
                        ((Number) p.get("lng")).doubleValue(),
                        ((Number) p.get("lat")).doubleValue(),
                        p.get("alt") == null ? 0 : ((Number) p.get("alt")).doubleValue()
                });
            }
        } catch (Exception e) {
            log.warn("Route parse failed: {}", e.getMessage());
        }
        return result;
    }

    /** 无航线时:以归航点为中心生成默认矩形航线 */
    private List<double[]> defaultRoute(Double lng, Double lat) {
        List<double[]> route = new ArrayList<>();
        if (lng == null || lat == null) {
            return route;
        }
        double dLng = 0.004, dLat = 0.003;
        route.add(new double[]{lng, lat, 120});
        route.add(new double[]{lng + dLng, lat, 120});
        route.add(new double[]{lng + dLng, lat + dLat, 120});
        route.add(new double[]{lng, lat + dLat, 120});
        route.add(new double[]{lng, lat, 120});
        return route;
    }

    private static double round(double v, int scale) {
        double f = Math.pow(10, scale);
        return Math.round(v * f) / f;
    }

    /** 单任务模拟状态 */
    static class SimState {
        long taskId;
        List<double[]> route = new ArrayList<>();
        int progress;          // 当前段索引
        int persistTicks;      // 入库节流计数
        double segDone;        // 当前段已飞米数
        double lng, lat = 0;
        double altitude = 120;
        double speed = 10;
        double heading;
        double battery = 100;
        int satellites = 18;
        List<Map<String, Double>> track = new ArrayList<>();
    }
}
