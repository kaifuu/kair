package com.wrj.platform.gateway;

import com.wrj.platform.entity.ProtocolTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 解析引擎分发/错误可见化/缓存失效 */
class ProtocolEngineTest {

    @BeforeEach
    void isolateCache() {
        ProtocolEngine.evictAll();
    }

    private static ProtocolTemplate tlvTemplate(long id, String rulesJson) {
        ProtocolTemplate p = new ProtocolTemplate();
        p.setId(Long.valueOf(id));
        p.setName("test-tlv");
        p.setFrameFormat(ProtocolTemplate.FrameFormat.TLV);
        p.setRulesJson(rulesJson);
        p.setConfigJson("{}");
        return p;
    }

    /** tag=1 len=2 BE uint16 值 300(0x012C) */
    private static byte[] frame1() {
        return new byte[]{0x01, 0x00, 0x02, 0x01, 0x2C};
    }

    @Test
    void TLV按模板解析() {
        ProtocolTemplate p = tlvTemplate(101,
                "[{\"tag\":1,\"field\":\"speed\",\"type\":\"uint16\",\"scale\":0.1,\"unit\":\"m/s\"}]");

        Map<String, Object> out = ProtocolEngine.parse(p, frame1());

        assertThat(out.get("speed")).isEqualTo(30.0);
    }

    @Test
    void 规则变更后缓存即刻失效() {
        ProtocolTemplate p = tlvTemplate(102,
                "[{\"tag\":1,\"field\":\"speed\",\"type\":\"uint16\",\"scale\":0.1,\"unit\":\"m/s\"}]");
        assertThat(ProtocolEngine.parse(p, frame1()).get("speed")).isEqualTo(30.0);

        // 同一模板对象改规则:字符串比对判新,不允许陈旧结果
        p.setRulesJson("[{\"tag\":1,\"field\":\"speed\",\"type\":\"uint16\",\"scale\":1,\"unit\":\"m/s\"}]");
        assertThat(ProtocolEngine.parse(p, frame1()).get("speed")).isEqualTo(300L);
    }

    @Test
    void 规则JSON损坏以_error呈现() {
        ProtocolTemplate p = tlvTemplate(103, "{not-valid-json");

        Map<String, Object> out = ProtocolEngine.parse(p, frame1());

        assertThat(String.valueOf(out.get(ProtocolEngine.ERROR_KEY))).contains("rulesJson");
        assertThat(out).doesNotContainKey("speed");
    }

    @Test
    void 未命中字段时_warn提示() {
        ProtocolTemplate p = tlvTemplate(104,
                "[{\"tag\":9,\"field\":\"other\",\"type\":\"uint8\",\"scale\":1}]");

        Map<String, Object> out = ProtocolEngine.parse(p, frame1());

        assertThat(out).doesNotContainKey("other");
        assertThat(String.valueOf(out.get(ProtocolEngine.WARN_KEY))).contains("未解析出任何字段");
    }

    @Test
    void 定长偏移切分与越界置空() {
        ProtocolTemplate p = new ProtocolTemplate();
        p.setId(105L);
        p.setFrameFormat(ProtocolTemplate.FrameFormat.FIXED);
        p.setRulesJson("[]");
        p.setConfigJson("{\"fields\":["
                + "{\"offset\":0,\"len\":2,\"field\":\"temperature\",\"type\":\"int16\",\"scale\":0.1,\"unit\":\"℃\"},"
                + "{\"offset\":2,\"len\":4,\"field\":\"sn\",\"type\":\"string\"},"
                + "{\"offset\":6,\"len\":2,\"field\":\"tail\",\"type\":\"hex\"},"
                + "{\"offset\":99,\"len\":2,\"field\":\"oob\",\"type\":\"uint16\",\"scale\":1}]}");

        byte[] payload = new byte[]{0x00, 0x64, 'A', 'B', 'C', 'D', 0x11, 0x22};

        Map<String, Object> out = ProtocolEngine.parse(p, payload);

        assertThat(out.get("temperature")).isEqualTo(10.0);
        assertThat(out.get("sn")).isEqualTo("ABCD");
        assertThat(out.get("tail")).isEqualTo("11 22");
        assertThat(out.get("oob")).isNull();
    }

    @Test
    void Modbus寄存器映射() {
        ProtocolTemplate p = new ProtocolTemplate();
        p.setId(106L);
        p.setFrameFormat(ProtocolTemplate.FrameFormat.MODBUS);
        p.setRulesJson("[]");
        p.setConfigJson("{\"unitId\":1,\"regMap\":["
                + "{\"reg\":0,\"count\":1,\"field\":\"pressure\",\"type\":\"uint16\",\"scale\":0.1,\"unit\":\"kPa\"},"
                + "{\"reg\":1,\"count\":2,\"field\":\"total\",\"type\":\"int32\",\"scale\":1}]}");

        // 寄存器 0 = 0x01F4(500→50.0);寄存器 1..2 大端拼 int32 = 1
        byte[] regData = new byte[]{0x01, (byte) 0xF4, 0x00, 0x00, 0x00, 0x01};

        Map<String, Object> out = ProtocolEngine.parse(p, regData);

        assertThat(out.get("pressure")).isEqualTo(50.0);
        assertThat(out.get("total")).isEqualTo(1L);
    }

    @Test
    void 配置JSON损坏以_error呈现() {
        ProtocolTemplate p = new ProtocolTemplate();
        p.setId(107L);
        p.setFrameFormat(ProtocolTemplate.FrameFormat.FIXED);
        p.setRulesJson("[]");
        p.setConfigJson("{broken");

        Map<String, Object> out = ProtocolEngine.parse(p, new byte[]{0x01});

        assertThat(String.valueOf(out.get(ProtocolEngine.ERROR_KEY))).contains("configJson");
    }

    @SuppressWarnings("unused")
    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] b : parts) {
            out.writeBytes(b);
        }
        return out.toByteArray();
    }
}
