package com.wrj.platform.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wrj.platform.dto.WsMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** 遥测 WebSocket 处理器:广播推送无人机实时位置/告警 */
@Component
public class TelemetryWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(TelemetryWebSocketHandler.class);

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public TelemetryWebSocketHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.put(session.getId(), session);
        log.info("WS connected: {} (total {})", session.getId(), sessions.size());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session.getId());
        log.info("WS closed: {} (total {})", session.getId(), sessions.size());
    }

    /** 向所有在线客户端广播消息 */
    public void broadcast(String type, Object payload) {
        if (sessions.isEmpty()) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(new WsMessage(type, payload));
            TextMessage msg = new TextMessage(json);
            sessions.values().forEach(s -> {
                try {
                    if (s.isOpen()) {
                        synchronized (s) {
                            s.sendMessage(msg);
                        }
                    }
                } catch (IOException e) {
                    log.warn("WS send failed: {}", e.getMessage());
                }
            });
        } catch (Exception e) {
            log.error("Broadcast error", e);
        }
    }

    public int onlineCount() {
        return sessions.size();
    }
}
