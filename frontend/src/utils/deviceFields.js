/**
 * 设备遥测字段展示元信息(物联网面板 / 视频面板 / 设备详情弹窗共用)
 * labels 中文短名,units 单位后缀,digits 小数位数(string 字段无 digits)
 */
export const FIELD_META = {
  // 无人机遥测
  lng: { label: '经度', unit: '°', digits: 6 },
  lat: { label: '纬度', unit: '°', digits: 6 },
  altitude: { label: '高度', unit: 'm', digits: 1 },
  speed: { label: '速度', unit: 'm/s', digits: 1 },
  heading: { label: '航向', unit: '°', digits: 1 },
  battery: { label: '电量', unit: '%', digits: 0 },
  satellites: { label: '卫星', unit: '颗', digits: 0 },
  // 气象
  temperature: { label: '温度', unit: '℃', digits: 1 },
  humidity: { label: '湿度', unit: '%RH', digits: 1 },
  windSpeed: { label: '风速', unit: 'm/s', digits: 1 },
  pressure: { label: '气压', unit: 'hPa', digits: 1 },
  // 环境/水文传感
  noiseDb: { label: '噪声', unit: 'dB', digits: 1 },
  pm25: { label: 'PM2.5', unit: 'μg/m³', digits: 0 },
  pm10: { label: 'PM10', unit: 'μg/m³', digits: 0 },
  co2: { label: 'CO₂', unit: 'ppm', digits: 0 },
  waterLevelM: { label: '水位', unit: 'm', digits: 2 },
  flowRate: { label: '流量', unit: 'm³/h', digits: 1 },
  signal: { label: '信号', unit: 'dBm', digits: 0 },
  // 视频
  fps: { label: '帧率', unit: 'fps', digits: 0 },
  bitrateKbps: { label: '码率', unit: 'kbps', digits: 0 },
  online: { label: '在线', unit: '', digits: 0 },
  alarms: { label: '告警', unit: '次', digits: 0 },
  // ADS-B / 机库 / PLC
  aircraft: { label: '航空器', unit: '架', digits: 0 },
  chargePct: { label: '电量', unit: '%', digits: 0 },
  doorState: { label: '舱门', unit: '', digits: null }
}

/** 字段格式化:数值按精度舍入并拼单位;文本原样;未知字段回落 key=value */
export function fmtField(key, value) {
  if (value === null || value === undefined || value === '') return '—'
  const meta = FIELD_META[key]
  if (!meta) {
    return `${key} ${value}`
  }
  if (typeof value === 'string' || meta.digits === null || typeof value !== 'number') {
    return `${value}${meta.unit ? ' ' + meta.unit : ''}`
  }
  const v = Number(value.toFixed(meta.digits))
  return `${v}${meta.unit ? ' ' + meta.unit : ''}`
}

export const fieldLabel = (key) => FIELD_META[key]?.label || key
