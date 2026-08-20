package com.wrj.platform.controller;

import com.wrj.platform.common.ApiResponse;
import com.wrj.platform.common.OpLog;
import com.wrj.platform.entity.MsgMessage;
import com.wrj.platform.entity.MsgRead;
import com.wrj.platform.entity.MsgSendLog;
import com.wrj.platform.repository.MsgMessageRepository;
import com.wrj.platform.repository.MsgReadRepository;
import com.wrj.platform.repository.MsgSendLogRepository;
import com.wrj.platform.service.MessageDispatchService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 消息发送 / 站内收件箱 / 发送记录 */
@RestController
@RequestMapping("/api/msg")
public class MsgMessageController {

    private final MessageDispatchService dispatchService;
    private final MsgMessageRepository messageRepository;
    private final MsgReadRepository readRepository;
    private final MsgSendLogRepository logRepository;

    public MsgMessageController(MessageDispatchService dispatchService,
                                MsgMessageRepository messageRepository,
                                MsgReadRepository readRepository,
                                MsgSendLogRepository logRepository) {
        this.dispatchService = dispatchService;
        this.messageRepository = messageRepository;
        this.readRepository = readRepository;
        this.logRepository = logRepository;
    }

    /** 多通道发送:channels 为通道类型数组 */
    @PostMapping("/send")
    @OpLog(module = "消息管理", action = "发送消息")
    public ApiResponse<Map<String, Object>> send(@RequestBody Map<String, Object> body,
                                                 HttpServletRequest request) {
        @SuppressWarnings("unchecked")
        List<String> channels = (List<String>) body.getOrDefault("channels", List.of());
        String title = String.valueOf(body.getOrDefault("title", "")).trim();
        String content = String.valueOf(body.getOrDefault("content", "")).trim();
        String level = String.valueOf(body.getOrDefault("level", "INFO"));
        Object rcv = body.get("receivers");
        List<String> receivers = rcv instanceof List<?> l
                ? l.stream().map(String::valueOf).filter(s -> !s.isBlank()).toList()
                : (rcv == null || String.valueOf(rcv).isBlank() ? List.of()
                        : List.of(String.valueOf(rcv).split("[,;，；\\s]+")));
        if (channels.isEmpty()) throw new IllegalArgumentException("请至少选择一个通道");
        if (title.isEmpty() || content.isEmpty()) throw new IllegalArgumentException("标题与内容不能为空");
        String sender = (String) request.getAttribute("currentNickname");
        if (sender == null || sender.isBlank()) sender = (String) request.getAttribute("currentUser");
        Map<String, Object> result = dispatchService.send(channels, title, content, receivers, level, sender);
        return ApiResponse.ok(result);
    }

    /** 我的收件箱 */
    @GetMapping("/inbox")
    public ApiResponse<Map<String, Object>> inbox(@RequestParam(defaultValue = "1") int page,
                                                  @RequestParam(defaultValue = "10") int size,
                                                  HttpServletRequest request) {
        String user = currentUser(request);
        Page<MsgMessage> result = messageRepository
                .findByReceiverOrReceiverOrderByCreatedAtDesc(user, "ALL", PageRequest.of(Math.max(0, page - 1), size));
        List<Long> readIds = result.getContent().stream()
                .map(m -> readRepository.findByMessageIdAndUsername(m.getId(), user).isPresent() ? m.getId() : null)
                .filter(id -> id != null)
                .toList();
        List<Map<String, Object>> items = result.getContent().stream().map(m -> Map.of(
                "id", (Object) m.getId(),
                "title", m.getTitle(),
                "content", m.getContent(),
                "level", m.getLevel() == null ? "INFO" : m.getLevel(),
                "sender", m.getSender() == null ? "-" : m.getSender(),
                "receiver", m.getReceiver(),
                "read", readIds.contains(m.getId()),
                "createdAt", String.valueOf(m.getCreatedAt())
        )).toList();
        return ApiResponse.ok(Map.of("items", items, "total", result.getTotalElements(), "page", page, "size", size));
    }

    /** 标记已读 */
    @PostMapping("/inbox/{id}/read")
    public ApiResponse<Void> markRead(@PathVariable Long id, HttpServletRequest request) {
        messageRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("消息不存在: " + id));
        String user = currentUser(request);
        if (readRepository.findByMessageIdAndUsername(id, user).isEmpty()) {
            readRepository.save(new MsgRead(id, user));
        }
        return ApiResponse.ok();
    }

    /** 我的未读数(顶栏铃铛) */
    @GetMapping("/unread-count")
    public ApiResponse<Long> unreadCount(HttpServletRequest request) {
        return ApiResponse.ok(messageRepository.countUnread(currentUser(request)));
    }

    /** 发送记录 */
    @GetMapping("/logs")
    public ApiResponse<Map<String, Object>> logs(@RequestParam(defaultValue = "1") int page,
                                                 @RequestParam(defaultValue = "10") int size,
                                                 @RequestParam(required = false) String channel) {
        PageRequest pageable = PageRequest.of(Math.max(0, page - 1), size);
        Page<MsgSendLog> result = (channel == null || channel.isBlank())
                ? logRepository.findAllByOrderByCreatedAtDesc(pageable)
                : logRepository.findByChannelTypeOrderByCreatedAtDesc(channel, pageable);
        return ApiResponse.ok(Map.of(
                "items", result.getContent(),
                "total", result.getTotalElements(),
                "page", page,
                "size", size
        ));
    }

    private static String currentUser(HttpServletRequest request) {
        String u = (String) request.getAttribute("currentUser");
        return u == null ? "admin" : u;
    }
}
