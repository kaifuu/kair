package com.wrj.platform.controller;

import com.wrj.platform.common.ApiResponse;
import com.wrj.platform.common.OpLog;
import com.wrj.platform.dto.CommandRequest;
import com.wrj.platform.entity.Device;
import com.wrj.platform.entity.DeviceMessage;
import com.wrj.platform.gateway.BaseCodec;
import com.wrj.platform.gateway.DeviceFrame;
import com.wrj.platform.gateway.DeviceSessionManager;
import com.wrj.platform.repository.DeviceMessageRepository;
import com.wrj.platform.repository.DeviceRepository;
import com.wrj.platform.service.DeviceEventService;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** 设备报文:经 Netty 通道下发 COMMAND 帧 + 上下行整帧日志分页查询 */
@RestController
public class DeviceMessageController {

    private static final int MAX_COMMAND_PAYLOAD = 1024;

    private final DeviceRepository deviceRepository;
    private final DeviceMessageRepository messageRepository;
    private final DeviceSessionManager sessionManager;
    private final DeviceEventService eventService;

    public DeviceMessageController(DeviceRepository deviceRepository,
                                   DeviceMessageRepository messageRepository,
                                   DeviceSessionManager sessionManager,
                                   DeviceEventService eventService) {
        this.deviceRepository = deviceRepository;
        this.messageRepository = messageRepository;
        this.sessionManager = sessionManager;
        this.eventService = eventService;
    }

    /** 下发指令:content 按 base(bin/oct/dec/hex)解析为 payload,组 COMMAND 帧写入设备通道 */
    @PostMapping("/api/devices/{id}/messages")
    @OpLog(module = "设备管理", action = "下发指令")
    public ApiResponse<Map<String, Object>> send(@PathVariable Long id, @RequestBody CommandRequest body) {
        Device device = deviceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("设备不存在: " + id));
        if (Boolean.TRUE.equals(device.getVirtual())) {
            throw new IllegalArgumentException("虚拟设备不支持指令下发");
        }
        byte[] payload = BaseCodec.decode(body.content(), body.base());
        if (payload.length > MAX_COMMAND_PAYLOAD) {
            throw new IllegalArgumentException("指令 payload 过大: " + payload.length + "B(上限 " + MAX_COMMAND_PAYLOAD + "B)");
        }
        Channel channel = sessionManager.channelOf(id);
        if (channel == null || !channel.isActive()) {
            throw new IllegalArgumentException("设备 " + device.getCode() + " 不在线,无法下发指令");
        }
        byte[] frame = DeviceFrame.encode(DeviceFrame.TYPE_COMMAND, device.getCode(), payload);
        channel.writeAndFlush(Unpooled.wrappedBuffer(frame));
        eventService.logDownFrame(device.getId(), device.getCode(),
                DeviceFrame.typeName(DeviceFrame.TYPE_COMMAND), frame);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("contentHex", BaseCodec.encode(frame, "hex"));
        data.put("length", frame.length);
        data.put("frameType", DeviceFrame.typeName(DeviceFrame.TYPE_COMMAND));
        return ApiResponse.ok(data);
    }

    @GetMapping("/api/device-messages")
    public ApiResponse<Map<String, Object>> list(@RequestParam(required = false) Long deviceId,
                                                 @RequestParam(required = false) String direction,
                                                 @RequestParam(required = false) String frameType,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "20") int size) {
        DeviceMessage.Direction dir = null;
        if (direction != null && !direction.isBlank()) {
            try {
                dir = DeviceMessage.Direction.valueOf(direction.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("方向取值非法: " + direction + "(可用 UP/DOWN)");
            }
        }
        String type = (frameType == null || frameType.isBlank()) ? null : frameType.trim().toUpperCase();
        Page<DeviceMessage> result = messageRepository.search(deviceId, dir, type, PageRequest.of(page, size));

        Map<String, Object> data = new HashMap<>();
        data.put("items", result.getContent());
        data.put("total", result.getTotalElements());
        return ApiResponse.ok(data);
    }
}
