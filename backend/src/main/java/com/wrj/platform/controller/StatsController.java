package com.wrj.platform.controller;

import com.wrj.platform.common.ApiResponse;
import com.wrj.platform.entity.Alert;
import com.wrj.platform.entity.Drone;
import com.wrj.platform.entity.FlightTask;
import com.wrj.platform.entity.Pilot;
import com.wrj.platform.repository.AlertRepository;
import com.wrj.platform.repository.DroneRepository;
import com.wrj.platform.repository.FlightTaskRepository;
import com.wrj.platform.repository.PilotRepository;
import com.wrj.platform.service.AlertService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/stats")
public class StatsController {

    private final DroneRepository droneRepository;
    private final PilotRepository pilotRepository;
    private final FlightTaskRepository taskRepository;
    private final AlertRepository alertRepository;
    private final AlertService alertService;

    public StatsController(DroneRepository droneRepository,
                           PilotRepository pilotRepository,
                           FlightTaskRepository taskRepository,
                           AlertRepository alertRepository,
                           AlertService alertService) {
        this.droneRepository = droneRepository;
        this.pilotRepository = pilotRepository;
        this.taskRepository = taskRepository;
        this.alertRepository = alertRepository;
        this.alertService = alertService;
    }

    /** 概览统计(大屏顶部卡片) */
    @GetMapping("/overview")
    public ApiResponse<Map<String, Object>> overview() {
        List<Drone> drones = droneRepository.findAll();
        List<Pilot> pilots = pilotRepository.findAll();
        List<FlightTask> tasks = taskRepository.findAll();

        long flying = drones.stream().filter(d -> d.getStatus() == Drone.Status.FLYING).count();
        long flyingTasks = tasks.stream().filter(t -> t.getStatus() == FlightTask.Status.FLYING).count();
        long pendingTasks = tasks.stream().filter(t -> t.getStatus() == FlightTask.Status.PENDING).count();

        Map<String, Object> data = new HashMap<>();
        data.put("droneTotal", drones.size());
        data.put("flyingNow", flying);
        data.put("pilotTotal", pilots.size());
        data.put("taskTotal", tasks.size());
        data.put("taskFlying", flyingTasks);
        data.put("taskPending", pendingTasks);
        data.put("alertUnhandled", alertService.unhandledCount());
        data.put("fenceTotal", 0);
        return ApiResponse.ok(data);
    }

    /** 近 7 日飞行趋势(按任务数) */
    @GetMapping("/trend")
    public ApiResponse<List<Map<String, Object>>> trend() {
        List<FlightTask> tasks = taskRepository.findAll();
        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate day = LocalDate.now().minusDays(i);
            long count = tasks.stream()
                    .filter(t -> t.getStartTime() != null && t.getStartTime().toLocalDate().equals(day))
                    .count();
            Map<String, Object> row = new HashMap<>();
            row.put("date", day.toString());
            row.put("flights", count);
            result.add(row);
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

    /** 机型分布 */
    @GetMapping("/drone-model")
    public ApiResponse<List<Map<String, Object>>> droneModel() {
        List<Drone> drones = droneRepository.findAll();
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Drone d : drones) {
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
