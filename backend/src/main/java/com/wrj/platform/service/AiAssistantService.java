package com.wrj.platform.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wrj.platform.entity.Alert;
import com.wrj.platform.entity.Device;
import com.wrj.platform.entity.DeviceDataHistory;
import com.wrj.platform.entity.FlightTask;
import com.wrj.platform.entity.MsgChannel;
import com.wrj.platform.repository.AlertRepository;
import com.wrj.platform.repository.DeviceDataHistoryRepository;
import com.wrj.platform.repository.DeviceRepository;
import com.wrj.platform.repository.FlightTaskRepository;
import com.wrj.platform.repository.GeoFenceRepository;
import com.wrj.platform.repository.PilotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * AI 助手:监控值班 Copilot(工具调用)、告警智能研判、态势日报生成。
 * 全部异步/低频调用——LLM 不进实时环路,遥测毫秒级判断由 ThreatService 传统算法承担。
 */
@Service
public class AiAssistantService {

    private static final Logger log = LoggerFactory.getLogger(AiAssistantService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int TOOL_ROUNDS = 4;

    private final LlmService llmService;
    private final DeviceRepository deviceRepository;
    private final FlightTaskRepository taskRepository;
    private final AlertRepository alertRepository;
    private final GeoFenceRepository fenceRepository;
    private final PilotRepository pilotRepository;
    private final DeviceDataHistoryRepository historyRepository;
    private final MessageDispatchService messageService;

    @Value("${ai.enabled:true}")
    private boolean enabled;
    @Value("${ai.alert-assess:true}")
    private boolean alertAssessEnabled;

    private final ExecutorService executor = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "ai-assistant-" + SEQ.incrementAndGet());
        t.setDaemon(true);
        return t;
    });
    private static final AtomicInteger SEQ = new AtomicInteger();

    public AiAssistantService(LlmService llmService,
                              DeviceRepository deviceRepository,
                              FlightTaskRepository taskRepository,
                              AlertRepository alertRepository,
                              GeoFenceRepository fenceRepository,
                              PilotRepository pilotRepository,
                              DeviceDataHistoryRepository historyRepository,
                              MessageDispatchService messageService) {
        this.llmService = llmService;
        this.deviceRepository = deviceRepository;
        this.taskRepository = taskRepository;
        this.alertRepository = alertRepository;
        this.fenceRepository = fenceRepository;
        this.pilotRepository = pilotRepository;
        this.historyRepository = historyRepository;
        this.messageService = messageService;
    }

    // ==================== P0-1 值班 Copilot ====================

    /**
     * 值班助手对话:注入当前态势快照 + 平台数据工具,最多 TOOL_ROUNDS 轮工具调用。
     * history 为前端传来的近几轮 [{role, content}],question 为本轮问题。
     */
    public Map<String, Object> copilot(Long modelId, List<Map<String, String>> history, String question) {
        ArrayNode messages = copilotMessages(history, question);
        ArrayNode tools = buildTools();
        for (int round = 0; round < TOOL_ROUNDS; round++) {
            JsonNode reply = llmService.call(modelId, messages, tools, "COPILOT");
            JsonNode calls = reply.path("tool_calls");
            if (calls.isEmpty() || !calls.isArray()) {
                return Map.of("content", reply.path("content").asText(""),
                        "rounds", (Object) (round + 1));
            }
            appendToolRound(messages, reply);
        }
        // 轮次用尽仍无结论:直接收尾
        ObjectNode finalQ = MAPPER.createObjectNode();
        finalQ.put("role", "user");
        finalQ.put("content", "请基于已获取的信息直接给出最终结论,不要再调用工具。");
        messages.add(finalQ);
        JsonNode reply = llmService.call(modelId, messages, null, "COPILOT");
        return Map.of("content", reply.path("content").asText(""), "rounds", (Object) TOOL_ROUNDS);
    }

    /** 流式值班对话:SSE 事件 {t: delta|tool|done|error, v: 文本};工具轮之间持续推送增量。 */
    public SseEmitter copilotStream(Long modelId, List<Map<String, String>> history, String question) {
        SseEmitter emitter = new SseEmitter(180_000L);
        emitter.onError(e -> log.debug("Copilot stream error: {}", String.valueOf(e)));
        emitter.onTimeout(() -> log.debug("Copilot stream timeout"));
        executor.submit(() -> {
            try {
                ArrayNode messages = copilotMessages(history, question);
                ArrayNode tools = buildTools();
                for (int round = 0; round < TOOL_ROUNDS; round++) {
                    JsonNode reply = llmService.callStream(modelId, messages, tools, "COPILOT",
                            delta -> emit(emitter, "delta", delta));
                    JsonNode calls = reply.path("tool_calls");
                    if (calls.isEmpty() || !calls.isArray()) {
                        emit(emitter, "done", round + 1);
                        emitter.complete();
                        return;
                    }
                    for (JsonNode call : calls) {
                        emit(emitter, "tool", call.path("function").path("name").asText());
                    }
                    appendToolRound(messages, reply);
                }
                ObjectNode finalQ = MAPPER.createObjectNode();
                finalQ.put("role", "user");
                finalQ.put("content", "请基于已获取的信息直接给出最终结论,不要再调用工具。");
                messages.add(finalQ);
                llmService.callStream(modelId, messages, null, "COPILOT", delta -> emit(emitter, "delta", delta));
                emit(emitter, "done", TOOL_ROUNDS);
                emitter.complete();
            } catch (Exception e) {
                log.warn("Copilot stream failed: {}", e.getMessage());
                emit(emitter, "error", e.getMessage() == null ? "调用失败" : e.getMessage());
                emitter.complete();
            }
        });
        return emitter;
    }

    /** SSE 事件发送(连接已断等异常静默,由外层收尾) */
    private void emit(SseEmitter emitter, String t, Object v) {
        try {
            emitter.send(SseEmitter.event().data(Map.of("t", t, "v", v == null ? "" : String.valueOf(v))));
        } catch (Exception ignored) {
        }
    }

    /** 组装值班对话消息:系统提示(含态势快照)+ 近 8 轮历史 + 本轮问题 */
    private ArrayNode copilotMessages(List<Map<String, String>> history, String question) {
        ArrayNode messages = MAPPER.createArrayNode();
        ObjectNode sys = messages.addObject();
        sys.put("role", "system");
        sys.put("content", """
                你是无人机低空监管平台的值班 AI 助手,面向监管值班员和领导。回答规则:
                1) 优先用工具查询平台实时数据,不要凭空编造设备/告警/任务数据;
                2) 回答简洁专业,用中文,涉及风险时给出明确处置建议;
                3) 数据中没有的信息直接说明"当前数据中无此信息",不要臆测。
                当前态势快照(可能已滞后,精确数据请调工具):
                """ + snapshotJson());
        int kept = 0;
        if (history != null) {
            for (Map<String, String> m : history) {
                if (kept++ >= 8) break;
                String role = m.getOrDefault("role", "user");
                if ("user".equals(role) || "assistant".equals(role)) {
                    ObjectNode one = messages.addObject();
                    one.put("role", role);
                    one.put("content", m.getOrDefault("content", ""));
                }
            }
        }
        ObjectNode q = messages.addObject();
        q.put("role", "user");
        q.put("content", question == null ? "" : question);
        return messages;
    }

    /** 把 assistant 的 tool_calls 原样追加,再逐个回填工具结果 */
    private void appendToolRound(ArrayNode messages, JsonNode reply) {
        JsonNode calls = reply.path("tool_calls");
        ObjectNode assistantMsg = MAPPER.createObjectNode();
        assistantMsg.put("role", "assistant");
        assistantMsg.set("tool_calls", calls);
        if (reply.has("content") && !reply.path("content").isNull()
                && !reply.path("content").asText("").isEmpty()) {
            assistantMsg.set("content", reply.get("content"));
        }
        messages.add(assistantMsg);
        for (JsonNode call : calls) {
            String name = call.path("function").path("name").asText();
            String args = call.path("function").path("arguments").asText("{}");
            String result = execTool(name, args);
            ObjectNode toolMsg = messages.addObject();
            toolMsg.put("role", "tool");
            toolMsg.put("tool_call_id", call.path("id").asText());
            toolMsg.put("content", result);
        }
    }

    /** 工具定义(OpenAI function calling 格式) */
    private ArrayNode buildTools() {
        ArrayNode tools = MAPPER.createArrayNode();
        toolDef(tools.addObject(), "get_overview", "获取平台总览统计(设备/无人机/任务/告警/飞手/围栏数量)", null);
        toolDef(tools.addObject(), "list_flying", "获取当前在飞无人机列表及实时遥测(经纬度/高度/速度/电量)", null);
        toolDef(tools.addObject(), "list_alerts", "获取告警列表",
                Map.of("unhandledOnly", "仅看未处理,true/false", "limit", "条数,默认10"));
        toolDef(tools.addObject(), "list_tasks", "获取飞行任务列表",
                Map.of("status", "状态:PENDING/FLYING/COMPLETED/ABORTED"));
        toolDef(tools.addObject(), "get_device", "按设备编码查设备档案",
                Map.of("code", "设备编码,如 UAV-2024-0001"));
        toolDef(tools.addObject(), "list_fences", "获取电子围栏列表(含禁飞区/限飞区)", null);
        return tools;
    }

    private void toolDef(ObjectNode tool, String name, String desc, Map<String, String> params) {
        tool.put("type", "function");
        ObjectNode fn = tool.putObject("function");
        fn.put("name", name);
        fn.put("description", desc);
        ObjectNode props = fn.putObject("parameters").putObject("properties");
        if (params != null) {
            params.forEach((k, v) -> props.putObject(k).put("type", "string").put("description", v));
        }
    }

    /** 工具执行:直连仓储,不走 HTTP */
    private String execTool(String name, String argsJson) {
        try {
            JsonNode args = MAPPER.readTree(argsJson == null || argsJson.isBlank() ? "{}" : argsJson);
            return switch (name) {
                case "get_overview" -> MAPPER.writeValueAsString(overview());
                case "list_flying" -> MAPPER.writeValueAsString(flying());
                case "list_alerts" -> MAPPER.writeValueAsString(alerts(
                        args.path("unhandledOnly").asBoolean(false),
                        Math.min(20, args.path("limit").asInt(10))));
                case "list_tasks" -> MAPPER.writeValueAsString(tasks(args.path("status").asText(null)));
                case "get_device" -> MAPPER.writeValueAsString(device(args.path("code").asText()));
                case "list_fences" -> MAPPER.writeValueAsString(fences());
                default -> "{\"error\":\"未知工具: " + name + "\"}";
            };
        } catch (Exception e) {
            return "{\"error\":\"" + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()) + "\"}";
        }
    }

    // ==================== P0-2 告警智能研判 ====================

    /** 告警产生后异步研判:严重度复核 + 原因分析 + 处置建议,写回 aiAdvice */
    public void assessAsync(Long alertId) {
        if (!enabled || !alertAssessEnabled) return;
        executor.submit(() -> {
            try {
                Alert alert = alertRepository.findById(alertId).orElse(null);
                if (alert == null || alert.getAiAdvice() != null) return;
                assess(alert);
            } catch (Exception e) {
                log.warn("Alert assess failed ({}): {}", alertId, e.getMessage());
            }
        });
    }

    /** 同步研判(按需接口/测试用),返回生成的研判文本 */
    @Transactional
    public String assess(Alert alert) {
        // 懒加载字段在异步线程会话外不可用,先取出快照
        String deviceCode = alert.getDevice() == null ? "-" : alert.getDevice().getCode();
        String deviceName = alert.getDevice() == null ? "-" : alert.getDevice().getName();
        String taskName = alert.getTask() == null ? null : alert.getTask().getName();
        Device device = alert.getDevice() == null ? null : deviceRepository.findById(alert.getDevice().getId()).orElse(null);
        String recent = recentTelemetry(device);

        List<Map<String, String>> messages = List.of(
                Map.of("role", "system", "content", """
                        你是无人机监管专家。根据告警信息与设备近期遥测,输出简明研判。格式(纯文本,三行以内,不要 JSON):
                        严重度:低/中/高(一句话理由)
                        原因:最可能的原因分析
                        建议:给值班员的具体处置动作"""),
                Map.of("role", "user", "content",
                        "告警类型: " + alert.getType() + " / 级别: " + alert.getLevel()
                                + "\n设备: " + deviceCode + " " + deviceName
                                + (taskName == null ? "" : "\n关联任务: " + taskName)
                                + "\n告警内容: " + alert.getMessage()
                                + "\n位置: lng=" + alert.getLng() + ", lat=" + alert.getLat() + ", alt=" + alert.getAltitude()
                                + "\n设备近期遥测(最近5条): " + recent));
        Map<String, Object> r = llmService.chat(null, messages, "ALERT_ASSESS");
        String advice = String.valueOf(r.get("content")).trim();
        if (!advice.isBlank()) {
            Alert row = alertRepository.findById(alert.getId()).orElse(null);
            if (row != null && row.getAiAdvice() == null) {
                row.setAiAdvice(advice.length() > 2000 ? advice.substring(0, 2000) : advice);
                alertRepository.save(row);
            }
        }
        return advice;
    }

    private String recentTelemetry(Device device) {
        if (device == null) return "无";
        try {
            List<DeviceDataHistory> rows = historyRepository.findRecent(device.getId(),
                    LocalDateTime.now().minusMinutes(10),
                    org.springframework.data.domain.PageRequest.of(0, 5));
            StringBuilder sb = new StringBuilder("[");
            for (DeviceDataHistory h : rows) {
                sb.append(h.getFieldsJson()).append(";");
            }
            return sb.append("]").toString();
        } catch (Exception e) {
            return "无";
        }
    }

    // ==================== P0-3 态势日报 ====================

    /** 每日 07:36 自动生成昨日日报并发站内信(可配置关闭) */
    @Scheduled(cron = "${ai.report-cron:0 36 7 * * *}")
    public void dailyReport() {
        if (!enabled) return;
        executor.submit(() -> {
            try {
                generateReport(null);
            } catch (Exception e) {
                log.warn("Daily report failed: {}", e.getMessage());
            }
        });
    }

    /** 生成态势日报(默认近 24h),LLM 产出 markdown,站内信推送全体 */
    public Map<String, Object> generateReport(Long modelId) {
        Map<String, Object> data = reportData();
        List<Map<String, String>> messages = List.of(
                Map.of("role", "system", "content", """
                        你是低空监管平台的值班长,为局领导撰写运行日报。要求:
                        1) markdown 格式,先一句话总体态势,再分【飞行情况】【告警情况】【设备情况】【风险提示与建议】四节;
                        2) 用数据说话,数据来自下文 JSON,不要编造;篇幅 300 字以内;
                        3) 语气正式,面向政府领导汇报。"""),
                Map.of("role", "user", "content", "统计周期: 近24小时\n数据: " + data.get("json")));
        Map<String, Object> r = llmService.chat(modelId, messages, "REPORT");
        String content = String.valueOf(r.get("content")).trim();

        String title = "低空运行日报 · " + LocalDate.now();
        try {
            messageService.send(List.of(MsgChannel.TYPE_INAPP), title, content, List.of(), "INFO", "AI 值班助手");
        } catch (Exception e) {
            log.warn("Report in-app push failed: {}", e.getMessage());
        }
        Map<String, Object> out = new LinkedHashMap<>(data);
        out.put("report", content);
        out.put("title", title);
        return out;
    }

    private Map<String, Object> reportData() {
        LocalDateTime since = LocalDateTime.now().minusHours(24);
        List<FlightTask> tasks = taskRepository.findAll().stream()
                .filter(t -> t.getStartTime() != null && t.getStartTime().isAfter(since)).toList();
        List<Alert> alerts = alertRepository.findAll().stream()
                .filter(a -> a.getCreatedAt() != null && a.getCreatedAt().isAfter(since)).toList();
        Map<String, Long> byType = new LinkedHashMap<>();
        alerts.forEach(a -> byType.merge(String.valueOf(a.getType()), 1L, Long::sum));
        Map<String, Long> tasksByStatus = new LinkedHashMap<>();
        tasks.forEach(t -> tasksByStatus.merge(String.valueOf(t.getStatus()), 1L, Long::sum));
        List<Device> devices = deviceRepository.findAll();
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("period", "last24h");
        json.put("flights", tasks.size());
        json.put("tasksByStatus", tasksByStatus);
        json.put("alertsTotal", alerts.size());
        json.put("alertsUnhandled", alerts.stream().filter(a -> !Boolean.TRUE.equals(a.getHandled())).count());
        json.put("alertsByType", byType);
        json.put("deviceTotal", devices.size());
        json.put("deviceOnline", devices.stream().filter(d -> d.getStatus() == Device.Status.ONLINE
                || d.getStatus() == Device.Status.FLYING).count());
        json.put("droneTotal", devices.stream().filter(d -> d.getCategory() == Device.Category.DRONE).count());
        json.put("pilotTotal", pilotRepository.count());
        json.put("fenceTotal", fenceRepository.count());
        try {
            return Map.of("json", MAPPER.writeValueAsString(json), "data", json);
        } catch (Exception e) {
            return Map.of("json", "{}", "data", json);
        }
    }

    // ==================== 态势数据组装(copilot 快照复用) ====================

    private String snapshotJson() {
        try {
            return MAPPER.writeValueAsString(Map.of(
                    "overview", overview(),
                    "flying", flying(),
                    "unhandledAlerts", alertRepository.countByHandledFalse()));
        } catch (Exception e) {
            return "{}";
        }
    }

    private Map<String, Object> overview() {
        List<Device> devices = deviceRepository.findAll();
        List<FlightTask> tasks = taskRepository.findAll();
        return Map.of(
                "deviceTotal", devices.size(),
                "droneTotal", devices.stream().filter(d -> d.getCategory() == Device.Category.DRONE).count(),
                "deviceOnline", devices.stream().filter(d -> d.getStatus() == Device.Status.ONLINE
                        || d.getStatus() == Device.Status.FLYING).count(),
                "flyingNow", devices.stream().filter(d -> d.getStatus() == Device.Status.FLYING).count(),
                "taskTotal", tasks.size(),
                "taskFlying", tasks.stream().filter(t -> t.getStatus() == FlightTask.Status.FLYING).count(),
                "taskPending", tasks.stream().filter(t -> t.getStatus() == FlightTask.Status.PENDING).count(),
                "alertUnhandled", alertRepository.countByHandledFalse(),
                "pilotTotal", pilotRepository.count(),
                "fenceTotal", fenceRepository.count());
    }

    private List<Map<String, Object>> flying() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Device d : deviceRepository.findAll()) {
            if (d.getCategory() != Device.Category.DRONE) continue;
            if (d.getStatus() != Device.Status.FLYING) continue;
            DeviceDataHistory last = historyRepository.findFirstByDeviceIdOrderByTsDesc(d.getId());
            out.add(Map.of(
                    "code", d.getCode(), "name", String.valueOf(d.getName()),
                    "model", String.valueOf(d.getModel()),
                    "latestTelemetry", last == null ? "无" : last.getFieldsJson()));
        }
        return out;
    }

    private List<Map<String, Object>> alerts(boolean unhandledOnly, int limit) {
        return (unhandledOnly
                ? alertRepository.findByHandledFalse()
                : alertRepository.findAllByOrderByCreatedAtDesc())
                .stream().limit(limit)
                .<Map<String, Object>>map(a -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", a.getId());
                    m.put("type", String.valueOf(a.getType()));
                    m.put("level", String.valueOf(a.getLevel()));
                    m.put("device", a.getDevice() == null ? "-" : a.getDevice().getCode());
                    m.put("message", a.getMessage());
                    m.put("handled", Boolean.TRUE.equals(a.getHandled()));
                    m.put("createdAt", a.getCreatedAt() == null ? "-" : a.getCreatedAt().toString());
                    return m;
                }).toList();
    }

    private List<Map<String, Object>> tasks(String status) {
        return taskRepository.findAll().stream()
                .filter(t -> status == null || status.isBlank() || t.getStatus().name().equals(status))
                .sorted((a, b) -> Long.compare(b.getId(), a.getId()))
                .limit(10)
                .map(t -> Map.of("id", (Object) t.getId(), "name", t.getName(),
                        "status", String.valueOf(t.getStatus()),
                        "device", t.getDevice() == null ? "-" : t.getDevice().getCode(),
                        "startTime", t.getStartTime() == null ? "-" : t.getStartTime().toString()))
                .toList();
    }

    private Map<String, Object> device(String code) {
        Device d = deviceRepository.findByCode(code).orElse(null);
        if (d == null) return Map.of("error", "设备不存在: " + code);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("code", d.getCode());
        out.put("name", d.getName());
        out.put("category", String.valueOf(d.getCategory()));
        out.put("status", String.valueOf(d.getStatus()));
        out.put("model", d.getModel());
        out.put("virtual", d.getVirtual());
        DeviceDataHistory last = historyRepository.findFirstByDeviceIdOrderByTsDesc(d.getId());
        out.put("latestTelemetry", last == null ? "无" : last.getFieldsJson());
        return out;
    }

    private List<Map<String, Object>> fences() {
        return fenceRepository.findAll().stream()
                .map(f -> Map.of("name", (Object) f.getName(), "type", String.valueOf(f.getType()),
                        "enabled", Boolean.TRUE.equals(f.getEnabled())))
                .toList();
    }
}
