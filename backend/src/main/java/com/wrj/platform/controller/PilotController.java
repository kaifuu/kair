package com.wrj.platform.controller;

import com.wrj.platform.common.ApiResponse;
import com.wrj.platform.entity.Pilot;
import com.wrj.platform.repository.PilotRepository;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/pilots")
public class PilotController {

    private final PilotRepository pilotRepository;

    public PilotController(PilotRepository pilotRepository) {
        this.pilotRepository = pilotRepository;
    }

    @GetMapping
    public ApiResponse<List<Pilot>> list(@RequestParam(required = false) String keyword) {
        List<Pilot> all = pilotRepository.findAll();
        if (keyword == null || keyword.isBlank()) {
            return ApiResponse.ok(all);
        }
        return ApiResponse.ok(all.stream()
                .filter(p -> p.getName().contains(keyword)
                        || p.getLicenseNo().contains(keyword)
                        || (p.getOrg() != null && p.getOrg().contains(keyword)))
                .toList());
    }

    @PostMapping
    public ApiResponse<Pilot> create(@RequestBody Pilot body) {
        if (body.getLicenseNo() == null || body.getLicenseNo().isBlank()) {
            throw new IllegalArgumentException("执照编号不能为空");
        }
        if (pilotRepository.existsByLicenseNo(body.getLicenseNo())) {
            throw new IllegalArgumentException("执照编号已存在");
        }
        // 执照到期自动标记
        if (body.getLicenseExpiry() != null && body.getLicenseExpiry().isBefore(LocalDate.now())) {
            body.setStatus(Pilot.Status.EXPIRED);
        }
        return ApiResponse.ok(pilotRepository.save(body));
    }

    @PutMapping("/{id}")
    public ApiResponse<Pilot> update(@PathVariable Long id, @RequestBody Pilot body) {
        Pilot pilot = pilotRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("飞手不存在: " + id));
        if (body.getName() != null) pilot.setName(body.getName());
        if (body.getPhone() != null) pilot.setPhone(body.getPhone());
        if (body.getOrg() != null) pilot.setOrg(body.getOrg());
        if (body.getLicenseType() != null) pilot.setLicenseType(body.getLicenseType());
        if (body.getLicenseGrade() != null) pilot.setLicenseGrade(body.getLicenseGrade());
        if (body.getLicenseIssue() != null) pilot.setLicenseIssue(body.getLicenseIssue());
        if (body.getLicenseExpiry() != null) pilot.setLicenseExpiry(body.getLicenseExpiry());
        if (body.getStatus() != null) pilot.setStatus(body.getStatus());
        return ApiResponse.ok(pilotRepository.save(pilot));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        pilotRepository.deleteById(id);
        return ApiResponse.ok();
    }
}
