<template>
  <div class="page stats-light">
    <div class="light-root">
      <!-- 顶部横幅:政务蓝渐变 -->
      <div class="gov-banner">
        <div class="banner-glow"></div>
        <div class="banner-left">
          <span class="title-mark"></span>
          <div>
            <div class="banner-title">飞行运行统计分析</div>
            <div class="banner-sub">低空监管平台 · 数据总览</div>
          </div>
        </div>
        <div class="banner-right">
          <span class="live-pill"><i class="live-dot"></i>数据实时更新</span>
          <span class="clock">{{ clock }}</span>
          <el-button class="banner-btn" :icon="Refresh" @click="loadAll">刷新</el-button>
        </div>
      </div>

      <!-- KPI 指标卡 -->
      <div class="kpi-strip">
        <div v-for="k in kpis" :key="k.label" class="kpi-card" :style="k.style">
          <div class="kpi-icon" :style="k.iconStyle">{{ k.icon }}</div>
          <div class="kpi-body">
            <div class="kpi-num">{{ k.display }}<span class="unit">{{ k.unit }}</span></div>
            <div class="kpi-label">{{ k.label }}</div>
            <div class="kpi-sub">{{ k.sub }}</div>
          </div>
        </div>
      </div>

      <!-- 图表网格 -->
      <div class="grid">
        <div class="card-panel span-2">
          <div class="card-title">近 30 日飞行与告警趋势</div>
          <div ref="trendRef" class="chart"></div>
        </div>
        <div class="card-panel">
          <div class="card-title">任务状态分布</div>
          <div ref="taskStatusRef" class="chart"></div>
        </div>

        <div class="card-panel">
          <div class="card-title">24 小时飞行活跃度</div>
          <div ref="hourlyRef" class="chart"></div>
        </div>
        <div class="card-panel">
          <div class="card-title">无人机机型分布</div>
          <div ref="modelRef" class="chart"></div>
        </div>
        <div class="card-panel">
          <div class="card-title">设备构成</div>
          <div ref="categoryRef" class="chart"></div>
        </div>

        <div class="card-panel">
          <div class="card-title">告警类型分布</div>
          <div ref="alertRef" class="chart"></div>
        </div>
        <div class="card-panel span-2">
          <div class="title-row">
            <div class="card-title">飞手飞行时长排行 TOP10</div>
            <div class="rank-legend">
              <span><i style="background:#155eef"></i>前三名</span>
              <span><i style="background:#7fb0f7"></i>其他</span>
            </div>
          </div>
          <div ref="rankRef" class="chart"></div>
        </div>
      </div>

      <div class="page-footer">数据来源:平台运行数据库 · 每分钟自动更新 · 统计时间 {{ clock.slice(0, 10) }}</div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import * as echarts from 'echarts'
import { Refresh } from '@element-plus/icons-vue'
import http from '../api'

const trendRef = ref(null)
const taskStatusRef = ref(null)
const hourlyRef = ref(null)
const modelRef = ref(null)
const categoryRef = ref(null)
const alertRef = ref(null)
const rankRef = ref(null)

const clock = ref('')
const kpis = ref([])

let charts = {}
let timer = null
let clockTimer = null

/* ===== 亮蓝白主题色板(政务风) ===== */
const C = {
  blue: '#155eef', sky: '#0ea5e9', cyan: '#22b8e6', purple: '#7c3aed',
  green: '#12b76a', yellow: '#f79009', red: '#f04438', pink: '#d6556f',
  textDim: '#667085', text: '#344054',
  axisLine: 'rgba(84,118,180,0.35)',
  split: '#eef2f7',
  tooltipBg: '#ffffff', tooltipBorder: '#e4eaf2'
}
const PALETTE = [C.blue, C.sky, C.purple, C.green, C.yellow, C.pink, C.cyan]

const lightAxis = {
  axisLine: { lineStyle: { color: C.axisLine } },
  axisLabel: { color: C.textDim, fontSize: 11 },
  splitLine: { lineStyle: { color: C.split } }
}
const lightTooltip = {
  backgroundColor: C.tooltipBg,
  borderColor: C.tooltipBorder,
  textStyle: { color: C.text, fontSize: 12 },
  extraCssText: 'box-shadow: 0 6px 20px rgba(21,94,239,0.12);'
}

onMounted(() => {
  tickClock()
  clockTimer = setInterval(tickClock, 1000)
  loadAll()
  timer = setInterval(loadAll, 60_000)
  window.addEventListener('resize', resizeAll)
})

onUnmounted(() => {
  clearInterval(timer)
  clearInterval(clockTimer)
  window.removeEventListener('resize', resizeAll)
  Object.values(charts).forEach((c) => c.dispose())
  charts = {}
})

function tickClock() {
  const d = new Date()
  const p = (n) => String(n).padStart(2, '0')
  clock.value = `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}

function resizeAll() {
  Object.values(charts).forEach((c) => c.resize())
}

async function loadAll() {
  const [overview, trend, taskStatus, hourly, models, categories, alertTypes, rank] = await Promise.all([
    http.get('/stats/overview'),
    http.get('/stats/trend?days=30'),
    http.get('/stats/task-status'),
    http.get('/stats/hourly-flights'),
    http.get('/stats/drone-model'),
    http.get('/stats/device-category'),
    http.get('/stats/alert-type'),
    http.get('/stats/pilot-rank')
  ])
  renderKpis(overview)
  await nextTick()
  renderTrend(trend)
  renderTaskStatus(taskStatus)
  renderHourly(hourly)
  renderModel(models)
  renderCategory(categories)
  renderAlert(alertTypes)
  renderRank(rank)
}

/* ===== KPI 指标卡 ===== */
function renderKpis(o) {
  const defs = [
    { label: '接入设备', icon: '◈', unit: '台', value: o.deviceTotal, sub: `在线 ${o.deviceOnline} · 在线率 ${o.deviceTotal ? Math.round((o.deviceOnline / o.deviceTotal) * 100) : 0}%`, color: C.blue },
    { label: '无人机', icon: '✦', unit: '架', value: o.droneTotal, sub: `正在飞行 ${o.flyingNow}`, color: C.sky },
    { label: '飞行任务', icon: '➤', unit: '个', value: o.taskTotal, sub: `执行中 ${o.taskFlying} · 待执行 ${o.taskPending}`, color: C.purple },
    { label: '未处理告警', icon: '⚠', unit: '条', value: o.alertUnhandled, sub: '待确认处置', color: o.alertUnhandled > 0 ? C.red : C.green },
    { label: '认证飞手', icon: '☉', unit: '人', value: o.pilotTotal, sub: '持证上岗', color: C.green },
    { label: '电子围栏', icon: '⬡', unit: '个', value: o.fenceTotal, sub: '空域管控', color: C.yellow }
  ]
  kpis.value = defs.map((d) => ({
    ...d,
    display: d.value,
    style: { '--kpi-line': d.color },
    iconStyle: {
      color: '#ffffff',
      background: `linear-gradient(135deg, ${d.color}, ${d.color}b8)`,
      boxShadow: `0 4px 10px ${d.color}40`
    }
  }))
}

/* ===== 各图表 ===== */
function chart(el, key) {
  charts[key]?.dispose()
  charts[key] = echarts.init(el)
  return charts[key]
}

function renderTrend(data) {
  chart(trendRef.value, 'trend').setOption({
    grid: { left: 44, right: 44, top: 42, bottom: 28 },
    legend: {
      top: 4, right: 8, icon: 'roundRect', itemWidth: 14, itemHeight: 4,
      textStyle: { color: C.textDim, fontSize: 11 }
    },
    tooltip: { trigger: 'axis', ...lightTooltip, axisPointer: { lineStyle: { color: C.axisLine } } },
    xAxis: { type: 'category', boundaryGap: false, data: data.map((d) => d.date.slice(5)), ...lightAxis },
    yAxis: [
      { type: 'value', name: '任务/告警', nameTextStyle: { color: C.textDim }, ...lightAxis, minInterval: 1 },
      { type: 'value', name: '时长(h)', nameTextStyle: { color: C.textDim }, ...lightAxis, splitLine: { show: false } }
    ],
    series: [
      {
        name: '飞行任务', type: 'line', smooth: true, symbolSize: 6, z: 3,
        data: data.map((d) => d.flights),
        lineStyle: { width: 3, color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [{ offset: 0, color: C.blue }, { offset: 1, color: C.sky }]) },
        itemStyle: { color: C.sky, borderColor: '#ffffff', borderWidth: 2 },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(21,94,239,0.18)' },
            { offset: 1, color: 'rgba(14,165,233,0)' }
          ])
        },
        markLine: {
          silent: true, symbol: 'none',
          lineStyle: { color: '#f79009aa', type: 'dashed', width: 1 },
          label: { color: '#b25e09', fontSize: 10, formatter: '日均 {c}' },
          data: [{ type: 'average', name: '日均' }]
        }
      },
      {
        name: '告警', type: 'bar', barWidth: 8, z: 2,
        data: data.map((d) => d.alerts),
        itemStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [{ offset: 0, color: C.purple }, { offset: 1, color: 'rgba(124,58,237,0.12)' }]), borderRadius: [3, 3, 0, 0] }
      },
      {
        name: '时长(h)', type: 'line', smooth: true, symbol: 'none', yAxisIndex: 1, z: 1,
        data: data.map((d) => d.hours),
        lineStyle: { width: 2, type: 'dashed', color: C.green + 'bb' },
        itemStyle: { color: C.green }
      }
    ]
  })
}

function renderTaskStatus(data) {
  const total = data.reduce((s, d) => s + d.value, 0)
  chart(taskStatusRef.value, 'taskStatus').setOption({
    tooltip: { trigger: 'item', ...lightTooltip },
    legend: { bottom: 0, icon: 'circle', textStyle: { color: C.textDim, fontSize: 11 }, itemWidth: 8, itemHeight: 8 },
    title: {
      text: String(total), subtext: '任务总数', left: 'center', top: '34%',
      textStyle: { color: '#1d2939', fontSize: 28, fontWeight: 700 },
      subtextStyle: { color: C.textDim, fontSize: 11 }
    },
    series: [{
      type: 'pie', radius: ['52%', '70%'], center: ['50%', '42%'],
      itemStyle: { borderColor: '#ffffff', borderWidth: 2, borderRadius: 4 },
      label: { color: C.textDim, fontSize: 11, formatter: '{b}\n{c}' },
      labelLine: { lineStyle: { color: C.axisLine } },
      data: data.map((d, i) => ({ ...d, itemStyle: { color: PALETTE[i % PALETTE.length] } }))
    }]
  })
}

function renderHourly(data) {
  const max = Math.max(...data.map((d) => d.count), 1)
  chart(hourlyRef.value, 'hourly').setOption({
    grid: { left: 36, right: 14, top: 24, bottom: 28 },
    tooltip: { trigger: 'axis', ...lightTooltip, axisPointer: { type: 'shadow', shadowStyle: { color: 'rgba(21,94,239,0.06)' } } },
    xAxis: { type: 'category', data: data.map((d) => `${d.hour}时`), ...lightAxis, axisLabel: { ...lightAxis.axisLabel, interval: 1 } },
    yAxis: { type: 'value', ...lightAxis, minInterval: 1 },
    series: [{
      type: 'bar', barWidth: '55%',
      markPoint: {
        symbolSize: 42, symbolOffset: [0, -4],
        itemStyle: { color: 'rgba(21,94,239,0.9)' },
        label: { color: '#ffffff', fontSize: 10 },
        data: [{ type: 'max', name: '峰值' }]
      },
      data: data.map((d) => ({
        value: d.count,
        itemStyle: {
          borderRadius: [3, 3, 0, 0],
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: d.count >= max * 0.66 ? C.sky : C.blue },
            { offset: 1, color: 'rgba(21,94,239,0.08)' }
          ])
        }
      }))
    }]
  })
}

function renderModel(data) {
  chart(modelRef.value, 'model').setOption({
    tooltip: { trigger: 'item', ...lightTooltip },
    legend: { bottom: 0, textStyle: { color: C.textDim, fontSize: 11 }, itemWidth: 10, itemHeight: 8 },
    series: [{
      type: 'pie', roseType: 'area', radius: ['18%', '72%'], center: ['50%', '44%'],
      itemStyle: { borderColor: '#ffffff', borderWidth: 2 },
      label: { color: C.textDim, fontSize: 10, formatter: '{b} {d}%' },
      labelLine: { lineStyle: { color: C.axisLine }, length: 6, length2: 8 },
      data: data.map((d, i) => ({ ...d, itemStyle: { color: PALETTE[i % PALETTE.length], borderColor: '#ffffff', borderWidth: 2 } }))
    }]
  })
}

function renderCategory(data) {
  const total = data.reduce((s, d) => s + d.value, 0)
  chart(categoryRef.value, 'category').setOption({
    tooltip: { trigger: 'item', ...lightTooltip },
    legend: { orient: 'vertical', right: 6, top: 'middle', icon: 'circle', itemWidth: 8, itemHeight: 8, textStyle: { color: C.textDim, fontSize: 11 } },
    title: {
      text: String(total), subtext: '设备总量', left: '30%', top: '38%',
      textAlign: 'center',
      textStyle: { color: '#1d2939', fontSize: 24, fontWeight: 700 },
      subtextStyle: { color: C.textDim, fontSize: 11 }
    },
    series: [{
      type: 'pie', radius: ['54%', '74%'], center: ['34%', '50%'],
      itemStyle: { borderColor: '#ffffff', borderWidth: 2, borderRadius: 4 },
      label: { show: false },
      data: data.map((d, i) => ({ ...d, itemStyle: { color: PALETTE[i % PALETTE.length] } }))
    }]
  })
}

function renderAlert(data) {
  const nameMap = {
    GEOFENCE_BREACH: '禁飞区闯入', ALTITUDE_EXCEED: '超限高', LOW_BATTERY: '低电量',
    SIGNAL_LOST: '失联', NO_LICENSE: '黑飞嫌疑', TASK_OVERDUE: '超时未归',
    PREDICTED_BREACH: '预测闯入禁飞区', CONFLICT_ALERT: '多机接近冲突',
    BATTERY_ANOMALY: '电量骤降', ALTITUDE_JUMP: '高度突变', SIGNAL_WEAK: '卫星信号弱'
  }
  const colors = { GEOFENCE_BREACH: C.red, ALTITUDE_EXCEED: C.yellow, LOW_BATTERY: C.purple, SIGNAL_LOST: C.blue, NO_LICENSE: C.pink, TASK_OVERDUE: C.green, PREDICTED_BREACH: C.sky, CONFLICT_ALERT: C.red, BATTERY_ANOMALY: C.purple, ALTITUDE_JUMP: C.yellow, SIGNAL_WEAK: C.blue }
  chart(alertRef.value, 'alert').setOption({
    grid: { left: 86, right: 44, top: 14, bottom: 24 },
    tooltip: { ...lightTooltip },
    xAxis: { type: 'value', ...lightAxis, minInterval: 1, splitLine: { lineStyle: { color: C.split } } },
    yAxis: {
      type: 'category',
      data: data.map((d) => nameMap[d.type] || d.type).reverse(),
      ...lightAxis, splitLine: { show: false },
      axisLabel: { ...lightAxis.axisLabel, fontSize: 11 }
    },
    series: [{
      type: 'bar', barWidth: 10,
      data: data.map((d) => {
        const c = colors[d.type] || C.sky
        return {
          value: d.count,
          itemStyle: {
            color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [{ offset: 0, color: c + '2e' }, { offset: 1, color: c }]),
            borderRadius: [0, 5, 5, 0]
          }
        }
      }).reverse(),
      label: { show: true, position: 'right', color: C.textDim, fontSize: 11 },
      showBackground: true,
      backgroundStyle: { color: 'rgba(21,94,239,0.04)', borderRadius: [0, 5, 5, 0] }
    }]
  })
}

function renderRank(data) {
  chart(rankRef.value, 'rank').setOption({
    grid: { left: 66, right: 60, top: 14, bottom: 24 },
    tooltip: { ...lightTooltip },
    xAxis: { type: 'value', ...lightAxis, splitLine: { lineStyle: { color: C.split } } },
    yAxis: {
      type: 'category',
      data: data.map((d) => d.name).reverse(),
      ...lightAxis, splitLine: { show: false }
    },
    series: [{
      type: 'bar', barWidth: 12,
      // 数据降序,索引 0-2 即 TOP1-3(reverse 后排最上),用主蓝突出;其余浅蓝
      data: data.map((d, i) => {
        const top3 = i < 3
        const color = top3 ? C.blue : '#7fb0f7'
        return {
          value: d.hours,
          itemStyle: {
            borderRadius: [0, 6, 6, 0],
            color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [
              { offset: 0, color: top3 ? 'rgba(21,94,239,0.15)' : 'rgba(127,176,247,0.12)' },
              { offset: 1, color: color }
            ])
          },
          label: { color: top3 ? '#155eef' : C.textDim, fontWeight: top3 ? 600 : 400 }
        }
      }).reverse(),
      label: { show: true, position: 'right', fontSize: 11, formatter: '{c} h' },
      showBackground: true,
      backgroundStyle: { color: 'rgba(21,94,239,0.04)', borderRadius: [0, 6, 6, 0] }
    }]
  })
}
</script>

<style scoped>
/* ===== 页面底:亮白蓝 ===== */
.stats-light {
  margin: -18px;
  padding: 16px 18px;
  min-height: calc(100% + 36px);
  background:
    radial-gradient(900px 380px at 8% -10%, rgba(14, 165, 233, 0.08), transparent 60%),
    radial-gradient(800px 360px at 95% 110%, rgba(21, 94, 239, 0.06), transparent 60%),
    linear-gradient(180deg, #f8fbff 0%, #f3f7fc 100%);
  box-sizing: border-box;
}

/* ===== 顶部政务蓝横幅 ===== */
.gov-banner {
  position: relative;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 22px;
  margin-bottom: 14px;
  border-radius: 14px;
  background: linear-gradient(100deg, #155eef 0%, #2470f0 45%, #0ea5e9 100%);
  box-shadow: 0 8px 24px rgba(21, 94, 239, 0.28);
  overflow: hidden;
}
.banner-glow {
  position: absolute;
  inset: 0;
  background:
    radial-gradient(420px 120px at 82% -30%, rgba(255, 255, 255, 0.22), transparent 70%),
    radial-gradient(300px 100px at 12% 130%, rgba(255, 255, 255, 0.1), transparent 70%);
  pointer-events: none;
}
.banner-left { display: flex; align-items: center; gap: 12px; position: relative; }
.title-mark {
  width: 6px; height: 34px;
  background: rgba(255, 255, 255, 0.85);
  border-radius: 3px;
}
.banner-title {
  font-size: 19px;
  font-weight: 700;
  letter-spacing: 2px;
  color: #ffffff;
}
.banner-sub { font-size: 11px; letter-spacing: 1px; color: rgba(255, 255, 255, 0.75); margin-top: 2px; }
.banner-right { display: flex; align-items: center; gap: 14px; position: relative; }
.live-pill {
  display: inline-flex; align-items: center; gap: 6px;
  font-size: 11px; color: #ffffff;
  padding: 3px 11px; border: 1px solid rgba(255, 255, 255, 0.45);
  border-radius: 20px; background: rgba(255, 255, 255, 0.12);
}
.live-dot {
  width: 6px; height: 6px; border-radius: 50%; background: #7ef0b8;
  animation: live-blink 1.8s ease infinite;
}
@keyframes live-blink { 0%, 100% { opacity: 1; } 50% { opacity: 0.3; } }
.clock {
  font-family: 'DIN Alternate', 'Bahnschrift', 'Consolas', monospace;
  font-size: 14px; letter-spacing: 1px; color: #ffffff;
}
.banner-btn {
  --el-button-bg-color: rgba(255, 255, 255, 0.16);
  --el-button-border-color: rgba(255, 255, 255, 0.5);
  --el-button-text-color: #ffffff;
  --el-button-hover-bg-color: rgba(255, 255, 255, 0.28);
  --el-button-hover-border-color: #ffffff;
  --el-button-hover-text-color: #ffffff;
}

/* ===== KPI 指标卡 ===== */
.kpi-strip {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 14px;
  margin-bottom: 14px;
}
.kpi-card {
  display: flex; align-items: center; gap: 12px;
  padding: 14px 16px;
  background: #ffffff;
  border: 1px solid #e4eaf2;
  border-radius: 12px;
  box-shadow: 0 2px 10px rgba(16, 42, 93, 0.05);
  position: relative;
  overflow: hidden;
  transition: transform 0.22s ease, box-shadow 0.22s ease;
  animation: fade-in-up 0.35s ease both;
}
.kpi-card::before {
  content: '';
  position: absolute; top: 0; left: 0;
  width: 100%; height: 3px;
  background: linear-gradient(90deg, var(--kpi-line, #155eef), rgba(14, 165, 233, 0.15));
}
.kpi-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 22px rgba(16, 42, 93, 0.1);
}
.kpi-icon {
  flex: none;
  width: 42px; height: 42px;
  display: flex; align-items: center; justify-content: center;
  font-size: 19px;
  border: 1px solid; border-radius: 10px;
}
.kpi-body { min-width: 0; }
.kpi-num {
  font-family: 'DIN Alternate', 'Bahnschrift', 'Segoe UI', monospace;
  font-size: 26px; font-weight: 700; line-height: 1.15;
  background: linear-gradient(135deg, #1d2939 25%, #155eef 95%);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
}
.kpi-num .unit { font-size: 11px; font-weight: 400; color: #98a2b3; margin-left: 3px; -webkit-text-fill-color: #98a2b3; }
.kpi-label { font-size: 12px; color: #475467; margin-top: 2px; letter-spacing: 1px; }
.kpi-sub { font-size: 10px; color: #98a2b3; margin-top: 1px; white-space: nowrap; }

/* ===== 图表卡片 ===== */
.grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  grid-template-rows: 320px 300px 280px;
  gap: 14px;
}
.card-panel {
  background: #ffffff;
  border: 1px solid #e4eaf2;
  border-radius: 12px;
  padding: 12px 14px 10px;
  position: relative;
  display: flex;
  flex-direction: column;
  box-shadow: 0 2px 10px rgba(16, 42, 93, 0.05);
  transition: box-shadow 0.22s ease, transform 0.22s ease;
  overflow: hidden;
}
.card-panel:hover {
  box-shadow: 0 10px 26px rgba(16, 42, 93, 0.1);
  transform: translateY(-2px);
}
.span-2 { grid-column: span 2; }
.title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-right: 8px;
}
.card-title {
  font-size: 13px;
  font-weight: 600;
  letter-spacing: 1px;
  color: #1d2939;
  padding: 4px 0 10px;
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  min-width: 0;
  white-space: nowrap;
}
.card-title::before {
  content: '';
  flex: none;
  width: 4px; height: 14px;
  background: linear-gradient(180deg, #155eef, #0ea5e9);
  border-radius: 2px;
}
.card-title::after {
  content: '';
  flex: 1;
  height: 1px;
  margin-left: 10px;
  background: linear-gradient(90deg, #d6e4f7, rgba(214, 228, 247, 0));
}
.rank-legend {
  display: flex;
  gap: 12px;
  font-size: 11px;
  color: #667085;
  padding-bottom: 10px;
  white-space: nowrap;
}
.rank-legend span { display: inline-flex; align-items: center; gap: 5px; }
.rank-legend i { width: 10px; height: 6px; border-radius: 3px; display: inline-block; }
.chart { flex: 1; min-height: 0; }

/* ===== 页脚 ===== */
.page-footer {
  margin-top: 14px;
  text-align: center;
  font-size: 11px;
  letter-spacing: 1px;
  color: #98a2b3;
}

@media (max-width: 1200px) {
  .kpi-strip { grid-template-columns: repeat(3, 1fr); }
  .grid { grid-template-columns: repeat(2, 1fr); grid-template-rows: none; }
  .card-panel { min-height: 250px; }
  .span-2 { grid-column: span 2; }
}
</style>
