package com.wrj.platform.controller;

import com.wrj.platform.common.ApiResponse;
import com.wrj.platform.common.OpLog;
import com.wrj.platform.entity.SysConfig;
import com.wrj.platform.repository.SysConfigRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 地图密钥集中配置:百度 AK / 高德 Key+安全密钥 / 天地图 tk。
 * 页面保存 → sys_config(全浏览器生效);未保存时回落 application.yml 的 map-keys 缺省值。
 */
@RestController
@RequestMapping("/api/map-keys")
public class SysMapKeyController {

    private static final String K_BAIDU = "map.baiduAk";
    private static final String K_AMAP = "map.amapKey";
    private static final String K_AMAP_SEC = "map.amapSec";
    private static final String K_TDT = "map.tdtKey";

    private final SysConfigRepository configRepository;
    private final String defBaidu;
    private final String defAmap;
    private final String defAmapSec;
    private final String defTdt;

    public SysMapKeyController(SysConfigRepository configRepository,
                               @Value("${map-keys.baidu-ak:}") String defBaidu,
                               @Value("${map-keys.amap-key:}") String defAmap,
                               @Value("${map-keys.amap-sec:}") String defAmapSec,
                               @Value("${map-keys.tdt-key:}") String defTdt) {
        this.configRepository = configRepository;
        this.defBaidu = defBaidu;
        this.defAmap = defAmap;
        this.defAmapSec = defAmapSec;
        this.defTdt = defTdt;
    }

    @GetMapping
    public ApiResponse<Map<String, String>> get() {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("baiduAk", value(K_BAIDU, defBaidu));
        data.put("amapKey", value(K_AMAP, defAmap));
        data.put("amapSec", value(K_AMAP_SEC, defAmapSec));
        data.put("tdtKey", value(K_TDT, defTdt));
        return ApiResponse.ok(data);
    }

    /** 保存密钥:字段值为空白即清除该键(回落 yml/.env);null 表示未提交不改动 */
    @PutMapping
    @OpLog(module = "地图管理", action = "保存密钥")
    public ApiResponse<Map<String, String>> save(@RequestBody Map<String, String> body) {
        upsert(K_BAIDU, body.get("baiduAk"));
        upsert(K_AMAP, body.get("amapKey"));
        upsert(K_AMAP_SEC, body.get("amapSec"));
        upsert(K_TDT, body.get("tdtKey"));
        return get();
    }

    private void upsert(String key, String v) {
        if (v == null) {
            return;
        }
        String val = v.trim();
        Optional<SysConfig> row = configRepository.findByCfgKey(key);
        if (val.isEmpty()) {
            row.ifPresent(configRepository::delete);
            return;
        }
        SysConfig cfg = row.orElseGet(() -> new SysConfig(key, val));
        cfg.setCfgValue(val);
        cfg.setUpdatedAt(LocalDateTime.now());
        configRepository.save(cfg);
    }

    private String value(String key, String def) {
        return configRepository.findByCfgKey(key).map(SysConfig::getCfgValue).orElse(def);
    }
}
