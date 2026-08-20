package com.wrj.platform.controller;

import com.wrj.platform.common.ApiResponse;
import com.wrj.platform.common.OpLog;
import com.wrj.platform.dto.AlertDto;
import com.wrj.platform.entity.Alert;
import com.wrj.platform.service.AlertService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    private static final Map<Alert.Level, String> LEVEL_TEXT = Map.of(
            Alert.Level.INFO, "提示", Alert.Level.WARNING, "警告", Alert.Level.CRITICAL, "紧急");
    private static final Map<Alert.Type, String> TYPE_TEXT = Map.ofEntries(
            Map.entry(Alert.Type.GEOFENCE_BREACH, "禁飞区闯入"), Map.entry(Alert.Type.ALTITUDE_EXCEED, "超限高"),
            Map.entry(Alert.Type.LOW_BATTERY, "低电量"), Map.entry(Alert.Type.SIGNAL_LOST, "失联"),
            Map.entry(Alert.Type.NO_LICENSE, "黑飞嫌疑"), Map.entry(Alert.Type.TASK_OVERDUE, "超时未归"),
            Map.entry(Alert.Type.PREDICTED_BREACH, "预测闯入禁飞区"), Map.entry(Alert.Type.CONFLICT_ALERT, "多机接近冲突"),
            Map.entry(Alert.Type.BATTERY_ANOMALY, "电量骤降"), Map.entry(Alert.Type.ALTITUDE_JUMP, "高度突变"),
            Map.entry(Alert.Type.SIGNAL_WEAK, "卫星信号弱"));

    private final AlertService alertService;

    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "false") boolean unhandled) {
        PageRequest pageable = PageRequest.of(Math.max(0, page - 1), size);
        Page<Alert> result = alertService.page(unhandled, pageable);
        List<AlertDto> items = result.getContent().stream().map(AlertService::toDto).toList();
        return ApiResponse.ok(Map.of(
                "items", items,
                "total", result.getTotalElements(),
                "page", page,
                "size", size
        ));
    }

    @GetMapping("/latest")
    public ApiResponse<List<AlertDto>> latest() {
        return ApiResponse.ok(alertService.latest(50).stream().map(AlertService::toDto).toList());
    }

    @PostMapping("/{id}/handle")
    public ApiResponse<AlertDto> handle(@PathVariable Long id,
                                        @RequestParam(required = false, defaultValue = "admin") String handler) {
        return ApiResponse.ok(AlertService.toDto(alertService.handle(id, handler)));
    }

    /** 导出 CSV(带 BOM,Excel 可直接打开中文),遵循当前过滤条件 */
    @GetMapping("/export")
    @OpLog(module = "告警中心", action = "导出告警")
    public ResponseEntity<ByteArrayResource> export(@RequestParam(defaultValue = "false") boolean unhandled) {
        List<Alert> alerts = alertService.listForExport(unhandled);
        StringBuilder sb = new StringBuilder();
        sb.append('﻿') // UTF-8 BOM
                .append("ID,级别,类型,无人机,任务,告警内容,经度,纬度,海拔(米),状态,处理人,处理时间,发生时间\r\n");
        for (Alert a : alerts) {
            sb.append(csv(String.valueOf(a.getId()))).append(',')
                    .append(csv(LEVEL_TEXT.getOrDefault(a.getLevel(), "-"))).append(',')
                    .append(csv(TYPE_TEXT.getOrDefault(a.getType(), "-"))).append(',')
                    .append(csv(a.getDevice() == null ? "-" : a.getDevice().getCode())).append(',')
                    .append(csv(a.getTask() == null ? "-" : a.getTask().getName())).append(',')
                    .append(csv(a.getMessage())).append(',')
                    .append(csv(a.getLng() == null ? "-" : String.valueOf(a.getLng()))).append(',')
                    .append(csv(a.getLat() == null ? "-" : String.valueOf(a.getLat()))).append(',')
                    .append(csv(a.getAltitude() == null ? "-" : String.valueOf(a.getAltitude()))).append(',')
                    .append(Boolean.TRUE.equals(a.getHandled()) ? "已处理" : "未处理").append(',')
                    .append(csv(nullSafe(a.getHandler()))).append(',')
                    .append(csv(nullSafe(a.getHandleTime()))).append(',')
                    .append(csv(nullSafe(a.getCreatedAt()))).append("\r\n");
        }
        byte[] bytes = sb.toString().getBytes(StandardCharsets.UTF_8);
        String filename = "告警数据_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".csv";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType(MediaType.parseMediaType("text/csv"), StandardCharsets.UTF_8));
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build());
        return ResponseEntity.ok().headers(headers).body(new ByteArrayResource(bytes));
    }

    /** 批量删除 */
    @DeleteMapping
    @OpLog(module = "告警中心", action = "批量删除告警")
    public ApiResponse<Map<String, Object>> deleteBatch(@RequestBody List<Long> ids) {
        long n = alertService.deleteBatch(ids);
        return ApiResponse.ok(Map.of("deleted", n));
    }

    /** 一键清空:handledOnly=true 仅清已处理 */
    @DeleteMapping("/all")
    @OpLog(module = "告警中心", action = "清空告警")
    public ApiResponse<Map<String, Object>> clearAll(@RequestParam(defaultValue = "false") boolean handledOnly) {
        long n = alertService.clear(handledOnly);
        return ApiResponse.ok(Map.of("deleted", n));
    }

    private static String nullSafe(Object o) {
        return o == null ? "-" : String.valueOf(o).replace('T', ' ');
    }

    /** CSV 字段转义:含逗号/引号/换行时加引号,内部引号翻倍 */
    private static String csv(String v) {
        if (v == null) return "";
        if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
            return '"' + v.replace("\"", "\"\"") + '"';
        }
        return v;
    }
}
