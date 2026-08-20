package com.wrj.platform.gateway;

import com.wrj.platform.service.DeviceEventService;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * PLC Modbus TCP 接入处理器(9529 端口,平台作 Modbus 服务端,@Sharable):
 * - FC 0x10/0x06 写寄存器 = PLC 上报数据:unitId 寻址设备 → regMap 解析 → 广播/落历史
 * - FC 0x03/0x04 读寄存器 = 轮询应答:返回最近写入的寄存器字(未写过为 0)
 * - 其余功能码 → 异常应答 0x01;unitId 无对应设备 → 0x0B
 */
@Component
@ChannelHandler.Sharable
public class ModbusServerHandler extends SimpleChannelInboundHandler<byte[]> {

    private static final Logger log = LoggerFactory.getLogger(ModbusServerHandler.class);

    private static final int FC_READ_HOLDING = 0x03;
    private static final int FC_READ_INPUT = 0x04;
    private static final int FC_WRITE_SINGLE = 0x06;
    private static final int FC_WRITE_MULTI = 0x10;

    private final DeviceSessionManager sessionManager;
    private final DeviceEventService eventService;

    public ModbusServerHandler(DeviceSessionManager sessionManager, DeviceEventService eventService) {
        this.sessionManager = sessionManager;
        this.eventService = eventService;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, byte[] adu) {
        int txId = ((adu[0] & 0xFF) << 8) | (adu[1] & 0xFF);
        int unitId = adu[6] & 0xFF;
        int fc = adu[7] & 0xFF;
        String ip = String.valueOf(ctx.channel().remoteAddress()).replaceAll("^/", "").split(":")[0];
        switch (fc) {
            case FC_WRITE_MULTI -> handleWriteMulti(ctx, adu, txId, unitId, ip);
            case FC_WRITE_SINGLE -> handleWriteSingle(ctx, adu, txId, unitId, ip);
            case FC_READ_HOLDING, FC_READ_INPUT -> handleRead(ctx, adu, txId, unitId, ip);
            default -> exception(ctx, txId, unitId, fc, 0x01);   // 非法功能码
        }
    }

    private void handleWriteMulti(ChannelHandlerContext ctx, byte[] adu, int txId, int unitId, String ip) {
        if (adu.length < 13) {
            exception(ctx, txId, unitId, FC_WRITE_MULTI, 0x03);
            return;
        }
        int startAddr = ((adu[8] & 0xFF) << 8) | (adu[9] & 0xFF);
        int qty = ((adu[10] & 0xFF) << 8) | (adu[11] & 0xFF);
        int byteCount = adu[12] & 0xFF;
        if (qty < 1 || qty > 123 || byteCount != qty * 2 || adu.length < 13 + byteCount) {
            exception(ctx, txId, unitId, FC_WRITE_MULTI, 0x03);
            return;
        }
        int[] words = new int[qty];
        for (int i = 0; i < qty; i++) {
            words[i] = ((adu[13 + i * 2] & 0xFF) << 8) | (adu[14 + i * 2] & 0xFF);
        }
        eventService.logRawFrame(null, "MB-" + unitId, "MODBUS_WRITE", adu);
        eventService.onModbusWrite(unitId, startAddr, words, ip).whenComplete((device, err) -> {
            if (!ctx.channel().isActive()) {
                return;
            }
            if (err != null) {
                log.error("Modbus write error (unit {}): {}", unitId, err.getMessage());
                exception(ctx, txId, unitId, FC_WRITE_MULTI, 0x0A);
                return;
            }
            if (device == null) {
                exception(ctx, txId, unitId, FC_WRITE_MULTI, 0x0B);   // 网关目标设备不存在
                return;
            }
            sessionManager.bind(device.getId(), device.getCode(), ctx.channel());
            // 标准成功应答:回显 fc + startAddr + qty
            byte[] resp = mbap(txId, unitId, 5);
            resp[7] = (byte) FC_WRITE_MULTI;
            resp[8] = (byte) (startAddr >> 8);
            resp[9] = (byte) startAddr;
            resp[10] = (byte) (qty >> 8);
            resp[11] = (byte) qty;
            ctx.writeAndFlush(Unpooled.wrappedBuffer(resp));
        });
    }

    private void handleWriteSingle(ChannelHandlerContext ctx, byte[] adu, int txId, int unitId, String ip) {
        if (adu.length < 12) {
            exception(ctx, txId, unitId, FC_WRITE_SINGLE, 0x03);
            return;
        }
        int addr = ((adu[8] & 0xFF) << 8) | (adu[9] & 0xFF);
        int value = ((adu[10] & 0xFF) << 8) | (adu[11] & 0xFF);
        eventService.logRawFrame(null, "MB-" + unitId, "MODBUS_WRITE", adu);
        eventService.onModbusWrite(unitId, addr, new int[]{value}, ip).whenComplete((device, err) -> {
            if (!ctx.channel().isActive()) {
                return;
            }
            if (err != null || device == null) {
                exception(ctx, txId, unitId, FC_WRITE_SINGLE, err == null ? 0x0B : 0x0A);
                return;
            }
            sessionManager.bind(device.getId(), device.getCode(), ctx.channel());
            ctx.writeAndFlush(Unpooled.wrappedBuffer(adu.clone()));   // 单写成功 = 回显原帧
        });
    }

    private void handleRead(ChannelHandlerContext ctx, byte[] adu, int txId, int unitId, String ip) {
        if (adu.length < 12) {
            exception(ctx, txId, unitId, adu[7] & 0xFF, 0x03);
            return;
        }
        int fc = adu[7] & 0xFF;
        int startAddr = ((adu[8] & 0xFF) << 8) | (adu[9] & 0xFF);
        int qty = ((adu[10] & 0xFF) << 8) | (adu[11] & 0xFF);
        if (qty < 1 || qty > 125) {
            exception(ctx, txId, unitId, fc, 0x03);
            return;
        }
        eventService.logRawFrame(null, "MB-" + unitId, "MODBUS_READ", adu);
        int[] words = eventService.readModbusRegisters(unitId, startAddr, qty);
        byte[] resp = mbap(txId, unitId, 3 + qty * 2);
        resp[7] = (byte) fc;
        resp[8] = (byte) (qty * 2);
        for (int i = 0; i < qty; i++) {
            resp[9 + i * 2] = (byte) (words[i] >> 8);
            resp[10 + i * 2] = (byte) words[i];
        }
        ctx.writeAndFlush(Unpooled.wrappedBuffer(resp));
    }

    /** 异常应答:fc|0x80 + 异常码 */
    private void exception(ChannelHandlerContext ctx, int txId, int unitId, int fc, int code) {
        if (!ctx.channel().isActive()) {
            return;
        }
        byte[] resp = mbap(txId, unitId, 3);
        resp[7] = (byte) (fc | 0x80);
        resp[8] = (byte) code;
        ctx.writeAndFlush(Unpooled.wrappedBuffer(resp));
    }

    private static byte[] mbap(int txId, int unitId, int len) {
        byte[] out = new byte[6 + len];
        out[0] = (byte) (txId >> 8);
        out[1] = (byte) txId;
        out[2] = 0;
        out[3] = 0;                        // protoId = 0(Modbus/TCP)
        out[4] = (byte) (len >> 8);
        out[5] = (byte) len;
        out[6] = (byte) unitId;
        return out;
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        Long deviceId = sessionManager.unbind(ctx.channel());
        if (deviceId != null) {
            eventService.onOffline(deviceId);
        }
        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.warn("Modbus channel error: {}", cause.getMessage());
        ctx.close();
    }
}
