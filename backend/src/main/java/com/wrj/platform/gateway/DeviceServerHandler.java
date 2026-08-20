package com.wrj.platform.gateway;

import com.wrj.platform.service.DeviceEventService;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleStateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 设备接入业务处理器(无逐连接状态,@Sharable 可安全共享)。
 * REGISTER→鉴权绑定;HEARTBEAT→保活;DATA→按协议解析遥测;读空闲超时断开。
 */
@Component
@ChannelHandler.Sharable
public class DeviceServerHandler extends SimpleChannelInboundHandler<DeviceFrame> {

    private static final Logger log = LoggerFactory.getLogger(DeviceServerHandler.class);

    private final DeviceSessionManager sessionManager;
    private final DeviceEventService eventService;

    public DeviceServerHandler(DeviceSessionManager sessionManager, DeviceEventService eventService) {
        this.sessionManager = sessionManager;
        this.eventService = eventService;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, DeviceFrame frame) {
        eventService.logUpFrame(frame, sessionManager.deviceIdOf(ctx.channel()));
        switch (frame.type()) {
            case DeviceFrame.TYPE_REGISTER -> handleRegister(ctx, frame);
            case DeviceFrame.TYPE_HEARTBEAT -> handleHeartbeat(ctx, frame);
            case DeviceFrame.TYPE_DATA -> handleData(ctx, frame);
            default -> ack(ctx, false, "未知帧类型: " + String.format("0x%02X", frame.type()));
        }
    }

    private void handleRegister(ChannelHandlerContext ctx, DeviceFrame frame) {
        String secret = TlvParser.parseSecret(frame.payload());
        eventService.authenticate(frame.code(), secret).whenComplete((device, err) -> {
            boolean ok = err == null && device != null;
            if (ctx.channel().isActive()) {
                // 注册应答的 code 回填对端设备编码(此时尚未 bind)
                writeFrame(ctx, DeviceFrame.TYPE_ACK, frame.code(),
                        DeviceFrame.encodeAck(ok, ok ? "OK" : "设备编码或密钥错误"));
            }
            if (err != null || device == null) {
                eventService.logRegisterFailed(frame.code(), secret);
                ctx.close();
                return;
            }
            Channel old = sessionManager.bind(device.getId(), device.getCode(), ctx.channel());
            if (old != null && old != ctx.channel()) {
                log.info("Device {} re-registered, kick old channel", device.getCode());
                old.close();    // 顶替旧连接(其 channelInactive 不会误下线,unbind 校验映射)
            }
            String ip = remoteIp(ctx);
            eventService.onOnline(device.getId(), ip);
        });
    }

    private void handleHeartbeat(ChannelHandlerContext ctx, DeviceFrame frame) {
        Long deviceId = sessionManager.deviceIdOf(ctx.channel());
        if (deviceId == null) {
            ack(ctx, false, "未注册");
            return;
        }
        ack(ctx, true, "OK");
        eventService.onHeartbeat(deviceId);
    }

    private void handleData(ChannelHandlerContext ctx, DeviceFrame frame) {
        Long deviceId = sessionManager.deviceIdOf(ctx.channel());
        if (deviceId == null) {
            ack(ctx, false, "未注册");
            return;
        }
        eventService.onData(deviceId, frame.payload());
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            log.info("Channel idle timeout, close: {}", ctx.channel().remoteAddress());
            ctx.close();
            return;
        }
        super.userEventTriggered(ctx, evt);
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
        log.warn("Gateway channel error: {}", cause.getMessage());
        ctx.close();
    }

    /** 组帧下发并留痕:encode 产物是 byte[],须包成 ByteBuf 才能落出站(管道无 MessageToByteEncoder) */
    private void writeFrame(ChannelHandlerContext ctx, byte type, String code, byte[] payload) {
        if (!ctx.channel().isActive()) {
            return;
        }
        byte[] frame = DeviceFrame.encode(type, code, payload);
        ctx.writeAndFlush(Unpooled.wrappedBuffer(frame));
        eventService.logDownFrame(sessionManager.deviceIdOf(ctx.channel()), code,
                DeviceFrame.typeName(type), frame);
    }

    private void ack(ChannelHandlerContext ctx, boolean ok, String msg) {
        // ACK 帧的 code 回填对端设备编码(注册时写入;未注册场景为空串)
        writeFrame(ctx, DeviceFrame.TYPE_ACK, sessionManager.codeOf(ctx.channel()),
                DeviceFrame.encodeAck(ok, msg));
    }

    private static String remoteIp(ChannelHandlerContext ctx) {
        String addr = String.valueOf(ctx.channel().remoteAddress());
        return addr.replaceAll("^/", "").split(":")[0];
    }
}
