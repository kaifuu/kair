<template>
  <div class="login-page">
    <!-- ===== 满屏空域场景 ===== -->
    <div class="grid-bg"></div>
    <div class="glow glow-1"></div>
    <div class="glow glow-2"></div>

    <!-- AI 神经网络(左下) -->
    <svg class="neural-net" viewBox="0 0 300 170" fill="none">
      <g stroke="rgba(186,230,253,.35)" stroke-width="1">
        <path d="M30 30 L110 55 M30 85 L110 55 M30 140 L110 95 M30 85 L110 95 M30 30 L110 20 M30 140 L110 130 M30 85 L110 130"/>
        <path d="M110 20 L200 75 M110 55 L200 75 M110 95 L200 75 M110 130 L200 75 M110 20 L200 130 M110 130 L200 130 M110 55 L200 130 M110 95 L200 130"/>
        <path d="M200 75 L272 55 M200 130 L272 55 M200 75 L272 105 M200 130 L272 105"/>
      </g>
      <g fill="#7dd3fc">
        <circle cx="30" cy="30" r="3.4"/><circle cx="30" cy="85" r="3.4"/><circle cx="30" cy="140" r="3.4"/>
        <circle cx="110" cy="20" r="3"/><circle cx="110" cy="55" r="3.4"/><circle cx="110" cy="95" r="3"/><circle cx="110" cy="130" r="3.4"/>
        <circle cx="200" cy="75" r="4"/><circle cx="200" cy="130" r="3.4"/>
      </g>
      <g fill="#e0f2fe">
        <circle cx="272" cy="55" r="3.6"/><circle cx="272" cy="105" r="3.6"/>
      </g>
      <circle r="2.4" fill="#ffffff">
        <animateMotion dur="2.2s" repeatCount="indefinite" path="M30 85 L110 55 L200 75 L272 55"/>
      </circle>
      <circle r="2" fill="#bae6fd">
        <animateMotion dur="3s" begin="0.8s" repeatCount="indefinite" path="M30 140 L110 130 L200 130 L272 105"/>
      </circle>
    </svg>

    <!-- 态势雷达(中部后景) -->
    <svg class="radar" viewBox="0 0 640 640" fill="none">
      <defs>
        <linearGradient id="sweepGrad" x1="0" y1="1" x2="0.5" y2="0">
          <stop offset="0" stop-color="#7dd3fc" stop-opacity=".3"/>
          <stop offset="1" stop-color="#7dd3fc" stop-opacity="0"/>
        </linearGradient>
      </defs>
      <circle cx="320" cy="320" r="300" stroke="rgba(191,219,254,.35)" stroke-width="1"/>
      <circle cx="320" cy="320" r="232" stroke="rgba(191,219,254,.45)" stroke-width="1" stroke-dasharray="4 8"/>
      <circle cx="320" cy="320" r="164" stroke="rgba(191,219,254,.35)" stroke-width="1"/>
      <circle cx="320" cy="320" r="96" stroke="rgba(191,219,254,.45)" stroke-width="1" stroke-dasharray="3 7"/>
      <circle cx="320" cy="320" r="30" stroke="rgba(219,234,254,.5)" stroke-width="1.2"/>
      <path d="M320 6 V634 M6 320 H634 M96 96 L544 544 M544 96 L96 544" stroke="rgba(219,234,254,.18)" stroke-width="1"/>
      <g>
        <path d="M320 320 L320 22 A298 298 0 0 1 470 91 Z" fill="url(#sweepGrad)"/>
        <line x1="320" y1="320" x2="320" y2="22" stroke="#bae6fd" stroke-width="1.4" opacity=".85"/>
        <animateTransform attributeName="transform" type="rotate" from="0 320 320" to="360 320 320" dur="6s" repeatCount="indefinite"/>
      </g>
      <g><circle cx="212" cy="212" r="4" fill="#ffffff"/><circle cx="212" cy="212" r="4" fill="none" stroke="#ffffff"><animate attributeName="r" values="4;18" dur="2.6s" repeatCount="indefinite"/><animate attributeName="opacity" values=".8;0" dur="2.6s" repeatCount="indefinite"/></circle></g>
      <g><circle cx="452" cy="268" r="3.5" fill="#bae6fd"/><circle cx="452" cy="268" r="3.5" fill="none" stroke="#bae6fd"><animate attributeName="r" values="3.5;16" dur="3.2s" repeatCount="indefinite"/><animate attributeName="opacity" values=".8;0" dur="3.2s" repeatCount="indefinite"/></circle></g>
      <g><circle cx="352" cy="452" r="3" fill="#c4b5fd"/><circle cx="352" cy="452" r="3" fill="none" stroke="#c4b5fd"><animate attributeName="r" values="3;14" dur="2.9s" repeatCount="indefinite"/><animate attributeName="opacity" values=".8;0" dur="2.9s" repeatCount="indefinite"/></circle></g>
    </svg>

    <!-- 无人机主机 + 扫描光束 -->
    <div class="drone-stage">
      <div class="scan-beam"></div>
      <svg class="drone-hero" viewBox="0 0 240 240" fill="none">
        <defs>
          <linearGradient id="bodyGrad" x1="0" y1="0" x2="1" y2="1">
            <stop offset="0" stop-color="#ffffff"/><stop offset="1" stop-color="#dbeafe"/>
          </linearGradient>
        </defs>
        <path d="M96 96 L66 66 M144 96 L174 66 M96 144 L66 174 M144 144 L174 174" stroke="#e0f2fe" stroke-width="7" stroke-linecap="round"/>
        <circle cx="62" cy="62" r="9" fill="rgba(255,255,255,.25)" stroke="#e0f2fe" stroke-width="2.6"/>
        <circle cx="178" cy="62" r="9" fill="rgba(255,255,255,.25)" stroke="#e0f2fe" stroke-width="2.6"/>
        <circle cx="62" cy="178" r="9" fill="rgba(255,255,255,.25)" stroke="#e0f2fe" stroke-width="2.6"/>
        <circle cx="178" cy="178" r="9" fill="rgba(255,255,255,.25)" stroke="#e0f2fe" stroke-width="2.6"/>
        <g opacity=".95">
          <circle cx="62" cy="62" r="33" stroke="#bae6fd" stroke-width="2" stroke-dasharray="18 10" opacity=".7">
            <animateTransform attributeName="transform" type="rotate" from="0 62 62" to="360 62 62" dur="0.6s" repeatCount="indefinite"/></circle>
          <circle cx="178" cy="62" r="33" stroke="#bae6fd" stroke-width="2" stroke-dasharray="18 10" opacity=".7">
            <animateTransform attributeName="transform" type="rotate" from="0 178 62" to="360 178 62" dur="0.55s" repeatCount="indefinite"/></circle>
          <circle cx="62" cy="178" r="33" stroke="#bae6fd" stroke-width="2" stroke-dasharray="18 10" opacity=".7">
            <animateTransform attributeName="transform" type="rotate" from="0 62 178" to="360 62 178" dur="0.65s" repeatCount="indefinite"/></circle>
          <circle cx="178" cy="178" r="33" stroke="#bae6fd" stroke-width="2" stroke-dasharray="18 10" opacity=".7">
            <animateTransform attributeName="transform" type="rotate" from="0 178 178" to="360 178 178" dur="0.5s" repeatCount="indefinite"/></circle>
        </g>
        <rect x="90" y="90" width="60" height="60" rx="16" fill="url(#bodyGrad)" stroke="#e0f2fe" stroke-width="2.6"/>
        <circle cx="120" cy="120" r="12" fill="#bae6fd" stroke="#7dd3fc" stroke-width="2"/>
        <circle cx="120" cy="120" r="4.5" fill="#155eef"/>
        <rect x="108" y="140" width="24" height="5" rx="2.5" fill="#93c5fd"/>
        <circle cx="120" cy="102" r="2.6" fill="#6ee7b7" class="led"/>
      </svg>
    </div>

    <!-- 巡航僚机 -->
    <div class="drone-mini">
      <svg viewBox="0 0 240 240" fill="none">
        <path d="M96 96 L66 66 M144 96 L174 66 M96 144 L66 174 M144 144 L174 174" stroke="#e0f2fe" stroke-width="10" stroke-linecap="round" opacity=".9"/>
        <circle cx="62" cy="62" r="26" stroke="#bae6fd" stroke-width="3" stroke-dasharray="14 8" opacity=".55">
          <animateTransform attributeName="transform" type="rotate" from="0 62 62" to="360 62 62" dur="0.5s" repeatCount="indefinite"/></circle>
        <circle cx="178" cy="62" r="26" stroke="#bae6fd" stroke-width="3" stroke-dasharray="14 8" opacity=".55">
          <animateTransform attributeName="transform" type="rotate" from="0 178 62" to="360 178 62" dur="0.55s" repeatCount="indefinite"/></circle>
        <circle cx="62" cy="178" r="26" stroke="#bae6fd" stroke-width="3" stroke-dasharray="14 8" opacity=".55">
          <animateTransform attributeName="transform" type="rotate" from="0 62 178" to="360 62 178" dur="0.6s" repeatCount="indefinite"/></circle>
        <circle cx="178" cy="178" r="26" stroke="#bae6fd" stroke-width="3" stroke-dasharray="14 8" opacity=".55">
          <animateTransform attributeName="transform" type="rotate" from="0 178 178" to="360 178 178" dur="0.45s" repeatCount="indefinite"/></circle>
        <rect x="92" y="92" width="56" height="56" rx="14" fill="#ffffff" opacity=".95"/>
        <circle cx="120" cy="120" r="9" fill="#7dd3fc"/>
      </svg>
    </div>

    <!-- 航迹光弧 -->
    <svg class="flight-paths" viewBox="0 0 900 700" fill="none">
      <defs>
        <linearGradient id="trailA" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0" stop-color="#bae6fd" stop-opacity="0"/>
          <stop offset=".5" stop-color="#e0f2fe" stop-opacity=".7"/>
          <stop offset="1" stop-color="#c4b5fd" stop-opacity="0"/>
        </linearGradient>
      </defs>
      <path d="M40 600 C 240 500, 330 340, 510 280" stroke="url(#trailA)" stroke-width="1.6" stroke-dasharray="6 10" class="dash-fly"/>
      <path d="M80 660 C 300 600, 430 470, 650 420" stroke="url(#trailA)" stroke-width="1.2" stroke-dasharray="4 12" class="dash-fly slow"/>
      <path d="M60 500 C 200 440, 300 280, 470 200" stroke="url(#trailA)" stroke-width="1" stroke-dasharray="3 9" class="dash-fly slower"/>
      <circle r="3.2" fill="#e0f2fe" opacity=".95">
        <animateMotion dur="11s" repeatCount="indefinite" path="M40 600 C 240 500, 330 340, 510 280"/>
      </circle>
    </svg>

    <!-- 数据链路:态势场 → 控制台 -->
    <svg class="data-link" viewBox="0 0 320 140" fill="none" preserveAspectRatio="none">
      <path d="M0 96 C 110 96, 170 40, 320 44" stroke="rgba(186,230,253,.45)" stroke-width="1.4" stroke-dasharray="5 7"/>
      <circle r="3" fill="#e0f2fe">
        <animateMotion dur="3.2s" repeatCount="indefinite" path="M0 96 C 110 96, 170 40, 320 44"/>
      </circle>
      <circle r="2.2" fill="#7dd3fc">
        <animateMotion dur="3.2s" begin="1.6s" repeatCount="indefinite" path="M0 96 C 110 96, 170 40, 320 44"/>
      </circle>
    </svg>

    <!-- ===== 前景:左品牌 / 右悬浮登录卡 ===== -->
    <div class="layout">
      <!-- 左:品牌信息 -->
      <div class="brand-half">
        <div class="brand-content">
          <div class="brand-logo">
            <svg viewBox="0 0 24 24" width="30" height="30" fill="none" stroke="currentColor" stroke-width="1.5">
              <circle cx="5" cy="5" r="2.6"/><circle cx="19" cy="5" r="2.6"/>
              <circle cx="5" cy="19" r="2.6"/><circle cx="19" cy="19" r="2.6"/>
              <path d="M7 6.5 L17 6.5 M6.5 7 L17.5 17.5 M6.5 17 L17.5 6.5 M7 17.5 L17 17.5"/>
            </svg>
            <span>无人机智能监管平台</span>
          </div>

          <h1 class="slogan">AI 驱动<br />低空态势感知中枢</h1>
          <p class="slogan-sub">神经网络全域感知 · 智能目标识别 · 秒级自主预警<br />构建天空地一体化的数字低空监管体系</p>

          <div class="feature-chips">
            <span class="chip"><i class="chip-dot"></i>AI 态势感知</span>
            <span class="chip"><i class="chip-dot cyan"></i>智能目标识别</span>
            <span class="chip"><i class="chip-dot violet"></i>自主预警响应</span>
            <span class="chip"><i class="chip-dot green"></i>全域协同监管</span>
          </div>

          <div class="brand-footer">
            <span class="dot-pulse"></span>AI 引擎全天候值守 · 多源态势数据实时融合
          </div>
        </div>
      </div>

      <!-- 右:悬浮白卡 -->
      <div class="form-half">
        <div class="card-dock fade-in-up">
          <i class="lock-tick tl"></i><i class="lock-tick tr"></i><i class="lock-tick bl"></i><i class="lock-tick br"></i>
          <div class="login-card">
          <div class="card-head">
            <div class="orb-wrap">
              <svg class="ai-orb" viewBox="0 0 120 120" fill="none">
              <defs>
                <radialGradient id="orbGrad" cx=".38" cy=".32" r=".9">
                  <stop offset="0" stop-color="#ffffff"/>
                  <stop offset=".45" stop-color="#9ddcff"/>
                  <stop offset="1" stop-color="#155eef"/>
                </radialGradient>
              </defs>
              <circle cx="60" cy="60" r="24" fill="url(#orbGrad)"/>
              <g stroke="rgba(21,94,239,.55)" stroke-width="1" fill="#155eef">
                <path d="M52 52 L60 60 L68 52 M52 68 L60 60 L68 68 M52 52 L52 68 M68 52 L68 68"/>
                <circle cx="52" cy="52" r="2"/><circle cx="68" cy="52" r="2"/>
                <circle cx="52" cy="68" r="2"/><circle cx="68" cy="68" r="2"/><circle cx="60" cy="60" r="2.4"/>
              </g>
              <path d="M16 60 A44 16 0 1 0 104 60 A44 16 0 1 0 16 60" stroke="rgba(21,94,239,.4)" stroke-width="1.4"/>
              <circle r="3" fill="#0ea5e9">
                <animateMotion dur="4s" repeatCount="indefinite" path="M16 60 A44 16 0 1 0 104 60 A44 16 0 1 0 16 60"/>
              </circle>
              <g transform="rotate(60 60 60)">
                <path d="M20 60 A40 14 0 1 0 100 60 A40 14 0 1 0 20 60" stroke="rgba(124,58,237,.35)" stroke-width="1.2" stroke-dasharray="3 5"/>
                <circle r="2.4" fill="#7c3aed">
                  <animateMotion dur="5.5s" repeatCount="indefinite" path="M20 60 A40 14 0 1 0 100 60 A40 14 0 1 0 20 60"/>
                </circle>
              </g>
              <g transform="rotate(-55 60 60)">
                <path d="M24 60 A36 12 0 1 0 96 60 A36 12 0 1 0 24 60" stroke="rgba(14,165,233,.28)" stroke-width="1"/>
              </g>
              </svg>
            </div>
            <h2>AI 智能监管控制台</h2>
            <i class="head-bar"></i>
          </div>

          <div class="card-body">
            <el-form :model="form" size="large" @keyup.enter="doLogin">
              <el-form-item>
                <el-input v-model="form.username" placeholder="请输入账号" :prefix-icon="User" />
              </el-form-item>

              <el-form-item>
                <el-input v-model="form.password" type="password" placeholder="请输入密码" :prefix-icon="Lock" show-password />
              </el-form-item>

              <el-form-item>
                <div class="captcha-row">
                  <el-input v-model="form.captcha" placeholder="请输入验证码" :prefix-icon="Key" maxlength="4"
                            style="flex: 1" @input="form.captcha = form.captcha.toUpperCase()" />
                  <div class="captcha-box" @click="loadCaptcha" title="点击刷新">
                    <div v-if="captchaLoading" class="captcha-loading"><el-icon class="is-loading"><Loading /></el-icon></div>
                    <img v-else-if="captchaSvg" :src="svgUrl" alt="验证码" />
                    <span v-else class="captcha-retry">点击获取</span>
                  </div>
                </div>
              </el-form-item>

              <el-button class="submit-btn" :loading="loading" @click="doLogin">登 录</el-button>
            </el-form>

            <div class="engine-line">
              <span class="ai-pulse"></span>AI 态势感知引擎在线<span class="ver">ENGINE ONLINE</span>
            </div>
            <div class="demo-line">演示账号:admin / admin123</div>
          </div>
        </div>
        </div>

        <p class="copy">© 2026 低空飞行服务监管中心 · Low-Altitude Supervision Center</p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock, Key, Loading } from '@element-plus/icons-vue'
import http from '../api'

const router = useRouter()
const loading = ref(false)
const captchaSvg = ref('')
const captchaLoading = ref(false)
const form = reactive({ username: 'admin', password: '', captcha: '', cid: '' })

const svgUrl = computed(() =>
  captchaSvg.value ? 'data:image/svg+xml;utf8,' + encodeURIComponent(captchaSvg.value) : ''
)

onMounted(() => {
  loadCaptcha()
})

async function loadCaptcha() {
  captchaLoading.value = true
  try {
    const data = await http.get('/auth/captcha')
    form.cid = data.cid
    captchaSvg.value = data.svg
    form.captcha = ''
  } finally {
    captchaLoading.value = false
  }
}

async function doLogin() {
  if (!form.username || !form.password) return ElMessage.warning('请输入账号和密码')
  if (!form.captcha) return ElMessage.warning('请输入验证码')
  loading.value = true
  try {
    const data = await http.post('/auth/login', form)
    localStorage.setItem('token', data.token)
    localStorage.setItem('nickname', data.nickname || data.username)
    localStorage.setItem('roleCode', data.roleCode || '')
    if (data.menus) localStorage.setItem('menus', JSON.stringify(data.menus))
    ElMessage.success('登录成功')
    router.push('/')
  } catch (e) {
    // 验证码错误/过期 → 刷新
    loadCaptcha()
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
/* ===== 满屏亮蓝空域 ===== */
.login-page {
  position: relative; height: 100%; overflow: hidden;
  background:
    radial-gradient(900px 560px at 82% 10%, rgba(56, 189, 248, 0.30) 0%, transparent 55%),
    radial-gradient(760px 520px at 6% 94%, rgba(99, 102, 241, 0.26) 0%, transparent 52%),
    linear-gradient(158deg, #1e6bff 0%, #0f5ae8 44%, #0280cf 80%, #0284c7 100%);
}

.grid-bg {
  position: absolute; inset: 0;
  background-image:
    linear-gradient(rgba(224, 242, 254, 0.08) 1px, transparent 1px),
    linear-gradient(90deg, rgba(224, 242, 254, 0.08) 1px, transparent 1px);
  background-size: 48px 48px;
  mask-image: radial-gradient(ellipse at 42% 45%, #000 45%, transparent 82%);
}

.glow { position: absolute; border-radius: 50%; filter: blur(110px); opacity: .45; }
.glow-1 { width: 480px; height: 480px; background: #38bdf8; top: -150px; right: 6%; }
.glow-2 { width: 440px; height: 440px; background: #6366f1; bottom: -170px; left: -110px; }

/* AI 神经网络 */
.neural-net {
  position: absolute; left: 4%; bottom: 7%;
  width: 300px; opacity: .5;
  pointer-events: none;
}

/* 态势雷达:左侧内容与卡片之间 */
.radar {
  position: absolute; top: 52%; left: 46%;
  width: 700px; height: 700px;
  transform: translate(-50%, -50%);
  opacity: .55; pointer-events: none;
}

/* 无人机主机:沿巡航路径飞行 */
.drone-stage {
  position: absolute; top: 20%; right: 30%;
  width: 210px; height: 300px;
  pointer-events: none;
  animation: drone-fly 26s ease-in-out infinite;
  will-change: transform;
}
@keyframes drone-fly {
  0%   { transform: translate(0, 0) rotate(0deg); }
  10%  { transform: translate(-70px, 40px) rotate(-6deg); }
  22%  { transform: translate(-150px, -30px) rotate(-10deg); }
  34%  { transform: translate(-110px, -100px) rotate(-4deg); }
  46%  { transform: translate(-10px, -130px) rotate(6deg); }
  58%  { transform: translate(60px, -70px) rotate(9deg); }
  70%  { transform: translate(20px, 30px) rotate(4deg); }
  82%  { transform: translate(-60px, 80px) rotate(-5deg); }
  92%  { transform: translate(-30px, 30px) rotate(-2deg); }
  100% { transform: translate(0, 0) rotate(0deg); }
}
.drone-hero {
  position: absolute; top: 0; left: 50%;
  width: 190px; height: 190px;
  transform: translateX(-50%);
  filter: drop-shadow(0 18px 30px rgba(2, 44, 94, 0.45));
  animation: hover-float 4.5s ease-in-out infinite;
}
@keyframes hover-float {
  0%, 100% { transform: translateX(-50%) translateY(0); }
  50% { transform: translateX(-50%) translateY(-14px); }
}
.led { animation: led-blink 2s infinite; }
@keyframes led-blink { 0%, 100% { opacity: 1; } 50% { opacity: .25; } }

/* 下视扫描光束 */
.scan-beam {
  position: absolute; top: 150px; left: 50%;
  width: 150px; height: 130px;
  transform: translateX(-50%);
  clip-path: polygon(36% 0, 64% 0, 100% 100%, 0 100%);
  background: linear-gradient(180deg, rgba(125, 211, 252, 0.38), rgba(125, 211, 252, 0));
  animation: beam-pulse 2.6s ease-in-out infinite;
}
@keyframes beam-pulse { 0%, 100% { opacity: .55; } 50% { opacity: 1; } }

/* 巡航僚机 */
.drone-mini {
  position: absolute; top: 20%; left: 0;
  width: 64px; height: 64px;
  z-index: 1; opacity: .9;
  pointer-events: none;
  filter: drop-shadow(0 8px 16px rgba(2, 44, 94, 0.4));
  animation: mini-fly 34s ease-in-out infinite;
  will-change: transform;
}
@keyframes mini-fly {
  0%   { transform: translate(-110px, 0) rotate(8deg); }
  18%  { transform: translate(240px, -60px) rotate(-6deg); }
  36%  { transform: translate(460px, 20px) rotate(4deg); }
  52%  { transform: translate(560px, -35px) rotate(-4deg); }
  68%  { transform: translate(380px, 70px) rotate(6deg); }
  84%  { transform: translate(150px, 10px) rotate(-5deg); }
  100% { transform: translate(-110px, 0) rotate(8deg); }
}

.flight-paths { position: absolute; inset: 0; width: 100%; height: 100%; }

/* 数据链路:态势场 → 控制台 */
.data-link {
  position: absolute; left: 42%; top: 50%;
  width: 24%; height: 140px;
  transform: translateY(-50%);
  pointer-events: none;
}
@keyframes dashmove { to { stroke-dashoffset: -320; } }
.dash-fly { animation: dashmove 9s linear infinite; }
.dash-fly.slow { animation-duration: 14s; }
.dash-fly.slower { animation-duration: 20s; }

/* ===== 前景布局 ===== */
.layout {
  position: relative; z-index: 2;
  height: 100%;
  display: flex;
}

/* 左:品牌 */
.brand-half {
  flex: 1;
  display: flex; flex-direction: column; justify-content: center;
  min-width: 0;
}
.brand-content { padding: 0 8%; max-width: 660px; }

.brand-logo {
  display: inline-flex; align-items: center; gap: 10px;
  padding: 8px 16px 8px 10px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.14);
  border: 1px solid rgba(224, 242, 254, 0.35);
  color: #e0f2fe;
  font-size: 14px; font-weight: 600; letter-spacing: 2px;
  margin-bottom: 36px;
}
.brand-logo svg { color: #7dd3fc; }

.slogan {
  font-size: 46px; line-height: 1.3; font-weight: 800; letter-spacing: 3px;
  background: linear-gradient(100deg, #ffffff 20%, #bae6fd 70%, #7dd3fc);
  -webkit-background-clip: text; background-clip: text; -webkit-text-fill-color: transparent;
  margin-bottom: 20px;
}
.slogan-sub { font-size: 15px; color: rgba(219, 234, 254, 0.85); line-height: 2; letter-spacing: 1.5px; }

.feature-chips { display: flex; flex-wrap: wrap; gap: 12px; margin-top: 40px; }
.chip {
  display: inline-flex; align-items: center; gap: 8px;
  padding: 9px 16px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.13);
  border: 1px solid rgba(224, 242, 254, 0.32);
  font-size: 13px; font-weight: 600; letter-spacing: 1.5px;
  color: #f0f9ff;
}
.chip-dot { width: 7px; height: 7px; border-radius: 50%; background: #ffffff; box-shadow: 0 0 10px rgba(255,255,255,.9); }
.chip-dot.cyan { background: #7dd3fc; box-shadow: 0 0 10px #7dd3fc; }
.chip-dot.violet { background: #c4b5fd; box-shadow: 0 0 10px #c4b5fd; }
.chip-dot.green { background: #6ee7b7; box-shadow: 0 0 10px #6ee7b7; }

.brand-footer {
  margin-top: 42px; display: flex; align-items: center; gap: 8px;
  font-size: 12.5px; color: rgba(219, 234, 254, 0.75); letter-spacing: 1px;
}
.dot-pulse {
  width: 8px; height: 8px; border-radius: 50%;
  background: #6ee7b7; box-shadow: 0 0 10px #6ee7b7;
  animation: pulse-dot 2s infinite;
}
@keyframes pulse-dot { 0%, 100% { opacity: 1; } 50% { opacity: .35; } }

/* 右:悬浮白卡 */
.form-half {
  width: 520px; flex-shrink: 0;
  display: flex; flex-direction: column;
  align-items: center; justify-content: center;
  position: relative;
}

/* 卡片锚定坞:柔光 + HUD 锁定刻度 */
.card-dock { position: relative; }
.card-dock::before {
  content: '';
  position: absolute; inset: -34px;
  border-radius: 40px;
  background: radial-gradient(closest-side, rgba(56, 189, 248, 0.22), transparent 78%);
  filter: blur(12px);
  z-index: -1;
}
.lock-tick {
  position: absolute; width: 18px; height: 18px;
  border: 2px solid rgba(125, 211, 252, 0.85);
  animation: tick-blink 2.6s ease-in-out infinite;
}
.lock-tick.tl { top: -9px; left: -9px; border-right: none; border-bottom: none; border-top-left-radius: 5px; }
.lock-tick.tr { top: -9px; right: -9px; border-left: none; border-bottom: none; border-top-right-radius: 5px; animation-delay: .3s; }
.lock-tick.bl { bottom: -9px; left: -9px; border-right: none; border-top: none; border-bottom-left-radius: 5px; animation-delay: .6s; }
.lock-tick.br { bottom: -9px; right: -9px; border-left: none; border-top: none; border-bottom-right-radius: 5px; animation-delay: .9s; }
@keyframes tick-blink { 0%, 100% { opacity: .4; } 50% { opacity: 1; } }

.login-card {
  position: relative;
  width: 400px;
  border-radius: 22px;
  overflow: hidden;
  background: #ffffff;
  box-shadow:
    0 40px 100px -20px rgba(2, 32, 71, 0.55),
    0 8px 24px rgba(2, 32, 71, 0.18);
}
.login-card::before {
  content: '';
  position: absolute; top: 0; left: 0; right: 0; height: 3px;
  background: linear-gradient(90deg, #155eef, #0ea5e9, #7c3aed);
  z-index: 1;
}

/* 头部:浅冰蓝分区(渐隐入白) */
.card-head {
  position: relative;
  padding: 32px 32px 24px;
  text-align: center;
  background: linear-gradient(180deg, #f1f7ff 0%, #ffffff 100%);
}
.card-head::before {
  content: '';
  position: absolute; inset: 0;
  background-image:
    linear-gradient(rgba(21, 94, 239, 0.05) 1px, transparent 1px),
    linear-gradient(90deg, rgba(21, 94, 239, 0.05) 1px, transparent 1px);
  background-size: 22px 22px;
  mask-image: linear-gradient(180deg, #000 0%, transparent 72%);
  pointer-events: none;
}

/* 地球底座:光环 + 悬浮投影 */
.orb-wrap {
  position: relative;
  width: 56px; height: 56px;
  margin: 0 auto;
}
.orb-wrap::before {
  content: '';
  position: absolute; left: 50%; top: 50%;
  width: 124px; height: 124px;
  transform: translate(-50%, -50%);
  border-radius: 50%;
  background: radial-gradient(circle, rgba(14, 165, 233, 0.14) 0%, transparent 68%);
}
.orb-wrap::after {
  content: '';
  position: absolute; left: 50%; bottom: -13px;
  width: 96px; height: 15px;
  transform: translateX(-50%);
  border-radius: 50%;
  background: radial-gradient(closest-side, rgba(21, 94, 239, 0.17), transparent 76%);
  filter: blur(2px);
}
.ai-orb {
  position: relative;
  width: 56px; height: 56px;
  filter: drop-shadow(0 6px 16px rgba(21, 94, 239, 0.35));
  transition: transform .35s ease, filter .35s ease;
}
/* 输入时 AI 核苏醒 */
.login-card:focus-within .ai-orb {
  transform: scale(1.12);
  filter: drop-shadow(0 8px 24px rgba(21, 94, 239, 0.55));
}

.card-head h2 {
  position: relative;
  font-size: 19px; font-weight: 700; color: #0c1d3a;
  letter-spacing: 3px; margin-top: 16px;
}

/* 标题下渐变点睛条 */
.head-bar {
  display: block;
  width: 34px; height: 3px;
  margin: 12px auto 0;
  border-radius: 2px;
  background: linear-gradient(90deg, #155eef, #0ea5e9, #7c3aed);
}

.card-body { padding: 20px 34px 24px; }

.el-form-item { margin-bottom: 18px; }

:deep(.el-input__wrapper) {
  border-radius: 10px;
  background: #f6f9fd;
  box-shadow: inset 0 0 0 1px #e2e9f4;
}
:deep(.el-input__wrapper.is-focus) {
  box-shadow: inset 0 0 0 1.5px #155eef, 0 0 0 4px rgba(21, 94, 239, 0.12) !important;
}

.captcha-row { display: flex; gap: 10px; width: 100%; }
.captcha-box {
  width: 112px; height: 40px; flex-shrink: 0;
  border-radius: 10px; overflow: hidden; cursor: pointer;
  border: 1px solid #e2e9f4;
  background: #f6f9fd;
  display: flex; align-items: center; justify-content: center;
  transition: border-color .2s;
}
.captcha-box:hover { border-color: #155eef; }
.captcha-box img { width: 100%; height: 100%; display: block; }
.captcha-loading { color: #98a2b3; display: flex; }
.captcha-retry { font-size: 12px; color: #98a2b3; }

.submit-btn {
  width: 100%; height: 46px; margin-top: 4px;
  font-size: 15px; font-weight: 600; letter-spacing: 10px;
  border-radius: 10px;
  background: linear-gradient(90deg, #155eef, #0ea5e9, #38bdf8, #0ea5e9, #155eef);
  background-size: 220% 100%;
  background-position: 0% 0;
  border: none; color: #fff;
  box-shadow: 0 10px 26px -8px rgba(21, 94, 239, 0.55);
  transition: background-position .6s ease, transform .25s, box-shadow .25s;
}
.submit-btn:hover {
  transform: translateY(-1px);
  background-position: 95% 0;
  box-shadow: 0 14px 32px -8px rgba(21, 94, 239, 0.65);
}

/* AI 引擎在线 · 演示账号 */
.engine-line {
  margin-top: 22px; padding-top: 15px;
  border-top: 1px dashed #e4ecf7;
  display: flex; align-items: center; justify-content: center; gap: 8px;
  font-size: 12px; font-weight: 600; color: #5b7398;
  letter-spacing: 1px;
}
.engine-line .ver { font-size: 10px; font-weight: 700; color: #0ea5e9; letter-spacing: 1.5px; }
.ai-pulse {
  position: relative; width: 7px; height: 7px; border-radius: 50%;
  background: #0ea5e9; flex-shrink: 0;
}
.ai-pulse::after {
  content: ''; position: absolute; inset: 0; border-radius: 50%;
  border: 1.5px solid #0ea5e9;
  animation: ai-ring 1.8s ease-out infinite;
}
@keyframes ai-ring {
  0% { transform: scale(1); opacity: .9; }
  100% { transform: scale(2.6); opacity: 0; }
}
.demo-line {
  margin-top: 9px; text-align: center;
  font-size: 12px; color: #98a2b3;
}

.copy {
  position: absolute; bottom: 26px; left: 0; right: 0;
  text-align: center;
  font-size: 12px; color: rgba(219, 234, 254, 0.62); letter-spacing: 1px;
}

/* 中等屏 */
@media (max-width: 1200px) {
  .radar { width: 560px; height: 560px; opacity: .45; }
  .drone-stage { width: 170px; height: 250px; }
  .drone-hero { width: 155px; height: 155px; }
  .scan-beam { top: 125px; width: 125px; }
  .neural-net { width: 240px; opacity: .35; }
  .slogan { font-size: 38px; }
}

/* 窄屏:仅登录卡 */
@media (max-width: 900px) {
  .brand-half { display: none; }
  .form-half { width: 100%; }
  .login-card { width: calc(100% - 48px); max-width: 400px; }
  .radar { left: 50%; opacity: .35; }
  .data-link { display: none; }
}
</style>
