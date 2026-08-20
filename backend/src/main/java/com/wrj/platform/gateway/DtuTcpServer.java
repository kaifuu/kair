package com.wrj.platform.gateway;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.DelimiterBasedFrameDecoder;
import io.netty.handler.timeout.IdleStateHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * RS232/RS485 串口设备透传接入网关(默认 9528):
 * 串口传感器/PLC 经 DTU(串口转 TCP 透传模式)接入,行式文本协议(\r\n 分隔),
 * 注册 → 多进制数据行上报,详见 DtuServerHandler。
 */
@Component
public class DtuTcpServer extends AbstractNettyTcpServer {

    private final DtuServerHandler handler;
    private final int port;
    private final int heartbeatSeconds;
    private final int maxFrameLength;
    private final boolean enabled;

    public DtuTcpServer(DtuServerHandler handler,
                        @Value("${device.gateway.dtu-port:9528}") int port,
                        @Value("${device.gateway.heartbeat-timeout-seconds:75}") int heartbeatSeconds,
                        @Value("${device.gateway.max-frame-length:8192}") int maxFrameLength,
                        @Value("${device.gateway.enabled:true}") boolean enabled) {
        this.handler = handler;
        this.port = port;
        this.heartbeatSeconds = heartbeatSeconds;
        this.maxFrameLength = maxFrameLength;
        this.enabled = enabled;
    }

    @Override
    protected String name() {
        return "DTU gateway";
    }

    @Override
    protected int port() {
        return port;
    }

    @Override
    protected boolean enabled() {
        return enabled;
    }

    @Override
    protected ChannelInitializer<SocketChannel> initializer() {
        return new ChannelInitializer<>() {
            @Override
            protected void initChannel(SocketChannel ch) {
                ch.pipeline()
                        .addLast(new IdleStateHandler(heartbeatSeconds, 0, 0, TimeUnit.SECONDS))
                        .addLast(new DelimiterBasedFrameDecoder(maxFrameLength,
                                Unpooled.copiedBuffer(new byte[]{'\r', '\n'}),
                                Unpooled.copiedBuffer(new byte[]{'\n'})))
                        .addLast(handler);
            }
        };
    }
}
