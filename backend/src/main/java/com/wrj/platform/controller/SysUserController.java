package com.wrj.platform.controller;

import com.wrj.platform.common.ApiResponse;
import com.wrj.platform.common.OpLog;
import com.wrj.platform.entity.SysRole;
import com.wrj.platform.entity.SysUser;
import com.wrj.platform.repository.SysRoleRepository;
import com.wrj.platform.repository.SysUserRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 人员管理 */
@RestController
@RequestMapping("/api/users")
public class SysUserController {

    public static final String DEFAULT_PASSWORD = "123456";
    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

    private final SysUserRepository userRepository;
    private final SysRoleRepository roleRepository;

    public SysUserController(SysUserRepository userRepository, SysRoleRepository roleRepository) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
    }

    @GetMapping
    public ApiResponse<List<SysUser>> list(@RequestParam(required = false) String keyword) {
        List<SysUser> all = userRepository.findAll();
        return ApiResponse.ok(all.stream()
                .filter(u -> keyword == null || keyword.isBlank()
                        || contains(u.getUsername(), keyword)
                        || contains(u.getNickname(), keyword)
                        || contains(u.getPhone(), keyword))
                .toList());
    }

    @PostMapping
    @OpLog(module = "人员管理", action = "新增")
    public ApiResponse<SysUser> create(@RequestBody SysUser body) {
        if (body.getUsername() == null || body.getUsername().isBlank()) {
            throw new IllegalArgumentException("用户名不能为空");
        }
        if (userRepository.existsByUsername(body.getUsername())) {
            throw new IllegalArgumentException("用户名已存在: " + body.getUsername());
        }
        if (body.getStatus() == null) {
            body.setStatus(SysUser.Status.ENABLED);
        }
        body.setPassword(ENCODER.encode(DEFAULT_PASSWORD));
        body.setRole(resolveRole(body));
        return ApiResponse.ok(userRepository.save(body));
    }

    @PutMapping("/{id}")
    @OpLog(module = "人员管理", action = "修改")
    public ApiResponse<SysUser> update(@PathVariable Long id, @RequestBody SysUser body) {
        SysUser user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在: " + id));
        if (body.getNickname() != null) user.setNickname(body.getNickname());
        if (body.getPhone() != null) user.setPhone(body.getPhone());
        if (body.getOrgId() != null) user.setOrgId(body.getOrgId());
        if (body.getTenantId() != null) user.setTenantId(body.getTenantId());
        if (body.getStatus() != null) user.setStatus(body.getStatus());
        if (body.getRole() != null && body.getRole().getId() != null) {
            user.setRole(resolveRole(body));
        }
        return ApiResponse.ok(userRepository.save(user));
    }

    @DeleteMapping("/{id}")
    @OpLog(module = "人员管理", action = "删除")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        SysUser user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在: " + id));
        if ("admin".equals(user.getUsername())) {
            throw new IllegalArgumentException("内置管理员不允许删除");
        }
        userRepository.delete(user);
        return ApiResponse.ok();
    }

    /** 重置密码为默认 123456 */
    @PostMapping("/{id}/reset-password")
    @OpLog(module = "人员管理", action = "重置密码")
    public ApiResponse<Void> resetPassword(@PathVariable Long id) {
        SysUser user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在: " + id));
        user.setPassword(ENCODER.encode(DEFAULT_PASSWORD));
        userRepository.save(user);
        return ApiResponse.ok();
    }

    private SysRole resolveRole(SysUser body) {
        if (body.getRole() != null && body.getRole().getId() != null) {
            return roleRepository.findById(body.getRole().getId())
                    .orElseThrow(() -> new IllegalArgumentException("角色不存在"));
        }
        return null;
    }

    private static boolean contains(String v, String kw) {
        return v != null && v.contains(kw);
    }
}
