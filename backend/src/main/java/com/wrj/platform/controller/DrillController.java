package com.wrj.platform.controller;

import com.wrj.platform.common.ApiResponse;
import com.wrj.platform.common.OpLog;
import com.wrj.platform.entity.DrillRun;
import com.wrj.platform.service.DrillService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 攻防演练:布防/开始/增派/速度/自动守候/人工处置/记录 */
@RestController
@RequestMapping("/api/drill")
public class DrillController {

    private final DrillService drillService;

    public DrillController(DrillService drillService) {
        this.drillService = drillService;
    }

    /** 当前演练状态(未开始返回 phase=IDLE) */
    @GetMapping("/state")
    public ApiResponse<Map<String, Object>> state() {
        return ApiResponse.ok(drillService.state());
    }

    @OpLog(module = "攻防演练", action = "开始演练")
    @PostMapping("/start")
    public ApiResponse<Map<String, Object>> start(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(drillService.start(body));
    }

    @OpLog(module = "攻防演练", action = "中止演练")
    @PostMapping("/stop")
    public ApiResponse<Map<String, Object>> stop() {
        return ApiResponse.ok(drillService.stop());
    }

    @OpLog(module = "攻防演练", action = "重置演练")
    @PostMapping("/reset")
    public ApiResponse<Map<String, Object>> reset() {
        return ApiResponse.ok(drillService.reset());
    }

    /** 增派敌机波次:count 数量(1-8),kind SCOUT/FAST(可选) */
    @OpLog(module = "攻防演练", action = "增派敌机")
    @PostMapping("/wave")
    public ApiResponse<Map<String, Object>> wave(@RequestBody Map<String, Object> body) {
        int count = body.get("count") instanceof Number n ? n.intValue() : 2;
        String kind = body.get("kind") instanceof String s && !s.isBlank() ? s : null;
        return ApiResponse.ok(drillService.wave(count, kind));
    }

    @PostMapping("/autoguard")
    public ApiResponse<Map<String, Object>> autoguard(@RequestBody Map<String, Object> body) {
        boolean on = Boolean.TRUE.equals(body.get("on"));
        return ApiResponse.ok(drillService.setAutoguard(on));
    }

    @PostMapping("/speed")
    public ApiResponse<Map<String, Object>> speed(@RequestBody Map<String, Object> body) {
        int s = body.get("speed") instanceof Number n ? n.intValue() : 1;
        return ApiResponse.ok(drillService.setSpeed(s));
    }

    /** 人工反制处置(压制/激光/网捕,动作由装备类型决定) */
    @OpLog(module = "攻防演练", action = "反制处置")
    @PostMapping("/engage")
    public ApiResponse<Map<String, Object>> engage(@RequestBody Map<String, Object> body) {
        long deviceId = body.get("deviceId") instanceof Number n ? n.longValue() : -1;
        String enemyId = String.valueOf(body.get("enemyId"));
        return ApiResponse.ok(drillService.engage(deviceId, enemyId));
    }

    /** 最近 20 次演练记录(汇总评分/处置明细) */
    @GetMapping("/runs")
    public ApiResponse<List<DrillRun>> runs() {
        return ApiResponse.ok(drillService.runs());
    }
}
