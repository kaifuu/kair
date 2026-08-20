package com.wrj.platform.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wrj.platform.entity.LlmModel;
import com.wrj.platform.entity.LlmUsageLog;
import com.wrj.platform.repository.LlmModelRepository;
import com.wrj.platform.repository.LlmUsageLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 大模型调用:GLM / QWEN / DEEPSEEK / 本地 Ollama 均兼容 OpenAI Chat Completions 协议,
 * 统一走 {baseUrl}/chat/completions,并逐次记录 Token 用量。
 */
@Service
public class LlmService {

    private static final Logger log = LoggerFactory.getLogger(LlmService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    /** 用量日志全局保留条数 */
    private static final long USAGE_KEEP = 2000;
    private static final int TRIM_EVERY = 100;

    private final LlmModelRepository modelRepository;
    private final LlmUsageLogRepository usageRepository;
    private final AtomicLong insertCounter = new AtomicLong();

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    public LlmService(LlmModelRepository modelRepository, LlmUsageLogRepository usageRepository) {
        this.modelRepository = modelRepository;
        this.usageRepository = usageRepository;
    }

    /**
     * 对话调用。messages:[{role, content}],modelId 为空时用默认/唯一启用模型。
     * 返回 {content, model, promptTokens, completionTokens, totalTokens, durationMs}
     * 注意:不能加事务——失败时用量日志须独立提交,不能随异常回滚。
     */
    public Map<String, Object> chat(Long modelId, List<Map<String, String>> messages, String scene) {
        LlmModel model = resolveModel(modelId);
        ArrayNode msgs = MAPPER.createArrayNode();
        for (Map<String, String> m : messages) {
            ObjectNode one = msgs.addObject();
            one.put("role", m.getOrDefault("role", "user"));
            one.put("content", m.getOrDefault("content", ""));
        }
        JsonNode message = callModel(model, msgs, null, scene);
        String content = message.path("content").asText("");
        return Map.of("content", content, "model", (Object) model.getName(),
                "modelCode", model.getModelCode(), "provider", model.getProvider());
    }

    /**
     * 底层调用:messages 为完整 OpenAI 格式(可含 tool 角色),tools 可空。
     * 返回 choices[0].message 原始节点(content 或 tool_calls 由调用方处理)。
     */
    public JsonNode call(Long modelId, ArrayNode messages, ArrayNode tools, String scene) {
        return callModel(resolveModel(modelId), messages, tools, scene);
    }

    /**
     * 流式底层调用:content 增量逐段回调 onDelta,tool_calls 增量在内部拼装。
     * 返回拼装完成的 message(content / tool_calls 结构与 call() 一致),供工具循环续跑。
     */
    public JsonNode callStream(Long modelId, ArrayNode messages, ArrayNode tools, String scene,
                               java.util.function.Consumer<String> onDelta) {
        return streamModel(resolveModel(modelId), messages, tools, scene, onDelta);
    }

    private JsonNode streamModel(LlmModel model, ArrayNode messages, ArrayNode tools, String scene,
                                 java.util.function.Consumer<String> onDelta) {
        long start = System.currentTimeMillis();
        try {
            JsonNode params = MAPPER.readTree(model.getParamsJson() == null ? "{}" : model.getParamsJson());
            ObjectNode body = MAPPER.createObjectNode();
            body.put("model", model.getModelCode());
            body.set("messages", messages);
            body.put("stream", true);
            body.putObject("stream_options").put("include_usage", true);
            if (tools != null && !tools.isEmpty()) {
                body.set("tools", tools);
                body.put("tool_choice", "auto");
            }
            if (params.has("temperature")) body.put("temperature", params.path("temperature").asDouble());
            if (params.has("maxTokens") && params.path("maxTokens").asInt() > 0) {
                body.put("max_tokens", params.path("maxTokens").asInt());
            }

            HttpRequest.Builder rb = HttpRequest.newBuilder(URI.create(trimSlash(model.getBaseUrl()) + "/chat/completions"))
                    // 流式生成整体耗时更长,超时下限放宽到 120s
                    .timeout(Duration.ofSeconds(Math.max(120, params.path("timeoutSeconds").asInt(60))))
                    .header("Content-Type", "application/json")
                    .header("Accept", "text/event-stream")
                    .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body), StandardCharsets.UTF_8));
            if (model.getApiKey() != null && !model.getApiKey().isBlank()) {
                rb.header("Authorization", "Bearer " + model.getApiKey());
            }
            HttpResponse<java.io.InputStream> resp = http.send(rb.build(),
                    HttpResponse.BodyHandlers.ofInputStream());
            if (resp.statusCode() / 100 != 2) {
                String err = new String(resp.body().readAllBytes(), StandardCharsets.UTF_8);
                throw new IllegalArgumentException("模型返回 " + resp.statusCode() + ": " + truncate(err));
            }

            StringBuilder content = new StringBuilder();
            Map<Integer, ObjectNode> toolAcc = new java.util.TreeMap<>();
            long pt = 0, ct = 0;
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(resp.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith(":") || !line.startsWith("data:")) continue;
                    String payload = line.substring(5).trim();
                    if ("[DONE]".equals(payload)) break;
                    JsonNode chunk = MAPPER.readTree(payload);
                    JsonNode usage = chunk.path("usage");
                    if (usage.isObject() && usage.has("total_tokens")) {
                        pt = usage.path("prompt_tokens").asLong(0);
                        ct = usage.path("completion_tokens").asLong(0);
                    }
                    JsonNode delta = chunk.path("choices").path(0).path("delta");
                    String piece = delta.path("content").asText("");
                    if (!piece.isEmpty()) {
                        content.append(piece);
                        if (onDelta != null) onDelta.accept(piece);
                    }
                    JsonNode tcs = delta.path("tool_calls");
                    if (tcs.isArray()) {
                        for (JsonNode tc : tcs) {
                            ObjectNode acc = toolAcc.computeIfAbsent(tc.path("index").asInt(0), k -> MAPPER.createObjectNode());
                            String id = tc.path("id").asText("");
                            if (!id.isEmpty() && acc.path("id").asText("").isEmpty()) acc.put("id", id);
                            String nm = tc.path("function").path("name").asText("");
                            if (!nm.isEmpty() && acc.path("name").asText("").isEmpty()) acc.put("name", nm);
                            acc.put("args", acc.path("args").asText("") + tc.path("function").path("arguments").asText(""));
                        }
                    }
                }
            }

            ObjectNode message = MAPPER.createObjectNode();
            message.put("role", "assistant");
            if (content.length() == 0 && !toolAcc.isEmpty()) {
                message.putNull("content");
            } else {
                message.put("content", content.toString());
            }
            if (!toolAcc.isEmpty()) {
                ArrayNode calls = MAPPER.createArrayNode();
                for (ObjectNode acc : toolAcc.values()) {
                    ObjectNode c = calls.addObject();
                    c.put("id", acc.path("id").asText(""));
                    c.put("type", "function");
                    ObjectNode f = c.putObject("function");
                    f.put("name", acc.path("name").asText(""));
                    f.put("arguments", acc.path("args").asText().isBlank() ? "{}" : acc.path("args").asText());
                }
                message.set("tool_calls", calls);
            }
            saveUsage(model, scene, pt, ct, pt + ct, System.currentTimeMillis() - start, "SUCCESS", null);
            return message;
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            saveUsage(model, scene == null ? "CHAT" : scene, 0, 0, 0,
                    System.currentTimeMillis() - start, "FAIL", msg);
            throw new IllegalArgumentException("调用失败: " + msg);
        }
    }

    private JsonNode callModel(LlmModel model, ArrayNode messages, ArrayNode tools, String scene) {
        long start = System.currentTimeMillis();
        try {
            JsonNode params = MAPPER.readTree(model.getParamsJson() == null ? "{}" : model.getParamsJson());
            ObjectNode body = MAPPER.createObjectNode();
            body.put("model", model.getModelCode());
            body.set("messages", messages);
            if (tools != null && !tools.isEmpty()) {
                body.set("tools", tools);
                body.put("tool_choice", "auto");
            }
            if (params.has("temperature")) body.put("temperature", params.path("temperature").asDouble());
            if (params.has("maxTokens") && params.path("maxTokens").asInt() > 0) {
                body.put("max_tokens", params.path("maxTokens").asInt());
            }

            HttpRequest.Builder rb = HttpRequest.newBuilder(URI.create(trimSlash(model.getBaseUrl()) + "/chat/completions"))
                    .timeout(Duration.ofSeconds(params.path("timeoutSeconds").asInt(60)))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body), StandardCharsets.UTF_8));
            if (model.getApiKey() != null && !model.getApiKey().isBlank()) {
                rb.header("Authorization", "Bearer " + model.getApiKey());
            }
            HttpResponse<String> resp = http.send(rb.build(), HttpResponse.BodyHandlers.ofString());
            long cost = System.currentTimeMillis() - start;
            if (resp.statusCode() / 100 != 2) {
                throw new IllegalArgumentException("模型返回 " + resp.statusCode() + ": " + truncate(resp.body()));
            }
            JsonNode root = MAPPER.readTree(resp.body());
            JsonNode message = root.path("choices").path(0).path("message");
            JsonNode usage = root.path("usage");
            long pt = usage.path("prompt_tokens").asLong(0);
            long ct = usage.path("completion_tokens").asLong(0);
            long tt = usage.path("total_tokens").asLong(pt + ct);
            saveUsage(model, scene, pt, ct, tt, cost, "SUCCESS", null);
            return message;
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            saveUsage(model, scene == null ? "CHAT" : scene, 0, 0, 0,
                    System.currentTimeMillis() - start, "FAIL", msg);
            throw new IllegalArgumentException("调用失败: " + msg);
        }
    }

    private LlmModel resolveModel(Long modelId) {
        if (modelId != null) {
            return modelRepository.findById(modelId)
                    .orElseThrow(() -> new IllegalArgumentException("模型不存在: " + modelId));
        }
        return modelRepository.findFirstByIsDefaultTrueAndEnabledTrue()
                .or(() -> modelRepository.findFirstByEnabledTrueOrderByUpdatedAtDesc())
                .orElseThrow(() -> new IllegalArgumentException("无可用模型,请先在模型配置中启用"));
    }

    private void saveUsage(LlmModel model, String scene, long pt, long ct, long tt,
                           long costMs, String status, String error) {
        try {
            LlmUsageLog u = new LlmUsageLog();
            u.setModelId(model.getId());
            u.setModelName(model.getName());
            u.setProvider(model.getProvider());
            u.setScene(scene == null || scene.isBlank() ? "CHAT" : scene);
            u.setPromptTokens(pt);
            u.setCompletionTokens(ct);
            u.setTotalTokens(tt);
            u.setDurationMs(costMs);
            u.setStatus(status);
            u.setError(error);
            usageRepository.save(u);
            if (insertCounter.incrementAndGet() % TRIM_EVERY == 0) {
                trimUsage();
            }
        } catch (Exception e) {
            log.warn("Save llm usage failed: {}", e.getMessage());
        }
    }

    /** 用量裁剪:仅保留最近 USAGE_KEEP 条 */
    private void trimUsage() {
        long total = usageRepository.count();
        if (total > USAGE_KEEP) {
            usageRepository.deleteAllById(
                    usageRepository.findIdsAsc(PageRequest.of(0, (int) (total - USAGE_KEEP))));
        }
    }

    // ---------- 统计视图 ----------

    @Transactional(readOnly = true)
    public Map<String, Object> overview() {
        Object[] o = usageRepository.overview().get(0);
        long todayTokens = 0;
        for (Object[] row : usageRepository.dailyStats(LocalDateTime.now().toLocalDate().atStartOfDay())) {
            if (String.valueOf(row[0]).equals(LocalDateTime.now().toLocalDate().toString())) {
                todayTokens = toLong(row[1]) + toLong(row[2]);
            }
        }
        return Map.of(
                "calls", toLong(o[0]),
                "totalTokens", toLong(o[1]),
                "failCalls", toLong(o[2]),
                "avgDurationMs", toLong(o[3]),
                "todayTokens", todayTokens
        );
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> dailyStats(int days) {
        LocalDateTime since = LocalDateTime.now().minusDays(Math.max(1, days) - 1).toLocalDate().atStartOfDay();
        return usageRepository.dailyStats(since).stream()
                .map(r -> Map.of("date", (Object) String.valueOf(r[0]),
                        "promptTokens", toLong(r[1]), "completionTokens", toLong(r[2]), "calls", toLong(r[3])))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> modelStats(int days) {
        LocalDateTime since = LocalDateTime.now().minusDays(Math.max(1, days) - 1).toLocalDate().atStartOfDay();
        return usageRepository.modelStats(since).stream()
                .map(r -> Map.of(
                        "modelId", r[0] == null ? 0L : (Object) toLong(r[0]),
                        "modelName", (Object) String.valueOf(r[1]),
                        "provider", (Object) String.valueOf(r[2]),
                        "calls", toLong(r[3]),
                        "totalTokens", toLong(r[4]),
                        "failCalls", toLong(r[5]),
                        "avgDurationMs", toLong(r[6])))
                .toList();
    }

    private static long toLong(Object o) {
        return o == null ? 0 : ((Number) o).longValue();
    }

    private static String trimSlash(String s) {
        return s == null ? "" : s.replaceAll("/+$", "");
    }

    private static String truncate(String s) {
        if (s == null) return "";
        return s.length() > 400 ? s.substring(0, 400) : s;
    }
}
