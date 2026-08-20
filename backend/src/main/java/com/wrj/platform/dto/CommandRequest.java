package com.wrj.platform.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** 下行指令请求:content 为指定进制(bin/oct/dec/hex)的 payload 文本 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CommandRequest(String base, String content) {
}
