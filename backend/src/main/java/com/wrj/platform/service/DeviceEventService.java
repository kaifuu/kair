package com.wrj.platform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wrj.platform.config.TelemetryWebSocketHandler;
import com.wrj.platform.dto.TelemetryDto;
import com.wrj.platform.entity.Device;
import com.wrj.platform.entity.DeviceDataHistory;
import com.wrj.platform.entity.DeviceMessage;
import com.wrj.platform.entity.ModbusRegister;
import com.wrj.platform.entity.ProtocolTemplate;
import com.wrj.platform.entity.SysLog;
import com.wrj.platform.gateway.DeviceFrame;
import com.wrj.platform.gateway.ProtocolEngine;
import com.wrj.platform.repository.DeviceDataHistoryRepository;
import com.wrj.platform.repository.DeviceMessageRepository;
import com.wrj.platform.repository.DeviceRepository;
import com.wrj.platform.repository.ModbusRegisterRepository;
import com.wrj.platform.repository.SysLogRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Netty 设备事件 → 业务唯一通道:
 * dbExecutor(可配线程数,device.db-threads)把 JPA 访问移出 EventLoop;
 * 上线/下线/心跳/数据解析后经 WS 广播,与 FlightSimulator 殊途同归;
 * 上下行整帧同步留痕 device_message(心跳默认不入库,防冲掉有价值记录)。
 */
@Service
public class DeviceEventService {

    private static final Logger log = LoggerFactory.getLogger(DeviceEventService.class);
    private static final int TRACK_LIMIT = 60;
    private static final long HEARTBEAT_REFRESH_MS = 10_000;
    private static final long ONLINE_PERSIST_MS = 10_000;  // DATA 帧刷新在线时间的落库节流
    private static final int TRIM_EVERY = 50;      // 每落库 N 条触发一次裁剪
    private static final int MAX_HEX_LEN = 30_000; // contentHex 列防御截断

    private final DeviceRepository deviceRepository;
    private final SysLogRepository logRepository;
    private final DeviceMessageRepository messageRepository;
    private final DeviceDataHistoryRepository historyRepository;
    private final ModbusRegisterRepository modbusRegisterRepository;
    private final TelemetryWebSocketHandler wsHandler;
    private final ObjectMapper objectMapper;
    private final ThreatService threatService;

    private final boolean logHeartbeat;
    private final long messageKeep;
    private final int historyKeep;

    /** JPA 落库线程池:默认 4 线程,设备量大时可经 device.db-threads 调整 */
    private final ExecutorService dbExecutor;

    /** DRONE 轨迹缓存:deviceId → 最近 60 点 */
    private final Map<Long, List<Map<String, Double>>> trackCache = new ConcurrentHashMap<>();

    /** 心跳刷新节流:deviceId → 上次落库时间戳 */
    private final Map<Long, Long> lastBeat = new ConcurrentHashMap<>();

    /** DATA 帧在线刷新节流:deviceId → 上次 lastOnlineAt 落库时间戳 */
    private final Map<Long, Long> lastOnlinePersist = new ConcurrentHashMap<>();

    /** 报文落库计数(裁剪节流) */
    private final AtomicLong msgInserts = new AtomicLong();

    /** 历史数据落库计数(裁剪节流) */
    private final AtomicLong historyInserts = new AtomicLong();

    /** Modbus 寄存器缓存:unitId → (寄存器号 → 16 位字),FC16 写入合并 + 落库,FC3/4 读取应答 */
    private final Map<Integer, Map<Integer, Integer>> modbusWords = new ConcurrentHashMap<>();

    private final AtomicInteger dbThreadSeq = new AtomicInteger();

    public DeviceEventService(DeviceRepository deviceRepository,
                              SysLogRepository logRepository,
                              DeviceMessageRepository messageRepository,
                              DeviceDataHistoryRepository historyRepository,
                              ModbusRegisterRepository modbusRegisterRepository,
                              TelemetryWebSocketHandler wsHandler,
                              ObjectMapper objectMapper,
                              ThreatService threatService,
                              @Value("${device.message-log.log-heartbeat:false}") boolean logHeartbeat,
                              @Value("${device.message-log.keep:500}") long messageKeep,
                              @Value("${device.history.keep-per-device:2000}") int historyKeep,
                              @Value("${device.db-threads:4}") int dbThreads) {
        this.deviceRepository = deviceRepository;
        this.logRepository = logRepository;
        this.messageRepository = messageRepository;
        this.historyRepository = historyRepository;
        this.modbusRegisterRepository = modbusRegisterRepository;
        this.wsHandler = wsHandler;
        this.objectMapper = objectMapper;
        this.threatService = threatService;
        this.logHeartbeat = logHeartbeat;
        this.messageKeep = messageKeep;
        this.historyKeep = historyKeep;
        int threads = Math.max(1, dbThreads);
        this.dbExecutor = Executors.newFixedThreadPool(threads, r -> {
            Thread t = new Thread(r, "device-db-" + dbThreadSeq.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
    }

    /** 重启回加载:Modbus 寄存器持久值 → 内存缓存,FC3/4 读取不再归零 */
    @PostConstruct
    public void loadModbusRegisters() {
        try {
            for (ModbusRegister r : modbusRegisterRepository.findAll()) {
                modbusWords.computeIfAbsent(r.getUnitId(), k -> new ConcurrentHashMap<>())
                        .put(r.getAddr(), r.getValue() & 0xFFFF);
            }
            log.info("Modbus registers restored: {} units", modbusWords.size());
        } catch (Exception e) {
            log.warn("Restore modbus registers failed: {}", e.getMessage());
        }
    }

    @PreDestroy
    public void shutdown() {
        dbExecutor.shutdown();
        try {
            dbExecutor.awaitTermination(3, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** REGISTER 鉴权:编码存在 + 启用 + 密钥一致 */
    public CompletableFuture<Device> authenticate(String code, String secret) {
        CompletableFuture<Device> future = new CompletableFuture<>();
        dbExecutor.execute(() -> {
            try {
                Device device = deviceRepository.findByCode(code).orElse(null);
                boolean ok = device != null
                        && Boolean.TRUE.equals(device.getEnabled())
                        && device.getSecret() != null
                        && device.getSecret().equals(secret);
                future.complete(ok ? device : null);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    /** 注册鉴权失败:记设备日志(密钥打码) */
    public void logRegisterFailed(String code, String secret) {
        dbExecutor.execute(() -> {
            try {
                String masked = secret == null ? "空" :
                        (secret.length() <= 4 ? "****" : secret.substring(0, 4) + "****");
                logRepository.save(new SysLog(SysLog.Type.DEVICE, code, "注册被拒",
                        "密钥校验失败(secret=" + masked + ")", null, false));
            } catch (Exception e) {
                log.warn("Save register-failed log error: {}", e.getMessage());
            }
        });
    }

    /** 设备上线:置 ONLINE + 记录 IP/时间 + 设备日志 + 广播 */
    public void onOnline(Long deviceId, String ip) {
        dbExecutor.execute(() -> {
            try {
                Device device = deviceRepository.findById(deviceId).orElse(null);
                if (device == null) {
                    return;
                }
                device.setStatus(Device.Status.ONLINE);
                device.setLastOnlineAt(java.time.LocalDateTime.now());
                device.setLastIp(ip);
                deviceRepository.save(device);
                logRepository.save(new SysLog(SysLog.Type.DEVICE, device.getCode(), "设备上线",
                        "IP " + ip, ip, true));
                broadcastStatus(device);
                log.info("Device {} online from {}", device.getCode(), ip);
            } catch (Exception e) {
                log.error("onOnline error: {}", e.getMessage());
            }
        });
    }

    /** 心跳:10s 节流刷新 lastOnlineAt */
    public void onHeartbeat(Long deviceId) {
        long now = System.currentTimeMillis();
        Long last = lastBeat.get(deviceId);
        if (last != null && now - last < HEARTBEAT_REFRESH_MS) {
            return;
        }
        lastBeat.put(deviceId, now);
        dbExecutor.execute(() -> {
            try {
                deviceRepository.findById(deviceId).ifPresent(d -> {
                    d.setLastOnlineAt(java.time.LocalDateTime.now());
                    deviceRepository.save(d);
                });
            } catch (Exception e) {
                log.warn("onHeartbeat error: {}", e.getMessage());
            }
        });
    }

    /** 掉线:非虚拟设备置 OFFLINE + 设备日志 + 广播 */
    public void onOffline(Long deviceId) {
        dbExecutor.execute(() -> {
            try {
                Device device = deviceRepository.findById(deviceId).orElse(null);
                if (device == null || Boolean.TRUE.equals(device.getVirtual())) {
                    return;
                }
                if (device.getStatus() != Device.Status.OFFLINE) {
                    device.setStatus(Device.Status.OFFLINE);
                    deviceRepository.save(device);
                }
                trackCache.remove(deviceId);
                lastBeat.remove(deviceId);
                logRepository.save(new SysLog(SysLog.Type.DEVICE, device.getCode(), "设备离线",
                        "连接断开或心跳超时", device.getLastIp(), true));
                broadcastStatus(device);
                log.info("Device {} offline", device.getCode());
            } catch (Exception e) {
                log.error("onOffline error: {}", e.getMessage());
            }
        });
    }

    /** DATA 帧:按协议模板解析;DRONE 含经纬度→组装遥测广播,其余→原始字段广播;解析结果落历史 */
    public void onData(Long deviceId, byte[] payload) {
        dbExecutor.execute(() -> {
            try {
                Device device = deviceRepository.findById(deviceId).orElse(null);
                if (device == null) {
                    return;
                }
                refreshOnlineQuietly(device);
                ProtocolTemplate protocol = device.getProtocol();
                if (protocol == null) {
                    logRepository.save(new SysLog(SysLog.Type.DEVICE, device.getCode(), "解析失败",
                            "设备未绑定协议模板", device.getLastIp(), false));
                    return;
                }
                // 按 frameFormat(TLV/FIXED/MODBUS)分发解析,支持多进制数据拆分;坏帧经 _error 呈现并记设备日志
                Map<String, Object> fields = ProtocolEngine.parse(protocol, payload);
                Object parseError = fields.get(ProtocolEngine.ERROR_KEY);
                if (parseError != null) {
                    dbLogQuietly(deviceId, "解析失败", String.valueOf(parseError));
                }
                emitFields(device, fields);
            } catch (Exception e) {
                log.error("onData error: {}", e.getMessage());
                dbLogQuietly(deviceId, "解析异常", e.getMessage());
            }
        });
    }

    /** 已解析字段入口(Modbus 寄存器写入等网关侧已拆分场景):刷新在线 + 广播 + 落历史 */
    public void onFields(Long deviceId, Map<String, Object> fields) {
        dbExecutor.execute(() -> {
            try {
                Device device = deviceRepository.findById(deviceId).orElse(null);
                if (device == null || fields == null || fields.isEmpty()) {
                    return;
                }
                refreshOnlineQuietly(device);
                emitFields(device, fields);
            } catch (Exception e) {
                log.error("onFields error: {}", e.getMessage());
            }
        });
    }

    /** 广播 + 历史留痕(dbExecutor 线程池内调用):无人机→telemetry,其余→deviceData */
    private void emitFields(Device device, Map<String, Object> fields) {
        if (device.getCategory() == Device.Category.DRONE
                && fields.containsKey("lng") && fields.containsKey("lat")) {
            wsHandler.broadcast("telemetry", List.of(buildTelemetry(device, fields)));
        } else {
            Map<String, Object> msg = new LinkedHashMap<>();
            msg.put("deviceId", device.getId());
            msg.put("deviceCode", device.getCode());
            msg.put("name", device.getName());
            msg.put("category", device.getCategory().name());
            msg.put("fields", fields);
            msg.put("ts", System.currentTimeMillis());
            wsHandler.broadcast("deviceData", msg);
        }
        saveHistoryQuietly(device, fields);
        if (device.getCategory() == Device.Category.DRONE) {
            // 威胁感知喂入(轨迹预测/多机冲突/遥测异常,内部自带节流与容错)
            threatService.onTelemetry(device, null, fields);
        }
    }

    private void saveHistoryQuietly(Device device, Map<String, Object> fields) {
        try {
            String json = objectMapper.writeValueAsString(fields);
            if (json.length() > 4000) {
                json = json.substring(0, 4000);
            }
            historyRepository.save(new DeviceDataHistory(device.getId(), device.getCode(),
                    device.getCategory(), json));
            if (historyInserts.incrementAndGet() % TRIM_EVERY == 0) {
                trimHistory(device.getId());
            }
        } catch (Exception e) {
            log.warn("Save history error (device {}): {}", device.getCode(), e.getMessage());
        }
    }

    /** Modbus TCP 从站单元号寻址(9529 网关) */
    public CompletableFuture<Device> findDeviceByModbusUnit(int unitId) {
        CompletableFuture<Device> future = new CompletableFuture<>();
        dbExecutor.execute(() -> {
            try {
                future.complete(deviceRepository.findByModbusUnitId(unitId).orElse(null));
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    /**
     * Modbus FC16(写多寄存器)数据接入:寄存器合并缓存 + unitId 寻址设备 →
     * MODBUS 模板 regMap 解析 → 广播/落历史 + 设备上线;设备不存在返回 null(应答异常码 0x0B)。
     */
    public CompletableFuture<Device> onModbusWrite(int unitId, int startAddr, int[] words, String ip) {
        CompletableFuture<Device> future = new CompletableFuture<>();
        dbExecutor.execute(() -> {
            try {
                Map<Integer, Integer> wm = modbusWords.computeIfAbsent(unitId, k -> new ConcurrentHashMap<>());
                for (int i = 0; i < words.length; i++) {
                    wm.put(startAddr + i, words[i] & 0xFFFF);
                }
                persistModbusWords(unitId, startAddr, words);
                Device device = deviceRepository.findByModbusUnitId(unitId).orElse(null);
                if (device == null) {
                    future.complete(null);
                    return;
                }
                if (device.getStatus() != Device.Status.ONLINE) {
                    device.setStatus(Device.Status.ONLINE);
                    device.setLastIp(ip);
                    broadcastStatus(device);
                }
                ProtocolTemplate protocol = device.getProtocol();
                if (protocol != null) {
                    // 重建 0..maxReg 全量寄存器字节(reg N → 偏移 N*2,大端),交给解析引擎
                    int maxReg = wm.keySet().stream().max(Integer::compareTo).orElse(0);
                    byte[] regData = new byte[(maxReg + 1) * 2];
                    wm.forEach((reg, w) -> {
                        regData[reg * 2] = (byte) (w >> 8);
                        regData[reg * 2 + 1] = (byte) w.intValue();
                    });
                    refreshOnlineQuietly(device);
                    emitFields(device, ProtocolEngine.parse(protocol, regData));
                }
                future.complete(device);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    /** FC16 写入落库(重启回加载);失败仅告警不影响接入主流程 */
    private void persistModbusWords(int unitId, int startAddr, int[] words) {
        try {
            Map<Integer, ModbusRegister> existing = new HashMap<>();
            for (ModbusRegister r : modbusRegisterRepository.findAllByUnitId(unitId)) {
                existing.put(r.getAddr(), r);
            }
            List<ModbusRegister> changed = new ArrayList<>(words.length);
            for (int i = 0; i < words.length; i++) {
                int addr = startAddr + i;
                int value = words[i] & 0xFFFF;
                ModbusRegister r = existing.get(addr);
                if (r == null) {
                    r = new ModbusRegister(unitId, addr, value);
                } else if (r.getValue() != null && r.getValue() == value) {
                    continue;    // 值未变跳过,减少无谓 UPDATE
                } else {
                    r.setValue(value);
                }
                r.setUpdatedAt(java.time.LocalDateTime.now());
                changed.add(r);
            }
            if (!changed.isEmpty()) {
                modbusRegisterRepository.saveAll(changed);
            }
        } catch (Exception e) {
            log.warn("Persist modbus registers failed (unit {}): {}", unitId, e.getMessage());
        }
    }

    /** Modbus FC3/FC4 读取应答:从寄存器缓存取字(纯内存,EventLoop 可直接调用),未写过返回 0 */
    public int[] readModbusRegisters(int unitId, int startAddr, int qty) {
        Map<Integer, Integer> wm = modbusWords.getOrDefault(unitId, Map.of());
        int[] out = new int[qty];
        for (int i = 0; i < qty; i++) {
            out[i] = wm.getOrDefault(startAddr + i, 0);
        }
        return out;
    }

    /** 任意字节帧留痕(DTU 透传/Modbus 等非 DeviceFrame 语法接入) */
    public void logRawFrame(Long deviceId, String deviceCode, String frameType, byte[] frame) {
        dbExecutor.execute(() -> {
            try {
                saveMessage(new DeviceMessage(deviceId, deviceCode, DeviceMessage.Direction.UP,
                        frameType, frame, true));
            } catch (Exception e) {
                log.warn("logRawFrame error: {}", e.getMessage());
            }
        });
    }

    /** 上行整帧留痕:重编码(组帧确定且 CRC 已过,与线上字节一致);心跳按开关决定是否记录 */
    public void logUpFrame(DeviceFrame frame, Long deviceId) {
        if (frame.type() == DeviceFrame.TYPE_HEARTBEAT && !logHeartbeat) {
            return;
        }
        dbExecutor.execute(() -> {
            try {
                byte[] full = DeviceFrame.encode(frame.type(), frame.code(), frame.payload());
                saveMessage(new DeviceMessage(deviceId, frame.code(), DeviceMessage.Direction.UP,
                        DeviceFrame.typeName(frame.type()), full, true));
            } catch (Exception e) {
                log.warn("logUpFrame error: {}", e.getMessage());
            }
        });
    }

    /** 下行整帧留痕(ACK / COMMAND 等) */
    public void logDownFrame(Long deviceId, String deviceCode, String frameType, byte[] frame) {
        dbExecutor.execute(() -> {
            try {
                saveMessage(new DeviceMessage(deviceId, deviceCode, DeviceMessage.Direction.DOWN,
                        frameType, frame, true));
            } catch (Exception e) {
                log.warn("logDownFrame error: {}", e.getMessage());
            }
        });
    }

    private void saveMessage(DeviceMessage m) {
        if (m.getContentHex() != null && m.getContentHex().length() > MAX_HEX_LEN) {
            m.setContentHex(m.getContentHex().substring(0, MAX_HEX_LEN));
        }
        messageRepository.save(m);
        if (msgInserts.incrementAndGet() % TRIM_EVERY == 0) {
            trimMessages();
        }
    }

    /** 只保留最近 messageKeep 条(多线程池下 synchronized 串行,避免裁剪竞态) */
    private synchronized void trimMessages() {
        long total = messageRepository.count();
        if (total > messageKeep) {
            messageRepository.deleteAllById(
                    messageRepository.findIdsAsc(PageRequest.of(0, (int) (total - messageKeep))));
        }
    }

    /** 每设备历史裁剪,同上串行化 */
    private synchronized void trimHistory(Long deviceId) {
        historyRepository.trimPerDevice(deviceId, historyKeep);
    }

    /** 组装无人机遥测(字段名与 FlightSimulator 输出保持一致,Monitor 零改动) */
    private TelemetryDto buildTelemetry(Device device, Map<String, Object> fields) {
        TelemetryDto dto = new TelemetryDto();
        dto.setDroneId(device.getId());
        dto.setDroneCode(device.getCode());
        dto.setModel(device.getModel());
        dto.setStatus("ONLINE");
        dto.setLng(d(fields, "lng"));
        dto.setLat(d(fields, "lat"));
        dto.setAltitude(d(fields, "altitude"));
        dto.setSpeed(d(fields, "speed"));
        dto.setHeading(d(fields, "heading"));
        dto.setBattery(d(fields, "battery"));
        dto.setSatellites((int) d(fields, "satellites"));

        List<Map<String, Double>> track = trackCache.computeIfAbsent(device.getId(), k -> new ArrayList<>());
        Map<String, Double> point = new HashMap<>();
        point.put("lng", dto.getLng());
        point.put("lat", dto.getLat());
        track.add(point);
        if (track.size() > TRACK_LIMIT) {
            track.remove(0);
        }
        dto.setTrack(new ArrayList<>(track));
        return dto;
    }

    private static double d(Map<String, Object> fields, String key) {
        Object v = fields.get(key);
        return v instanceof Number n ? n.doubleValue() : 0;
    }

    /** DATA 帧在线刷新:10s 节流,避免每帧一次 UPDATE */
    private void refreshOnlineQuietly(Device device) {
        long now = System.currentTimeMillis();
        Long last = lastOnlinePersist.get(device.getId());
        if (last != null && now - last < ONLINE_PERSIST_MS) {
            return;
        }
        lastOnlinePersist.put(device.getId(), now);
        device.setLastOnlineAt(java.time.LocalDateTime.now());
        deviceRepository.save(device);
    }

    private void dbLogQuietly(Long deviceId, String action, String detail) {
        try {
            deviceRepository.findById(deviceId).ifPresent(d ->
                    logRepository.save(new SysLog(SysLog.Type.DEVICE, d.getCode(), action,
                            detail == null ? "" : detail.substring(0, Math.min(500, detail.length())),
                            d.getLastIp(), false)));
        } catch (Exception ignored) {
        }
    }

    private void broadcastStatus(Device device) {
        Map<String, Object> msg = new HashMap<>();
        msg.put("deviceId", device.getId());
        msg.put("deviceCode", device.getCode());
        msg.put("status", device.getStatus().name());
        wsHandler.broadcast("deviceStatus", msg);
    }
}
