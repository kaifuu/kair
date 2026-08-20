package com.wrj.platform.service;

import com.wrj.platform.entity.Device;
import com.wrj.platform.repository.DeviceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 物联网虚拟设备模拟引擎(与 FlightSimulator 同构,仅驱动 virtual=true 的非无人机设备):
 * 每 5s 生成一轮贴近物理规律的传感数据(正弦基线 + 抖动)→ 广播 deviceData + 落历史,
 * 为实时监控的物联网面板、视频监控面板与设备历史曲线提供开箱数据。
 */
@Component
public class IoTSimulator {

    private static final Logger log = LoggerFactory.getLogger(IoTSimulator.class);

    private final DeviceRepository deviceRepository;
    private final DeviceEventService eventService;

    /** 各设备相位(同类别设备曲线错开) */
    private final Map<Long, Double> phases = new java.util.concurrent.ConcurrentHashMap<>();

    public IoTSimulator(DeviceRepository deviceRepository,
                        DeviceEventService eventService) {
        this.deviceRepository = deviceRepository;
        this.eventService = eventService;
    }

    @Scheduled(fixedDelay = 5000)
    public void tick() {
        try {
            List<Device> devices = deviceRepository.findByVirtualTrue();
            for (Device device : devices) {
                if (device.getCategory() == Device.Category.DRONE) {
                    continue;    // 无人机由 FlightSimulator 驱动
                }
                if (device.getStatus() != Device.Status.ONLINE) {
                    eventService.onOnline(device.getId(), "simulator");
                }
                eventService.onFields(device.getId(), synthesize(device));
            }
        } catch (Exception e) {
            log.warn("IoT simulator tick error: {}", e.getMessage());
        }
    }

    /** 按设备分类合成传感字段:基线正弦 + 随机抖动,字段名与协议模板/前端面板一致 */
    private Map<String, Object> synthesize(Device device) {
        double phase = phases.computeIfAbsent(device.getId(), k -> Math.random() * Math.PI * 2);
        double t = System.currentTimeMillis() / 1000.0;
        Map<String, Object> f = new LinkedHashMap<>();
        switch (device.getCategory()) {
            case WEATHER -> {
                f.put("temperature", round(sin(t, 60, 14, phase) + 21 + jitter(0.6)));
                f.put("humidity", round(58 + 15 * Math.sin(t / 90 + phase) + jitter(3), 1));
                f.put("windSpeed", round(3.2 + 1.5 * Math.sin(t / 45 + phase) + jitter(0.4), 1));
                f.put("pressure", Math.round(1008 + 6 * Math.sin(t / 300 + phase) + jitter(0.8)));
            }
            case CAMERA -> {
                f.put("fps", 25);
                f.put("bitrateKbps", (int) (3800 + 500 * Math.sin(t / 30 + phase) + jitter(120)));
                f.put("online", 1);
                f.put("alarms", Math.random() < 0.03 ? 1 : 0);
            }
            case ADSB -> {
                f.put("aircraft", (int) (4 + 3 * Math.abs(Math.sin(t / 120 + phase)) + jitter(1)));
            }
            case DOCK -> {
                f.put("chargePct", Math.round(88 + 10 * Math.sin(t / 600 + phase)));
                f.put("doorState", Math.sin(t / 240 + phase) > 0.9 ? "OPEN" : "CLOSED");
                f.put("temperature", round(26 + 4 * Math.sin(t / 120 + phase) + jitter(0.5)));
            }
            default -> synthesizeByCode(device, t, phase, f);
        }
        return f;
    }

    /** SENSOR 类按编码前缀区分量纲:NS-噪声 / AQ-空气质量 / WL-水位,其余给通用遥测 */
    private void synthesizeByCode(Device device, double t, double phase, Map<String, Object> f) {
        String code = device.getCode() == null ? "" : device.getCode().toUpperCase();
        if (code.startsWith("NS")) {
            f.put("noiseDb", round(58 + 12 * Math.abs(Math.sin(t / 70 + phase)) + jitter(1.5), 1));
        } else if (code.startsWith("AQ")) {
            f.put("pm25", Math.round(42 + 25 * Math.sin(t / 180 + phase) + jitter(4)));
            f.put("pm10", Math.round(66 + 30 * Math.sin(t / 180 + phase) + jitter(5)));
            f.put("co2", Math.round(420 + 80 * Math.sin(t / 240 + phase) + jitter(15)));
        } else if (code.startsWith("WL")) {
            f.put("waterLevelM", round(2.4 + 0.6 * Math.sin(t / 300 + phase) + jitter(0.05), 2));
            f.put("flowRate", round(6.5 + 2 * Math.sin(t / 120 + phase) + jitter(0.3), 1));
        } else {
            f.put("signal", Math.round(-62 + 8 * Math.sin(t / 60 + phase)));
            f.put("battery", Math.round(92 - (t % 86400) / 86400.0 * 30));
        }
    }

    private static double sin(double t, double periodSec, double amp, double phase) {
        return amp * Math.sin(2 * Math.PI * t / periodSec + phase);
    }

    private static double jitter(double amp) {
        return (Math.random() - 0.5) * 2 * amp;
    }

    private static double round(double v, int scale) {
        double f = Math.pow(10, scale);
        return Math.round(v * f) / f;
    }

    private static double round(double v) {
        return round(v, 1);
    }
}
