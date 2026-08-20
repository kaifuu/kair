package com.wrj.platform.gateway;

import com.wrj.platform.dto.ProtocolConfig;
import com.wrj.platform.dto.TlvRule;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** TLV 解析:标准头/小端长度/残帧/未知 tag/注册密钥 */
class TlvParserTest {

    /** 标准头组帧:tag 1B + len 2B 大端 + value */
    private static byte[] tlv(int tag, byte[] value) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(tag & 0xFF);
        out.write((value.length >> 8) & 0xFF);
        out.write(value.length & 0xFF);
        out.writeBytes(value);
        return out.toByteArray();
    }

    private static byte[] be32(long v) {
        return new byte[]{(byte) (v >> 24), (byte) (v >> 16), (byte) (v >> 8), (byte) v};
    }

    @Test
    void 标准头多字段解析() {
        List<TlvRule> rules = List.of(
                new TlvRule(1, "lng", "uint32", 1.0E-6, "deg", 10),
                new TlvRule(2, "battery", "uint8", 1.0, "%", 10),
                new TlvRule(3, "code", "string", null, null, 10));
        byte[] payload = concat(
                tlv(1, be32(116407357L)),
                tlv(2, new byte[]{(byte) 85}),
                tlv(3, "UAV-01".getBytes(StandardCharsets.UTF_8)));

        Map<String, Object> out = TlvParser.parse(payload, rules);

        assertThat((Double) out.get("lng")).isCloseTo(116.407357, within(1e-9));
        assertThat(out.get("battery")).isEqualTo(85L);
        assertThat(out.get("code")).isEqualTo("UAV-01");
        assertThat(out).doesNotContainKey(ProtocolEngine.ERROR_KEY);
    }

    @Test
    void 小端长度字段() {
        ProtocolConfig cfg = new ProtocolConfig(1, 2, true, null, null, null);
        List<TlvRule> rules = List.of(new TlvRule(1, "alt", "uint16", 0.1, "m", 10));
        // len 小端:0x02 0x00 = 2,值 0x012C = 300 × 0.1 = 30.0
        byte[] payload = new byte[]{0x01, 0x02, 0x00, 0x01, 0x2C};

        Map<String, Object> out = TlvParser.parse(payload, rules, cfg);

        assertThat(out.get("alt")).isEqualTo(30.0);
        assertThat(out).doesNotContainKey(ProtocolEngine.ERROR_KEY);
    }

    @Test
    void 半截帧以_error呈现残留() {
        List<TlvRule> rules = List.of(new TlvRule(1, "v", "uint8", 1.0, null, 10));
        // 头声明 len=4 但只剩 2 字节 → 半截 TLV
        byte[] payload = new byte[]{0x01, 0x00, 0x04, 0x11, 0x22};

        Map<String, Object> out = TlvParser.parse(payload, rules);

        assertThat(out).doesNotContainKey("v");
        assertThat(String.valueOf(out.get(ProtocolEngine.ERROR_KEY))).contains("残留 2 字节");
    }

    @Test
    void 未知tag跳过不影响后续字段() {
        List<TlvRule> rules = List.of(new TlvRule(2, "battery", "uint8", 1.0, "%", 10));
        byte[] payload = concat(tlv(9, new byte[]{0x63}), tlv(2, new byte[]{0x64}));

        Map<String, Object> out = TlvParser.parse(payload, rules);

        assertThat(out).hasSize(1);
        assertThat(out.get("battery")).isEqualTo(100L);
    }

    @Test
    void 注册密钥提取() {
        byte[] payload = concat(
                tlv(0x02, "noise".getBytes(StandardCharsets.UTF_8)),
                tlv(0x01, "secret-0002".getBytes(StandardCharsets.UTF_8)));

        assertThat(TlvParser.parseSecret(payload)).isEqualTo("secret-0002");
        assertThat(TlvParser.parseSecret(new byte[]{0x01, 0x00, 0x0A, 0x01})).isNull();
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] p : parts) {
            out.writeBytes(p);
        }
        return out.toByteArray();
    }
}
