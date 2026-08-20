package com.wrj.platform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wrj.platform.dto.MenuDto;
import com.wrj.platform.entity.SysMenu;
import com.wrj.platform.entity.SysRole;
import com.wrj.platform.entity.SysUser;
import com.wrj.platform.repository.SysMenuRepository;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** 菜单下发:按用户角色过滤(ADMIN 全量),BIZ 组在前按 sort 排序 */
@Service
public class MenuService {

    private final SysMenuRepository menuRepository;
    private final ObjectMapper objectMapper;

    public MenuService(SysMenuRepository menuRepository, ObjectMapper objectMapper) {
        this.menuRepository = menuRepository;
        this.objectMapper = objectMapper;
    }

    public List<MenuDto> mine(SysUser user) {
        List<SysMenu> all = menuRepository.findByEnabledTrueOrderBySort();
        Set<Long> allowed = allowedMenuIds(user);
        if (allowed != null) {
            all = all.stream().filter(m -> allowed.contains(m.getId())).toList();
        }
        return all.stream()
                .sorted(Comparator.comparing((SysMenu m) -> m.getGroup())
                        .thenComparing(m -> m.getSort() == null ? 0 : m.getSort()))
                .map(m -> new MenuDto(m.getId(), m.getName(), m.getPath(), m.getIcon(),
                        m.getGroup() == null ? null : m.getGroup().name(), m.getSort()))
                .toList();
    }

    /** null 表示不限制(ADMIN);否则为授权菜单 id 集合 */
    private Set<Long> allowedMenuIds(SysUser user) {
        SysRole role = user.getRole();
        if (role == null || "ADMIN".equals(role.getCode())) {
            return null;
        }
        try {
            List<Long> ids = objectMapper.readValue(role.getMenuIdsJson() == null ? "[]" : role.getMenuIdsJson(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Long.class));
            return ids.stream().collect(Collectors.toSet());
        } catch (Exception e) {
            return Set.of();
        }
    }
}
