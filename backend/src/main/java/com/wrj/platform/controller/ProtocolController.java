package com.wrj.platform.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wrj.platform.common.ApiResponse;
import com.wrj.platform.common.OpLog;
import com.wrj.platform.dto.ProtocolConfig;
import com.wrj.platform.dto.TlvRule;
import com.wrj.platform.entity.ProtocolTemplate;
import com.wrj.platform.gateway.BaseCodec;
import com.wrj.platform.gateway.ProtocolEngine;
import com.wrj.platform.gateway.ValueCodec;
import com.wrj.platform.repository.DeviceRepository;
import com.wrj.platform.repository.ProtocolRepository;
import org.springframework.web.bind.annotation.*;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 设备接入协议模板管理(TLV/FIXED/MODBUS,多进制数据拆分) */
@RestController
@RequestMapping("/api/protocols")
public class ProtocolController {

    private final ProtocolRepository protocolRepository;
    private final DeviceRepository deviceRepository;
    private final ObjectMapper objectMapper;

    public ProtocolController(ProtocolRepository protocolRepository,
                              DeviceRepository deviceRepository,
                              ObjectMapper objectMapper) {
        this.protocolRepository = protocolRepository;
        this.deviceRepository = deviceRepository;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public ApiResponse<List<ProtocolTemplate>> list() {
        return ApiResponse.ok(protocolRepository.findAll());
    }

    @GetMapping("/{id}")
    public ApiResponse<ProtocolTemplate> get(@PathVariable Long id) {
        return ApiResponse.ok(protocolRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("协议模板不存在: " + id)));
    }

    @PostMapping
    @OpLog(module = "协议管理", action = "新增")
    public ApiResponse<ProtocolTemplate> create(@RequestBody ProtocolTemplate body) {
        if (body.getName() == null || body.getName().isBlank()) {
            throw new IllegalArgumentException("协议名称不能为空");
        }
        validateRules(body.getRulesJson());
        validateConfig(body);
        if (body.getRulesJson() == null || body.getRulesJson().isBlank()) {
            body.setRulesJson("[]");
        }
        if (body.getConfigJson() == null || body.getConfigJson().isBlank()) {
            body.setConfigJson("{}");
        }
        if (body.getTransport() == null) body.setTransport(ProtocolTemplate.Transport.TCP);
        if (body.getFrameFormat() == null) body.setFrameFormat(ProtocolTemplate.FrameFormat.TLV);
        ProtocolTemplate saved = protocolRepository.save(body);
        ProtocolEngine.evict(saved.getId());
        return ApiResponse.ok(saved);
    }

    @PutMapping("/{id}")
    @OpLog(module = "协议管理", action = "修改")
    public ApiResponse<ProtocolTemplate> update(@PathVariable Long id, @RequestBody ProtocolTemplate body) {
        ProtocolTemplate protocol = protocolRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("协议模板不存在: " + id));
        if (body.getName() != null) protocol.setName(body.getName());
        if (body.getDescription() != null) protocol.setDescription(body.getDescription());
        if (body.getTransport() != null) protocol.setTransport(body.getTransport());
        if (body.getFrameFormat() != null) protocol.setFrameFormat(body.getFrameFormat());
        if (body.getRulesJson() != null) {
            validateRules(body.getRulesJson());
            protocol.setRulesJson(body.getRulesJson());
        }
        if (body.getConfigJson() != null) {
            validateConfig(body);
            protocol.setConfigJson(body.getConfigJson());
        }
        ProtocolTemplate saved = protocolRepository.save(protocol);
        ProtocolEngine.evict(saved.getId());
        return ApiResponse.ok(saved);
    }

    /**
     * 解析测试:输入任意进制(bin/oct/dec/hex)报文文本,按模板拆分字段。
     * MODBUS 帧入参为完整 MBAP 帧,TLV/FIXED 为裸载荷。
     */
    @PostMapping("/{id}/parse")
    public ApiResponse<Map<String, Object>> parseTest(@PathVariable Long id, @RequestBody Map<String, String> body) {
        ProtocolTemplate protocol = protocolRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("协议模板不存在: " + id));
        String base = body.getOrDefault("base", "hex");
        byte[] frame = BaseCodec.decode(body.get("frame"), base);
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Object> fields;
        if (protocol.getFrameFormat() == ProtocolTemplate.FrameFormat.MODBUS) {
            // 完整 ADU:截取 FC16 写入的寄存器数据段
            fields = parseModbusAdu(protocol, frame);
            result.put("inputBase", base);
            result.put("frameHex", ValueCodec.hex(frame));
        } else {
            fields = ProtocolEngine.parse(protocol, frame);
        }
        result.put("fields", fields);
        result.put("byteLength", frame.length);
        return ApiResponse.ok(result);
    }

    /** MODBUS 解析测试:从完整 MBAP+FC16 帧中截取寄存器字节段再走 regMap 映射 */
    private Map<String, Object> parseModbusAdu(ProtocolTemplate protocol, byte[] adu) {
        if (adu.length < 13 || (adu[7] & 0xFF) != 0x10) {
            throw new IllegalArgumentException("MODBUS 测试帧须为 FC16(0x10 写多寄存器)完整 MBAP 帧");
        }
        int byteCount = adu[12] & 0xFF;
        if (adu.length < 13 + byteCount) {
            throw new IllegalArgumentException("帧长度与 byteCount 不一致");
        }
        return ProtocolEngine.parse(protocol, java.util.Arrays.copyOfRange(adu, 13, 13 + byteCount));
    }

    @DeleteMapping("/{id}")
    @OpLog(module = "协议管理", action = "删除")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        ProtocolTemplate protocol = protocolRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("协议模板不存在: " + id));
        long bound = deviceRepository.findAll().stream()
                .filter(d -> d.getProtocol() != null && d.getProtocol().getId().equals(id))
                .count();
        if (bound > 0) {
            throw new IllegalArgumentException("仍有 " + bound + " 台设备绑定该协议,先解绑再删除");
        }
        protocolRepository.delete(protocol);
        ProtocolEngine.evict(id);
        return ApiResponse.ok();
    }

    /** rulesJson 必须可解析为规则数组且 tag 不重复 */
    private void validateRules(String rulesJson) {
        if (rulesJson == null || rulesJson.isBlank()) {
            return;
        }
        List<TlvRule> rules;
        try {
            rules = objectMapper.readValue(rulesJson, new TypeReference<List<TlvRule>>() {
            });
        } catch (Exception e) {
            throw new IllegalArgumentException("规则 JSON 解析失败: " + e.getMessage());
        }
        Set<Integer> tags = new HashSet<>();
        for (TlvRule r : rules) {
            if (r.field() == null || r.field().isBlank()) {
                throw new IllegalArgumentException("规则字段名(field)不能为空");
            }
            if (!tags.add(r.tag())) {
                throw new IllegalArgumentException("tag 重复: " + r.tag());
            }
            if (r.radix() != null && (r.radix() != 2 && r.radix() != 8 && r.radix() != 10 && r.radix() != 16)) {
                throw new IllegalArgumentException("字段 " + r.field() + " 进制取值非法: " + r.radix() + "(可用 2/8/10/16)");
            }
            if (r.radix() != null && r.radix() != 10
                    && ("string".equals(r.type()) || "hex".equals(r.type()) || "float32".equals(r.type()))) {
                throw new IllegalArgumentException("字段 " + r.field() + " 类型 " + r.type() + " 不支持非十进制解析");
            }
        }
    }

    /** configJson 按帧格式校验:FIXED 偏移表/TLV 头参数/MODBUS 寄存器映射 */
    private void validateConfig(ProtocolTemplate body) {
        String json = body.getConfigJson();
        if (json == null || json.isBlank()) {
            return;
        }
        ProtocolConfig cfg;
        try {
            cfg = objectMapper.readValue(json, ProtocolConfig.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("帧结构 configJson 解析失败: " + e.getMessage());
        }
        ProtocolTemplate.FrameFormat ff = body.getFrameFormat() == null
                ? ProtocolTemplate.FrameFormat.TLV : body.getFrameFormat();
        switch (ff) {
            case FIXED -> {
                if (cfg.fields() == null || cfg.fields().isEmpty()) {
                    throw new IllegalArgumentException("定长帧格式须在 configJson 配置 fields 偏移切分表");
                }
                Set<String> names = new HashSet<>();
                for (ProtocolConfig.FixedField f : cfg.fields()) {
                    if (f.field() == null || f.field().isBlank()) {
                        throw new IllegalArgumentException("偏移字段名不能为空");
                    }
                    if (!names.add(f.field())) {
                        throw new IllegalArgumentException("偏移字段名重复: " + f.field());
                    }
                    if (f.offset() < 0 || f.len() < 1) {
                        throw new IllegalArgumentException("字段 " + f.field() + " 偏移/长度非法");
                    }
                }
            }
            case MODBUS -> {
                if (cfg.regMap() == null || cfg.regMap().isEmpty()) {
                    throw new IllegalArgumentException("MODBUS 格式须在 configJson 配置 regMap 寄存器映射");
                }
                Set<String> names = new HashSet<>();
                for (ProtocolConfig.ModbusReg r : cfg.regMap()) {
                    if (r.field() == null || r.field().isBlank()) {
                        throw new IllegalArgumentException("寄存器字段名不能为空");
                    }
                    if (!names.add(r.field())) {
                        throw new IllegalArgumentException("寄存器字段名重复: " + r.field());
                    }
                    if (r.reg() < 0 || r.count() < 1) {
                        throw new IllegalArgumentException("字段 " + r.field() + " 寄存器地址/个数非法");
                    }
                }
            }
            default -> {
                if (cfg.tagLen() != null && (cfg.tagLen() != 1 && cfg.tagLen() != 2 && cfg.tagLen() != 4)) {
                    throw new IllegalArgumentException("tagLen 取值非法(可用 1/2/4)");
                }
                if (cfg.lenLen() != null && (cfg.lenLen() != 1 && cfg.lenLen() != 2 && cfg.lenLen() != 4)) {
                    throw new IllegalArgumentException("lenLen 取值非法(可用 1/2/4)");
                }
            }
        }
    }
}
