package com.wrj.platform.gateway;

import com.wrj.platform.service.DeviceEventService;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleStateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * RS232/RS485 DTU 透传处理器(9528 端口,无逐连接状态,@Sharable):
 * 串口设备经 DTU 透传模式上 TCP,行协议(默认 \r\n 分隔):
 * - 注册:REG:<code>:<secret>      → 鉴权绑定,回 OK / ERR:<原因>
 * - 心跳:PING                      → 回 PONG
 * - 数据:DATA:<radix>:<payload>    → radix∈bin/oct/dec/hex,按进制拆字节后交设备协议模板解析
 *   (简化用法:注册后直接发 <radix>:<payload> 或裸十六进制行,详见 parseDataLine)
 */
@Component
@ChannelHandler.Sharable
public class DtuServerHandler extends SimpleChannelInboundHandler<ByteBuf> {

    private static final Logger log = LoggerFactory.getLogger(DtuServerHandler.class);

    private final DeviceSessionManager sessionManager;
    private final DeviceEventService eventService;

    public DtuServerHandler(DeviceSessionManager sessionManager, DeviceEventService eventService) {
        this.sessionManager = sessionManager;
        this.eventService = eventService;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf frame) {
        byte[] raw = new byte[frame.readableBytes()];
        frame.readBytes(raw);
        String line = new String(raw, StandardCharsets.UTF_8).trim();
        if (line.isEmpty()) {
            return;
        }
        if (line.startsWith("REG:")) {
            handleRegister(ctx, line.substring(4));
            return;
        }
        if ("PING".equalsIgnoreCase(line)) {
            Long deviceId = sessionManager.deviceIdOf(ctx.channel());
            if (deviceId != null) {
                eventService.onHeartbeat(deviceId);
                reply(ctx, "PONG");
            } else {
                reply(ctx, "ERR:NOT_REGISTERED");
            }
            return;
        }
        handleData(ctx, line);
    }

    private void handleRegister(ChannelHandlerContext ctx, String body) {
        String[] parts = body.split(":", 2);
        if (parts.length != 2) {
            reply(ctx, "ERR:BAD_REG_FORMAT");
            ctx.close();
            return;
        }
        String code = parts[0].trim();
        String secret = parts[1].trim();
        eventService.authenticate(code, secret).whenComplete((device, err) -> {
            if (!ctx.channel().isActive()) {
                return;
            }
            if (err != null || device == null) {
                eventService.logRegisterFailed(code, secret);
                reply(ctx, "ERR:AUTH_FAILED");
                ctx.close();
                return;
            }
            Channel old = sessionManager.bind(device.getId(), device.getCode(), ctx.channel());
            if (old != null && old != ctx.channel()) {
                old.close();
            }
            eventService.logRawFrame(device.getId(), device.getCode(), "DTU_REG", rawBytes(code, secret));
            String ip = String.valueOf(ctx.channel().remoteAddress()).replaceAll("^/", "").split(":")[0];
            eventService.onOnline(device.getId(), ip);
            reply(ctx, "OK");
        });
    }

    private void handleData(ChannelHandlerContext ctx, String line) {
        Long deviceId = sessionManager.deviceIdOf(ctx.channel());
        if (deviceId == null) {
            reply(ctx, "ERR:NOT_REGISTERED");
            ctx.close();    // 透传链路先注册再上报,乱序直接断开
            return;
        }
        byte[] payload;
        try {
            payload = parseDataLine(line);
        } catch (Exception e) {
            eventService.logRawFrame(deviceId, sessionManager.codeOf(ctx.channel()), "DTU_BAD", line.getBytes(StandardCharsets.UTF_8));
            reply(ctx, "ERR:" + e.getMessage());
            return;
        }
        eventService.logRawFrame(deviceId, sessionManager.codeOf(ctx.channel()), "DTU_DATA", payload);
        eventService.onData(deviceId, payload);
        reply(ctx, "OK");
    }

    /** DATA:<radix>:<payload> 或 <radix>:<payload>;纯 hex token 行按 hex 兜底 */
    static byte[] parseDataLine(String line) {
        String body = line.startsWith("DATA:") ? line.substring(5) : line;
        String radix = "hex";
        int colon = body.indexOf(':');
        if (colon > 0) {
            String head = body.substring(0, colon).trim().toLowerCase();
            if ("bin".equals(head) || "oct".equals(head) || "dec".equals(head) || "hex".equals(head)) {
                radix = head;
                body = body.substring(colon + 1);
            }
        }
        return BaseCodec.decode(body.trim(), radix);
    }

    private static byte[] rawBytes(String code, String secret) {
        return ("REG:" + code + ":" + secret).getBytes(StandardCharsets.UTF_8);
    }

    private void reply(ChannelHandlerContext ctx, String msg) {
        if (ctx.channel().isActive()) {
            ctx.writeAndFlush(Unpooled.copiedBuffer((msg + "\r\n").getBytes(StandardCharsets.UTF_8)));
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            log.info("DTU channel idle timeout, close: {}", ctx.channel().remoteAddress());
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
        log.warn("DTU channel error: {}", cause.getMessage());
        ctx.close();
    }
}
