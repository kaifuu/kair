package com.wrj.platform.gateway;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.util.List;

/**
 * Modbus TCP ADU 拆包:MBAP 头 txId(2)+protoId(2)=0+len(2)+unitId(1)+PDU(len-1)。
 * 整帧 = 6 + len;protoId 非 0 或 len 异常按脏字节重新同步。
 */
public class ModbusFrameDecoder extends ByteToMessageDecoder {

    private static final int MBAP_LEN = 7;    // 头 6B + 至少 fc 1B
    private static final int MAX_PDU = 253;

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        if (in.readableBytes() < MBAP_LEN) {
            return;
        }
        int len = in.getUnsignedShort(in.readerIndex() + 4);
        if (len < 2 || len > MAX_PDU + 1) {
            in.skipBytes(1);    // 伪长度字段,重新同步
            return;
        }
        int total = 6 + len;
        if (in.readableBytes() < total) {
            return;
        }
        int protoId = in.getUnsignedShort(in.readerIndex() + 2);
        if (protoId != 0) {
            in.skipBytes(1);    // 非 Modbus/TCP,重新同步
            return;
        }
        byte[] adu = new byte[total];
        in.readBytes(adu);
        out.add(adu);
    }
}
