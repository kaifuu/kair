package com.wrj.platform.controller;

import com.wrj.platform.common.ApiResponse;
import com.wrj.platform.entity.Alert;
import com.wrj.platform.entity.Device;
import com.wrj.platform.entity.FlightTask;
import com.wrj.platform.entity.Pilot;
import com.wrj.platform.repository.AlertRepository;
import com.wrj.platform.repository.DeviceRepository;
import com.wrj.platform.repository.GeoFenceRepository;
import com.wrj.platform.repository.FlightTaskRepository;
import com.wrj.platform.repository.PilotRepository;
import com.wrj.platform.service.AlertService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/stats")
public class StatsController {

    private final DeviceRepository deviceRepository;
    private final PilotRepository pilotRepository;
    private final FlightTaskRepository taskRepository;
    private final AlertRepository alertRepository;
    private final AlertService alertService;
    private final GeoFenceRepository fenceRepository;

    public StatsController(DeviceRepository deviceRepository,
                           PilotRepository pilotRepository,
                           FlightTaskRepository taskRepository,
                           AlertRepository alertRepository,
                           AlertService alertService,
                           GeoFenceRepository fenceRepository) {
        this.deviceRepository = deviceRepository;
        this.pilotRepository = pilotRepository;
        this.taskRepository = taskRepository;
        this.alertRepository = alertRepository;
        this.alertService = alertService;
        this.fenceRepository = fenceRepository;
    }

    /** 概览统计(大屏顶部卡片) */
    @GetMapping("/overview")
    public ApiResponse<Map<String, Object>> overview() {
        List<Device> devices = deviceRepository.findAll();
        List<Device> drones = devices.stream()
                .filter(d -> d.getCategory() == Device.Category.DRONE).toList();
        List<Pilot> pilots = pilotRepository.findAll();
        List<FlightTask> tasks = taskRepository.findAll();

        long flying = drones.stream().filter(d -> d.getStatus() == Device.Status.FLYING).count();
        long flyingTasks = tasks.stream().filter(t -> t.getStatus() == FlightTask.Status.FLYING).count();
        long pendingTasks = tasks.stream().filter(t -> t.getStatus() == FlightTask.Status.PENDING).count();
        long online = devices.stream().filter(d -> d.getStatus() == Device.Status.ONLINE
                || d.getStatus() == Device.Status.FLYING).count();

        Map<String, Object> data = new HashMap<>();
        data.put("droneTotal", drones.size());
        data.put("deviceTotal", devices.size());
        data.put("deviceOnline", online);
        data.put("flyingNow", flying);
        data.put("pilotTotal", pilots.size());
        data.put("taskTotal", tasks.size());
        data.put("taskFlying", flyingTasks);
        data.put("taskPending", pendingTasks);
        data.put("alertUnhandled", alertService.unhandledCount());
        data.put("fenceTotal", fenceRepository.count());
        return ApiResponse.ok(data);
    }

    /** 近 N 日飞行趋势(任务数 + 告警数 + 计划飞行时长) */
    @GetMapping("/trend")
    public ApiResponse<List<Map<String, Object>>> trend(@RequestParam(defaultValue = "7") int days) {
        int window = Math.min(90, Math.max(1, days));
        List<FlightTask> tasks = taskRepository.findAll();
        List<Alert> alerts = alertRepository.findAll();
        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = window - 1; i >= 0; i--) {
            LocalDate day = LocalDate.now().minusDays(i);
            List<FlightTask> dayTasks = tasks.stream()
                    .filter(t -> t.getStartTime() != null && t.getStartTime().toLocalDate().equals(day))
                    .toList();
            double hours = dayTasks.stream()
                    .mapToDouble(t -> t.getPlannedDuration() == null ? 0 : t.getPlannedDuration())
                    .sum() / 60.0;
            long alertCount = alerts.stream()
                    .filter(a -> a.getCreatedAt() != null && a.getCreatedAt().toLocalDate().equals(day))
                    .count();
            Map<String, Object> row = new HashMap<>();
            row.put("date", day.toString());
            row.put("flights", dayTasks.size());
            row.put("alerts", alertCount);
            row.put("hours", Math.round(hours * 10) / 10.0);
            result.add(row);
        }
        return ApiResponse.ok(result);
    }

    /** 任务状态分布(执行状态 × 审批状态) */
    @GetMapping("/task-status")
    public ApiResponse<List<Map<String, Object>>> taskStatus() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (FlightTask t : taskRepository.findAll()) {
            String key = switch (t.getStatus()) {
                case PENDING -> t.getApproval() == FlightTask.Approval.REJECTED ? "已驳回"
                        : t.getApproval() == FlightTask.Approval.PENDING ? "待审批" : "待执行";
                case FLYING -> "执行中";
                case COMPLETED -> "已完成";
                case ABORTED -> "已中止";
            };
            counts.merge(key, 1L, Long::sum);
        }
        return ApiResponse.ok(counts.entrySet().stream()
                .map(e -> Map.<String, Object>of("name", e.getKey(), "value", e.getValue()))
                .toList());
    }

    /** 设备构成(按分类) */
    @GetMapping("/device-category")
    public ApiResponse<List<Map<String, Object>>> deviceCategory() {
        Map<String, String> label = Map.of(
                "DRONE", "无人机", "DOCK", "机场机巢", "CAMERA", "摄像机",
                "WEATHER", "气象站", "ADSB", "ADS-B", "GATEWAY", "网关", "SENSOR", "传感器");
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Device d : deviceRepository.findAll()) {
            counts.merge(d.getCategory() == null ? "其他"
                    : label.getOrDefault(d.getCategory().name(), d.getCategory().name()), 1L, Long::sum);
        }
        return ApiResponse.ok(counts.entrySet().stream()
                .map(e -> Map.<String, Object>of("name", e.getKey(), "value", e.getValue()))
                .toList());
    }

    /** 24 小时飞行活跃度(任务开始时刻分布) */
    @GetMapping("/hourly-flights")
    public ApiResponse<List<Map<String, Object>>> hourlyFlights() {
        int[] bins = new int[24];
        for (FlightTask t : taskRepository.findAll()) {
            if (t.getStartTime() != null) {
                bins[t.getStartTime().getHour()]++;
            }
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (int h = 0; h < 24; h++) {
            result.add(Map.of("hour", String.format("%02d", h), "count", bins[h]));
        }
        return ApiResponse.ok(result);
    }

    /** 告警类型分布 */
    @GetMapping("/alert-type")
    public ApiResponse<List<Map<String, Object>>> alertType() {
        List<Alert> alerts = alertRepository.findAll();
        Map<Alert.Type, Long> counts = new EnumMap<>(Alert.Type.class);
        for (Alert a : alerts) {
            if (a.getType() != null) {
                counts.merge(a.getType(), 1L, Long::sum);
            }
        }
        return ApiResponse.ok(counts.entrySet().stream()
                .map(e -> Map.<String, Object>of("type", e.getKey().name(), "count", e.getValue()))
                .toList());
    }

    /** 机型分布(仅无人机) */
    @GetMapping("/drone-model")
    public ApiResponse<List<Map<String, Object>>> droneModel() {
        List<Device> drones = deviceRepository.findByCategory(Device.Category.DRONE);
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Device d : drones) {
            counts.merge(d.getModel() == null ? "未知" : d.getModel(), 1L, Long::sum);
        }
        return ApiResponse.ok(counts.entrySet().stream()
                .map(e -> Map.<String, Object>of("name", e.getKey(), "value", e.getValue()))
                .toList());
    }

    /** 飞手飞行时长排行 */
    @GetMapping("/pilot-rank")
    public ApiResponse<List<Map<String, Object>>> pilotRank() {
        List<Pilot> pilots = pilotRepository.findAll();
        return ApiResponse.ok(pilots.stream()
                .sorted(Comparator.comparing(Pilot::getTotalFlightHours,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(10)
                .map(p -> Map.<String, Object>of(
                        "name", p.getName(),
                        "hours", p.getTotalFlightHours() == null ? 0 : p.getTotalFlightHours(),
                        "flights", p.getTotalFlights() == null ? 0 : p.getTotalFlights()
                ))
                .toList());
    }
}
