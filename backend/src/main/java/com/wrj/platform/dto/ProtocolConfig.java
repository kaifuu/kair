package com.wrj.platform.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * 协议帧结构参数(configJson 反序列化,字段按 frameFormat 取用,未知键忽略):
 * - TLV:tagLen/lenLen/littleEndian
 * - FIXED:fields 偏移切分表(支持 bin/oct/dec/hex 多进制呈现)
 * - MODBUS:unitId/regMap 寄存器映射
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProtocolConfig(
        Integer tagLen,
        Integer lenLen,
        Boolean littleEndian,
        List<FixedField> fields,
        Integer unitId,
        List<ModbusReg> regMap) {

    /** 定长帧字段切分规则 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FixedField(int offset, int len, String field, String type,
                             Double scale, String unit, Integer radix) {
    }

    /** Modbus 寄存器映射:起始寄存器号 + 寄存器个数 → 字段 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ModbusReg(int reg, int count, String field, String type,
                            Double scale, String unit) {
    }
}
