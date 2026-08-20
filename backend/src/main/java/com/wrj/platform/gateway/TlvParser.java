package com.wrj.platform.gateway;

import com.wrj.platform.dto.ProtocolConfig;
import com.wrj.platform.dto.TlvRule;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * TLV 报文解析:tag + length + value,按协议模板规则映射为字段 Map。
 * 头参数可配(configJson):tagLen(1/2/4B)、lenLen(1/2/4B)、littleEndian(长度字段字节序);
 * 缺省 tag=1B、len=2B 大端,与标准 AA55 帧设备保持兼容。
 */
public final class TlvParser {

    private TlvParser() {
    }

    /** 兼容入口:标准头(tag 1B + len 2B 大端) */
    public static Map<String, Object> parse(byte[] payload, List<TlvRule> rules) {
        return parse(payload, rules, null);
    }

    public static Map<String, Object> parse(byte[] payload, List<TlvRule> rules, ProtocolConfig cfg) {
        int tagLen = clamp(cfg == null || cfg.tagLen() == null ? 1 : cfg.tagLen(), 1, 4);
        int lenLen = clamp(cfg == null || cfg.lenLen() == null ? 2 : cfg.lenLen(), 1, 4);
        boolean little = cfg != null && Boolean.TRUE.equals(cfg.littleEndian());

        Map<Integer, TlvRule> byTag = new LinkedHashMap<>();
        for (TlvRule r : rules) {
            byTag.put(r.tag(), r);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        ByteBuffer buf = ByteBuffer.wrap(payload);
        while (buf.remaining() >= tagLen + lenLen) {
            long tag = readUnsigned(buf, tagLen);
            int len = (int) readLen(buf, lenLen, little);
            if (len < 0 || buf.remaining() < len) {
                break;    // 半截 TLV,丢弃
            }
            byte[] v = new byte[len];
            buf.get(v);
            TlvRule rule = byTag.get((int) tag);
            if (rule != null && rule.field() != null && !rule.field().isBlank()) {
                if ("string".equals(rule.type())) {
                    out.put(rule.field(), ValueCodec.utf8(v));
                } else if ("hex".equals(rule.type())) {
                    out.put(rule.field(), ValueCodec.hex(v));
                } else {
                    out.put(rule.field(), rule.convert(v));
                }
            }
        }
        // 尾部残留(半截 TLV 或多余字节)以 _residual 呈现,便于发现粘包/截断
        if (buf.hasRemaining()) {
            byte[] rest = new byte[buf.remaining()];
            buf.get(rest);
            String hex = ValueCodec.hex(rest.length > 32 ? Arrays.copyOf(rest, 32) : rest);
            out.put(ProtocolEngine.ERROR_KEY, "TLV 尾部残留 " + rest.length + " 字节(半截帧或粘包): " + hex);
        }
        return out;
    }

    /** REGISTER payload:取 tag=0x01 的 secret 字符串(标准头,注册路径不随模板变化) */
    public static String parseSecret(byte[] payload) {
        ByteBuffer buf = ByteBuffer.wrap(payload);
        while (buf.remaining() >= 3) {
            int tag = buf.get() & 0xFF;
            int len = buf.getShort() & 0xFFFF;
            if (buf.remaining() < len) {
                return null;
            }
            byte[] v = new byte[len];
            buf.get(v);
            if (tag == 0x01) {
                return ValueCodec.utf8(v);
            }
        }
        return null;
    }

    private static long readUnsigned(ByteBuffer buf, int len) {
        long out = 0;
        for (int i = 0; i < len; i++) {
            out = (out << 8) | (buf.get() & 0xFF);
        }
        return out;
    }

    private static long readLen(ByteBuffer buf, int len, boolean little) {
        if (!little) {
            return readUnsigned(buf, len);
        }
        long out = 0;
        for (int i = 0; i < len; i++) {
            out |= (buf.get() & 0xFFL) << (8 * i);
        }
        return out;
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
