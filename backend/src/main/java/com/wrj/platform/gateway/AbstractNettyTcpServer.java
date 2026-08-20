package com.wrj.platform.gateway;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

import java.util.concurrent.TimeUnit;

/**
 * Netty TCP 服务器生命周期骨架:随 Spring 启停,独立线程绑定端口不阻塞主线程。
 * 子类只需提供端口与管道初始化(9527 标准 AA55 帧 / 9528 DTU 透传 / 9529 Modbus TCP)。
 */
public abstract class AbstractNettyTcpServer implements SmartLifecycle {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private ChannelFuture serverFuture;
    private volatile boolean running;

    /** 服务名(日志用) */
    protected abstract String name();

    protected abstract int port();

    protected abstract boolean enabled();

    protected abstract ChannelInitializer<SocketChannel> initializer();

    @Override
    public void start() {
        if (!enabled()) {
            log.info("{} disabled, skip", name());
            return;
        }
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();
        ServerBootstrap bootstrap = new ServerBootstrap()
                .group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childHandler(initializer());
        Thread starter = new Thread(() -> {
            try {
                serverFuture = bootstrap.bind(port()).sync();
                log.info("{} started on port {}", name(), port());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.error("{} bind failed on port {}: {}", name(), port(), e.getMessage());
            }
        }, "netty-" + name() + "-starter");
        starter.setDaemon(true);
        starter.start();
        running = true;
    }

    @Override
    public void stop() {
        if (!running) {
            return;
        }
        running = false;
        try {
            if (serverFuture != null) {
                serverFuture.channel().close().sync();
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully(0, 2, TimeUnit.SECONDS);
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully(0, 2, TimeUnit.SECONDS);
        }
        log.info("{} stopped", name());
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
