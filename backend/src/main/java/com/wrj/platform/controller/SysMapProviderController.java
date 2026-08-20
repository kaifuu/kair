package com.wrj.platform.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wrj.platform.common.ApiResponse;
import com.wrj.platform.common.OpLog;
import com.wrj.platform.entity.SysMapProvider;
import com.wrj.platform.repository.SysMapProviderRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 底图厂商配置管理:
 * 新增/编辑弹窗维护厂商凭证(AK/KEY/密钥)、自定义瓦片配置与默认底图,替代旧的固定四键值表单。
 */
@RestController
@RequestMapping("/api/map-providers")
public class SysMapProviderController {

    /** CUSTOM 瓦片模板须含 {z}/{x}/{y} */
    private static final Pattern TILE_URL = Pattern.compile("\\{z}.*\\{x}.*\\{y}|\\{x}.*\\{y}.*\\{z}");

    private final SysMapProviderRepository repository;
    private final ObjectMapper objectMapper;

    public SysMapProviderController(SysMapProviderRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public ApiResponse<List<SysMapProvider>> list() {
        return ApiResponse.ok(repository.findAllByOrderBySortAscIdAsc());
    }

    @PostMapping
    @OpLog(module = "地图管理", action = "新增厂商配置")
    public ApiResponse<SysMapProvider> create(@RequestBody SysMapProvider body) {
        validate(body, true);
        if (body.getEnabled() == null) body.setEnabled(true);
        if (body.getIsDefault() == null) body.setIsDefault(false);
        SysMapProvider saved = repository.save(body);
        if (Boolean.TRUE.equals(saved.getIsDefault())) {
            setDefaultInternal(saved.getId());
        }
        return ApiResponse.ok(repository.findById(saved.getId()).orElse(saved));
    }

    @PutMapping("/{id}")
    @OpLog(module = "地图管理", action = "修改厂商配置")
    public ApiResponse<SysMapProvider> update(@PathVariable Long id, @RequestBody SysMapProvider body) {
        SysMapProvider p = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("厂商配置不存在: " + id));
        if (body.getName() != null && !body.getName().isBlank()) p.setName(body.getName());
        if (body.getDescription() != null) p.setDescription(body.getDescription());
        if (body.getCredentialsJson() != null) {
            validateCredentials(body.getVendor() != null ? body.getVendor() : p.getVendor(), body.getCredentialsJson());
            p.setCredentialsJson(body.getCredentialsJson());
        }
        if (body.getTileUrl() != null) p.setTileUrl(body.getTileUrl());
        if (body.getEngine() != null) p.setEngine(body.getEngine());
        if (body.getGrad() != null) p.setGrad(body.getGrad());
        if (body.getEnabled() != null) p.setEnabled(body.getEnabled());
        if (body.getSort() != null) p.setSort(body.getSort());
        return ApiResponse.ok(repository.save(p));
    }

    /** 设为平台默认底图(全平台唯一) */
    @PutMapping("/{id}/default")
    @OpLog(module = "地图管理", action = "设置默认底图")
    @Transactional
    public ApiResponse<Void> setDefault(@PathVariable Long id) {
        setDefaultInternal(id);
        return ApiResponse.ok();
    }

    @DeleteMapping("/{id}")
    @OpLog(module = "地图管理", action = "删除厂商配置")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        SysMapProvider p = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("厂商配置不存在: " + id));
        if (Boolean.TRUE.equals(p.getIsDefault())) {
            throw new IllegalArgumentException("默认底图不可删除,请先把其他配置设为默认");
        }
        if (List.of("baidu", "amap", "tdt").contains(p.getCode())) {
            throw new IllegalArgumentException("内置厂商配置不可删除,可停用或清空其密钥");
        }
        repository.delete(p);
        return ApiResponse.ok();
    }

    private void setDefaultInternal(Long id) {
        SysMapProvider target = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("厂商配置不存在: " + id));
        if (!Boolean.TRUE.equals(target.getEnabled())) {
            throw new IllegalArgumentException("停用状态的配置不能设为默认");
        }
        repository.findAll().forEach(p -> {
            boolean hit = p.getId().equals(id);
            if (!Boolean.valueOf(hit).equals(p.getIsDefault())) {
                p.setIsDefault(hit);
                repository.save(p);
            }
        });
    }

    private void validate(SysMapProvider body, boolean creating) {
        if (creating) {
            if (body.getCode() == null || body.getCode().isBlank()) {
                throw new IllegalArgumentException("提供商标识(code)不能为空");
            }
            if (!body.getCode().matches("[a-zA-Z0-9_-]{2,32}")) {
                throw new IllegalArgumentException("code 仅允许字母/数字/下划线/中划线,2-32 位");
            }
            if (repository.findByCode(body.getCode()).isPresent()) {
                throw new IllegalArgumentException("提供商标识已存在: " + body.getCode());
            }
            if (body.getName() == null || body.getName().isBlank()) {
                throw new IllegalArgumentException("配置名称不能为空");
            }
            String vendor = body.getVendor() == null ? SysMapProvider.VENDOR_CUSTOM : body.getVendor();
            body.setVendor(vendor);
            if (SysMapProvider.VENDOR_CUSTOM.equals(vendor)) {
                if (body.getTileUrl() == null || !TILE_URL.matcher(body.getTileUrl()).find()) {
                    throw new IllegalArgumentException("自定义瓦片地址须包含 {z}/{x}/{y} 模板占位");
                }
                if (body.getEngine() == null || !List.of("tdt", "amap", "baidu").contains(body.getEngine())) {
                    throw new IllegalArgumentException("自定义配置须选择渲染引擎(tdt/amap/baidu)");
                }
            }
        }
        String vendor = body.getVendor();
        if (vendor != null && body.getCredentialsJson() != null) {
            validateCredentials(vendor, body.getCredentialsJson());
        }
    }

    /** 凭证 JSON 结构校验(值为空允许,保存后前端按「未配置」提示) */
    @SuppressWarnings("unchecked")
    private void validateCredentials(String vendor, String json) {
        Map<String, Object> creds;
        try {
            creds = objectMapper.readValue(json == null || json.isBlank() ? "{}" : json, Map.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("凭证 JSON 解析失败: " + e.getMessage());
        }
        switch (vendor == null ? SysMapProvider.VENDOR_CUSTOM : vendor) {
            case SysMapProvider.VENDOR_BAIDU -> checkKeys(creds, "ak", "百度");
            case SysMapProvider.VENDOR_AMAP -> checkKeys(creds, "key", "高德");
            case SysMapProvider.VENDOR_TDT -> checkKeys(creds, "tk", "天地图");
            default -> checkKeys(creds, "key", "自定义");
        }
    }

    private void checkKeys(Map<String, Object> creds, String required, String label) {
        if (!creds.containsKey(required)) {
            throw new IllegalArgumentException(label + "凭证须包含字段: " + required);
        }
    }
}
