<template>
  <Teleport to="body">
    <!-- 无人机样式悬浮球(可拖动,单击展开/收起) -->
    <div class="ai-ball" :class="{ thinking }" :style="ballStyle"
         v-show="!open" title="AI 值班助手(拖动移位,单击展开)"
         @pointerdown="onBallDown">
      <svg class="ai-ball-drone" viewBox="0 0 64 64" aria-hidden="true">
        <!-- 机臂 -->
        <g stroke="#ffffff" stroke-width="3.6" stroke-linecap="round" opacity="0.95">
          <line x1="26.5" y1="26.5" x2="18" y2="18" />
          <line x1="37.5" y1="26.5" x2="46" y2="18" />
          <line x1="26.5" y1="37.5" x2="18" y2="46" />
          <line x1="37.5" y1="37.5" x2="46" y2="46" />
        </g>
        <!-- 旋翼(带旋转动画) -->
        <g class="rotor" style="--d:0s"><ellipse cx="18" cy="18" rx="10.5" ry="2.4" /></g>
        <g class="rotor" style="--d:.06s"><ellipse cx="46" cy="18" rx="10.5" ry="2.4" /></g>
        <g class="rotor" style="--d:.12s"><ellipse cx="18" cy="46" rx="10.5" ry="2.4" /></g>
        <g class="rotor" style="--d:.18s"><ellipse cx="46" cy="46" rx="10.5" ry="2.4" /></g>
        <!-- 机身 + 云台 -->
        <rect x="23.5" y="23.5" width="17" height="17" rx="5.5" fill="#ffffff" />
        <rect x="27" y="34.5" width="10" height="6.5" rx="3" fill="#e8effc" />
        <circle cx="32" cy="37.5" r="2.6" fill="#155eef" />
        <circle cx="32" cy="36.7" r="0.9" fill="#ffffff" />
        <!-- 指示灯 -->
        <circle cx="32" cy="27.5" r="1.3" class="ai-led" />
      </svg>
      <span class="ai-ball-tag">AI</span>
      <span class="ai-ball-ring" v-if="thinking"></span>
    </div>

    <!-- 对话窗口:标题栏可拖动,右下角可拖拽调整大小 -->
    <div class="ai-win" ref="winRef" v-show="open" :style="winStyle">
      <div class="ai-win-head" @pointerdown="onHeadDown" @dblclick.stop>
        <span class="ai-win-logo">
          <svg viewBox="0 0 64 64" aria-hidden="true">
            <g stroke="#155eef" stroke-width="5" stroke-linecap="round">
              <line x1="26" y1="26" x2="17" y2="17" /><line x1="38" y1="26" x2="47" y2="17" />
              <line x1="26" y1="38" x2="17" y2="47" /><line x1="38" y1="38" x2="47" y2="47" />
            </g>
            <g fill="#155eef">
              <ellipse cx="17" cy="17" rx="11" ry="3" /><ellipse cx="47" cy="17" rx="11" ry="3" />
              <ellipse cx="17" cy="47" rx="11" ry="3" /><ellipse cx="47" cy="47" rx="11" ry="3" />
            </g>
            <rect x="23" y="23" width="18" height="18" rx="6" fill="#155eef" />
            <circle cx="32" cy="38" r="3" fill="#ffffff" />
          </svg>
        </span>
        <div class="ai-win-tt">
          <b>AI 值班助手</b>
          <i>低空监管 · 智能问答</i>
        </div>
        <span class="ai-win-status" v-if="thinking"><span class="ai-win-status-dot"></span>{{ statusText }}</span>
        <span class="ai-win-close" title="收起" @pointerdown.stop @click.stop="open = false">✕</span>
      </div>

      <div class="ai-win-body">
        <div class="ai-list" ref="listRef">
          <div v-if="!msgs.length" class="ai-empty">
            <svg class="ai-empty-drone" viewBox="0 0 64 64" aria-hidden="true">
              <g stroke="#9dbdf3" stroke-width="3.6" stroke-linecap="round">
                <line x1="26.5" y1="26.5" x2="18" y2="18" /><line x1="37.5" y1="26.5" x2="46" y2="18" />
                <line x1="26.5" y1="37.5" x2="18" y2="46" /><line x1="37.5" y1="37.5" x2="46" y2="46" />
              </g>
              <g fill="#b9cff8">
                <ellipse cx="18" cy="18" rx="10.5" ry="2.4" /><ellipse cx="46" cy="18" rx="10.5" ry="2.4" />
                <ellipse cx="18" cy="46" rx="10.5" ry="2.4" /><ellipse cx="46" cy="46" rx="10.5" ry="2.4" />
              </g>
              <rect x="23.5" y="23.5" width="17" height="17" rx="5.5" fill="#e3edff" />
              <circle cx="32" cy="37.5" r="2.6" fill="#155eef" />
            </svg>
            <div class="ai-empty-title">值班 AI 助手</div>
            <div class="ai-empty-sub">可查询实时态势、告警、任务与设备<br>并给出处置建议</div>
          </div>
          <div v-for="(m, i) in msgs" :key="i" class="ai-row" :class="m.role">
            <span class="ai-avatar" v-if="m.role === 'assistant'">
              <svg viewBox="0 0 64 64" aria-hidden="true">
                <g stroke="#155eef" stroke-width="5" stroke-linecap="round">
                  <line x1="26" y1="26" x2="17" y2="17" /><line x1="38" y1="26" x2="47" y2="17" />
                  <line x1="26" y1="38" x2="17" y2="47" /><line x1="38" y1="38" x2="47" y2="47" />
                </g>
                <rect x="23" y="23" width="18" height="18" rx="6" fill="#155eef" />
                <circle cx="32" cy="38" r="3" fill="#fff" />
              </svg>
            </span>
            <div class="ai-bubble">
              <div class="ai-text" v-html="render(m.content)"></div>
              <span v-if="thinking && i === msgs.length - 1 && m.role === 'assistant' && !m.content"
                    class="ai-dot-group"><span class="ai-dot"></span><span class="ai-dot"></span><span class="ai-dot"></span></span>
              <span v-else-if="thinking && i === msgs.length - 1 && m.role === 'assistant'" class="ai-caret"></span>
            </div>
            <span class="ai-avatar user" v-if="m.role !== 'assistant'">我</span>
          </div>
        </div>

        <div class="ai-chips" v-if="!thinking">
          <span v-for="q in QUICK" :key="q" class="ai-chip" @click="ask(q)">
            <i></i>{{ q }}
          </span>
        </div>

        <div class="ai-input">
          <el-input v-model="question" size="small" placeholder="问点什么,如:当前有什么风险?"
                    :disabled="thinking" @keyup.enter="send" />
          <el-button size="small" type="primary" :loading="thinking" @click="send">发送</el-button>
        </div>

        <div class="ai-win-foot">内容由 AI 生成,请以平台实时数据为准</div>
      </div>

      <span class="ai-win-resize" title="拖动调整大小"></span>
    </div>
  </Teleport>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted, nextTick } from 'vue'

const QUICK = ['当前态势如何', '有哪些未处理告警', '在飞的无人机', '近期任务执行情况']
const TOOL_TEXT = {
  get_overview: '平台总览', list_flying: '在飞列表', list_alerts: '告警',
  list_tasks: '任务', get_device: '设备档案', list_fences: '电子围栏'
}

const msgs = ref([])
const question = ref('')
const thinking = ref(false)
const statusText = ref('')
const listRef = ref(null)
const winRef = ref(null)
const open = ref(false)

/* ---------- 悬浮球:位置持久化 + 拖动 ---------- */
const ball = reactive({ x: 0, y: 0 })
const ballStyle = computed(() => ({ left: ball.x + 'px', top: ball.y + 'px' }))

const win = reactive({ x: 0, y: 0, w: 420, h: 560 })
const winStyle = computed(() => ({ left: win.x + 'px', top: win.y + 'px' }))

onMounted(() => {
  const saved = loadPos()
  ball.x = saved?.x ?? (window.innerWidth - 80)
  ball.y = saved?.y ?? (window.innerHeight - 180)
  clampBall()
})

function loadPos() {
  try { return JSON.parse(localStorage.getItem('aiBallPos') || 'null') } catch { return null }
}
function savePos() {
  try { localStorage.setItem('aiBallPos', JSON.stringify({ x: ball.x, y: ball.y })) } catch { /* ignore */ }
}
function clampBall() {
  ball.x = Math.min(Math.max(8, ball.x), window.innerWidth - 60)
  ball.y = Math.min(Math.max(8, ball.y), window.innerHeight - 60)
}

/** 指针拖动通用:down 记录起点,move 超阈值算拖动,up 时未拖动视为点击 */
function makeDrag(onMove, onClick) {
  let sx = 0, sy = 0, moved = false
  const move = (e) => {
    if (Math.abs(e.clientX - sx) + Math.abs(e.clientY - sy) > 4) moved = true
    if (moved) onMove(e)
  }
  const up = () => {
    window.removeEventListener('pointermove', move)
    window.removeEventListener('pointerup', up)
    if (!moved && onClick) onClick()
    if (moved) savePos()
  }
  return (e) => {
    sx = e.clientX; sy = e.clientY; moved = false
    window.addEventListener('pointermove', move)
    window.addEventListener('pointerup', up)
  }
}

/* 拖动锚点:按下瞬间记录「指针 - 元素」偏移,move 时还原 */
const ballOff = { dx: 0, dy: 0 }
const winOff = { x: 0, y: 0 }
const ballDrag = makeDrag((e) => {
  ball.x = Math.min(Math.max(8, e.clientX - ballOff.dx), window.innerWidth - 60)
  ball.y = Math.min(Math.max(8, e.clientY - ballOff.dy), window.innerHeight - 60)
}, () => openWin())
const headDrag = makeDrag((e) => {
  win.x = Math.min(Math.max(0, e.clientX - winOff.x), window.innerWidth - 120)
  win.y = Math.min(Math.max(0, e.clientY - winOff.y), window.innerHeight - 60)
})

function onBallDown(e) {
  ballOff.dx = e.clientX - ball.x
  ballOff.dy = e.clientY - ball.y
  ballDrag(e)
}
function onHeadDown(e) {
  winOff.x = e.clientX - win.x
  winOff.y = e.clientY - win.y
  headDrag(e)
}

function openWin() {
  if (!open.value) {
    // 首次打开:窗口置于球旁边(超出屏幕则回收到右下角)
    if (!winInited.value) {
      const x = Math.min(ball.x - 440, window.innerWidth - 440)
      const y = Math.min(ball.y - 120, window.innerHeight - 580)
      win.x = Math.max(12, x)
      win.y = Math.max(12, y)
      winInited.value = true
      nextTick(() => { if (winRef.value) { winRef.value.style.width = win.w + 'px'; winRef.value.style.height = win.h + 'px' } })
    }
    open.value = true
    scrollBottom()
  }
}
const winInited = ref(false)

onUnmounted(savePos)

/* ---------- 流式问答(fetch + SSE 解析) ---------- */
async function send() {
  const q = question.value.trim()
  if (!q || thinking.value) return
  question.value = ''
  await ask(q)
}

async function ask(q) {
  if (thinking.value) return
  msgs.value.push({ role: 'user', content: q })
  msgs.value.push({ role: 'assistant', content: '' })
  const idx = msgs.value.length - 1
  thinking.value = true
  statusText.value = '思考中…'
  scrollBottom()
  try {
    const res = await fetch('/api/ai/copilot/stream', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: 'Bearer ' + (localStorage.getItem('token') || '')
      },
      body: JSON.stringify({
        question: q,
        history: msgs.value.slice(0, -2).slice(-8).map((m) => ({ role: m.role, content: m.content }))
      })
    })
    if (res.status === 401) {
      localStorage.removeItem('token')
      location.hash = '#/login'
      throw new Error('登录已过期')
    }
    if (!res.ok || !res.body) throw new Error('服务不可用(' + res.status + ')')

    const reader = res.body.getReader()
    const decoder = new TextDecoder('utf-8')
    let buf = ''
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buf += decoder.decode(value, { stream: true })
      let nl
      while ((nl = buf.indexOf('\n')) >= 0) {
        const line = buf.slice(0, nl).trim()
        buf = buf.slice(nl + 1)
        if (!line.startsWith('data:')) continue
        const payload = line.slice(5).trim()
        if (!payload || payload === '[DONE]') continue
        let ev
        try { ev = JSON.parse(payload) } catch { continue }
        if (ev.t === 'delta') {
          msgs.value[idx].content += ev.v || ''
          scrollBottom()
        } else if (ev.t === 'tool') {
          statusText.value = '正在查询 ' + (TOOL_TEXT[ev.v] || ev.v) + '…'
        } else if (ev.t === 'error') {
          throw new Error(ev.v || '调用失败')
        }
      }
    }
  } catch (e) {
    msgs.value[idx].content += (msgs.value[idx].content ? '\n' : '') +
      '(调用失败:' + (e.message || '网络异常') + ',请检查「系统设置-模型配置」。)'
  } finally {
    if (!msgs.value[idx].content) msgs.value[idx].content = '(空回复)'
    thinking.value = false
    statusText.value = ''
    scrollBottom()
  }
}

function scrollBottom() {
  nextTick(() => {
    if (listRef.value) listRef.value.scrollTop = listRef.value.scrollHeight
  })
}

/** 极简 markdown:加粗 / 换行;内容来自自家 LLM 输出,仅做文本级替换 */
function render(text) {
  const s = String(text || '')
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    .replace(/\*\*(.+?)\*\*/g, '<b>$1</b>')
    .replace(/\n/g, '<br>')
  return s
}
</script>

<style scoped>
/* ==================== 无人机悬浮球 ==================== */
.ai-ball {
  position: fixed; z-index: 2100; width: 56px; height: 56px; border-radius: 50%;
  background:
    radial-gradient(circle at 30% 24%, #7fb2ff 0%, #3b82f6 42%, #155eef 100%);
  border: 2px solid rgba(255, 255, 255, 0.85);
  box-shadow: 0 8px 22px rgba(21, 94, 239, 0.42), 0 0 0 6px rgba(21, 94, 239, 0.08);
  display: flex; align-items: center; justify-content: center;
  cursor: grab; user-select: none; touch-action: none;
  animation: aiFloat 3.2s ease-in-out infinite;
  transition: box-shadow 0.25s ease, transform 0.25s ease;
}
.ai-ball:hover {
  box-shadow: 0 10px 28px rgba(21, 94, 239, 0.55), 0 0 0 9px rgba(21, 94, 239, 0.12);
  transform: scale(1.06);
}
.ai-ball:active { cursor: grabbing; animation-play-state: paused; }
@keyframes aiFloat {
  0%, 100% { translate: 0 0; }
  50% { translate: 0 -4px; }
}

.ai-ball-drone { width: 40px; height: 40px; filter: drop-shadow(0 1px 2px rgba(9, 51, 133, 0.35)); }

/* 旋翼旋转:悬停缓慢怠转,思考时加速 */
.ai-ball .rotor ellipse {
  fill: rgba(255, 255, 255, 0.92);
  transform-box: fill-box; transform-origin: center;
  animation: rotorSpin 1.4s linear infinite;
  animation-delay: var(--d, 0s);
}
.ai-ball:hover .rotor ellipse { animation-duration: 0.5s; }
.ai-ball.thinking .rotor ellipse { animation-duration: 0.16s; }
@keyframes rotorSpin { to { transform: rotate(360deg); } }

/* 机头指示灯:思考时闪烁 */
.ai-ball .ai-led { fill: #a7f3d0; animation: ledBlink 2.4s ease-in-out infinite; }
.ai-ball.thinking .ai-led { fill: #fde68a; animation-duration: 0.6s; }
@keyframes ledBlink { 0%, 100% { opacity: 1; } 50% { opacity: 0.25; } }

/* AI 小标牌 */
.ai-ball-tag {
  position: absolute; right: -4px; bottom: -2px;
  font-size: 10px; font-weight: 800; letter-spacing: 0.5px;
  color: #155eef; background: #ffffff;
  border-radius: 999px; padding: 1px 6px;
  box-shadow: 0 2px 6px rgba(16, 24, 40, 0.25);
}

/* 思考光环 */
.ai-ball-ring {
  position: absolute; inset: -7px; border-radius: 50%;
  border: 2.5px solid rgba(59, 130, 246, 0.75); border-top-color: transparent;
  border-right-color: rgba(59, 130, 246, 0.35);
  animation: aiSpin 0.9s linear infinite;
}
@keyframes aiSpin { to { transform: rotate(360deg); } }

/* ==================== 对话窗口 ==================== */
.ai-win {
  position: fixed; z-index: 2100; min-width: 340px; min-height: 420px;
  max-width: 90vw; max-height: 88vh;
  width: 420px; height: 560px;     /* 初始尺寸,右下角拖拽可改 */
  background: #fff; border-radius: 16px; overflow: hidden;
  box-shadow: 0 18px 50px rgba(16, 24, 40, 0.28), 0 0 0 1px rgba(21, 94, 239, 0.08);
  display: flex; flex-direction: column;
  resize: both;
  animation: winIn 0.22s cubic-bezier(0.2, 0.9, 0.35, 1.2);
}
@keyframes winIn {
  from { opacity: 0; transform: translateY(14px) scale(0.96); }
  to { opacity: 1; transform: translateY(0) scale(1); }
}

/* ---- 标题栏 ---- */
.ai-win-head {
  flex-shrink: 0; display: flex; align-items: center; gap: 9px;
  height: 48px; padding: 0 12px; cursor: move; user-select: none;
  background: linear-gradient(120deg, #2f7bff 0%, #155eef 60%, #1749c4 100%);
  position: relative;
}
.ai-win-head::after {   /* 底部细高光分隔 */
  content: ''; position: absolute; left: 0; right: 0; bottom: 0; height: 1px;
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.55), transparent);
}
.ai-win-logo {
  width: 30px; height: 30px; border-radius: 9px; background: #ffffff;
  display: flex; align-items: center; justify-content: center; flex-shrink: 0;
  box-shadow: 0 2px 6px rgba(9, 51, 133, 0.35);
}
.ai-win-logo svg { width: 20px; height: 20px; }
.ai-win-tt { display: flex; flex-direction: column; line-height: 1.25; }
.ai-win-tt b { color: #fff; font-size: 13.5px; letter-spacing: 0.5px; }
.ai-win-tt i { color: rgba(255, 255, 255, 0.78); font-size: 10.5px; font-style: normal; }
.ai-win-status {
  display: inline-flex; align-items: center; gap: 5px;
  margin-left: 6px; padding: 2px 9px; border-radius: 999px;
  background: rgba(255, 255, 255, 0.18); color: #fff; font-size: 11px;
  max-width: 150px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}
.ai-win-status-dot { width: 6px; height: 6px; border-radius: 50%; background: #7ef0b2; animation: ledBlink 1s infinite; flex-shrink: 0; }
.ai-win-close {
  margin-left: auto; cursor: pointer; color: #fff; font-size: 13px;
  width: 24px; height: 24px; border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  background: rgba(255, 255, 255, 0.14); transition: background 0.2s;
}
.ai-win-close:hover { background: rgba(255, 255, 255, 0.32); }

/* ---- 主体 ---- */
.ai-win-body {
  flex: 1; display: flex; flex-direction: column; min-height: 0;
  background: linear-gradient(180deg, #f5f9ff 0%, #ffffff 24%, #ffffff 100%);
}
.ai-list { flex: 1; overflow-y: auto; padding: 12px 14px; min-height: 60px; }
.ai-list::-webkit-scrollbar { width: 6px; }
.ai-list::-webkit-scrollbar-thumb { background: #c9d9f5; border-radius: 3px; }
.ai-list::-webkit-scrollbar-thumb:hover { background: #a9c3ee; }

/* 空状态 */
.ai-empty { padding: 34px 8px; text-align: center; }
.ai-empty-drone { width: 64px; height: 64px; opacity: 0.9; animation: aiFloat 3.2s ease-in-out infinite; }
.ai-empty-title {
  margin-top: 10px; font-size: 14px; font-weight: 800;
  background: linear-gradient(120deg, #2f7bff, #155eef);
  -webkit-background-clip: text; background-clip: text; color: transparent;
}
.ai-empty-sub { margin-top: 5px; font-size: 12px; color: var(--text-faint, #98a2b3); line-height: 1.6; }

/* 气泡 + 头像 */
.ai-row { display: flex; margin-bottom: 10px; gap: 7px; align-items: flex-start; }
.ai-row.user { flex-direction: row-reverse; }
.ai-avatar {
  width: 26px; height: 26px; border-radius: 50%; flex-shrink: 0; margin-top: 2px;
  background: #eaf1ff; border: 1px solid #d6e6ff;
  display: flex; align-items: center; justify-content: center;
}
.ai-avatar svg { width: 17px; height: 17px; }
.ai-avatar.user {
  background: linear-gradient(135deg, #3b82f6, #155eef); border: none;
  color: #fff; font-size: 11px; font-weight: 700;
}
.ai-bubble {
  max-width: 82%; padding: 8px 11px; font-size: 12.5px; line-height: 1.7;
  border-radius: 10px; word-break: break-word;
}
.ai-row.assistant .ai-bubble {
  background: #ffffff; border: 1px solid #dbe7fb; color: #344054;
  border-top-left-radius: 3px;
  box-shadow: 0 1px 4px rgba(16, 24, 40, 0.05);
}
.ai-row.user .ai-bubble {
  background: linear-gradient(135deg, #3b82f6, #155eef); color: #fff;
  border-top-right-radius: 3px;
  box-shadow: 0 2px 8px rgba(21, 94, 239, 0.28);
}
.ai-text :deep(b) { color: #155eef; font-weight: 700; }
.ai-row.user .ai-text :deep(b) { color: #fff; }

/* 打字指示 / 流式光标 */
.ai-dot-group { display: inline-flex; gap: 4px; padding: 3px 0; }
.ai-dot { display: inline-block; width: 5px; height: 5px; border-radius: 50%; background: #85adef; animation: aiBlink 1.2s infinite; }
.ai-dot:nth-child(2) { animation-delay: 0.2s; }
.ai-dot:nth-child(3) { animation-delay: 0.4s; }
@keyframes aiBlink { 0%, 80%, 100% { opacity: 0.25; } 40% { opacity: 1; } }
.ai-caret {
  display: inline-block; width: 2px; height: 13px; margin-left: 2px;
  background: #155eef; vertical-align: -2px; animation: aiBlink 0.9s steps(1) infinite;
}

/* 快捷问题 */
.ai-chips { flex-shrink: 0; display: flex; flex-wrap: wrap; gap: 6px; padding: 4px 14px 8px; }
.ai-chip {
  display: inline-flex; align-items: center; gap: 5px;
  font-size: 11px; color: #175cd3; background: #ffffff;
  border: 1px solid #cfdcfa; border-radius: 999px; padding: 3.5px 10px;
  cursor: pointer; user-select: none; transition: all 0.18s;
}
.ai-chip i {
  width: 5px; height: 5px; border-radius: 50%;
  background: linear-gradient(135deg, #3b82f6, #155eef);
}
.ai-chip:hover { background: #eaf3ff; border-color: #9cc0fb; transform: translateY(-1px); }

/* 输入区 */
.ai-input {
  flex-shrink: 0; display: flex; gap: 8px; padding: 9px 14px 6px;
  border-top: 1px solid #edf2fb; background: #ffffff;
}
.ai-input :deep(.el-input__wrapper) {
  border-radius: 9px; box-shadow: 0 0 0 1px #dbe7fb inset;
  background: #f7faff;
}
.ai-input :deep(.el-input__wrapper.is-focus) { box-shadow: 0 0 0 1.5px #155eef inset; background: #fff; }
.ai-input :deep(.el-button) {
  border-radius: 9px; background: linear-gradient(135deg, #3b82f6, #155eef);
  border: none; padding: 0 15px;
}

/* 底部提示 */
.ai-win-foot {
  flex-shrink: 0; text-align: center; padding: 0 0 7px;
  font-size: 10.5px; color: #b3c1d6; letter-spacing: 0.3px;
}

/* 右下角调整大小手柄(resize:both 生效,此处为视觉提示) */
.ai-win-resize {
  position: absolute; right: 4px; bottom: 4px; width: 15px; height: 15px;
  pointer-events: none; border-bottom-right-radius: 12px;
  background:
    linear-gradient(135deg, transparent 50%, #b9cdf5 50%),
    linear-gradient(135deg, transparent 68%, #d6e6ff 68%);
}
</style>
