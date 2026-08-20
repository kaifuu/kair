package com.wrj.platform.service;

import com.wrj.platform.config.TelemetryWebSocketHandler;
import com.wrj.platform.dto.AlertDto;
import com.wrj.platform.entity.Alert;
import com.wrj.platform.entity.Device;
import com.wrj.platform.entity.FlightTask;
import com.wrj.platform.repository.AlertRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AlertService {

    private final AlertRepository alertRepository;
    private final TelemetryWebSocketHandler wsHandler;
    private final AiAssistantService aiAssistant;

    public AlertService(AlertRepository alertRepository, TelemetryWebSocketHandler wsHandler,
                        AiAssistantService aiAssistant) {
        this.alertRepository = alertRepository;
        this.wsHandler = wsHandler;
        this.aiAssistant = aiAssistant;
    }

    /** 创建告警并即时 WS 推送;事务提交后触发 AI 研判(异步,失败不影响告警) */
    @Transactional
    public Alert raise(Alert.Type type, Alert.Level level, Device device, FlightTask task,
                       String message, Double lng, Double lat, Double altitude) {
        Alert alert = new Alert(type, level, device, task, message, lng, lat, altitude);
        alert = alertRepository.save(alert);
        try {
            wsHandler.broadcast("alert", toDto(alert));
        } catch (Exception ignored) {
        }
        Long alertId = alert.getId();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    aiAssistant.assessAsync(alertId);
                }
            });
        } else {
            aiAssistant.assessAsync(alertId);
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

    /** 导出用:按当前过滤条件取全量(不分页) */
    @Transactional(readOnly = true)
    public List<Alert> listForExport(boolean unhandledOnly) {
        return unhandledOnly
                ? alertRepository.findByHandledFalse()
                : alertRepository.findAllByOrderByCreatedAtDesc();
    }

    /** 批量删除,返回实际删除条数 */
    @Transactional
    public long deleteBatch(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return 0;
        alertRepository.deleteAllById(ids);
        return ids.size();
    }

    /** 一键清空:handledOnly=true 仅清已处理,否则全清;返回清除条数 */
    @Transactional
    public long clear(boolean handledOnly) {
        long count = handledOnly ? alertRepository.countByHandledTrue() : alertRepository.count();
        if (count == 0) return 0;
        if (handledOnly) {
            alertRepository.deleteByHandledTrue();
        } else {
            alertRepository.deleteAllInBatch();
        }
        return count;
    }

    public static AlertDto toDto(Alert a) {
        AlertDto dto = new AlertDto();
        dto.setId(a.getId());
        dto.setType(a.getType() == null ? null : a.getType().name());
        dto.setLevel(a.getLevel() == null ? null : a.getLevel().name());
        dto.setDroneCode(a.getDevice() == null ? null : a.getDevice().getCode());
        dto.setDroneId(a.getDevice() == null ? null : a.getDevice().getId());
        dto.setTaskName(a.getTask() == null ? null : a.getTask().getName());
        dto.setMessage(a.getMessage());
        dto.setLng(a.getLng());
        dto.setLat(a.getLat());
        dto.setAltitude(a.getAltitude());
        dto.setHandled(Boolean.TRUE.equals(a.getHandled()));
        dto.setHandler(a.getHandler());
        dto.setAiAdvice(a.getAiAdvice());
        dto.setCreatedAt(a.getCreatedAt() == null ? null : a.getCreatedAt().toString());
        return dto;
    }
}
