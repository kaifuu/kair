<template>
  <div class="dp">
    <el-tabs v-model="tab" class="dp-tabs">
      <el-tab-pane name="online">
        <template #label>
          在线<span class="tab-num on">{{ onlineDrones.length }}</span>
        </template>
        <div class="dp-list">
          <div v-if="!onlineDrones.length" class="dp-empty">
            <el-icon :size="26" color="#c3cfe3"><VideoCameraFilled /></el-icon>
            <p>暂无在线无人机</p>
            <span class="dp-sub">到「飞行任务」下发起飞后实时监控</span>
          </div>
          <template v-for="d in onlineDrones" :key="d.id">
            <!-- 在飞:遥测卡片 -->
            <div v-if="flyOf(d)" class="drone-item" :class="{ active: selectedId === d.id }"
                 @click="$emit('focus', flyOf(d))">
              <div class="drone-head">
                <span class="drone-code"><i class="fly-pulse"></i>{{ d.code }}</span>
                <span class="drone-task">{{ flyOf(d).taskName || d.name }}</span>
              </div>
              <div class="drone-meta">
                <span>高度 <b>{{ flyOf(d).altitude }}</b>m</span>
                <span>速度 <b>{{ flyOf(d).speed }}</b>m/s</span>
                <span>电量
                  <b :style="{ color: batColor(flyOf(d).battery) }">{{ flyOf(d).battery }}%</b>
                </span>
                <el-button class="rp-entry" link type="primary" size="small"
                           @click.stop="$emit('replay', d)">轨迹回放</el-button>
              </div>
              <el-progress :percentage="Math.round(flyOf(d).battery)" :show-text="false" :stroke="5"
                           :color="batColor(flyOf(d).battery)" />
            </div>
            <!-- 在线未起飞:档案行 -->
            <div v-else class="drone-item idle" @click="$emit('open', d)">
              <div class="drone-head">
                <span class="drone-code gray">{{ d.code }}</span>
                <span class="drone-task">{{ d.name }}</span>
              </div>
              <div class="drone-meta">
                <span>状态 <b>{{ statusText(d.status) }}</b></span>
                <span>机型 <b>{{ d.model || '—' }}</b></span>
                <el-button class="rp-entry" link type="primary" size="small"
                           @click.stop="$emit('replay', d)">轨迹回放</el-button>
              </div>
            </div>
          </template>
        </div>
      </el-tab-pane>

      <el-tab-pane name="offline">
        <template #label>
          离线<span class="tab-num off">{{ offlineDrones.length }}</span>
        </template>
        <div class="dp-list">
          <div v-if="!offlineDrones.length" class="dp-empty"><p>无离线设备</p></div>
          <div v-for="d in offlineDrones" :key="d.id" class="drone-item offline" @click="$emit('open', d)">
            <div class="drone-head">
              <span class="drone-code gray"><i class="off-dot"></i>{{ d.code }}</span>
              <span class="drone-task">{{ d.name }}</span>
            </div>
            <div class="drone-meta">
              <span>状态 <b>{{ statusText(d.status) }}</b></span>
              <span>机型 <b>{{ d.model || '—' }}</b></span>
              <el-button class="rp-entry" link type="primary" size="small"
                         @click.stop="$emit('replay', d)">轨迹回放</el-button>
            </div>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { VideoCameraFilled } from '@element-plus/icons-vue'

const props = defineProps({
  drones: { type: Array, default: () => [] },      // 全量无人机档案
  flying: { type: Array, default: () => [] },      // 在飞遥测列表
  selectedId: { type: Number, default: null }
})
defineEmits(['focus', 'open', 'replay'])

const tab = ref('online')
const onlineDrones = computed(() =>
  props.drones.filter((d) => ['ONLINE', 'FLYING', 'IDLE', 'CHARGING'].includes(d.status)))
const offlineDrones = computed(() =>
  props.drones.filter((d) => ['OFFLINE', 'MAINTENANCE'].includes(d.status)))

const flyOf = (d) => props.flying.find((t) => t.droneId === d.id)

const batColor = (b) => (b <= 20 ? '#f04438' : b <= 40 ? '#f79009' : '#12b76a')

function statusText(s) {
  return { ONLINE: '在线', IDLE: '待命', FLYING: '飞行中', CHARGING: '充电中', MAINTENANCE: '维保中', OFFLINE: '离线' }[s] || s
}
</script>

<style scoped>
.dp { display: flex; flex-direction: column; min-height: 0; flex: 1; }

.dp-tabs :deep(.el-tabs__header) { margin: 0 12px; }
.dp-tabs :deep(.el-tabs__nav-wrap::after) { height: 1px; }
.dp-tabs :deep(.el-tabs__item) { font-size: 13px; padding: 0 4px; }
.tab-num {
  margin-left: 4px; padding: 0 7px; border-radius: 999px;
  font-size: 11px; font-weight: 700; line-height: 17px;
}
.tab-num.on { background: #ecfdf3; color: #12b76a; }
.tab-num.off { background: #f2f4f7; color: #667085; }

.dp-list { flex: 1; overflow-y: auto; padding: 6px 12px 12px; min-height: 60px; max-height: 265px; }
.dp-empty {
  padding: 26px 0; text-align: center; color: var(--text-faint);
  font-size: 13px; display: flex; flex-direction: column; align-items: center; gap: 6px;
}
.dp-empty p { margin: 0; }
.dp-sub { font-size: 11.5px; opacity: .8; }

.drone-item {
  padding: 10px 12px; margin-bottom: 8px;
  border: 1px solid var(--border);
  border-radius: 11px;
  background: #fff;
  cursor: pointer;
  transition: all .2s;
}
.drone-item:hover { border-color: #b8ccf7; box-shadow: var(--shadow-sm); }
.drone-item.active { border-color: #155eef; background: #f5f8ff; box-shadow: inset 0 0 0 1px #d6e4ff; }
.drone-item.idle .drone-meta, .drone-item.offline .drone-meta { margin-bottom: 0; }

.drone-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 7px; }
.drone-code {
  font-weight: 700; color: #155eef; font-size: 13px;
  display: inline-flex; align-items: center; gap: 7px;
}
.drone-code.gray { color: #667085; }
.fly-pulse {
  width: 8px; height: 8px; border-radius: 50%;
  background: #0ea5e9;
  animation: fly-ping 1.8s infinite;
}
.off-dot { width: 8px; height: 8px; border-radius: 50%; background: #c3cfe3; }
@keyframes fly-ping {
  0% { box-shadow: 0 0 0 0 rgba(14, 165, 233, .5); }
  70% { box-shadow: 0 0 0 7px rgba(14, 165, 233, 0); }
  100% { box-shadow: 0 0 0 0 rgba(14, 165, 233, 0); }
}
.drone-task { font-size: 11.5px; color: var(--text-dim); max-width: 170px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.drone-meta { display: flex; align-items: center; gap: 11px; font-size: 12px; color: var(--text-dim); margin-bottom: 7px; }
.drone-meta b { color: #101828; font-weight: 600; }
.rp-entry { margin-left: auto; padding: 0; height: auto; }
</style>
