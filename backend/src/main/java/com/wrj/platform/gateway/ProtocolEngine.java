package com.wrj.platform.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wrj.platform.dto.ProtocolConfig;
import com.wrj.platform.dto.TlvRule;
import com.wrj.platform.entity.ProtocolTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 协议解析引擎统一入口:按协议模板 frameFormat 分发。
 * - TLV:tag/length/value,头参数(tagLen/lenLen/字节序)可配
 * - FIXED:定长偏移切分(二/八/十/十六进制数据拆分)
 * - MODBUS:寄存器映射(输入为寄存器数据字节,每寄存器 2 字节大端)
 * 供 Netty 网关 onData 与协议管理页「解析测试」共用。
 *
 * 错误可见化约定:解析异常/规则 JSON 损坏时结果中携带 {@link #ERROR_KEY}(前端与 WS 可见),
 * 不再静默返回空 Map;未命中任何字段时携带 {@link #WARN_KEY} 提示排查。
 *
 * 规则缓存:按模板 id 缓存已解析的 (rules, config),每帧以字符串等值比对判新——
 * JSON 未变则免重复解析,一变即刻重建,无 TTL 陈旧窗口。
 */
public final class ProtocolEngine {

    private static final Logger log = LoggerFactory.getLogger(ProtocolEngine.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 解析失败标记(值为错误说明,进入结果 Map 与 WS 载荷) */
    public static final String ERROR_KEY = "_error";
    /** 未解析出字段的可疑标记(规则 tag 与帧不匹配等) */
    public static final String WARN_KEY = "_warn";

    private ProtocolEngine() {
    }

    /** 已解析模板缓存:id → (原始 JSON + 解析产物),JSON 等值比对失效 */
    private static final Map<Long, CachedTemplate> CACHE = new ConcurrentHashMap<>();

    private record CachedTemplate(String rulesJson, String configJson, ProtocolTemplate.FrameFormat ff,
                                  List<TlvRule> rules, ProtocolConfig cfg) {
    }

    /** 按模板解析 payload;异常字段跳过不抛出,坏帧以 _error 呈现,不影响整批 */
    public static Map<String, Object> parse(ProtocolTemplate protocol, byte[] payload) {
        Map<String, Object> out = new LinkedHashMap<>();
        CachedTemplate t;
        try {
            t = cached(protocol);
        } catch (Exception e) {
            // 规则/配置 JSON 损坏:必须可见,不允许静默吞掉
            log.error("协议配置损坏(template {}): {}", protocol.getId(), e.getMessage());
            out.put(ERROR_KEY, "协议配置解析失败: " + e.getMessage());
            return out;
        }
        try {
            switch (t.ff()) {
                case FIXED -> parseFixed(payload, t.cfg(), out);
                case MODBUS -> parseModbus(payload, t.cfg(), out);
                default -> out.putAll(TlvParser.parse(payload, t.rules(), t.cfg()));
            }
        } catch (Exception e) {
            log.warn("Protocol parse failed (template {}): {}", protocol.getId(), e.getMessage());
            out.put(ERROR_KEY, "解析异常: " + e.getMessage());
        }
        if (!out.containsKey(ERROR_KEY) && out.isEmpty()) {
            out.put(WARN_KEY, "未解析出任何字段(检查规则 tag/偏移与实际帧是否匹配)");
        }
        return out;
    }

    /** TLV 规则解析(JSON 损坏时抛出,由 parse 统一转 _error) */
    public static List<TlvRule> readRules(ProtocolTemplate protocol) {
        return parseRules(protocol.getRulesJson() == null ? "[]" : protocol.getRulesJson());
    }

    public static ProtocolConfig readConfig(ProtocolTemplate protocol) {
        return parseConfig(protocol.getConfigJson() == null || protocol.getConfigJson().isBlank()
                ? "{}" : protocol.getConfigJson());
    }

    private static List<TlvRule> parseRules(String json) {
        try {
            return MAPPER.readValue(json, MAPPER.getTypeFactory().constructCollectionType(List.class, TlvRule.class));
        } catch (Exception e) {
            throw new IllegalStateException("rulesJson 解析失败: " + e.getMessage(), e);
        }
    }

    private static ProtocolConfig parseConfig(String json) {
        try {
            return MAPPER.readValue(json, ProtocolConfig.class);
        } catch (Exception e) {
            throw new IllegalStateException("configJson 解析失败: " + e.getMessage(), e);
        }
    }

    /** 缓存命中则复用解析产物;JSON 或帧格式变化即刻重建 */
    private static CachedTemplate cached(ProtocolTemplate p) {
        long id = p.getId();
        String rj = p.getRulesJson() == null ? "[]" : p.getRulesJson();
        String cj = p.getConfigJson() == null || p.getConfigJson().isBlank() ? "{}" : p.getConfigJson();
        ProtocolTemplate.FrameFormat ff = p.getFrameFormat() == null
                ? ProtocolTemplate.FrameFormat.TLV : p.getFrameFormat();
        CachedTemplate hit = CACHE.get(id);
        if (hit != null && hit.ff() == ff && hit.rulesJson().equals(rj) && hit.configJson().equals(cj)) {
            return hit;
        }
        CachedTemplate built = new CachedTemplate(rj, cj, ff, parseRules(rj), parseConfig(cj));
        CACHE.put(id, built);
        return built;
    }

    /** 协议模板增删改后清缓存(控制器调用) */
    public static void evict(Long protocolId) {
        if (protocolId != null) {
            CACHE.remove(protocolId);
        }
    }

    /** 单测隔离用 */
    static void evictAll() {
        CACHE.clear();
    }

    /** 定长偏移切分:offset/len 切片 → type/scale/radix 转换 */
    private static void parseFixed(byte[] payload, ProtocolConfig cfg, Map<String, Object> out) {
        List<ProtocolConfig.FixedField> fields = cfg.fields();
        if (fields == null) {
            out.put(WARN_KEY, "FIXED 帧未配置 fields 偏移切分表");
            return;
        }
        for (ProtocolConfig.FixedField f : fields) {
            if (f.field() == null || f.field().isBlank()) {
                continue;
            }
            if (f.offset() < 0 || f.len() <= 0 || f.offset() + f.len() > payload.length) {
                out.put(f.field(), null);    // 越界字段置空,便于前端发现帧长不匹配
                continue;
            }
            byte[] slice = Arrays.copyOfRange(payload, f.offset(), f.offset() + f.len());
            if ("string".equals(f.type())) {
                out.put(f.field(), ValueCodec.utf8(slice));
            } else if ("hex".equals(f.type())) {
                out.put(f.field(), ValueCodec.hex(slice));
            } else {
                out.put(f.field(), ValueCodec.convert(slice, f.type(), f.scale(), f.radix()));
            }
        }
    }

    /** Modbus 寄存器映射:payload 为寄存器数据字节(reg N 对应字节偏移 N*2,大端) */
    private static void parseModbus(byte[] payload, ProtocolConfig cfg, Map<String, Object> out) {
        List<ProtocolConfig.ModbusReg> regs = cfg.regMap();
        if (regs == null) {
            out.put(WARN_KEY, "MODBUS 帧未配置 regMap 寄存器映射");
            return;
        }
        for (ProtocolConfig.ModbusReg r : regs) {
            if (r.field() == null || r.field().isBlank()) {
                continue;
            }
            int off = r.reg() * 2;
            int len = r.count() * 2;
            if (off < 0 || len <= 0 || off + len > payload.length) {
                out.put(r.field(), null);
                continue;
            }
            byte[] slice = Arrays.copyOfRange(payload, off, off + len);
            out.put(r.field(), ValueCodec.convert(slice, r.type(), r.scale(), 10));
        }
    }
}
