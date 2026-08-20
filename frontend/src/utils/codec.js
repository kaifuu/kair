/**
 * 报文内容多进制编解码(与后端 BaseCodec 互为镜像):
 * byte[] ↔ BIN/OCT/DEC/HEX 文本互转;buildTlvPayload 按协议规则反向编码 TLV payload(值 ÷ scale 取整)。
 */

export const BASES = [
  { value: 'hex', label: '十六进制' },
  { value: 'dec', label: '十进制' },
  { value: 'oct', label: '八进制' },
  { value: 'bin', label: '二进制' }
]

const RADIX = { hex: 16, dec: 10, oct: 8, bin: 2 }
const PREFIX = { hex: /^0x/, oct: /^0o/, bin: /^0b/ }
const DIGITS = { 16: /^[0-9a-f]+$/, 10: /^[0-9]+$/, 8: /^[0-7]+$/, 2: /^[01]+$/ }

/** Uint8Array → 进制文本(hex 两位大写 / bin 补 8 位,空格分隔) */
export function encodeBytes(bytes, base) {
  const r = RADIX[base]
  if (!r) throw new Error(`未知进制: ${base}`)
  if (!bytes || bytes.length === 0) return ''
  const parts = []
  for (const b of bytes) {
    const v = b & 0xFF
    if (r === 16) parts.push(v.toString(16).toUpperCase().padStart(2, '0'))
    else if (r === 2) parts.push(v.toString(2).padStart(8, '0'))
    else parts.push(v.toString(r))
  }
  return parts.join(' ')
}

export function bytesToHex(bytes) {
  return encodeBytes(bytes, 'hex')
}

/** 进制文本 → Uint8Array(空白/逗号分隔,容忍与本进制匹配的 0x/0b/0o 前缀) */
export function decodeBytes(text, base) {
  const r = RADIX[base]
  if (!r) throw new Error(`未知进制: ${base}`)
  const tokens = String(text || '').trim().split(/[\s,]+/).filter(Boolean)
  if (!tokens.length) throw new Error('报文内容不能为空')
  return new Uint8Array(tokens.map((t, i) => {
    const s = t.toLowerCase().replace(PREFIX[base] || '', '')
    if (!DIGITS[r].test(s)) {
      throw new Error(`第 ${i + 1} 个字节非法: "${t}" 不是有效的${baseLabel(base)}字节(0..255)`)
    }
    const v = parseInt(s, r)
    if (v > 255) throw new Error(`第 ${i + 1} 个字节越界: ${t}(超出 0..255)`)
    return v
  }))
}

/** 十六进制串(可含空格)→ Uint8Array,TLV hex 类型字段编码用 */
export function hexToBytes(hex) {
  const clean = String(hex || '').replace(/\s+/g, '')
  if (!clean) return new Uint8Array(0)
  if (clean.length % 2) throw new Error('十六进制长度须为偶数')
  const out = []
  for (let i = 0; i < clean.length; i += 2) {
    const v = parseInt(clean.slice(i, i + 2), 16)
    if (Number.isNaN(v)) throw new Error(`非法十六进制字节: ${clean.slice(i, i + 2)}`)
    out.push(v)
  }
  return new Uint8Array(out)
}

/**
 * 按协议规则编码 TLV payload:tag(1B)+len(2B 大端)+value。
 * values 为 {字段名: 输入值} 映射;数值字段按 值 ÷ scale 取整后大端写入,空值字段跳过。
 */
export function buildTlvPayload(rules, values) {
  const chunks = []
  for (const r of rules || []) {
    if (!r.field) continue
    const raw = values?.[r.field]
    if (raw === undefined || raw === null || raw === '') continue
    const type = r.type || 'uint16'
    let valueBytes
    if (type === 'string') {
      valueBytes = new TextEncoder().encode(String(raw))
    } else if (type === 'hex') {
      valueBytes = hexToBytes(raw)
    } else {
      const num = Number(raw)
      if (Number.isNaN(num)) throw new Error(`字段 ${r.field} 的值不是数字`)
      const scale = (r.scale && r.scale !== 0) ? r.scale : 1
      valueBytes = encodeNumber(num / scale, type, r.field)
    }
    if (valueBytes.length > 0xFFFF) throw new Error(`字段 ${r.field} 编码后超长(${valueBytes.length}B)`)
    chunks.push(new Uint8Array([r.tag & 0xFF, valueBytes.length >> 8, valueBytes.length & 0xFF]), valueBytes)
  }
  const total = chunks.reduce((n, c) => n + c.length, 0)
  const out = new Uint8Array(total)
  let pos = 0
  for (const c of chunks) {
    out.set(c, pos)
    pos += c.length
  }
  return out
}

function encodeNumber(scaled, type, field) {
  const buf = new ArrayBuffer(4)
  const dv = new DataView(buf)
  const v = Math.round(scaled)
  const range = (min, max) => {
    if (v < min || v > max) {
      throw new Error(`字段 ${field} 编码后超出 ${type} 范围(${v},允许 ${min}..${max}),请检查值或 scale`)
    }
  }
  switch (type) {
    case 'uint8':
      range(0, 255)
      return new Uint8Array([v])
    case 'uint16':
      range(0, 65535)
      dv.setUint16(0, v)
      return new Uint8Array(buf.slice(0, 2))
    case 'int16':
      range(-32768, 32767)
      dv.setInt16(0, v)
      return new Uint8Array(buf.slice(0, 2))
    case 'uint32':
      range(0, 4294967295)
      dv.setUint32(0, v)
      return new Uint8Array(buf)
    case 'int32':
      range(-2147483648, 2147483647)
      dv.setInt32(0, v)
      return new Uint8Array(buf)
    case 'float32':
      dv.setFloat32(0, scaled)
      return new Uint8Array(buf)
    default:
      throw new Error(`字段 ${field} 类型 ${type} 不支持编码`)
  }
}

function baseLabel(base) {
  return { hex: '十六进制', dec: '十进制', oct: '八进制', bin: '二进制' }[base] || base
}
