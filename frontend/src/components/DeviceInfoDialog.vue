<template>
  <el-dialog v-model="visible" width="660px" top="6vh" destroy-on-close
             :title="device ? `${device.code} · ${device.name || ''}` : '设备详情'"
             @opened="initChart" @closed="disposeChart">
    <template v-if="device">
      <!-- 基础信息 -->
      <el-descriptions :column="2" size="small" border class="did-desc">
        <el-descriptions-item label="设备分类">{{ meta.label }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <span class="did-status" :class="online ? 'on' : 'off'">{{ statusText }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="厂商">{{ device.manufacturer || '—' }}</el-descriptions-item>
        <el-descriptions-item label="型号">{{ device.model || '—' }}</el-descriptions-item>
        <el-descriptions-item label="用途" :span="2">{{ device.usage || '—' }}</el-descriptions-item>
        <el-descriptions-item label="位置坐标">
          {{ device.homeLng ? `${device.homeLng}, ${device.homeLat}` : '—' }}
        </el-descriptions-item>
        <el-descriptions-item label="绑定协议">
          {{ device.protocol?.name || (device.modbusUnitId ? `Modbus unit=${device.modbusUnitId}` : '—') }}
        </el-descriptions-item>
      </el-descriptions>

      <!-- 最新遥测 -->
      <div v-if="latestFields" class="did-latest">
        <span class="did-lt-title">最新数据</span>
        <span v-for="(v, k) in latestFields" :key="k" class="did-chip">
          <i>{{ fieldLabel(k) }}</i>{{ fmtField(k, v) }}
        </span>
      </div>

      <!-- 历史曲线 -->
      <div class="did-chart-title">历史数据(近 60 分钟)</div>
      <div ref="chartRef" class="did-chart" v-loading="historyLoading" />
      <div v-if="!historyLoading && !hasNumeric" class="did-nochart">该设备暂无历史数据(接入上报后自动累积)</div>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed, watch, nextTick } from 'vue'
import * as echarts from 'echarts'
import http from '../api'
import { deviceMeta } from '../utils/map'
import { fieldLabel, fmtField } from '../utils/deviceFields'

const props = defineProps({
  device: { type: Object, default: null },
  latest: { type: Object, default: () => ({}) }        // Monitor 侧最新一帧 deviceId -> {fields, ts}
})

const visible = defineModel({ type: Boolean, default: false })

const chartRef = ref(null)
const history = ref([])
const historyLoading = ref(false)
let chart = null

const meta = computed(() => deviceMeta(props.device?.category))
const online = computed(() =>
  ['ONLINE', 'FLYING', 'IDLE', 'CHARGING'].includes(props.device?.status))
const statusText = computed(() =>
  ({ ONLINE: '在线', IDLE: '待命', FLYING: '飞行中', CHARGING: '充电中', MAINTENANCE: '维保中', OFFLINE: '离线' }[props.device?.status]) || props.device?.status || '—')
const latestFields = computed(() => props.latest[props.device?.id]?.fields || null)

/** 数值型字段(可成曲线) */
const numericKeys = computed(() => {
  const keys = new Set()
  for (const it of history.value) {
    for (const [k, v] of Object.entries(it.fields || {})) {
      if (typeof v === 'number' && Number.isFinite(v)) keys.add(k)
    }
  }
  return [...keys]
})
const hasNumeric = computed(() => numericKeys.value.length > 0)

watch(visible, async (open) => {
  if (!open || !props.device) {
    history.value = []
    return
  }
  historyLoading.value = true
  try {
    const res = await http.get(`/devices/${props.device.id}/history`, {
      params: { minutes: 60, limit: 500 }
    })
    history.value = res.items || []
  } catch (e) {
    history.value = []
  } finally {
    historyLoading.value = false
  }
  await nextTick()
  if (visible.value) initChart()
})

/** 弹窗动画结束后初始化(容器有尺寸才 init;关闭即销毁防泄漏) */
function initChart() {
  if (!chartRef.value || !hasNumeric.value) return
  disposeChart()
  chart = echarts.init(chartRef.value)
  const series = numericKeys.value.map((k) => ({
    name: fieldLabel(k),
    type: 'line',
    showSymbol: false,
    smooth: true,
    lineWidth: 1.5,
    data: history.value
      .filter((it) => typeof it.fields?.[k] === 'number')
      .map((it) => [it.ts, it.fields[k]])
  }))
  chart.setOption({
    color: ['#155eef', '#12b76a', '#f79009', '#dd2590', '#0e9384', '#7c3aed', '#36cfc9'],
    tooltip: { trigger: 'axis', textStyle: { fontSize: 12 } },
    legend: { top: 0, type: 'scroll', textStyle: { fontSize: 11, color: '#475467' } },
    grid: { left: 42, right: 14, top: 30, bottom: 26 },
    xAxis: {
      type: 'time',
      axisLabel: { fontSize: 10, color: '#98a2b3', formatter: '{HH}:{mm}' }
    },
    yAxis: { type: 'value', scale: true, axisLabel: { fontSize: 10, color: '#98a2b3' }, splitLine: { lineStyle: { color: '#eef2f6' } } },
    series
  })
  chart.resize()
}

function disposeChart() {
  if (chart) {
    chart.dispose()
    chart = null
  }
}
</script>

<style scoped>
.did-desc { margin-bottom: 12px; }

.did-status { font-weight: 700; }
.did-status.on { color: #12b76a; }
.did-status.off { color: #98a2b3; }

.did-latest { display: flex; flex-wrap: wrap; gap: 6px; align-items: center; margin-bottom: 12px; }
.did-lt-title { font-size: 12.5px; font-weight: 700; color: #344054; margin-right: 4px; }
.did-chip {
  font-size: 12px; color: #101828; font-weight: 600;
  padding: 2px 9px; border-radius: 7px;
  background: #f2f7ff; border: 1px solid #d6e4ff;
}
.did-chip i { font-style: normal; color: var(--text-dim); font-weight: 400; margin-right: 5px; }

.did-chart-title { font-size: 12.5px; font-weight: 700; color: #344054; margin-bottom: 6px; }
.did-chart { width: 100%; height: 260px; }
.did-nochart {
  height: 120px; display: flex; align-items: center; justify-content: center;
  font-size: 12.5px; color: var(--text-faint);
  border: 1px dashed var(--border); border-radius: 9px;
}
</style>
