package com.wrj.platform.gateway;

import java.util.ArrayList;
import java.util.List;

/** 报文内容多进制编解码:byte[] ↔ BIN/OCT/DEC/HEX 文本(空格分隔) */
public final class BaseCodec {

    private BaseCodec() {
    }

    /** 进制名 → 基数(bin/oct/dec/hex,忽略大小写) */
    public static int radixOf(String base) {
        String b = base == null ? "" : base.trim().toLowerCase();
        return switch (b) {
            case "bin" -> 2;
            case "oct" -> 8;
            case "dec" -> 10;
            case "hex" -> 16;
            default -> throw new IllegalArgumentException("不支持的进制: " + base + "(可用 bin/oct/dec/hex)");
        };
    }

    /** 文本 → 字节:空白/逗号分隔,容忍与本进制匹配的 0x/0b/0o 前缀,逐 token 校验 0..255 */
    public static byte[] decode(String text, String base) {
        int radix = radixOf(base);
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("报文内容不能为空");
        }
        String[] tokens = text.trim().split("[\\s,]+");
        byte[] out = new byte[tokens.length];
        for (int i = 0; i < tokens.length; i++) {
            String t = stripPrefix(tokens[i].toLowerCase(), radix);
            final long v;
            try {
                v = Long.parseLong(t, radix);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("第 " + (i + 1) + " 个字节非法: \"" + tokens[i]
                        + "\" 不是有效的" + baseName(radix) + "字节(0..255)");
            }
            if (v < 0 || v > 255) {
                throw new IllegalArgumentException("第 " + (i + 1) + " 个字节越界: " + tokens[i]
                        + "(超出 0..255)");
            }
            out[i] = (byte) v;
        }
        return out;
    }

    /** 字节 → 文本:hex 两位大写 / bin 补满 8 位 / oct、dec 不补位,空格分隔 */
    public static String encode(byte[] bytes, String base) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        int radix = radixOf(base);
        List<String> parts = new ArrayList<>(bytes.length);
        for (byte b : bytes) {
            int v = b & 0xFF;
            parts.add(switch (radix) {
                case 16 -> String.format("%02X", v);
                case 8 -> Integer.toOctalString(v);
                case 2 -> String.format("%8s", Integer.toBinaryString(v)).replace(' ', '0');
                default -> String.valueOf(v);
            });
        }
        return String.join(" ", parts);
    }

    private static String stripPrefix(String token, int radix) {
        return switch (radix) {
            case 16 -> token.replaceFirst("^0x", "");
            case 8 -> token.replaceFirst("^0o", "");
            case 2 -> token.replaceFirst("^0b", "");
            default -> token;
        };
    }

    private static String baseName(int radix) {
        return switch (radix) {
            case 16 -> "十六进制";
            case 8 -> "八进制";
            case 2 -> "二进制";
            default -> "十进制";
        };
    }
}
