package com.wrj.platform.controller;

import com.wrj.platform.common.ApiResponse;
import com.wrj.platform.common.OpLog;
import com.wrj.platform.entity.Alert;
import com.wrj.platform.repository.AlertRepository;
import com.wrj.platform.service.AiAssistantService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** AI 助手入口:值班 Copilot / 告警研判 / 态势日报 */
@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AiAssistantService aiAssistant;
    private final AlertRepository alertRepository;

    public AiController(AiAssistantService aiAssistant, AlertRepository alertRepository) {
        this.aiAssistant = aiAssistant;
        this.alertRepository = alertRepository;
    }

    /** 值班 Copilot 对话:带平台数据工具调用 */
    @PostMapping("/copilot")
    public ApiResponse<Map<String, Object>> copilot(@RequestBody Map<String, Object> body) {
        Long modelId = body.get("modelId") == null ? null : Long.valueOf(String.valueOf(body.get("modelId")));
        @SuppressWarnings("unchecked")
        List<Map<String, String>> history = (List<Map<String, String>>) body.get("history");
        String question = String.valueOf(body.get("question") == null ? "" : body.get("question"));
        if (question.isBlank()) throw new IllegalArgumentException("问题内容不能为空");
        return ApiResponse.ok(aiAssistant.copilot(modelId, history, question));
    }

    /** 值班 Copilot 流式对话(SSE):delta 增量 / tool 工具开始 / done / error */
    @PostMapping(value = "/copilot/stream", produces = org.springframework.http.MediaType.TEXT_EVENT_STREAM_VALUE)
    public org.springframework.web.servlet.mvc.method.annotation.SseEmitter copilotStream(
            @RequestBody Map<String, Object> body) {
        Long modelId = body.get("modelId") == null ? null : Long.valueOf(String.valueOf(body.get("modelId")));
        @SuppressWarnings("unchecked")
        List<Map<String, String>> history = (List<Map<String, String>>) body.get("history");
        String question = String.valueOf(body.get("question") == null ? "" : body.get("question"));
        if (question.isBlank()) throw new IllegalArgumentException("问题内容不能为空");
        return aiAssistant.copilotStream(modelId, history, question);
    }

    /** 告警按需研判(同步返回,写回 aiAdvice) */
    @PostMapping("/alert/{id}/assess")
    @OpLog(module = "AI 助手", action = "告警智能研判")
    public ApiResponse<Map<String, Object>> assess(@PathVariable Long id) {
        Alert alert = alertRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("告警不存在: " + id));
        String advice = aiAssistant.assess(alert);
        String stored = alertRepository.findById(id).map(Alert::getAiAdvice).orElse(null);
        return ApiResponse.ok(Map.of("id", id, "aiAdvice", stored == null ? advice : stored));
    }

    /** 手动生成态势日报(近 24h),同时推送站内信 */
    @PostMapping("/report/generate")
    @OpLog(module = "AI 助手", action = "生成态势日报")
    public ApiResponse<Map<String, Object>> report(@RequestBody(required = false) Map<String, Object> body) {
        Long modelId = body == null || body.get("modelId") == null
                ? null : Long.valueOf(String.valueOf(body.get("modelId")));
        return ApiResponse.ok(aiAssistant.generateReport(modelId));
    }
}
