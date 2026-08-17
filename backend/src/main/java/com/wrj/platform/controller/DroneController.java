package com.wrj.platform.controller;

import com.wrj.platform.common.ApiResponse;
import com.wrj.platform.entity.Drone;
import com.wrj.platform.repository.DroneRepository;
import com.wrj.platform.repository.PilotRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/drones")
public class DroneController {

    private final DroneRepository droneRepository;
    private final PilotRepository pilotRepository;

    public DroneController(DroneRepository droneRepository, PilotRepository pilotRepository) {
        this.droneRepository = droneRepository;
        this.pilotRepository = pilotRepository;
    }

    @GetMapping
    public ApiResponse<List<Drone>> list(@RequestParam(required = false) String keyword,
                                         @RequestParam(required = false) String status) {
        List<Drone> all = droneRepository.findAll();
        return ApiResponse.ok(all.stream()
                .filter(d -> keyword == null || keyword.isBlank()
                        || d.getCode().contains(keyword)
                        || (d.getModel() != null && d.getModel().contains(keyword)))
                .filter(d -> status == null || status.isBlank()
                        || d.getStatus().name().equals(status))
                .toList());
    }

    @GetMapping("/{id}")
    public ApiResponse<Drone> get(@PathVariable Long id) {
        return ApiResponse.ok(droneRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("无人机不存在: " + id)));
    }

    @PostMapping
    public ApiResponse<Drone> create(@RequestBody Drone body) {
        if (body.getCode() == null || body.getCode().isBlank()) {
            throw new IllegalArgumentException("机身编号不能为空");
        }
        if (droneRepository.existsByCode(body.getCode())) {
            throw new IllegalArgumentException("机身编号已存在: " + body.getCode());
        }
        if (body.getPilot() != null && body.getPilot().getId() != null) {
            body.setPilot(pilotRepository.findById(body.getPilot().getId())
                    .orElseThrow(() -> new IllegalArgumentException("飞手不存在")));
        } else {
            body.setPilot(null);
        }
        if (body.getStatus() == null) {
            body.setStatus(Drone.Status.IDLE);
        }
        return ApiResponse.ok(droneRepository.save(body));
    }

    @PutMapping("/{id}")
    public ApiResponse<Drone> update(@PathVariable Long id, @RequestBody Drone body) {
        Drone drone = droneRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("无人机不存在: " + id));
        if (body.getCode() != null) drone.setCode(body.getCode());
        if (body.getModel() != null) drone.setModel(body.getModel());
        if (body.getManufacturer() != null) drone.setManufacturer(body.getManufacturer());
        if (body.getCategory() != null) drone.setCategory(body.getCategory());
        if (body.getStatus() != null) drone.setStatus(body.getStatus());
        if (body.getHomeLng() != null) drone.setHomeLng(body.getHomeLng());
        if (body.getHomeLat() != null) drone.setHomeLat(body.getHomeLat());
        if (body.getMaxAltitude() != null) drone.setMaxAltitude(body.getMaxAltitude());
        if (body.getMaxEndurance() != null) drone.setMaxEndurance(body.getMaxEndurance());
        if (body.getPilot() != null && body.getPilot().getId() != null) {
            drone.setPilot(pilotRepository.findById(body.getPilot().getId()).orElse(null));
        } else if (body.getPilot() == null) {
            drone.setPilot(null);
        }
        return ApiResponse.ok(droneRepository.save(drone));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        if (droneRepository.existsById(id)) {
            droneRepository.deleteById(id);
        }
        return ApiResponse.ok();
    }

    @GetMapping("/stats")
    public ApiResponse<Map<String, Long>> stats() {
        List<Drone> all = droneRepository.findAll();
        long flying = all.stream().filter(d -> d.getStatus() == Drone.Status.FLYING).count();
        long idle = all.stream().filter(d -> d.getStatus() == Drone.Status.IDLE).count();
        long charging = all.stream().filter(d -> d.getStatus() == Drone.Status.CHARGING).count();
        long maintenance = all.stream().filter(d -> d.getStatus() == Drone.Status.MAINTENANCE).count();
        long offline = all.stream().filter(d -> d.getStatus() == Drone.Status.OFFLINE).count();
        return ApiResponse.ok(Map.of(
                "total", (long) all.size(),
                "flying", flying,
                "idle", idle,
                "charging", charging,
                "maintenance", maintenance,
                "offline", offline
        ));
    }
}
