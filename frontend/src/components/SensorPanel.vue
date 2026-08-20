<template>
  <div class="sp">
    <div class="sp-list">
      <div v-if="!devices.length" class="sp-empty">暂无物联网设备</div>
      <div v-for="d in devices" :key="d.id" class="sp-item" @click="$emit('open', d)">
        <span class="sp-dot" :style="{ background: metaOf(d).color }" :class="{ live: isOnline(d) }" />
        <div class="sp-main">
          <div class="sp-head">
            <span class="sp-code">{{ d.code }}</span>
            <span class="sp-name">{{ d.name }}</span>
            <span class="sp-cat" :style="{ color: metaOf(d).color, background: metaOf(d).color + '14' }">
              {{ metaOf(d).label }}
            </span>
          </div>
          <div class="sp-fields">
            <template v-if="fieldsOf(d)">
              <span v-for="k in topFields(d)" :key="k" class="sp-field">
                <i>{{ fieldLabel(k) }}</i>{{ fmtField(k, fieldsOf(d)[k]) }}
              </span>
            </template>
            <span v-else class="sp-waiting">等待上报数据...</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { deviceMeta } from '../utils/map'
import { fieldLabel, fmtField } from '../utils/deviceFields'

const props = defineProps({
  devices: { type: Array, default: () => [] },                 // 非无人机/非摄像头的物联网设备
  latest: { type: Object, default: () => ({}) }                // deviceId -> 最近一帧 fields
})
defineEmits(['open'])

const metaOf = (d) => deviceMeta(d.category)
const fieldsOf = (d) => props.latest[d.id]?.fields
const isOnline = (d) => ['ONLINE', 'FLYING', 'IDLE', 'CHARGING'].includes(d.status)

/** 最多展示 3 个字段,优先数值字段 */
function topFields(d) {
  const f = fieldsOf(d) || {}
  return Object.keys(f).filter((k) => f[k] !== null && f[k] !== undefined).slice(0, 3)
}
</script>

<style scoped>
.sp { display: flex; flex-direction: column; min-height: 0; flex: 1; }
.sp-list { flex: 1; overflow-y: auto; padding: 2px 12px 12px; max-height: 250px; }
.sp-empty { padding: 22px 0; text-align: center; color: var(--text-faint); font-size: 13px; }

.sp-item {
  display: flex; gap: 10px; align-items: flex-start;
  padding: 9px 11px; margin-bottom: 8px;
  border: 1px solid var(--border); border-radius: 10px; background: #fff;
  cursor: pointer; transition: all .2s;
}
.sp-item:hover { border-color: #b8ccf7; box-shadow: var(--shadow-sm); }

.sp-dot {
  width: 9px; height: 9px; border-radius: 50%; margin-top: 6px; flex-shrink: 0;
}
.sp-dot.live { animation: sp-ping 2s infinite; }
@keyframes sp-ping {
  0% { box-shadow: 0 0 0 0 currentColor; }
  70% { box-shadow: 0 0 0 6px transparent; }
  100% { box-shadow: 0 0 0 0 transparent; }
}

.sp-main { flex: 1; min-width: 0; }
.sp-head { display: flex; align-items: center; gap: 7px; }
.sp-code { font-size: 12.5px; font-weight: 700; color: #101828; }
.sp-name {
  font-size: 11.5px; color: var(--text-dim);
  flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.sp-cat { font-size: 10.5px; padding: 0 7px; border-radius: 999px; line-height: 17px; flex-shrink: 0; }

.sp-fields { display: flex; flex-wrap: wrap; gap: 5px 12px; margin-top: 6px; }
.sp-field { font-size: 11.5px; color: #344054; font-weight: 600; }
.sp-field i { font-style: normal; color: var(--text-faint); font-weight: 400; margin-right: 4px; }
.sp-waiting { font-size: 11.5px; color: var(--text-faint); }
</style>
