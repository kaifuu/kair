package com.wrj.platform.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** 菜单:一级扁平结构,group 决定归属「业务菜单/系统管理」分组,前端按 group+sort 渲染 */
@Entity
@Table(name = "sys_menu")
public class SysMenu {

    public enum Group { BIZ, SYS }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String name;

    @Column(length = 64)
    private String path;

    /** Element Plus 图标名(已全局注册,前端 component :is 渲染) */
    @Column(length = 32)
    private String icon;

    @Enumerated(EnumType.STRING)
    @Column(name = "menu_group")   // group 为 H2 保留字
    private Group group = Group.BIZ;

    private Integer sort = 0;

    private Boolean enabled = true;

    private LocalDateTime createdAt = LocalDateTime.now();

    public SysMenu() {
    }

    public SysMenu(String name, String path, String icon, Group group, int sort) {
        this.name = name;
        this.path = path;
        this.icon = icon;
        this.group = group;
        this.sort = sort;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }

    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }

    public Group getGroup() { return group; }
    public void setGroup(Group group) { this.group = group; }

    public Integer getSort() { return sort; }
    public void setSort(Integer sort) { this.sort = sort; }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
