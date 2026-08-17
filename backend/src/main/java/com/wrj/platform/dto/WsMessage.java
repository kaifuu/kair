package com.wrj.platform.dto;

/** WS 推送消息:type + payload */
public class WsMessage<T> {

    private String type;      // telemetry | alert | status
    private T payload;
    private long ts = System.currentTimeMillis();

    public WsMessage() {
    }

    public WsMessage(String type, T payload) {
        this.type = type;
        this.payload = payload;
    }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public T getPayload() { return payload; }
    public void setPayload(T payload) { this.payload = payload; }

    public long getTs() { return ts; }
    public void setTs(long ts) { this.ts = ts; }
}
