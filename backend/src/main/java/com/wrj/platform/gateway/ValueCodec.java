package com.wrj.platform.gateway;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * 字节值统一转换:type 字节读取(uint8/16/32、int16/32、float32)× scale 缩放 × radix 进制呈现。
 * TLV / FIXED 定长切分 / MODBUS 寄存器映射三种引擎共用,保证多进制拆分口径一致。
 */
public final class ValueCodec {

    private ValueCodec() {
    }

    /** 按类型读取字节值并应用 scale/进制:radix=10 → 数值(×scale),2/8/16 → 码值字符串(0b…/0o…/0x…) */
    public static Object convert(byte[] v, String type, Double scale, Integer radix) {
        Number raw = readRaw(v, type);
        if (raw == null) {
            return hex(v);
        }
        int rad = radix == null ? 10 : radix;
        if (rad != 10) {
            return formatRadix(raw.longValue(), rad, bitsOf(type));
        }
        double s = (scale == null || scale == 0) ? 1.0 : scale;
        double scaled = raw.doubleValue() * s;
        if (scaled == Math.floor(scaled) && !Double.isInfinite(scaled)
                && Math.abs(scaled) < Long.MAX_VALUE && s == 1.0) {
            return (long) scaled;
        }
        return scaled;
    }

    public static Number readRaw(byte[] v, String type) {
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

    /** 大端读取指定字节数的无符号值(寄存器/偏移切片场景,长度 1/2/4) */
    public static Number readUnsigned(byte[] v, int off, int len) {
        long out = 0;
        for (int i = 0; i < len; i++) {
            out = (out << 8) | (v[off + i] & 0xFF);
        }
        return out;
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

    public static String utf8(byte[] v) {
        return v == null ? "" : new String(v, StandardCharsets.UTF_8);
    }

    /** 数值类型的位宽(hex/bin 按位宽补齐输出) */
    public static int bitsOf(String type) {
        return switch (type == null ? "" : type) {
            case "uint16", "int16" -> 16;
            case "uint32", "int32" -> 32;
            default -> 8;
        };
    }

    /** 码值按进制格式化:负数(有符号类型)先转位宽内无符号补码 */
    public static String formatRadix(long raw, int radix, int bits) {
        long u = raw & ((1L << bits) - 1);
        return switch (radix) {
            case 16 -> "0x" + String.format("%0" + (bits / 4) + "X", u);
            case 8 -> "0o" + Long.toOctalString(u);
            case 2 -> "0b" + String.format("%" + bits + "s", Long.toBinaryString(u)).replace(' ', '0');
            default -> String.valueOf(u);
        };
    }
}
