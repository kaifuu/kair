package com.wrj.platform.controller;

import com.wrj.platform.common.ApiResponse;
import com.wrj.platform.common.OpLog;
import com.wrj.platform.dto.MenuDto;
import com.wrj.platform.entity.SysMenu;
import com.wrj.platform.entity.SysUser;
import com.wrj.platform.repository.SysMenuRepository;
import com.wrj.platform.repository.SysUserRepository;
import com.wrj.platform.service.MenuService;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.List;

/** 菜单管理 + 当前用户菜单下发 */
@RestController
@RequestMapping("/api/menus")
public class SysMenuController {

    private final SysMenuRepository menuRepository;
    private final SysUserRepository userRepository;
    private final MenuService menuService;

    public SysMenuController(SysMenuRepository menuRepository,
                             SysUserRepository userRepository,
                             MenuService menuService) {
        this.menuRepository = menuRepository;
        this.userRepository = userRepository;
        this.menuService = menuService;
    }

    /** 当前登录用户可见菜单(登录后/刷新后拉取) */
    @GetMapping("/mine")
    public ApiResponse<List<MenuDto>> mine(jakarta.servlet.http.HttpServletRequest request) {
        String username = (String) request.getAttribute("currentUser");
        SysUser user = userRepository.findByUsername(username == null ? "" : username)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        return ApiResponse.ok(menuService.mine(user));
    }

    @GetMapping
    public ApiResponse<List<SysMenu>> list() {
        return ApiResponse.ok(menuRepository.findAll().stream()
                .sorted(Comparator.comparing((SysMenu m) -> m.getGroup())
                        .thenComparing(m -> m.getSort() == null ? 0 : m.getSort()))
                .toList());
    }

    @PostMapping
    @OpLog(module = "菜单管理", action = "新增")
    public ApiResponse<SysMenu> create(@RequestBody SysMenu body) {
        if (body.getName() == null || body.getName().isBlank()) {
            throw new IllegalArgumentException("菜单名称不能为空");
        }
        if (body.getGroup() == null) {
            body.setGroup(SysMenu.Group.BIZ);
        }
        return ApiResponse.ok(menuRepository.save(body));
    }

    @PutMapping("/{id}")
    @OpLog(module = "菜单管理", action = "修改")
    public ApiResponse<SysMenu> update(@PathVariable Long id, @RequestBody SysMenu body) {
        SysMenu menu = menuRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("菜单不存在: " + id));
        if (body.getName() != null) menu.setName(body.getName());
        if (body.getPath() != null) menu.setPath(body.getPath());
        if (body.getIcon() != null) menu.setIcon(body.getIcon());
        if (body.getGroup() != null) menu.setGroup(body.getGroup());
        if (body.getSort() != null) menu.setSort(body.getSort());
        if (body.getEnabled() != null) menu.setEnabled(body.getEnabled());
        return ApiResponse.ok(menuRepository.save(menu));
    }

    @DeleteMapping("/{id}")
    @OpLog(module = "菜单管理", action = "删除")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        if (menuRepository.existsById(id)) {
            menuRepository.deleteById(id);
        }
        return ApiResponse.ok();
    }
}
