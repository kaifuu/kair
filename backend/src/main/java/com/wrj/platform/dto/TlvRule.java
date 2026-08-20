package com.wrj.platform.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * TLV 解析规则:tag → 字段映射,type 决定字节读取方式,最终值 = 原始值 × scale。
 * radix 指定消息体值的解析进制(2/8/16 时按原始码值输出 0b…/0o…/0x… 字符串,不乘 scale)。
 * JSON 形如 {"tag":1,"field":"lng","type":"uint32","scale":1.0E-6,"unit":"deg","radix":10}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TlvRule(int tag, String field, String type, Double scale, String unit, Integer radix) {

    public Double scaleOrDefault() {
        return scale == null || scale == 0 ? 1.0 : scale;
    }

    public int radixOrDefault() {
        return radix == null ? 10 : radix;
    }

    /** 按类型读取字节值:radix=10 乘 scale 保持数值语义,其余进制按码值格式化呈现 */
    public Object convert(byte[] v) {
        Number raw = readRaw(v);
        if (raw == null) {
            return hex(v);
        }
        if (radixOrDefault() != 10) {
            return formatRadix(raw.longValue(), radixOrDefault(), bitsOf(type));
        }
        double scaled = raw.doubleValue() * scaleOrDefault();
        // scale 为 1 时保持整数语义
        if (scaled == Math.floor(scaled) && !Double.isInfinite(scaled)
                && Math.abs(scaled) < Long.MAX_VALUE && scaleOrDefault() == 1.0) {
            return (long) scaled;
        }
        return scaled;
    }

    private Number readRaw(byte[] v) {
        try {
            ByteBuffer buf = ByteBuffer.wrap(v);
            return switch (type == null ? "hex" : type) {
                case "uint8" -> v.length >= 1 ? (buf.get() & 0xFF) : null;
                case "uint16" -> v.length >= 2 ? (buf.getShort() & 0xFFFF) : null;
                case "uint32" -> v.length >= 4 ? (buf.getInt() & 0xFFFFFFFFL) : null;
                case "int16" -> v.length >= 2 ? buf.getShort() : null;
                case "int32" -> v.length >= 4 ? buf.getInt() : null;
                case "float32" -> v.length >= 4 ? buf.getFloat() : null;
                default -> null;
            };
        } catch (Exception e) {
            return null;
        }
    }

    public static String hex(byte[] v) {
        if (v == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : v) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }

    /** 数值类型的位宽(hex/bin 按位宽补齐输出) */
    private static int bitsOf(String type) {
        return switch (type == null ? "" : type) {
            case "uint8" -> 8;
            case "uint16", "int16" -> 16;
            case "uint32", "int32" -> 32;
            default -> 8;
        };
    }

    /** 码值按进制格式化:负数(有符号类型)先转位宽内无符号补码 */
    private static String formatRadix(long raw, int radix, int bits) {
        long u = raw & ((1L << bits) - 1);
        return switch (radix) {
            case 16 -> "0x" + String.format("%0" + (bits / 4) + "X", u);
            case 8 -> "0o" + Long.toOctalString(u);
            case 2 -> "0b" + String.format("%" + bits + "s", Long.toBinaryString(u)).replace(' ', '0');
            default -> String.valueOf(u);
        };
    }

    public static String utf8(byte[] v) {
        return v == null ? "" : new String(v, StandardCharsets.UTF_8);
    }
}
