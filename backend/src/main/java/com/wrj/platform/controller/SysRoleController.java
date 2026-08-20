package com.wrj.platform.controller;

import com.wrj.platform.common.ApiResponse;
import com.wrj.platform.common.OpLog;
import com.wrj.platform.entity.SysRole;
import com.wrj.platform.repository.SysRoleRepository;
import com.wrj.platform.repository.SysUserRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 角色管理 */
@RestController
@RequestMapping("/api/roles")
public class SysRoleController {

    private final SysRoleRepository roleRepository;
    private final SysUserRepository userRepository;

    public SysRoleController(SysRoleRepository roleRepository, SysUserRepository userRepository) {
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
    }

    @GetMapping
    public ApiResponse<List<SysRole>> list() {
        return ApiResponse.ok(roleRepository.findAll());
    }

    @PostMapping
    @OpLog(module = "角色管理", action = "新增")
    public ApiResponse<SysRole> create(@RequestBody SysRole body) {
        if (body.getName() == null || body.getName().isBlank()) {
            throw new IllegalArgumentException("角色名称不能为空");
        }
        if (body.getCode() == null || body.getCode().isBlank()) {
            throw new IllegalArgumentException("角色编码不能为空");
        }
        if (roleRepository.findByCode(body.getCode()).isPresent()) {
            throw new IllegalArgumentException("角色编码已存在: " + body.getCode());
        }
        if (body.getMenuIdsJson() == null || body.getMenuIdsJson().isBlank()) {
            body.setMenuIdsJson("[]");
        }
        return ApiResponse.ok(roleRepository.save(body));
    }

    @PutMapping("/{id}")
    @OpLog(module = "角色管理", action = "修改")
    public ApiResponse<SysRole> update(@PathVariable Long id, @RequestBody SysRole body) {
        SysRole role = roleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("角色不存在: " + id));
        if (body.getName() != null) role.setName(body.getName());
        if (body.getRemark() != null) role.setRemark(body.getRemark());
        if (body.getMenuIdsJson() != null) role.setMenuIdsJson(body.getMenuIdsJson());
        if (body.getEnabled() != null) role.setEnabled(body.getEnabled());
        return ApiResponse.ok(roleRepository.save(role));
    }

    @DeleteMapping("/{id}")
    @OpLog(module = "角色管理", action = "删除")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        SysRole role = roleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("角色不存在: " + id));
        if ("ADMIN".equals(role.getCode())) {
            throw new IllegalArgumentException("内置管理员角色不允许删除");
        }
        long bound = userRepository.findAll().stream()
                .filter(u -> u.getRole() != null && u.getRole().getId().equals(id))
                .count();
        if (bound > 0) {
            throw new IllegalArgumentException("仍有 " + bound + " 个用户使用该角色,先调整再删除");
        }
        roleRepository.delete(role);
        return ApiResponse.ok();
    }
}
