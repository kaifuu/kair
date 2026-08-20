package com.wrj.platform.controller;

import com.wrj.platform.common.ApiResponse;
import com.wrj.platform.entity.SysLog;
import com.wrj.platform.repository.SysLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 日志管理:操作/登录/设备 三类,分页查询 */
@RestController
@RequestMapping("/api/logs")
public class SysLogController {

    private final SysLogRepository logRepository;

    public SysLogController(SysLogRepository logRepository) {
        this.logRepository = logRepository;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> list(@RequestParam(required = false) String type,
                                                 @RequestParam(required = false) String keyword,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "20") int size) {
        SysLog.Type enumType = null;
        if (type != null && !type.isBlank()) {
            enumType = SysLog.Type.valueOf(type);
        }
        String kw = (keyword == null || keyword.isBlank()) ? null : keyword;
        Page<SysLog> result = logRepository.search(enumType, kw, PageRequest.of(page, size));

        Map<String, Object> data = new HashMap<>();
        data.put("items", result.getContent());
        data.put("total", result.getTotalElements());
        return ApiResponse.ok(data);
    }

    /** 各类日志条数(页签徽标) */
    @GetMapping("/count")
    public ApiResponse<Map<String, Long>> count() {
        Map<String, Long> data = new HashMap<>();
        for (SysLog.Type t : SysLog.Type.values()) {
            data.put(t.name(), logRepository.countByType(t));
        }
        return ApiResponse.ok(data);
    }
}
