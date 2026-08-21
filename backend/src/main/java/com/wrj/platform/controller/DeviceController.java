package com.wrj.platform.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wrj.platform.common.ApiResponse;
import com.wrj.platform.common.OpLog;
import com.wrj.platform.entity.Device;
import com.wrj.platform.entity.DeviceDataHistory;
import com.wrj.platform.entity.Pilot;
import com.wrj.platform.entity.ProtocolTemplate;
import com.wrj.platform.repository.DeviceDataHistoryRepository;
import com.wrj.platform.repository.DeviceRepository;
import com.wrj.platform.repository.PilotRepository;
import com.wrj.platform.repository.ProtocolRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 统一设备管理(无人机与物联网设备) */
@RestController
@RequestMapping("/api/devices")
public class DeviceController {

    private final DeviceRepository deviceRepository;
    private final PilotRepository pilotRepository;
    private final ProtocolRepository protocolRepository;
    private final DeviceDataHistoryRepository historyRepository;
    private final ObjectMapper objectMapper;
    private final SecureRandom random = new SecureRandom();

    public DeviceController(DeviceRepository deviceRepository,
                            PilotRepository pilotRepository,
                            ProtocolRepository protocolRepository,
                            DeviceDataHistoryRepository historyRepository,
                            ObjectMapper objectMapper) {
        this.deviceRepository = deviceRepository;
        this.pilotRepository = pilotRepository;
        this.protocolRepository = protocolRepository;
        this.historyRepository = historyRepository;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public ApiResponse<List<Device>> list(@RequestParam(required = false) String keyword,
                                          @RequestParam(required = false) String category,
                                          @RequestParam(required = false) String status) {
        List<Device> all = deviceRepository.findAll();
        return ApiResponse.ok(all.stream()
                .filter(d -> keyword == null || keyword.isBlank()
                        || d.getCode().contains(keyword)
                        || (d.getName() != null && d.getName().contains(keyword))
                        || (d.getModel() != null && d.getModel().contains(keyword)))
                .filter(d -> category == null || category.isBlank()
                        || d.getCategory().name().equals(category))
                .filter(d -> status == null || status.isBlank()
                        || d.getStatus().name().equals(status))
                .toList());
    }

    /** 设备管理分页查询(1 起页码):keyword 模糊编码/名称/型号,category/status 精确 */
    @GetMapping("/page")
    public ApiResponse<Map<String, Object>> page(@RequestParam(defaultValue = "1") int page,
                                                 @RequestParam(defaultValue = "10") int size,
                                                 @RequestParam(required = false) String keyword,
                                                 @RequestParam(required = false) String category,
                                                 @RequestParam(required = false) String status) {
        Specification<Device> spec = (root, q, cb) -> {
            List<jakarta.persistence.criteria.Predicate> ps = new ArrayList<>();
            if (keyword != null && !keyword.isBlank()) {
                String kw = "%" + keyword.trim() + "%";
                ps.add(cb.or(cb.like(root.get("code"), kw),
                        cb.like(root.get("name"), kw),
                        cb.like(root.get("model"), kw)));
            }
            if (category != null && !category.isBlank()) {
                ps.add(cb.equal(root.get("category"), Device.Category.valueOf(category)));
            }
            if (status != null && !status.isBlank()) {
                ps.add(cb.equal(root.get("status"), Device.Status.valueOf(status)));
            }
            return cb.and(ps.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        Page<Device> result = deviceRepository.findAll(spec,
                PageRequest.of(Math.max(0, page - 1), Math.min(100, Math.max(1, size)),
                        Sort.by(Sort.Direction.DESC, "id")));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("rows", result.getContent());
        out.put("total", result.getTotalElements());
        return ApiResponse.ok(out);
    }

    @GetMapping("/{id}")
    public ApiResponse<Device> get(@PathVariable Long id) {
        return ApiResponse.ok(deviceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("设备不存在: " + id)));
    }

    @PostMapping
    @OpLog(module = "设备管理", action = "新增")
    public ApiResponse<Device> create(@RequestBody Device body) {
        if (body.getCode() == null || body.getCode().isBlank()) {
            throw new IllegalArgumentException("设备编码不能为空");
        }
        if (deviceRepository.existsByCode(body.getCode())) {
            throw new IllegalArgumentException("设备编码已存在: " + body.getCode());
        }
        if (body.getCategory() == null) {
            body.setCategory(Device.Category.DRONE);
        }
        if (body.getStatus() == null) {
            body.setStatus(Device.Status.OFFLINE);
        }
        body.setPilot(resolvePilot(body));
        body.setProtocol(resolveProtocol(body));
        if (body.getSecret() == null || body.getSecret().isBlank()) {
            body.setSecret(genSecret());
        }
        return ApiResponse.ok(deviceRepository.save(body));
    }

    @PutMapping("/{id}")
    @OpLog(module = "设备管理", action = "修改")
    public ApiResponse<Device> update(@PathVariable Long id, @RequestBody Device body) {
        Device device = deviceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("设备不存在: " + id));
        if (body.getCode() != null) device.setCode(body.getCode());
        if (body.getName() != null) device.setName(body.getName());
        if (body.getCategory() != null) device.setCategory(body.getCategory());
        if (body.getUsage() != null) device.setUsage(body.getUsage());
        if (body.getManufacturer() != null) device.setManufacturer(body.getManufacturer());
        if (body.getModel() != null) device.setModel(body.getModel());
        if (body.getStatus() != null) device.setStatus(body.getStatus());
        if (body.getHomeLng() != null) device.setHomeLng(body.getHomeLng());
        if (body.getHomeLat() != null) device.setHomeLat(body.getHomeLat());
        if (body.getMaxAltitude() != null) device.setMaxAltitude(body.getMaxAltitude());
        if (body.getMaxEndurance() != null) device.setMaxEndurance(body.getMaxEndurance());
        if (body.getVirtual() != null) device.setVirtual(body.getVirtual());
        if (body.getEnabled() != null) device.setEnabled(body.getEnabled());
        if (body.getOrgId() != null) device.setOrgId(body.getOrgId());
        if (body.getModbusUnitId() != null) device.setModbusUnitId(body.getModbusUnitId());
        if (body.getVideoUrl() != null) device.setVideoUrl(body.getVideoUrl());
        if (body.getScanRange() != null) device.setScanRange(body.getScanRange());
        if (body.getIcon() != null) device.setIcon(body.getIcon().isBlank() ? null : body.getIcon());
        device.setPilot(resolvePilot(body));
        device.setProtocol(resolveProtocol(body));
        return ApiResponse.ok(deviceRepository.save(device));
    }

    @DeleteMapping("/{id}")
    @OpLog(module = "设备管理", action = "删除")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        if (deviceRepository.existsById(id)) {
            deviceRepository.deleteById(id);
        }
        return ApiResponse.ok();
    }

    /** 重置设备密钥(重置后旧连接下次注册失效) */
    @PostMapping("/{id}/secret")
    @OpLog(module = "设备管理", action = "重置密钥")
    public ApiResponse<String> resetSecret(@PathVariable Long id) {
        Device device = deviceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("设备不存在: " + id));
        device.setSecret(genSecret());
        deviceRepository.save(device);
        return ApiResponse.ok(device.getSecret());
    }

    /** 设备历史数据(地图设备详情/传感面板趋势图):时间窗内按时间升序 */
    @GetMapping("/{id}/history")
    public ApiResponse<Map<String, Object>> history(@PathVariable Long id,
                                                    @RequestParam(defaultValue = "60") int minutes,
                                                    @RequestParam(defaultValue = "500") int limit) {
        Device device = deviceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("设备不存在: " + id));
        LocalDateTime since = LocalDateTime.now().minusMinutes(Math.max(1, minutes));
        List<DeviceDataHistory> rows = historyRepository.findRecent(id, since,
                PageRequest.of(0, Math.max(1, Math.min(2000, limit))));
        Collections.reverse(rows);    // 查询按 id 倒序取最新 N 条,返回时转为时间升序
        List<Map<String, Object>> items = new ArrayList<>(rows.size());
        for (DeviceDataHistory h : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("ts", h.getTs());
            item.put("fields", parseFields(h.getFieldsJson()));
            items.add(item);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("deviceId", id);
        out.put("deviceCode", device.getCode());
        out.put("items", items);
        return ApiResponse.ok(out);
    }

    /** 各设备最新一帧数据(物联网面板初值,未上报过的设备无记录) */
    @GetMapping("/latest-data")
    public ApiResponse<List<DeviceDataHistory>> latestData() {
        return ApiResponse.ok(historyRepository.findLatestPerDevice());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseFields(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            return Map.of();
        }
    }

    /** 设备状态统计(全分类) */
    @GetMapping("/stats")
    public ApiResponse<Map<String, Long>> stats() {        List<Device> all = deviceRepository.findAll();
        long online = all.stream().filter(d -> d.getStatus() == Device.Status.ONLINE).count();
        long flying = all.stream().filter(d -> d.getStatus() == Device.Status.FLYING).count();
        long idle = all.stream().filter(d -> d.getStatus() == Device.Status.IDLE).count();
        long maintenance = all.stream().filter(d -> d.getStatus() == Device.Status.MAINTENANCE).count();
        long offline = all.stream().filter(d -> d.getStatus() == Device.Status.OFFLINE).count();
        return ApiResponse.ok(Map.of(
                "total", (long) all.size(),
                "online", online,
                "flying", flying,
                "idle", idle,
                "maintenance", maintenance,
                "offline", offline
        ));
    }

    private Pilot resolvePilot(Device body) {
        if (body.getPilot() != null && body.getPilot().getId() != null) {
            return pilotRepository.findById(body.getPilot().getId())
                    .orElseThrow(() -> new IllegalArgumentException("飞手不存在"));
        }
        return null;
    }

    private ProtocolTemplate resolveProtocol(Device body) {
        if (body.getProtocol() != null && body.getProtocol().getId() != null) {
            return protocolRepository.findById(body.getProtocol().getId())
                    .orElseThrow(() -> new IllegalArgumentException("协议模板不存在"));
        }
        return null;
    }

    private String genSecret() {
        String chars = "abcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder("sec-");
        for (int i = 0; i < 12; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }
}
