import net from 'node:net';

const code = process.argv[2] || 'WS-0001';
const secret = process.argv[3] || 'secret-ws01';

function crc16Modbus(buf, offset, len) {
  let crc = 0xFFFF;
  for (let i = offset; i < offset + len; i++) {
    crc ^= buf[i];
    for (let j = 0; j < 8; j++) crc = (crc & 1) ? (crc >> 1) ^ 0xA001 : (crc >> 1);
  }
  return crc & 0xFFFF;
}
function tlv(tag, value) {
  const head = Buffer.alloc(3);
  head[0] = tag; head.writeUInt16BE(value.length, 1);
  return Buffer.concat([head, value]);
}
const c = Buffer.from(code, 'ascii');
const p = tlv(0x01, Buffer.from(secret, 'utf8'));
const frame = Buffer.alloc(9 + c.length + p.length);
frame[0] = 0xAA; frame[1] = 0x55; frame[2] = 0x01; frame[3] = 0x01;
frame[4] = c.length; c.copy(frame, 5);
frame.writeUInt16BE(p.length, 5 + c.length); p.copy(frame, 7 + c.length);
const crc = crc16Modbus(frame, 2, frame.length - 4);
frame[frame.length - 2] = crc & 0xFF; frame[frame.length - 1] = (crc >> 8) & 0xFF;

const sock = net.connect({ host: '127.0.0.1', port: 9527 }, () => {
  console.log('sent:', frame.toString('hex'));
  sock.write(frame);
});
sock.on('data', (d) => {
  console.log('recv:', d.toString('hex'), JSON.stringify(d.toString('latin1')));
  sock.destroy();
});
sock.on('close', () => { console.log('closed'); process.exit(0); });
setTimeout(() => { console.log('TIMEOUT no reply'); process.exit(1); }, 6000);
