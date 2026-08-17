package com.wrj.platform.service;

import com.wrj.platform.config.TelemetryWebSocketHandler;
import com.wrj.platform.dto.AlertDto;
import com.wrj.platform.entity.Alert;
import com.wrj.platform.entity.Drone;
import com.wrj.platform.entity.FlightTask;
import com.wrj.platform.repository.AlertRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AlertService {

    private final AlertRepository alertRepository;
    private final TelemetryWebSocketHandler wsHandler;

    public AlertService(AlertRepository alertRepository, TelemetryWebSocketHandler wsHandler) {
        this.alertRepository = alertRepository;
        this.wsHandler = wsHandler;
    }

    /** 创建告警并即时 WS 推送 */
    @Transactional
    public Alert raise(Alert.Type type, Alert.Level level, Drone drone, FlightTask task,
                       String message, Double lng, Double lat, Double altitude) {
        Alert alert = new Alert(type, level, drone, task, message, lng, lat, altitude);
        alert = alertRepository.save(alert);
        try {
            wsHandler.broadcast("alert", toDto(alert));
        } catch (Exception ignored) {
        }
        return alert;
    }

    @Transactional(readOnly = true)
    public Page<Alert> page(boolean unhandledOnly, Pageable pageable) {
        return unhandledOnly
                ? alertRepository.findByHandledFalse(pageable)
                : alertRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    @Transactional(readOnly = true)
    public List<Alert> latest(int limit) {
        return alertRepository.findTop50ByOrderByCreatedAtDesc();
    }

    @Transactional
    public Alert handle(Long id, String handler) {
        Alert alert = alertRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("告警不存在: " + id));
        alert.setHandled(true);
        alert.setHandler(handler);
        alert.setHandleTime(LocalDateTime.now());
        return alertRepository.save(alert);
    }

    @Transactional(readOnly = true)
    public long unhandledCount() {
        return alertRepository.countByHandledFalse();
    }

    public static AlertDto toDto(Alert a) {
        AlertDto dto = new AlertDto();
        dto.setId(a.getId());
        dto.setType(a.getType() == null ? null : a.getType().name());
        dto.setLevel(a.getLevel() == null ? null : a.getLevel().name());
        dto.setDroneCode(a.getDrone() == null ? null : a.getDrone().getCode());
        dto.setDroneId(a.getDrone() == null ? null : a.getDrone().getId());
        dto.setTaskName(a.getTask() == null ? null : a.getTask().getName());
        dto.setMessage(a.getMessage());
        dto.setLng(a.getLng());
        dto.setLat(a.getLat());
        dto.setAltitude(a.getAltitude());
        dto.setHandled(Boolean.TRUE.equals(a.getHandled()));
        dto.setHandler(a.getHandler());
        dto.setCreatedAt(a.getCreatedAt() == null ? null : a.getCreatedAt().toString());
        return dto;
    }
}
