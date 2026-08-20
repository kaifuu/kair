package com.wrj.platform.controller;

import com.wrj.platform.common.ApiResponse;
import com.wrj.platform.common.OpLog;
import com.wrj.platform.dto.OrgDto;
import com.wrj.platform.entity.SysOrg;
import com.wrj.platform.repository.SysOrgRepository;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 组织管理(树形) */
@RestController
@RequestMapping("/api/orgs")
public class SysOrgController {

    private final SysOrgRepository orgRepository;

    public SysOrgController(SysOrgRepository orgRepository) {
        this.orgRepository = orgRepository;
    }

    /** 返回组织树 */
    @GetMapping
    public ApiResponse<List<OrgDto>> tree() {
        List<SysOrg> all = orgRepository.findAll().stream()
                .sorted(Comparator.comparing(o -> o.getSort() == null ? 0 : o.getSort()))
                .toList();
        Map<Long, OrgDto> dtoMap = new LinkedHashMap<>();
        for (SysOrg org : all) {
            dtoMap.put(org.getId(), toDto(org));
        }
        List<OrgDto> roots = new ArrayList<>();
        for (SysOrg org : all) {
            OrgDto dto = dtoMap.get(org.getId());
            OrgDto parent = org.getParentId() == null ? null : dtoMap.get(org.getParentId());
            if (parent != null) {
                parent.getChildren().add(dto);
            } else {
                roots.add(dto);
            }
        }
        return ApiResponse.ok(roots);
    }

    @PostMapping
    @OpLog(module = "组织管理", action = "新增")
    public ApiResponse<SysOrg> create(@RequestBody SysOrg body) {
        if (body.getName() == null || body.getName().isBlank()) {
            throw new IllegalArgumentException("组织名称不能为空");
        }
        if (body.getParentId() != null && !orgRepository.existsById(body.getParentId())) {
            throw new IllegalArgumentException("上级组织不存在");
        }
        return ApiResponse.ok(orgRepository.save(body));
    }

    @PutMapping("/{id}")
    @OpLog(module = "组织管理", action = "修改")
    public ApiResponse<SysOrg> update(@PathVariable Long id, @RequestBody SysOrg body) {
        SysOrg org = orgRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("组织不存在: " + id));
        if (body.getName() != null) org.setName(body.getName());
        if (body.getOrgCode() != null) org.setOrgCode(body.getOrgCode());
        if (body.getSort() != null) org.setSort(body.getSort());
        if (body.getEnabled() != null) org.setEnabled(body.getEnabled());
        if (body.getParentId() != null) {
            if (body.getParentId().equals(id)) {
                throw new IllegalArgumentException("上级组织不能是自己");
            }
            org.setParentId(body.getParentId());
        }
        return ApiResponse.ok(orgRepository.save(org));
    }

    @DeleteMapping("/{id}")
    @OpLog(module = "组织管理", action = "删除")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        if (!orgRepository.existsById(id)) {
            throw new IllegalArgumentException("组织不存在: " + id);
        }
        long children = orgRepository.findAll().stream()
                .filter(o -> id.equals(o.getParentId())).count();
        if (children > 0) {
            throw new IllegalArgumentException("存在下级组织,先删除子组织");
        }
        orgRepository.deleteById(id);
        return ApiResponse.ok();
    }

    private static OrgDto toDto(SysOrg org) {
        OrgDto dto = new OrgDto();
        dto.setId(org.getId());
        dto.setName(org.getName());
        dto.setParentId(org.getParentId());
        dto.setOrgCode(org.getOrgCode());
        dto.setSort(org.getSort());
        dto.setEnabled(org.getEnabled());
        return dto;
    }
}
