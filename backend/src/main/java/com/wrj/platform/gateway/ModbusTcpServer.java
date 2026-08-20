package com.wrj.platform.gateway;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/** PLC Modbus TCP 接入网关(默认 9529):平台作为 Modbus 服务端,见 ModbusServerHandler */
@Component
public class ModbusTcpServer extends AbstractNettyTcpServer {

    private final ModbusServerHandler handler;
    private final int port;
    private final int heartbeatSeconds;
    private final boolean enabled;

    public ModbusTcpServer(ModbusServerHandler handler,
                           @Value("${device.gateway.modbus-port:9529}") int port,
                           @Value("${device.gateway.heartbeat-timeout-seconds:75}") int heartbeatSeconds,
                           @Value("${device.gateway.enabled:true}") boolean enabled) {
        this.handler = handler;
        this.port = port;
        this.heartbeatSeconds = heartbeatSeconds;
        this.enabled = enabled;
    }

    @Override
    protected String name() {
        return "Modbus gateway";
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
                        .addLast(new ModbusFrameDecoder())
                        .addLast(handler);
            }
        };
    }
}
