package com.wrj.platform.gateway;

import io.netty.channel.Channel;
import io.netty.util.AttributeKey;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 设备会话管理:deviceId ↔ Channel 映射(同设备重复注册顶替旧连接) */
@Component
public class DeviceSessionManager {

    public static final AttributeKey<Long> DEVICE_ID = AttributeKey.valueOf("deviceId");
    public static final AttributeKey<String> DEVICE_CODE = AttributeKey.valueOf("deviceCode");

    private final Map<Long, Channel> channels = new ConcurrentHashMap<>();

    /** 绑定设备与连接,返回被顶替的旧连接(可空) */
    public Channel bind(Long deviceId, String deviceCode, Channel channel) {
        channel.attr(DEVICE_ID).set(deviceId);
        channel.attr(DEVICE_CODE).set(deviceCode == null ? "" : deviceCode);
        return channels.put(deviceId, channel);
    }

    public String codeOf(Channel channel) {
        String code = channel.attr(DEVICE_CODE).get();
        return code == null ? "" : code;
    }

    /** 解绑:仅当映射仍指向该 channel 才移除(防顶替场景误下线) */
    public Long unbind(Channel channel) {
        Long id = channel.attr(DEVICE_ID).get();
        if (id == null) {
            return null;
        }
        channels.remove(id, channel);
        return id;
    }

    public Long deviceIdOf(Channel channel) {
        return channel.attr(DEVICE_ID).get();
    }

    public Channel channelOf(Long deviceId) {
        return channels.get(deviceId);
    }

    public int onlineCount() {
        return channels.size();
    }
}
