package com.wrj.platform.dto;

import java.util.ArrayList;
import java.util.List;

/** 组织树节点 DTO */
public class OrgDto {

    private Long id;
    private String name;
    private Long parentId;
    private String orgCode;
    private Integer sort;
    private Boolean enabled;
    private List<OrgDto> children = new ArrayList<>();

    public OrgDto() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }

    public String getOrgCode() { return orgCode; }
    public void setOrgCode(String orgCode) { this.orgCode = orgCode; }

    public Integer getSort() { return sort; }
    public void setSort(Integer sort) { this.sort = sort; }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }

    public List<OrgDto> getChildren() { return children; }
    public void setChildren(List<OrgDto> children) { this.children = children; }
}
