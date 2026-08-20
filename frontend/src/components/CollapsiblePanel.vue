<template>
  <div class="panel side-panel cp" :class="{ collapsed: collapsed }">
    <div class="cp-head" @click="collapsed = !collapsed">
      <span class="cp-title">
        {{ title }}
        <slot name="badge" />
      </span>
      <el-icon class="cp-arrow" :class="{ off: collapsed }"><ArrowDown /></el-icon>
    </div>
    <div v-show="!collapsed" class="cp-body">
      <slot />
    </div>
  </div>
</template>

<script setup>
import { ArrowDown } from '@element-plus/icons-vue'

defineProps({ title: { type: String, required: true } })
/** v-model:collapsed 双向收缩状态 */
const collapsed = defineModel('collapsed', { type: Boolean, default: false })
</script>

<style scoped>
.cp { display: flex; flex-direction: column; min-height: 0; overflow: hidden; flex-shrink: 0; }
.cp.collapsed { flex: 0 0 auto; }

.cp-head {
  display: flex; align-items: center; justify-content: space-between;
  padding: 12px 14px 10px; cursor: pointer; user-select: none;
  flex-shrink: 0;
}
.cp-head:hover .cp-arrow { color: #155eef; }
.cp.collapsed .cp-head { padding-bottom: 12px; }

.cp-title {
  font-size: 14px; font-weight: 700; color: #101828;
  display: inline-flex; align-items: center; gap: 8px; min-width: 0;
}
.cp-arrow { color: #98a2b3; transition: transform .25s, color .2s; flex-shrink: 0; }
.cp-arrow.off { transform: rotate(-90deg); }

.cp-body { flex: 1; min-height: 0; display: flex; flex-direction: column; overflow: hidden; }
</style>
