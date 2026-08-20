package com.wrj.platform.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wrj.platform.common.ApiResponse;
import com.wrj.platform.common.OpLog;
import com.wrj.platform.entity.LlmModel;
import com.wrj.platform.entity.LlmUsageLog;
import com.wrj.platform.repository.LlmModelRepository;
import com.wrj.platform.repository.LlmUsageLogRepository;
import com.wrj.platform.service.LlmService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 模型配置 + 对话调用 + Token 用量统计 */
@RestController
@RequestMapping("/api/llm")
public class LlmController {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final LlmModelRepository repository;
    private final LlmUsageLogRepository usageRepository;
    private final LlmService llmService;

    public LlmController(LlmModelRepository repository, LlmUsageLogRepository usageRepository,
                         LlmService llmService) {
        this.repository = repository;
        this.usageRepository = usageRepository;
        this.llmService = llmService;
    }

    // ---------- 模型 CRUD ----------

    @GetMapping("/models")
    public ApiResponse<List<LlmModel>> list() {
        List<LlmModel> all = repository.findAllByOrderByIsDefaultDescUpdatedAtDesc();
        all.forEach(this::maskKey);
        return ApiResponse.ok(all);
    }

    @PostMapping("/models")
    @OpLog(module = "模型配置", action = "新增模型")
    public ApiResponse<LlmModel> create(@RequestBody LlmModel body) {
        validate(body);
        body.setId(null);
        body.setIsDefault(false);
        body.setCreatedAt(LocalDateTime.now());
        body.setUpdatedAt(LocalDateTime.now());
        LlmModel saved = repository.save(body);
        maskKey(saved);
        return ApiResponse.ok(saved);
    }

    @PutMapping("/models/{id}")
    @OpLog(module = "模型配置", action = "修改模型")
    public ApiResponse<LlmModel> update(@PathVariable Long id, @RequestBody LlmModel body) {
        LlmModel m = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("模型不存在: " + id));
        validate(body);
        if (body.getName() != null) m.setName(body.getName());
        if (body.getProvider() != null) m.setProvider(body.getProvider());
        if (body.getBaseUrl() != null) m.setBaseUrl(body.getBaseUrl());
        if (body.getModelCode() != null) m.setModelCode(body.getModelCode());
        if (body.getParamsJson() != null) m.setParamsJson(body.getParamsJson());
        if (body.getRemark() != null) m.setRemark(body.getRemark());
        if (body.getEnabled() != null) m.setEnabled(body.getEnabled());
        // 掩码哨兵:保留旧 Key;显式传空串表示清除
        if (body.getApiKey() != null) {
            m.setApiKey("******".equals(body.getApiKey()) ? m.getApiKey() : body.getApiKey());
        }
        m.setUpdatedAt(LocalDateTime.now());
        LlmModel saved = repository.save(m);
        maskKey(saved);
        return ApiResponse.ok(saved);
    }

    @DeleteMapping("/models/{id}")
    @OpLog(module = "模型配置", action = "删除模型")
    @Transactional
    public ApiResponse<Void> delete(@PathVariable Long id) {
        LlmModel m = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("模型不存在: " + id));
        if (Boolean.TRUE.equals(m.getIsDefault())) throw new IllegalArgumentException("默认模型不可删除,请先切换默认");
        repository.delete(m);
        return ApiResponse.ok();
    }

    @PutMapping("/models/{id}/default")
    @OpLog(module = "模型配置", action = "设置默认模型")
    @Transactional
    public ApiResponse<Void> setDefault(@PathVariable Long id) {
        LlmModel m = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("模型不存在: " + id));
        if (!Boolean.TRUE.equals(m.getEnabled())) throw new IllegalArgumentException("请先启用该模型");
        repository.findAll().forEach(x -> {
            if (Boolean.TRUE.equals(x.getIsDefault()) && !x.getId().equals(id)) {
                x.setIsDefault(false);
                repository.save(x);
            }
        });
        m.setIsDefault(true);
        repository.save(m);
        return ApiResponse.ok();
    }

    /** 连通性测试:发一条固定提示词 */
    @PostMapping("/models/{id}/test")
    @OpLog(module = "模型配置", action = "模型连通测试")
    public ApiResponse<Map<String, Object>> test(@PathVariable Long id) {
        Map<String, Object> r = llmService.chat(id,
                List.of(Map.of("role", "user", "content", "连通性测试,请回复「收到」。")), "TEST");
        return ApiResponse.ok(r);
    }

    // ---------- 对话 ----------

    @PostMapping("/chat")
    public ApiResponse<Map<String, Object>> chat(@RequestBody Map<String, Object> body) {
        Long modelId = body.get("modelId") == null ? null : Long.valueOf(String.valueOf(body.get("modelId")));
        @SuppressWarnings("unchecked")
        List<Map<String, String>> messages = (List<Map<String, String>>) body.get("messages");
        if (messages == null || messages.isEmpty()) throw new IllegalArgumentException("消息内容不能为空");
        return ApiResponse.ok(llmService.chat(modelId, messages, "CHAT"));
    }

    // ---------- Token 统计 ----------

    @GetMapping("/stats/overview")
    public ApiResponse<Map<String, Object>> overview() {
        return ApiResponse.ok(llmService.overview());
    }

    @GetMapping("/stats/daily")
    public ApiResponse<List<Map<String, Object>>> daily(@RequestParam(defaultValue = "14") int days) {
        return ApiResponse.ok(llmService.dailyStats(days));
    }

    @GetMapping("/stats/models")
    public ApiResponse<List<Map<String, Object>>> byModel(@RequestParam(defaultValue = "30") int days) {
        return ApiResponse.ok(llmService.modelStats(days));
    }

    @GetMapping("/logs")
    public ApiResponse<Map<String, Object>> logs(@RequestParam(defaultValue = "1") int page,
                                                 @RequestParam(defaultValue = "10") int size,
                                                 @RequestParam(required = false) Long modelId) {
        PageRequest pageable = PageRequest.of(Math.max(0, page - 1), size);
        Page<LlmUsageLog> result = modelId == null
                ? usageRepository.findAllByOrderByCreatedAtDesc(pageable)
                : usageRepository.findByModelIdOrderByCreatedAtDesc(modelId, pageable);
        return ApiResponse.ok(Map.of("items", result.getContent(), "total", result.getTotalElements(),
                "page", page, "size", size));
    }

    // ---------- 工具 ----------

    private void validate(LlmModel body) {
        if (body.getName() == null || body.getName().isBlank()) throw new IllegalArgumentException("模型名称不能为空");
        if (body.getBaseUrl() == null || body.getBaseUrl().isBlank()) throw new IllegalArgumentException("接口地址不能为空");
        if (body.getModelCode() == null || body.getModelCode().isBlank()) throw new IllegalArgumentException("模型标识不能为空");
    }

    private void maskKey(LlmModel m) {
        try {
            JsonNode cfg = MAPPER.readTree(m.getParamsJson() == null ? "{}" : m.getParamsJson());
            m.setParamsJson(MAPPER.writeValueAsString(cfg));
        } catch (Exception ignored) {
        }
        if (m.getApiKey() != null && !m.getApiKey().isBlank()) {
            m.setApiKey("******");
        }
    }
}
