package com.wrj.platform.dto;

/** 菜单下发 DTO */
public class MenuDto {

    private Long id;
    private String name;
    private String path;
    private String icon;
    private String group;   // BIZ | SYS
    private Integer sort;

    public MenuDto() {
    }

    public MenuDto(Long id, String name, String path, String icon, String group, Integer sort) {
        this.id = id;
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

    public String getGroup() { return group; }
    public void setGroup(String group) { this.group = group; }

    public Integer getSort() { return sort; }
    public void setSort(Integer sort) { this.sort = sort; }
}
