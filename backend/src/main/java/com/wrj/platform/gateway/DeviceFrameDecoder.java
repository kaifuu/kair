package com.wrj.platform.gateway;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 设备帧拆包解码器:
 * 帧头同步(丢脏字节)→codeLen 合法性→payloadLen 长度判定→整帧读取→CRC 校验。
 * codeLen 变长导致 lengthFieldOffset 非常量,故不用 LengthFieldBasedFrameDecoder。
 */
public class DeviceFrameDecoder extends ByteToMessageDecoder {

    /** 头2+ver1+type1+codeLen1+len2+crc2 */
    private static final int MIN_FRAME = 9;

    private final int maxFrameLength;

    public DeviceFrameDecoder(int maxFrameLength) {
        this.maxFrameLength = maxFrameLength;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        // 帧头同步:跳过 AA55 之前的脏字节
        while (in.readableBytes() >= 2
                && (in.getUnsignedByte(in.readerIndex()) != 0xAA
                || in.getUnsignedByte(in.readerIndex() + 1) != 0x55)) {
            in.skipBytes(1);
        }
        if (in.readableBytes() < MIN_FRAME) {
            return;
        }
        int n = in.getUnsignedByte(in.readerIndex() + 4);
        if (n < 1 || n > 32) {
            in.skipBytes(2);    // 非法 codeLen,视为伪帧头重新同步
            return;
        }
        if (in.readableBytes() < MIN_FRAME + n) {
            return;
        }
        int m = in.getUnsignedShort(in.readerIndex() + 5 + n);
        if (m > maxFrameLength) {
            in.skipBytes(2);
            return;
        }
        int frameLen = MIN_FRAME + n + m;
        if (in.readableBytes() < frameLen) {
            return;
        }
        ByteBuf frame = in.readRetainedSlice(frameLen);
        try {
            // CRC 低字节在前,须按小端读取(与 encode/模拟器一致)
            int expected = frame.getUnsignedByte(frameLen - 2) | (frame.getUnsignedByte(frameLen - 1) << 8);
            // direct buffer 无 array(),拷贝到堆内再算 CRC
            byte[] crcRange = new byte[frameLen - 4];
            frame.getBytes(2, crcRange);
            int actual = Crc16.modbus(crcRange, 0, crcRange.length);
            if (actual == expected) {
                byte type = frame.getByte(3);
                String code = frame.toString(5, n, StandardCharsets.US_ASCII);
                byte[] payload = new byte[m];
                frame.getBytes(7 + n, payload);
                out.add(new DeviceFrame(type, code, payload));
            }
            // CRC 失败:静默丢帧(异常计数由上层心跳/日志观察)
        } finally {
            frame.release();
        }
    }
}
