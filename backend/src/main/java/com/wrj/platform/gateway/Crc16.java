package com.wrj.platform.gateway;

/** CRC-16/MODBUS:poly 0xA001(反转),init 0xFFFF,低字节在前 */
public final class Crc16 {

    private Crc16() {
    }

    public static int modbus(byte[] data, int offset, int len) {
        int crc = 0xFFFF;
        for (int i = offset; i < offset + len && i < data.length; i++) {
            crc ^= (data[i] & 0xFF);
            for (int b = 0; b < 8; b++) {
                if ((crc & 0x0001) != 0) {
                    crc = (crc >> 1) ^ 0xA001;
                } else {
                    crc >>= 1;
                }
            }
        }
        return crc & 0xFFFF;
    }

    public static int modbus(byte[] data) {
        return modbus(data, 0, data.length);
    }
}
