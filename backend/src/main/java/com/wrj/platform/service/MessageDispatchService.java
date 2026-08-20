package com.wrj.platform.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wrj.platform.config.TelemetryWebSocketHandler;
import com.wrj.platform.entity.MsgChannel;
import com.wrj.platform.entity.MsgMessage;
import com.wrj.platform.entity.MsgSendLog;
import com.wrj.platform.repository.MsgChannelRepository;
import com.wrj.platform.repository.MsgMessageRepository;
import com.wrj.platform.repository.MsgSendLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;

/**
 * 消息统一调度:按通道分发并落发送记录。
 * 密钥均存于通道 configJson(服务端持久化,不进代码仓库)。
 */
@Service
public class MessageDispatchService {

    private static final Logger log = LoggerFactory.getLogger(MessageDispatchService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    /** 掩码哨兵:前端回传该值表示「未修改」,保存时保留旧密钥 */
    public static final String MASK = "******";

    private final MsgChannelRepository channelRepository;
    private final MsgMessageRepository messageRepository;
    private final MsgSendLogRepository sendLogRepository;
    private final TelemetryWebSocketHandler wsHandler;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public MessageDispatchService(MsgChannelRepository channelRepository,
                                  MsgMessageRepository messageRepository,
                                  MsgSendLogRepository sendLogRepository,
                                  TelemetryWebSocketHandler wsHandler) {
        this.channelRepository = channelRepository;
        this.messageRepository = messageRepository;
        this.sendLogRepository = sendLogRepository;
        this.wsHandler = wsHandler;
    }

    /** 多通道发送结果:通道 -> 结果说明 */
    @Transactional
    public Map<String, Object> send(List<String> types, String title, String content,
                                    List<String> receivers, String level, String sender) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String type : types) {
            MsgChannel ch = channelRepository.findByType(type).orElse(null);
            if (ch == null || !Boolean.TRUE.equals(ch.getEnabled())) {
                result.put(type, Map.of("status", "SKIP", "msg", "通道未启用"));
                logSend(type, type, title, receivers, "SKIP", "通道未启用", 0);
                continue;
            }
            long start = System.currentTimeMillis();
            try {
                String detail = switch (type) {
                    case MsgChannel.TYPE_INAPP -> dispatchInapp(ch, title, content, receivers, level, sender);
                    case MsgChannel.TYPE_EMAIL -> dispatchEmail(ch, title, content, receivers);
                    case MsgChannel.TYPE_JPUSH -> dispatchJpush(ch, title, content, receivers);
                    case MsgChannel.TYPE_UMENG -> dispatchUmeng(ch, title, content, receivers);
                    case MsgChannel.TYPE_SMS -> dispatchSms(ch, title, content, receivers);
                    default -> throw new IllegalArgumentException("未知通道类型: " + type);
                };
                long cost = System.currentTimeMillis() - start;
                result.put(type, Map.of("status", "SUCCESS", "msg", detail, "costMs", cost));
                logSend(type, ch.getName(), title, receivers, "SUCCESS", null, cost);
            } catch (Exception e) {
                long cost = System.currentTimeMillis() - start;
                String err = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                result.put(type, Map.of("status", "FAIL", "msg", err, "costMs", cost));
                logSend(type, ch.getName(), title, receivers, "FAIL", err, cost);
            }
        }
        return result;
    }

    /** 站内信:receivers 为空广播 ALL,否则按人落行,并 WS 推送 */
    private String dispatchInapp(MsgChannel ch, String title, String content,
                                 List<String> receivers, String level, String sender) {
        List<MsgMessage> saved;
        if (receivers == null || receivers.isEmpty()) {
            saved = List.of(messageRepository.save(new MsgMessage(title, content, level, sender, "ALL")));
        } else {
            saved = receivers.stream()
                    .filter(r -> r != null && !r.isBlank())
                    .map(r -> messageRepository.save(new MsgMessage(title, content, level, sender, r.trim())))
                    .toList();
        }
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("title", title);
            payload.put("content", content);
            payload.put("level", level == null ? "INFO" : level);
            payload.put("sender", sender);
            payload.put("ts", System.currentTimeMillis());
            wsHandler.broadcast("inapp", payload);
        } catch (Exception ignored) {
        }
        return "站内信已发送 " + saved.size() + " 条";
    }

    /** 邮件:SMTP 参数全部来自通道配置,receivers 为邮箱列表 */
    private String dispatchEmail(MsgChannel ch, String title, String content, List<String> receivers) throws Exception {
        JsonNode cfg = readConfig(ch);
        String host = cfg.path("host").asText("");
        int port = cfg.path("port").asInt(465);
        String username = cfg.path("username").asText("");
        String password = cfg.path("password").asText("");
        String from = cfg.path("from").asText(username);
        if (host.isBlank()) throw new IllegalArgumentException("邮件通道未配置 SMTP 主机");
        List<String> to = validReceivers(receivers, cfg.path("testTo").asText(""));
        if (to.isEmpty()) throw new IllegalArgumentException("未指定收件邮箱");

        JavaMailSenderImpl mail = new JavaMailSenderImpl();
        mail.setHost(host);
        mail.setPort(port);
        mail.setUsername(username);
        mail.setPassword(password);
        mail.setDefaultEncoding("UTF-8");
        Properties props = mail.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout", "15000");
        if (cfg.path("ssl").asBoolean(port == 465)) {
            props.put("mail.smtp.ssl.enable", "true");
        } else {
            props.put("mail.smtp.starttls.enable", "true");
        }
        jakarta.mail.internet.MimeMessage mime = mail.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mime, false, "UTF-8");
        helper.setFrom(from);
        helper.setTo(to.toArray(new String[0]));
        helper.setSubject(title);
        helper.setText(content, false);
        mail.send(mime);
        return "邮件已发送至 " + to.size() + " 个邮箱";
    }

    /** 极光推送 REST API v3:Basic(appKey:masterSecret),receivers 作为 alias,空则全量广播 */
    private String dispatchJpush(MsgChannel ch, String title, String content, List<String> receivers) throws Exception {
        JsonNode cfg = readConfig(ch);
        String appKey = cfg.path("appKey").asText("");
        String masterSecret = cfg.path("masterSecret").asText("");
        if (appKey.isBlank() || masterSecret.isBlank()) throw new IllegalArgumentException("极光通道未配置 appKey/masterSecret");

        ObjectNode body = MAPPER.createObjectNode();
        body.putArray("platform").add("android").add("ios");
        ObjectNode audience = body.putObject("audience");
        if (receivers == null || receivers.isEmpty()) {
            audience.put("all", "");
        } else {
            audience.putArray("alias").addAll(
                    receivers.stream().map(r -> MAPPER.getNodeFactory().textNode(r.trim())).toList());
        }
        ObjectNode notification = body.putObject("notification");
        notification.putObject("android").put("alert", content).put("title", title);
        notification.putObject("ios").put("alert", title + ": " + content);

        String auth = Base64.getEncoder()
                .encodeToString((appKey + ":" + masterSecret).getBytes(StandardCharsets.UTF_8));
        HttpRequest req = HttpRequest.newBuilder(URI.create("https://api.jpush.cn/v3/push"))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Basic " + auth)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() / 100 != 2) {
            throw new IllegalArgumentException("极光返回 " + resp.statusCode() + ": " + truncate(resp.body()));
        }
        return "极光推送成功";
    }

    /** 友盟推送:sign = MD5(POST + url + body + appMasterSecret),receivers 作为 alias,空则 broadcast */
    private String dispatchUmeng(MsgChannel ch, String title, String content, List<String> receivers) throws Exception {
        JsonNode cfg = readConfig(ch);
        String appKey = cfg.path("appKey").asText("");
        String masterSecret = cfg.path("appMasterSecret").asText("");
        if (appKey.isBlank() || masterSecret.isBlank()) throw new IllegalArgumentException("友盟通道未配置 appKey/appMasterSecret");

        ObjectNode body = MAPPER.createObjectNode();
        body.put("appkey", appKey);
        body.put("timestamp", String.valueOf(System.currentTimeMillis()));
        body.put("production_mode", cfg.path("production").asBoolean(false));
        if (receivers == null || receivers.isEmpty()) {
            body.put("type", "broadcast");
        } else {
            body.put("type", "customized_cast");
            body.putObject("customized_cast").putArray("alias").addAll(
                    receivers.stream().map(r -> MAPPER.getNodeFactory().textNode(r.trim())).toList());
        }
        ObjectNode payload = body.putObject("payload");
        payload.put("display_type", "notification");
        payload.putObject("body").put("title", title).put("text", content);
        payload.putObject("extra").put("title", title);

        String url = "https://msgapi.umeng.com/api/send";
        String json = MAPPER.writeValueAsString(body);
        String sign = md5Hex("POST" + url + json + masterSecret);
        HttpRequest req = HttpRequest.newBuilder(URI.create(url + "?sign=" + sign))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() / 100 != 2) {
            throw new IllegalArgumentException("友盟返回 " + resp.statusCode() + ": " + truncate(resp.body()));
        }
        // 友盟 200 也可能带失败 RET
        JsonNode ret = MAPPER.readTree(resp.body());
        if (ret.has("ret") && ret.path("ret").asText("").contains("FAIL")) {
            throw new IllegalArgumentException("友盟返回: " + truncate(ret.path("ret").asText()));
        }
        return "友盟推送成功";
    }

    /**
     * 短信:通用 HTTP 网关适配(阿里/腾讯等均可经其 HTTP 接口或自建网关对接)。
     * bodyTemplate 支持 ${phone} / ${content} 占位,逐个接收人请求一次。
     */
    private String dispatchSms(MsgChannel ch, String title, String content, List<String> receivers) throws Exception {
        JsonNode cfg = readConfig(ch);
        String apiUrl = cfg.path("apiUrl").asText("");
        if (apiUrl.isBlank()) throw new IllegalArgumentException("短信通道未配置网关地址");
        List<String> phones = validReceivers(receivers, cfg.path("testPhone").asText(""));
        if (phones.isEmpty()) throw new IllegalArgumentException("未指定接收手机号");
        String method = cfg.path("method").asText("POST").toUpperCase();
        String template = cfg.path("bodyTemplate").asText("{\"phone\":\"${phone}\",\"content\":\"${content}\"}");
        String successContains = cfg.path("successContains").asText("");
        JsonNode headersNode = cfg.path("headers");

        int ok = 0;
        List<String> fails = new ArrayList<>();
        for (String phone : phones) {
            String body = template.replace("${phone}", phone)
                    .replace("${content}", content)
                    .replace("${title}", title);
            HttpRequest.Builder rb = HttpRequest.newBuilder(URI.create(apiUrl))
                    .timeout(Duration.ofSeconds(10));
            if (headersNode != null && headersNode.isObject()) {
                headersNode.fieldNames().forEachRemaining(k -> rb.header(k, headersNode.path(k).asText()));
            }
            if ("GET".equals(method)) {
                rb.GET();
            } else {
                rb.header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
            }
            HttpResponse<String> resp = http.send(rb.build(), HttpResponse.BodyHandlers.ofString());
            boolean good = resp.statusCode() / 100 == 2
                    && (successContains.isBlank() || resp.body().contains(successContains));
            if (good) ok++;
            else fails.add(phone + "(" + resp.statusCode() + ")");
        }
        if (ok == 0) throw new IllegalArgumentException("短信全部失败: " + String.join(",", fails));
        return "短信已发送 " + ok + "/" + phones.size() + " 条";
    }

    // ---------- 工具 ----------

    private JsonNode readConfig(MsgChannel ch) {
        try {
            return MAPPER.readTree(ch.getConfigJson() == null ? "{}" : ch.getConfigJson());
        } catch (Exception e) {
            throw new IllegalArgumentException("通道参数 JSON 解析失败: " + e.getMessage());
        }
    }

    /** receivers 为空时回落到配置的测试接收人,逗号分隔 */
    private List<String> validReceivers(List<String> receivers, String fallback) {
        if (receivers != null && !receivers.isEmpty()) {
            return receivers.stream().filter(r -> r != null && !r.isBlank()).map(String::trim).toList();
        }
        if (fallback == null || fallback.isBlank()) return List.of();
        return Arrays.stream(fallback.split("[,;，；\\s]+")).filter(s -> !s.isBlank()).toList();
    }

    private void logSend(String type, String name, String title, List<String> receivers,
                         String status, String error, long costMs) {
        try {
            sendLogRepository.save(new MsgSendLog(type, name, title,
                    receivers == null ? "-" : String.join(",", receivers), status, error, costMs));
        } catch (Exception e) {
            log.warn("Save send log failed: {}", e.getMessage());
        }
    }

    private static String md5Hex(String s) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] d = md.digest(s.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : d) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private static String truncate(String s) {
        if (s == null) return "";
        return s.length() > 300 ? s.substring(0, 300) : s;
    }
}
