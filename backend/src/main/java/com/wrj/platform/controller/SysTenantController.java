package com.wrj.platform.controller;

import com.wrj.platform.common.ApiResponse;
import com.wrj.platform.common.OpLog;
import com.wrj.platform.entity.SysTenant;
import com.wrj.platform.repository.SysTenantRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 租户管理 */
@RestController
@RequestMapping("/api/tenants")
public class SysTenantController {

    private final SysTenantRepository tenantRepository;

    public SysTenantController(SysTenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    @GetMapping
    public ApiResponse<List<SysTenant>> list() {
        return ApiResponse.ok(tenantRepository.findAll());
    }

    @PostMapping
    @OpLog(module = "租户管理", action = "新增")
    public ApiResponse<SysTenant> create(@RequestBody SysTenant body) {
        if (body.getName() == null || body.getName().isBlank()) {
            throw new IllegalArgumentException("租户名称不能为空");
        }
        if (body.getCode() == null || body.getCode().isBlank()) {
            throw new IllegalArgumentException("租户编码不能为空");
        }
        if (tenantRepository.existsByCode(body.getCode())) {
            throw new IllegalArgumentException("租户编码已存在: " + body.getCode());
        }
        return ApiResponse.ok(tenantRepository.save(body));
    }

    @PutMapping("/{id}")
    @OpLog(module = "租户管理", action = "修改")
    public ApiResponse<SysTenant> update(@PathVariable Long id, @RequestBody SysTenant body) {
        SysTenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("租户不存在: " + id));
        if (body.getName() != null) tenant.setName(body.getName());
        if (body.getRemark() != null) tenant.setRemark(body.getRemark());
        if (body.getEnabled() != null) tenant.setEnabled(body.getEnabled());
        return ApiResponse.ok(tenantRepository.save(tenant));
    }

    @DeleteMapping("/{id}")
    @OpLog(module = "租户管理", action = "删除")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        if (!tenantRepository.existsById(id)) {
            throw new IllegalArgumentException("租户不存在: " + id);
        }
        tenantRepository.deleteById(id);
        return ApiResponse.ok();
    }
}
