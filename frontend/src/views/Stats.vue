<template>
  <div class="page">
    <div class="page-header">
      <span class="page-title">统计分析</span>
      <el-button :icon="Refresh" @click="loadAll">刷新</el-button>
    </div>

    <div class="stats-body">
      <div class="row row-1">
        <div class="panel chart-card">
          <div class="panel-title">近 7 日飞行趋势</div>
          <div ref="trendRef" class="chart"></div>
        </div>
        <div class="panel chart-card">
          <div class="panel-title">机型分布</div>
          <div ref="modelRef" class="chart"></div>
        </div>
      </div>
      <div class="row row-2">
        <div class="panel chart-card">
          <div class="panel-title">告警类型分布</div>
          <div ref="alertRef" class="chart"></div>
        </div>
        <div class="panel chart-card">
          <div class="panel-title">飞手飞行时长排行 TOP10</div>
          <div ref="rankRef" class="chart"></div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import * as echarts from 'echarts'
import { Refresh } from '@element-plus/icons-vue'
import http from '../api'

const trendRef = ref(null)
const modelRef = ref(null)
const alertRef = ref(null)
const rankRef = ref(null)

let charts = []
const C = {
  cyan: '#0ea5e9', blue: '#155eef', green: '#12b76a',
  yellow: '#f79009', red: '#f04438', purple: '#7c3aed',
  textDim: '#667085', split: '#eef2f7', tooltipBg: '#ffffff',
  tooltipBorder: '#e4eaf2'
}

const baseAxis = {
  axisLine: { lineStyle: { color: 'rgba(84,118,180,0.4)' } },
  axisLabel: { color: C.textDim },
  splitLine: { lineStyle: { color: C.split } }
}

onMounted(loadAll)
onUnmounted(() => charts.forEach((c) => c.dispose()))

async function loadAll() {
  const [trend, models, alertTypes, rank] = await Promise.all([
    http.get('/stats/trend'),
    http.get('/stats/drone-model'),
    http.get('/stats/alert-type'),
    http.get('/stats/pilot-rank')
  ])
  await nextTick()
  renderTrend(trend)
  renderModel(models)
  renderAlert(alertTypes)
  renderRank(rank)
}

function mkChart(el) {
  echarts.getInstanceByDom(el)?.dispose()
  const c = echarts.init(el)
  charts.push(c)
  return c
}

function renderTrend(data) {
  mkChart(trendRef.value).setOption({
    grid: { left: 40, right: 20, top: 30, bottom: 28 },
    tooltip: { trigger: 'axis', backgroundColor: '#ffffff', borderColor: '#e4eaf2', textStyle: { color: '#344054' } },
    xAxis: { type: 'category', data: data.map((d) => d.date.slice(5)), ...baseAxis },
    yAxis: { type: 'value', ...baseAxis, minInterval: 1 },
    series: [{
      type: 'line', smooth: true, symbolSize: 7,
      data: data.map((d) => d.flights),
      lineStyle: { width: 3, color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [
        { offset: 0, color: C.blue }, { offset: 1, color: C.cyan }
      ]) },
      itemStyle: { color: C.cyan, borderColor: '#ffffff', borderWidth: 2 },
      areaStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: 'rgba(21,94,239,0.18)' },
          { offset: 1, color: 'rgba(14,165,233,0)' }
        ])
      }
    }]
  })
}

function renderModel(data) {
  const palette = [C.cyan, C.blue, C.purple, C.green, C.yellow, C.red]
  mkChart(modelRef.value).setOption({
    tooltip: { trigger: 'item', backgroundColor: '#ffffff', borderColor: '#e4eaf2', textStyle: { color: '#344054' } },
    legend: { bottom: 0, textStyle: { color: C.textDim, fontSize: 11 }, itemWidth: 12, itemHeight: 8 },
    series: [{
      type: 'pie', radius: ['42%', '66%'], center: ['50%', '44%'],
      itemStyle: { borderColor: '#ffffff', borderWidth: 2, borderRadius: 6 },
      label: { color: C.textDim, fontSize: 11 },
      data: data.map((d, i) => ({ ...d, itemStyle: { color: palette[i % palette.length] } }))
    }]
  })
}

function renderAlert(data) {
  const nameMap = {
    GEOFENCE_BREACH: '禁飞区闯入', ALTITUDE_EXCEED: '超限高', LOW_BATTERY: '低电量',
    SIGNAL_LOST: '失联', NO_LICENSE: '黑飞嫌疑', TASK_OVERDUE: '超时未归'
  }
  const colors = { GEOFENCE_BREACH: C.red, ALTITUDE_EXCEED: C.yellow, LOW_BATTERY: C.purple, SIGNAL_LOST: C.blue, NO_LICENSE: C.red, TASK_OVERDUE: C.green }
  mkChart(alertRef.value).setOption({
    grid: { left: 90, right: 40, top: 16, bottom: 28 },
    tooltip: { backgroundColor: '#ffffff', borderColor: '#e4eaf2', textStyle: { color: '#344054' } },
    xAxis: { type: 'value', ...baseAxis, minInterval: 1 },
    yAxis: {
      type: 'category',
      data: data.map((d) => nameMap[d.type] || d.type).reverse(),
      ...baseAxis, splitLine: { show: false }
    },
    series: [{
      type: 'bar', barWidth: 12,
      data: data.map((d) => ({ value: d.count, itemStyle: { color: colors[d.type] || C.cyan, borderRadius: [0, 6, 6, 0] } })).reverse(),
      label: { show: true, position: 'right', color: C.textDim }
    }]
  })
}

function renderRank(data) {
  mkChart(rankRef.value).setOption({
    grid: { left: 60, right: 50, top: 16, bottom: 28 },
    tooltip: { backgroundColor: '#ffffff', borderColor: '#e4eaf2', textStyle: { color: '#344054' } },
    xAxis: { type: 'value', ...baseAxis },
    yAxis: {
      type: 'category',
      data: data.map((d) => d.name).reverse(),
      ...baseAxis, splitLine: { show: false }
    },
    series: [{
      type: 'bar', barWidth: 12,
      data: data.map((d) => d.hours).reverse(),
      itemStyle: {
        borderRadius: [0, 6, 6, 0],
        color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [
          { offset: 0, color: C.blue }, { offset: 1, color: C.cyan }
        ])
      },
      label: { show: true, position: 'right', color: C.textDim, formatter: '{c} h' }
    }]
  })
}
</script>

<style scoped>
.stats-body {
  height: calc(100% - 50px);
  display: flex; flex-direction: column; gap: 12px;
}
.row { flex: 1; display: flex; gap: 12px; min-height: 0; }
.chart-card { flex: 1; display: flex; flex-direction: column; overflow: hidden; }
.chart { flex: 1; min-height: 0; }
</style>
