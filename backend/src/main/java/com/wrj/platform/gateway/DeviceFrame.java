package com.wrj.platform.gateway;

import java.nio.charset.StandardCharsets;

/** 设备接入帧 POJO:magic(AA55)+ver+type+codeLen+code+payloadLen+payload+crc16 */
public record DeviceFrame(byte type, String code, byte[] payload) {

    public static final byte TYPE_REGISTER = 0x01;
    public static final byte TYPE_HEARTBEAT = 0x02;
    public static final byte TYPE_DATA = 0x03;
    public static final byte TYPE_ACK = 0x04;
    public static final byte TYPE_COMMAND = 0x05;

    private static final byte[] MAGIC = {(byte) 0xAA, 0x55};
    private static final byte VERSION = 0x01;

    /** 组帧(含 CRC),下行 ACK/后续指令用 */
    public static byte[] encode(byte type, String code, byte[] payload) {
        byte[] c = code.getBytes(StandardCharsets.US_ASCII);
        byte[] p = payload == null ? new byte[0] : payload;
        byte[] frame = new byte[9 + c.length + p.length];
        frame[0] = MAGIC[0];
        frame[1] = MAGIC[1];
        frame[2] = VERSION;
        frame[3] = type;
        frame[4] = (byte) c.length;
        System.arraycopy(c, 0, frame, 5, c.length);
        int lenPos = 5 + c.length;
        frame[lenPos] = (byte) ((p.length >> 8) & 0xFF);
        frame[lenPos + 1] = (byte) (p.length & 0xFF);
        System.arraycopy(p, 0, frame, lenPos + 2, p.length);
        int crc = Crc16.modbus(frame, 2, frame.length - 4);   // 覆盖 [ver..payload]
        frame[frame.length - 2] = (byte) (crc & 0xFF);        // 低字节在前
        frame[frame.length - 1] = (byte) ((crc >> 8) & 0xFF);
        return frame;
    }

    /** 帧类型名(报文日志展示用) */
    public static String typeName(byte type) {
        return switch (type) {
            case TYPE_REGISTER -> "REGISTER";
            case TYPE_HEARTBEAT -> "HEARTBEAT";
            case TYPE_DATA -> "DATA";
            case TYPE_ACK -> "ACK";
            case TYPE_COMMAND -> "COMMAND";
            default -> "UNKNOWN_" + String.format("%02X", type);
        };
    }

    /** ACK payload:status(0 成功/1 失败)+msgLen+msg(UTF-8) */
    public static byte[] encodeAck(boolean ok, String msg) {
        byte[] m = (msg == null ? "" : msg).getBytes(StandardCharsets.UTF_8);
        byte[] payload = new byte[2 + m.length];
        payload[0] = (byte) (ok ? 0 : 1);
        payload[1] = (byte) m.length;
        System.arraycopy(m, 0, payload, 2, m.length);
        return payload;
    }
}
