package com.wrj.platform.controller;

import com.wrj.platform.common.ApiResponse;
import com.wrj.platform.entity.GeoFence;
import com.wrj.platform.repository.GeoFenceRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/fences")
public class GeoFenceController {

    private final GeoFenceRepository fenceRepository;

    public GeoFenceController(GeoFenceRepository fenceRepository) {
        this.fenceRepository = fenceRepository;
    }

    @GetMapping
    public ApiResponse<List<GeoFence>> list() {
        return ApiResponse.ok(fenceRepository.findAll());
    }

    @PostMapping
    public ApiResponse<GeoFence> create(@RequestBody GeoFence body) {
        if (body.getName() == null || body.getName().isBlank()) {
            throw new IllegalArgumentException("围栏名称不能为空");
        }
        if (body.getType() == null) body.setType(GeoFence.Type.NO_FLY);
        if (body.getShape() == null) body.setShape(GeoFence.Shape.POLYGON);
        if (body.getEnabled() == null) body.setEnabled(true);
        return ApiResponse.ok(fenceRepository.save(body));
    }

    @PutMapping("/{id}")
    public ApiResponse<GeoFence> update(@PathVariable Long id, @RequestBody GeoFence body) {
        GeoFence fence = fenceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("围栏不存在: " + id));
        if (body.getName() != null) fence.setName(body.getName());
        if (body.getType() != null) fence.setType(body.getType());
        if (body.getShape() != null) fence.setShape(body.getShape());
        if (body.getPointsJson() != null) fence.setPointsJson(body.getPointsJson());
        if (body.getRadius() != null) fence.setRadius(body.getRadius());
        if (body.getMaxAltitude() != null) fence.setMaxAltitude(body.getMaxAltitude());
        if (body.getEnabled() != null) fence.setEnabled(body.getEnabled());
        if (body.getRemark() != null) fence.setRemark(body.getRemark());
        return ApiResponse.ok(fenceRepository.save(fence));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        fenceRepository.deleteById(id);
        return ApiResponse.ok();
    }
}
