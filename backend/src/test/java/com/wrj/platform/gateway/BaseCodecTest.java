package com.wrj.platform.gateway;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 多进制文本编解码:容错/往返/非法输入 */
class BaseCodecTest {

    @Test
    void 十六进制解码容错前缀与逗号() {
        assertThat(BaseCodec.decode("01 02 FF", "hex")).containsExactly(0x01, 0x02, 0xFF);
        assertThat(BaseCodec.decode("0x01,0xAB", "hex")).containsExactly(0x01, 0xAB);
    }

    @Test
    void 编码格式() {
        assertThat(BaseCodec.encode(new byte[]{0x01, (byte) 0xAB}, "hex")).isEqualTo("01 AB");
        assertThat(BaseCodec.encode(new byte[]{0x0A}, "bin")).isEqualTo("00001010");
        assertThat(BaseCodec.encode(new byte[]{0x08}, "oct")).isEqualTo("10");
        assertThat(BaseCodec.encode(new byte[]{0x09}, "dec")).isEqualTo("9");
        assertThat(BaseCodec.encode(new byte[0], "hex")).isEmpty();
    }

    @Test
    void 解码往返一致() {
        for (String base : new String[]{"bin", "oct", "dec", "hex"}) {
            byte[] src = new byte[]{0x00, 0x01, 0x7F, (byte) 0xFF};
            assertThat(BaseCodec.decode(BaseCodec.encode(src, base), base)).containsExactly(src);
        }
    }

    @Test
    void 非法输入报错() {
        assertThatThrownBy(() -> BaseCodec.decode("GG", "hex"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("非法");
        assertThatThrownBy(() -> BaseCodec.decode("100", "hex"))     // 越界 0..255
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("越界");
        assertThatThrownBy(() -> BaseCodec.decode("01", "xxx"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不支持的进制");
        assertThatThrownBy(() -> BaseCodec.decode("  ", "hex"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不能为空");
    }
}
