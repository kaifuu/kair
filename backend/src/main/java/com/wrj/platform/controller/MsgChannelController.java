package com.wrj.platform.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wrj.platform.common.ApiResponse;
import com.wrj.platform.common.OpLog;
import com.wrj.platform.entity.MsgChannel;
import com.wrj.platform.repository.MsgChannelRepository;
import com.wrj.platform.service.MessageDispatchService;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 消息通道配置:密钥掩码展示,回传掩码哨兵时保留旧值 */
@RestController
@RequestMapping("/api/msg/channels")
public class MsgChannelController {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    /** 敏感键:包含即掩码 */
    private static final Set<String> SENSITIVE = Set.of("mastersecret", "appmastersecret", "password", "secret");

    private final MsgChannelRepository repository;
    private final MessageDispatchService dispatchService;

    public MsgChannelController(MsgChannelRepository repository, MessageDispatchService dispatchService) {
        this.repository = repository;
        this.dispatchService = dispatchService;
    }

    @GetMapping
    public ApiResponse<List<MsgChannel>> list() {
        List<MsgChannel> all = repository.findAllByOrderBySortAscIdAsc();
        all.forEach(this::maskConfig);
        return ApiResponse.ok(all);
    }

    @PutMapping("/{id}")
    @OpLog(module = "消息管理", action = "修改通道配置")
    public ApiResponse<MsgChannel> update(@PathVariable Long id, @RequestBody MsgChannel body) {
        MsgChannel ch = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("通道不存在: " + id));
        if (body.getName() != null) ch.setName(body.getName());
        if (body.getRemark() != null) ch.setRemark(body.getRemark());
        if (body.getEnabled() != null) ch.setEnabled(body.getEnabled());
        if (body.getConfigJson() != null) ch.setConfigJson(mergeMasked(ch.getConfigJson(), body.getConfigJson()));
        ch.setUpdatedAt(LocalDateTime.now());
        MsgChannel saved = repository.save(ch);
        maskConfig(saved);
        return ApiResponse.ok(saved);
    }

    /** 通道实测:发送一条测试消息,返回发送结果 */
    @PostMapping("/{id}/test")
    @OpLog(module = "消息管理", action = "通道测试")
    public ApiResponse<Map<String, Object>> test(@PathVariable Long id) {
        MsgChannel ch = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("通道不存在: " + id));
        if (!Boolean.TRUE.equals(ch.getEnabled())) {
            return ApiResponse.ok(Map.of("status", "SKIP", "msg", "通道未启用,请先启用并保存"));
        }
        Map<String, Object> result = dispatchService.send(
                List.of(ch.getType()), "通道测试", "来自无人机监管平台的消息通道测试 " + LocalDateTime.now().withNano(0),
                null, "INFO", "admin");
        Object r = result.get(ch.getType());
        boolean ok = r instanceof Map<?, ?> m && "SUCCESS".equals(m.get("status"));
        return ApiResponse.ok(Map.of("status", ok ? "OK" : "FAIL", "detail", r == null ? Map.of() : r));
    }

    // ---------- 掩码工具 ----------

    private void maskConfig(MsgChannel ch) {
        try {
            JsonNode node = MAPPER.readTree(ch.getConfigJson() == null ? "{}" : ch.getConfigJson());
            maskNode((ObjectNode) node);
            ch.setConfigJson(MAPPER.writeValueAsString(node));
        } catch (Exception ignored) {
        }
    }

    private void maskNode(ObjectNode node) {
        node.fieldNames().forEachRemaining(k -> {
            JsonNode v = node.path(k);
            if (v.isObject()) {
                maskNode((ObjectNode) v);
                return;
            }
            String s = v.asText("");
            if (SENSITIVE.contains(k.toLowerCase()) && !s.isBlank()) {
                node.put(k, MessageDispatchService.MASK);
            }
        });
    }

    /** 保存合并:回传值为掩码哨兵的键保留旧值 */
    private String mergeMasked(String oldJson, String newJson) {
        try {
            ObjectNode oldNode = (ObjectNode) MAPPER.readTree(oldJson == null ? "{}" : oldJson);
            ObjectNode newNode = (ObjectNode) MAPPER.readTree(newJson);
            newNode.fieldNames().forEachRemaining(k -> {
                if (MessageDispatchService.MASK.equals(newNode.path(k).asText()) && oldNode.has(k)) {
                    newNode.set(k, oldNode.path(k));
                }
            });
            return MAPPER.writeValueAsString(newNode);
        } catch (Exception e) {
            return newJson;
        }
    }
}
