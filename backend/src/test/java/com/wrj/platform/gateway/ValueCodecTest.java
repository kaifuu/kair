package com.wrj.platform.gateway;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.assertj.core.api.Assertions.assertThat;

/** 字节值转换:类型读取/缩放/进制呈现/容错 */
class ValueCodecTest {

    @Test
    void 无符号与有符号读取() {
        assertThat(ValueCodec.convert(new byte[]{0x01, 0x02}, "uint16", null, 10)).isEqualTo(258L);
        assertThat(ValueCodec.convert(new byte[]{(byte) 0xFF, (byte) 0xFF}, "int16", null, 10)).isEqualTo(-1L);
        assertThat(ValueCodec.convert(new byte[]{(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF},
                "uint32", null, 10)).isEqualTo(4294967295L);
    }

    @Test
    void 缩放与浮点() {
        assertThat(ValueCodec.convert(new byte[]{0x00, 0x64}, "uint16", 0.1, 10)).isEqualTo(10.0);
        assertThat(ValueCodec.convert(new byte[]{0x27, 0x10}, "int16", 0.01, 10)).isEqualTo(100.0);
        ByteBuffer buf = ByteBuffer.allocate(4).putFloat(1.5f);
        assertThat(ValueCodec.convert(buf.array(), "float32", null, 10)).isEqualTo(1.5);
    }

    @Test
    void 进制呈现() {
        assertThat(ValueCodec.convert(new byte[]{0x01, 0x02}, "uint16", null, 16)).isEqualTo("0x0102");
        assertThat(ValueCodec.convert(new byte[]{0x0A}, "uint8", null, 2)).isEqualTo("0b00001010");
        assertThat(ValueCodec.convert(new byte[]{0x1F}, "uint8", null, 8)).isEqualTo("0o37");
    }

    @Test
    void 字节不足回退hex呈现() {
        assertThat(ValueCodec.convert(new byte[]{0x0A}, "uint16", null, 10)).isEqualTo("0A");
        assertThat(ValueCodec.convert(new byte[0], "int32", null, 10)).isEqualTo("");
    }

    @Test
    void hex与utf8工具() {
        assertThat(ValueCodec.hex(new byte[]{0x01, (byte) 0xAB})).isEqualTo("01 AB");
        assertThat(ValueCodec.utf8("中文".getBytes())).isEqualTo("中文");
    }
}
