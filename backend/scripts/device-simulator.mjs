#!/usr/bin/env node
/**
 * 设备接入模拟器(零依赖,Node 18+),三种接入模式:
 *  1) 标准帧(默认,9527):  node device-simulator.mjs --code UAV-2024-0002 --secret secret-0002 --protocol drone
 *  2) DTU 串口透传(9528):  node device-simulator.mjs --mode transparent --code AQ-0001 --secret secret-aq01
 *  3) Modbus TCP PLC(9529): node device-simulator.mjs --mode modbus --unit 1
 *
 * 标准帧格式(与服务端 DeviceFrameDecoder 互为镜像):
 *   magic(2)=AA55 | ver(1)=01 | type(1) | codeLen(1) | code(ASCII) | payloadLen(2,BE) | payload | crc16(2,低字节在前)
 *   CRC-16/MODBUS poly 0xA001 init 0xFFFF, 覆盖 [ver..payload]
 * TLV 单元: tag(1B)+len(2B,BE)+value
 * DTU 透传行协议: REG:<code>:<secret>\r\n → OK / ERR,之后 DATA:hex:<hex>\r\n 上报定长帧
 * Modbus: MBAP 头 + FC16(0x10) 写保持寄存器 0-3(温度/湿度/压力/流量 ×0.1)
 */
import net from 'node:net';

// ---------- 参数 ----------
const args = process.argv.slice(2);
const opt = (name, def) => {
  const i = args.indexOf(`--${name}`);
  return i >= 0 && args[i + 1] ? args[i + 1] : def;
};
const mode = opt('mode', 'standard');              // standard | transparent | modbus
const code = opt('code', '');
const secret = opt('secret', '');
const protocol = opt('protocol', 'drone');        // drone | weather
const interval = Number(opt('interval', 3000));
const host = opt('host', '127.0.0.1');
const port = Number(opt('port', mode === 'transparent' ? 9528 : mode === 'modbus' ? 9529 : 9527));
const unitId = Number(opt('unit', 1));

if ((mode === 'standard' || mode === 'transparent') && (!code || !secret)) {
  console.error('用法: node device-simulator.mjs --mode standard|transparent --code <设备编码> --secret <密钥> [--protocol drone|weather] [--interval ms] [--host] [--port]');
  console.error('      node device-simulator.mjs --mode modbus [--unit 1] [--interval ms] [--host] [--port]');
  process.exit(1);
}

// ---------- 帧编解码 ----------
function crc16Modbus(buf, offset, len) {
  let crc = 0xFFFF;
  for (let i = offset; i < offset + len; i++) {
    crc ^= buf[i];
    for (let j = 0; j < 8; j++) {
      crc = (crc & 1) ? (crc >> 1) ^ 0xA001 : (crc >> 1);
    }
  }
  return crc & 0xFFFF;
}

const TYPE = { REGISTER: 0x01, HEARTBEAT: 0x02, DATA: 0x03, ACK: 0x04, COMMAND: 0x05 };

function buildFrame(type, deviceCode, payload) {
  const c = Buffer.from(deviceCode, 'ascii');
  const p = payload || Buffer.alloc(0);
  const frame = Buffer.alloc(9 + c.length + p.length);
  frame[0] = 0xAA; frame[1] = 0x55;
  frame[2] = 0x01; frame[3] = type;
  frame[4] = c.length;
  c.copy(frame, 5);
  const lenPos = 5 + c.length;
  frame.writeUInt16BE(p.length, lenPos);
  p.copy(frame, lenPos + 2);
  const crc = crc16Modbus(frame, 2, frame.length - 4);
  frame[frame.length - 2] = crc & 0xFF;
  frame[frame.length - 1] = (crc >> 8) & 0xFF;
  return frame;
}

function tlv(tag, value) {
  const head = Buffer.alloc(3);
  head[0] = tag;
  head.writeUInt16BE(value.length, 1);
  return Buffer.concat([head, value]);
}

/** 从缓冲区解析完整帧,返回 {frame, rest} 或 null */
function parseFrame(buf) {
  let start = buf.indexOf(0xAA);
  while (start !== -1 && start + 1 < buf.length && buf[start + 1] !== 0x55) {
    start = buf.indexOf(0xAA, start + 1);
  }
  if (start === -1 || start + 1 >= buf.length) return null;
  const b = buf.subarray(start);
  if (b.length < 9) return null;
  const codeLen = b[4];
  if (codeLen < 1 || codeLen > 32) {
    // 脏数据,跳过这个 0xAA
    return parseFrame(buf.subarray(start + 1));
  }
  const payloadLen = b.readUInt16BE(5 + codeLen);
  const total = 9 + codeLen + payloadLen;
  if (b.length < total) return null;
  const frame = b.subarray(0, total);
  const expect = crc16Modbus(frame, 2, total - 4);
  const got = frame[total - 2] | (frame[total - 1] << 8);
  if (expect !== got) {
    return parseFrame(buf.subarray(start + 1));
  }
  return { frame, type: frame[3], rest: buf.subarray(start + total) };
}

// ---------- 模拟数据 ----------
const t0 = Date.now();
let battery = 92;

function dronePayload() {
  const t = (Date.now() - t0) / 1000;
  const R = 0.0035;                                  // 绕圈半径(度)
  const lng = 116.408 + R * Math.cos(t / 20);
  const lat = 39.904 + R * Math.sin(t / 20) * 0.8;
  battery = Math.max(8, battery - 0.05);
  const heading = ((t / 20 * 57.3 + 90) % 360 + 360) % 360;
  return Buffer.concat([
    tlv(0x01, uint32(Math.round(lng * 1e6))),       // lng ×1e-6
    tlv(0x02, uint32(Math.round(lat * 1e6))),       // lat ×1e-6
    tlv(0x03, uint16(Math.round((110 + 10 * Math.sin(t / 8)) * 10))),  // altitude ×0.1
    tlv(0x04, Buffer.of(Math.round(battery))),      // battery
    tlv(0x05, uint16(Math.round((8 + 3 * Math.abs(Math.sin(t / 5))) * 10))),  // speed
    tlv(0x06, uint16(Math.round(heading * 10))),    // heading
    tlv(0x07, Buffer.of(14 + Math.floor(Math.random() * 7))),  // satellites
  ]);
}

function weatherPayload() {
  const t = (Date.now() - t0) / 1000;
  return Buffer.concat([
    tlv(0x01, int16(Math.round((22 + 4 * Math.sin(t / 60)) * 10))),     // temperature ×0.1
    tlv(0x02, uint16(Math.round((55 + 10 * Math.sin(t / 90 + 1)) * 10))),  // humidity ×0.1
    tlv(0x03, uint16(Math.round((3.2 + 1.5 * Math.abs(Math.sin(t / 45))) * 10))),  // windSpeed ×0.1
    tlv(0x04, uint16(Math.round(1013.2 * 10))),    // pressure ×0.1
  ]);
}

function uint16(v) { const b = Buffer.alloc(2); b.writeUInt16BE(v & 0xFFFF); return b; }
function int16(v) { const b = Buffer.alloc(2); b.writeInt16BE(v); return b; }
function uint32(v) { const b = Buffer.alloc(4); b.writeUInt32BE(v >>> 0); return b; }

// ---------- 主流程 ----------
if (mode === 'transparent') {
  transparentMode();
} else if (mode === 'modbus') {
  modbusMode();
} else {
  standardMode();
}

/** 模式 1:AA55 标准帧(9527) */
function standardMode() {
  const sock = net.connect({ host, port }, () => {
    console.log(`[${code}] connected ${host}:${port}, registering ...`);
    sock.write(buildFrame(TYPE.REGISTER, code, tlv(0x01, Buffer.from(secret, 'utf8'))));
  });

  let registered = false;
  let rx = Buffer.alloc(0);
  let dataTimer = null;
  let beatTimer = null;

  sock.on('data', (chunk) => {
    rx = Buffer.concat([rx, chunk]);
    for (;;) {
      const parsed = parseFrame(rx);
      if (!parsed) break;
      rx = parsed.rest;
      if (parsed.type === TYPE.COMMAND) {
        // 平台下发的指令帧:打印 payload(HEX 空格分隔)
        const codeLen = parsed.frame[4];
        const payloadLen = parsed.frame.readUInt16BE(5 + codeLen);
        const payload = parsed.frame.subarray(7 + codeLen, 7 + codeLen + payloadLen);
        const hex = [...payload].map((b) => b.toString(16).padStart(2, '0').toUpperCase()).join(' ');
        console.log(`[${code}] COMMAND received (${payloadLen}B): ${hex}`);
        continue;
      }
      if (parsed.type === TYPE.ACK) {
        // ACK payload 布局: ... payloadLen(2) payload(status+msgLen+msg) crc(2)
        const codeLen = parsed.frame[4];
        const payloadLen = parsed.frame.readUInt16BE(5 + codeLen);
        const payloadStart = 7 + codeLen;
        const status = payloadLen > 0 ? parsed.frame[payloadStart] : 1;
        const msgLen = payloadLen > 1 ? parsed.frame[payloadStart + 1] : 0;
        const msg = parsed.frame.subarray(payloadStart + 2, payloadStart + 2 + msgLen).toString('ascii');
        if (status === 0) {
          if (!registered) {
            registered = true;
            console.log(`[${code}] register OK (${msg || 'OK'}), streaming ${protocol} data every ${interval}ms`);
            dataTimer = setInterval(() => {
              sock.write(buildFrame(TYPE.DATA, code,
                protocol === 'weather' ? weatherPayload() : dronePayload()));
            }, interval);
            beatTimer = setInterval(() => {
              sock.write(buildFrame(TYPE.HEARTBEAT, code, Buffer.alloc(0)));
            }, Math.min(interval * 10, 60000));
          }
        } else {
          console.error(`[${code}] server rejected: ${msg || 'failed'}`);
          process.exit(2);
        }
      }
    }
  });

  sock.on('error', (e) => {
    console.error(`[${code}] socket error: ${e.message}`);
    process.exit(3);
  });

  sock.on('close', () => {
    if (dataTimer) clearInterval(dataTimer);
    if (beatTimer) clearInterval(beatTimer);
    console.log(`[${code}] connection closed`);
    process.exit(registered ? 0 : 4);
  });

  process.on('SIGINT', () => {
    console.log(`\n[${code}] SIGINT, closing ...`);
    sock.destroy();
  });
}

/** 模式 2:RS232/RS485 串口设备经 DTU 透传(9528 行协议 REG/PING/DATA) */
function transparentMode() {
  const sock = net.connect({ host, port }, () => {
    console.log(`[${code}] connected ${host}:${port} (DTU transparent), registering ...`);
    sock.write(`REG:${code}:${secret}\r\n`);
  });

  let registered = false;
  let lineBuf = '';
  let dataTimer = null;
  let pingTimer = null;

  sock.setEncoding('utf8');
  sock.on('data', (chunk) => {
    lineBuf += chunk;
    let idx;
    while ((idx = lineBuf.indexOf('\n')) >= 0) {
      const line = lineBuf.slice(0, idx).trim();
      lineBuf = lineBuf.slice(idx + 1);
      if (!line) continue;
      if (line.startsWith('OK')) {
        if (!registered) {
          registered = true;
          console.log(`[${code}] register OK, streaming fixed-frame (hex) data every ${interval}ms`);
          dataTimer = setInterval(() => {
            const bytes = [...fixedEnvFrame()].map((b) => b.toString(16).padStart(2, '0').toUpperCase());
            sock.write(`DATA:hex:${bytes.join(' ')}\r\n`);
          }, interval);
          pingTimer = setInterval(() => sock.write('PING\r\n'), Math.min(interval * 10, 60000));
        }
      } else if (line.startsWith('ERR')) {
        console.error(`[${code}] server rejected: ${line}`);
        process.exit(2);
      } else if (line.startsWith('PONG')) {
        // 心跳应答,静默
      } else {
        console.log(`[${code}] <- ${line}`);
      }
    }
  });

  sock.on('error', (e) => {
    console.error(`[${code}] socket error: ${e.message}`);
    process.exit(3);
  });

  sock.on('close', () => {
    if (dataTimer) clearInterval(dataTimer);
    if (pingTimer) clearInterval(pingTimer);
    console.log(`[${code}] connection closed`);
    process.exit(registered ? 0 : 4);
  });

  process.on('SIGINT', () => {
    console.log(`\n[${code}] SIGINT, closing ...`);
    sock.destroy();
  });
}

/** 环境微站 10 字节定长帧:温度/湿度 int16×0.1 + PM2.5/CO2 uint16 + 噪声 uint16×0.1(大端) */
function fixedEnvFrame() {
  const t = (Date.now() - t0) / 1000;
  return Buffer.concat([
    int16(Math.round((21 + 4 * Math.sin(t / 60)) * 10)),
    uint16(Math.round((58 + 10 * Math.sin(t / 90 + 1)) * 10)),
    uint16(Math.round(42 + 20 * Math.sin(t / 180))),
    uint16(Math.round(420 + 80 * Math.sin(t / 240))),
    uint16(Math.round((58 + 10 * Math.abs(Math.sin(t / 70))) * 10)),
  ]);
}

/** 模式 3:PLC 经 Modbus TCP(9529),FC16 周期写保持寄存器 0-3 */
function modbusMode() {
  let txId = 0;
  const sock = net.connect({ host, port }, () => {
    console.log(`[PLC unit=${unitId}] connected ${host}:${port} (Modbus TCP), writing regs 0-3 every ${interval}ms`);
    setInterval(() => {
      const t = (Date.now() - t0) / 1000;
      const regs = [
        Math.round((26 + 3 * Math.sin(t / 60)) * 10),     // 温度 ×0.1 ℃
        Math.round((55 + 10 * Math.sin(t / 90)) * 10),    // 湿度 ×0.1 %RH
        Math.round((101.3 + 2 * Math.sin(t / 300)) * 10), // 压力 ×0.1 kPa
        Math.round((6.5 + 2 * Math.sin(t / 120)) * 10),   // 流量 ×0.1 m³/h
      ];
      sock.write(fc16Write(txId++ & 0xFFFF, unitId, 0, regs));
    }, interval);
  });

  let rx = Buffer.alloc(0);
  sock.on('data', (chunk) => {
    rx = Buffer.concat([rx, chunk]);
    while (rx.length >= 9) {                    // MBAP(7) + FC(1) + 起始地址(2) ... 回显至少 9 字节
      const len = rx.readUInt16BE(4);
      if (rx.length < 6 + len) break;
      const frame = rx.subarray(0, 6 + len);
      rx = rx.subarray(6 + len);
      const fc = frame[7];
      if (fc === 0x10) {
        console.log(`[PLC] FC16 ack tid=${frame.readUInt16BE(0)} start=${frame.readUInt16BE(8)} qty=${frame.readUInt16BE(10)}`);
      } else if (fc > 0x80) {
        console.error(`[PLC] Modbus exception fc=0x${fc.toString(16)} code=0x${frame[8].toString(16)}`);
      } else {
        console.log(`[PLC] <- ${[...frame].map((b) => b.toString(16).padStart(2, '0')).join(' ').toUpperCase()}`);
      }
    }
  });

  sock.on('error', (e) => {
    console.error(`[PLC] socket error: ${e.message}`);
    process.exit(3);
  });

  sock.on('close', () => {
    console.log('[PLC] connection closed');
    process.exit(0);
  });

  process.on('SIGINT', () => {
    console.log('\n[PLC] SIGINT, closing ...');
    sock.destroy();
  });
}

/** Modbus FC16(写多个保持寄存器)请求 ADU:MBAP + PDU */
function fc16Write(tid, uid, startReg, regs) {
  const byteCount = regs.length * 2;
  const pdu = Buffer.alloc(6 + byteCount);
  pdu[0] = 0x10;
  pdu.writeUInt16BE(startReg, 1);
  pdu.writeUInt16BE(regs.length, 3);
  pdu[5] = byteCount;
  regs.forEach((v, i) => pdu.writeInt16BE(v & 0xFFFF, 6 + i * 2));
  const adu = Buffer.alloc(7 + pdu.length);    // 事务号(2)+协议号(2)+长度(2)+单元号(1)
  adu.writeUInt16BE(tid, 0);
  adu.writeUInt16BE(0, 2);
  adu.writeUInt16BE(1 + pdu.length, 4);
  adu[6] = uid;
  pdu.copy(adu, 7);
  return adu;
}
