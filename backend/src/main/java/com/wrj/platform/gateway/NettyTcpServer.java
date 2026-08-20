package com.wrj.platform.gateway;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 标准设备接入网关(默认 9527):AA55 帧 + CRC16 + TLV/FIXED 载荷,
 * 设备按 DeviceFrame grammar 注册/心跳/上报,详见 DeviceFrameDecoder。
 */
@Component
public class NettyTcpServer extends AbstractNettyTcpServer {

    private final DeviceServerHandler serverHandler;
    private final int port;
    private final int heartbeatSeconds;
    private final int maxFrameLength;
    private final boolean enabled;

    public NettyTcpServer(DeviceServerHandler serverHandler,
                          @Value("${device.gateway.port:9527}") int port,
                          @Value("${device.gateway.heartbeat-timeout-seconds:75}") int heartbeatSeconds,
                          @Value("${device.gateway.max-frame-length:8192}") int maxFrameLength,
                          @Value("${device.gateway.enabled:true}") boolean enabled) {
        this.serverHandler = serverHandler;
        this.port = port;
        this.heartbeatSeconds = heartbeatSeconds;
        this.maxFrameLength = maxFrameLength;
        this.enabled = enabled;
    }

    @Override
    protected String name() {
        return "TCP gateway";
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
                        .addLast(new DeviceFrameDecoder(maxFrameLength))
                        .addLast(serverHandler);
            }
        };
    }
}
