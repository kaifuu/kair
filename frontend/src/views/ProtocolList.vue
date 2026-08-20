<template>
  <div class="page">
    <div class="page-header">
      <span class="page-title">协议管理</span>
      <div class="actions">
        <el-button type="primary" @click="openDialog()">新增协议</el-button>
      </div>
    </div>

    <div class="panel table-panel">
      <el-table :data="protocols" v-loading="loading" stripe height="100%">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="name" label="协议名称" width="180">
          <template #default="{ row }"><span class="name">{{ row.name }}</span></template>
        </el-table-column>
        <el-table-column label="接入方式" width="110">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ TRANSPORT_TEXT[row.transport] || row.transport }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="帧格式" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="fmtTagType(row.frameFormat)" effect="light">{{ FORMAT_TEXT[row.frameFormat] || row.frameFormat }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="说明" min-width="160" show-overflow-tooltip />
        <el-table-column label="解析规则" min-width="320">
          <template #default="{ row }">
            <div class="rule-chips">
              <el-tag v-for="(r, i) in ruleChips(row)" :key="i" size="small" class="rule-chip" effect="light">
                <b>{{ r.tag }}</b>·{{ r.field }}
                <span v-if="r.rad" class="rad">{{ r.rad }}</span>
                <span class="dim">{{ r.type }}<template v-if="r.scale && r.scale !== 1">×{{ r.scale }}</template>{{ r.unit ? ' ' + r.unit : '' }}</span>
              </el-tag>
              <span v-if="!ruleChips(row).length" class="dim">—</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDialog(row)">编辑</el-button>
            <el-button link type="warning" size="small" @click="openTest(row)">解析测试</el-button>
            <el-button link type="success" size="small" @click="openSend(row)">编码发送</el-button>
            <el-popconfirm title="确认删除该协议?" @confirm="remove(row.id)">
              <template #reference>
                <el-button link type="danger" size="small">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 编辑器 -->
    <el-dialog v-model="dialog.visible" :title="dialog.form.id ? '编辑协议' : '新增协议'" width="980px" top="5vh">
      <el-form :model="dialog.form" label-width="90px">
        <el-form-item label="协议名称" required>
          <el-input v-model="dialog.form.name" placeholder="如:无人机标准 TLV 协议" />
        </el-form-item>
        <el-form-item label="接入方式" required>
          <el-select v-model="dialog.form.transport" style="width: 260px">
            <el-option v-for="t in TRANSPORTS" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
          <div class="field-tip">
            TCP:Netty 9527 标准帧(AA55+CRC16);RS232/RS485:串口设备经 DTU 透传接入 9528;
            MODBUS_TCP:PLC/RTU 经 Modbus TCP 接入 9529(FC16 写寄存器采集,FC3/4 可回读)。
          </div>
        </el-form-item>
        <el-form-item label="帧格式" required>
          <el-radio-group v-model="dialog.form.frameFormat" @change="onFormatChange">
            <el-radio value="TLV">TLV(tag/length/value)</el-radio>
            <el-radio value="FIXED">定长帧(偏移切分)</el-radio>
            <el-radio value="MODBUS">Modbus(寄存器映射)</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="dialog.form.description" type="textarea" :rows="2" />
        </el-form-item>

        <!-- TLV 编辑器 -->
        <template v-if="dialog.form.frameFormat === 'TLV'">
          <el-form-item label="帧头参数">
            <div class="tlv-head">
              <span class="th-label">tag 长度</span>
              <el-select v-model="dialog.tlv.tagLen" style="width: 90px">
                <el-option :value="1" label="1 字节" /><el-option :value="2" label="2 字节" /><el-option :value="4" label="4 字节" />
              </el-select>
              <span class="th-label">length 长度</span>
              <el-select v-model="dialog.tlv.lenLen" style="width: 90px">
                <el-option :value="1" label="1 字节" /><el-option :value="2" label="2 字节" /><el-option :value="4" label="4 字节" />
              </el-select>
              <el-checkbox v-model="dialog.tlv.littleEndian">length 小端</el-checkbox>
            </div>
          </el-form-item>
          <el-form-item label="解析规则">
            <div class="rules-editor">
              <div class="rules-head">
                <span style="width: 80px">tag</span><span style="width: 130px">字段名</span>
                <span style="width: 120px">类型</span><span style="width: 120px">scale(×倍率)</span>
                <span style="width: 96px">进制</span><span style="flex: 1">单位</span><span style="width: 30px"></span>
              </div>
              <div v-for="(r, i) in dialog.rules" :key="i" class="rule-row">
                <el-input-number v-model="r.tag" :min="1" :max="255" :controls="false" style="width: 80px" />
                <el-input v-model="r.field" placeholder="字段名" style="width: 130px" />
                <el-select v-model="r.type" style="width: 120px">
                  <el-option v-for="t in TYPES" :key="t" :label="t" :value="t" />
                </el-select>
                <el-input-number v-model="r.scale" :step="0.1" :min="0.000001" :max="100000" :controls="false" style="width: 120px" />
                <el-select v-model="r.radix" style="width: 96px" :disabled="radixDisabled(r.type)">
                  <el-option v-for="x in RADIX" :key="x.value" :label="x.label" :value="x.value" />
                </el-select>
                <el-input v-model="r.unit" placeholder="如 m / %" style="flex: 1" />
                <el-button link type="danger" @click="dialog.rules.splice(i, 1)">删</el-button>
              </div>
              <el-button size="small" dashed style="width: 100%" @click="dialog.rules.push({ tag: nextTag(), field: '', type: 'uint16', scale: 1, radix: 10, unit: '' })">
                + 添加规则
              </el-button>
              <div class="rules-tip">
                tag 对报文 TLV 单元标识;数值 = 原始值 × scale;string/hex 类型按文本/十六进制呈现。
                进制:消息体值的解析呈现方式,非十进制时输出原始码值(不乘 scale),如 0x1F / 0o37 / 0b00011111。
              </div>
            </div>
          </el-form-item>
        </template>

        <!-- 定长帧编辑器 -->
        <template v-else-if="dialog.form.frameFormat === 'FIXED'">
          <el-form-item label="字段切分">
            <div class="rules-editor">
              <div class="rules-head">
                <span style="width: 80px">偏移 offset</span><span style="width: 80px">长度 len</span>
                <span style="width: 130px">字段名</span><span style="width: 120px">类型</span>
                <span style="width: 120px">scale(×倍率)</span><span style="width: 96px">进制</span>
                <span style="flex: 1">单位</span><span style="width: 30px"></span>
              </div>
              <div v-for="(f, i) in dialog.fixed" :key="i" class="rule-row">
                <el-input-number v-model="f.offset" :min="0" :max="1023" :controls="false" style="width: 80px" />
                <el-input-number v-model="f.len" :min="1" :max="64" :controls="false" style="width: 80px" />
                <el-input v-model="f.field" placeholder="字段名" style="width: 130px" />
                <el-select v-model="f.type" style="width: 120px">
                  <el-option v-for="t in TYPES" :key="t" :label="t" :value="t" />
                </el-select>
                <el-input-number v-model="f.scale" :step="0.1" :min="0.000001" :max="100000" :controls="false" style="width: 120px" />
                <el-select v-model="f.radix" style="width: 96px" :disabled="radixDisabled(f.type)">
                  <el-option v-for="x in RADIX" :key="x.value" :label="x.label" :value="x.value" />
                </el-select>
                <el-input v-model="f.unit" placeholder="如 m / %" style="flex: 1" />
                <el-button link type="danger" @click="dialog.fixed.splice(i, 1)">删</el-button>
              </div>
              <el-button size="small" dashed style="width: 100%" @click="dialog.fixed.push({ offset: nextOffset(), len: 2, field: '', type: 'uint16', scale: 1, radix: 10, unit: '' })">
                + 添加字段
              </el-button>
              <div class="rules-tip">
                串口设备(RS232/RS485 经 DTU 透传)常见定长帧:按「偏移 + 长度」从原始字节切片,大端字节序;
                进制含义同 TLV(非十进制输出原始码值,不乘 scale),支持二/八/十六进制原始报文的拆分验证。
              </div>
            </div>
          </el-form-item>
        </template>

        <!-- Modbus 编辑器 -->
        <template v-else>
          <el-form-item label="从站单元">
            <el-input-number v-model="dialog.modbus.unitId" :min="1" :max="247" />
            <div class="field-tip">MBAP 单元标识;设备档案上的「Modbus 单元号」需与此一致(按单元号路由到设备)。</div>
          </el-form-item>
          <el-form-item label="寄存器映射">
            <div class="rules-editor">
              <div class="rules-head">
                <span style="width: 100px">起始寄存器</span><span style="width: 90px">数量</span>
                <span style="width: 150px">字段名</span><span style="width: 120px">类型</span>
                <span style="width: 120px">scale(×倍率)</span><span style="flex: 1">单位</span><span style="width: 30px"></span>
              </div>
              <div v-for="(m, i) in dialog.modbus.regMap" :key="i" class="rule-row">
                <el-input-number v-model="m.reg" :min="0" :max="65535" :controls="false" style="width: 100px" />
                <el-input-number v-model="m.count" :min="1" :max="8" :controls="false" style="width: 90px" />
                <el-input v-model="m.field" placeholder="字段名" style="width: 150px" />
                <el-select v-model="m.type" style="width: 120px">
                  <el-option v-for="t in MODBUS_TYPES" :key="t" :label="t" :value="t" />
                </el-select>
                <el-input-number v-model="m.scale" :step="0.1" :min="0.000001" :max="100000" :controls="false" style="width: 120px" />
                <el-input v-model="m.unit" placeholder="如 kPa" style="flex: 1" />
                <el-button link type="danger" @click="dialog.modbus.regMap.splice(i, 1)">删</el-button>
              </div>
              <el-button size="small" dashed style="width: 100%" @click="dialog.modbus.regMap.push({ reg: nextReg(), count: 1, field: '', type: 'uint16', scale: 1, unit: '' })">
                + 添加映射
              </el-button>
              <div class="rules-tip">
                保持寄存器(FC16 写)映射:寄存器号 N 占字节偏移 N×2;count>1 时按多寄存器拼接(uint16/int16 单寄存器,
                uint32/int32/float32 双寄存器)。PLC 周期性写寄存器即完成一次遥测上报。
              </div>
            </div>
          </el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="dialog.saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <!-- 解析测试(四进制输入 → 服务端引擎拆分) -->
    <el-dialog v-model="test.visible" :title="`解析测试 · ${test.protocol?.name || ''}`" width="720px" top="6vh">
      <el-form label-width="90px">
        <el-form-item label="输入进制">
          <el-radio-group v-model="test.base">
            <el-radio v-for="b in BASES" :key="b.value" :value="b.value">{{ b.label }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="原始报文" required>
          <el-input v-model="test.text" type="textarea" :rows="3" style="font-family: monospace"
                    :placeholder="testPlaceholder" />
          <div class="field-tip">{{ testHint }}</div>
        </el-form-item>
        <el-form-item label="解析结果">
          <div v-if="test.result" class="parse-result">
            <div class="pr-meta">
              <span>解析 {{ test.result.byteLength }} 字节</span>
              <span v-if="test.result.frameHex">HEX: <code>{{ test.result.frameHex }}</code></span>
            </div>
            <el-table :data="testRows" size="small" border>
              <el-table-column prop="field" label="字段" width="180" />
              <el-table-column prop="value" label="值" />
            </el-table>
          </div>
          <div v-else-if="test.error" class="preview-err">{{ test.error }}</div>
          <div v-else class="field-tip">点击「开始解析」按该协议的帧格式引擎拆分报文</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="test.visible = false">关闭</el-button>
        <el-button type="primary" :loading="test.loading" @click="runParse">开始解析</el-button>
      </template>
    </el-dialog>

    <!-- 编码发送(经 Netty 网关下发 COMMAND 帧) -->
    <el-dialog v-model="send.visible" :title="`编码发送 · ${send.protocol?.name || ''}`" width="720px" top="4vh">
      <el-form label-width="90px">
        <el-form-item label="目标设备" required>
          <el-select v-model="send.deviceId" filterable placeholder="选择设备(在线可下发)" style="width: 100%">
            <el-option v-for="d in sendDevices" :key="d.id" :value="d.id"
                       :label="`${d.code} · ${d.name}(${statusText[d.status] || d.status})`"
                       :disabled="d.virtual" />
          </el-select>
          <div class="field-tip">报文经 Netty TCP 网关组 COMMAND 帧(magic AA55 + CRC16)下发;仅实体设备在线时可发送</div>
        </el-form-item>
        <el-form-item label="编码方式">
          <el-radio-group v-model="send.mode">
            <el-radio value="tlv">按协议模板编码</el-radio>
            <el-radio value="raw">手动输入原始报文</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="send.mode === 'tlv'" label="字段值">
          <div class="send-rules">
            <div v-for="r in send.rules" :key="r.tag" class="send-rule">
              <span class="sr-tag">#{{ r.tag }} {{ r.field }}</span>
              <el-input v-if="r.type === 'string'" v-model="send.values[r.field]" placeholder="文本" size="small" />
              <el-input v-else-if="r.type === 'hex'" v-model="send.values[r.field]" placeholder="十六进制,如 01 A0" size="small" />
              <el-input-number v-else v-model="send.values[r.field]" :controls="false" size="small" style="width: 100%" />
              <span class="sr-type">{{ r.type }}<template v-if="r.scale && r.scale !== 1"> ×{{ r.scale }}</template></span>
            </div>
            <div v-if="!send.rules.length" class="field-tip">该协议暂无解析规则,可切换为手动输入</div>
          </div>
        </el-form-item>
        <template v-else>
          <el-form-item label="输入进制">
            <el-select v-model="send.rawBase" style="width: 160px">
              <el-option v-for="b in BASES" :key="b.value" :label="b.label" :value="b.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="报文内容">
            <el-input v-model="send.rawText" type="textarea" :rows="3"
                      :placeholder="rawPlaceholder" style="font-family: monospace" />
          </el-form-item>
        </template>
        <el-form-item label="编码预览">
          <div class="preview">
            <div v-if="payloadPreview.error" class="preview-err">{{ payloadPreview.error }}</div>
            <template v-else>
              <div v-for="b in BASES" :key="b.value" class="preview-line">
                <span class="pl-label">{{ b.label }}</span>
                <code>{{ payloadPreview.text[b.value] || '(空)' }}</code>
              </div>
            </template>
          </div>
        </el-form-item>
        <el-form-item v-if="send.result" label="下发结果">
          <code class="result-frame">{{ send.result }}</code>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="send.visible = false">关闭</el-button>
        <el-button type="primary" :loading="send.sending" :disabled="!!payloadPreview.error" @click="doSend">
          发送 COMMAND 帧
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import http from '../api'
import { BASES, buildTlvPayload, decodeBytes, encodeBytes } from '../utils/codec'

const TYPES = ['uint8', 'uint16', 'uint32', 'int16', 'int32', 'float32', 'string', 'hex']
const MODBUS_TYPES = ['uint16', 'int16', 'uint32', 'int32', 'float32']
const RADIX = [
  { value: 10, label: '十进制' },
  { value: 2, label: '二进制' },
  { value: 8, label: '八进制' },
  { value: 16, label: '十六进制' }
]
const RADIX_TAG = { 2: 'BIN', 8: 'OCT', 16: 'HEX' }
const TRANSPORTS = [
  { value: 'TCP', label: 'TCP 标准帧(9527)' },
  { value: 'UDP', label: 'UDP' },
  { value: 'RS232', label: 'RS232 串口(DTU 9528)' },
  { value: 'RS485', label: 'RS485 串口(DTU 9528)' },
  { value: 'MODBUS_TCP', label: 'Modbus TCP(9529)' }
]
const TRANSPORT_TEXT = { TCP: 'TCP 标准帧', UDP: 'UDP', RS232: 'RS232 串口', RS485: 'RS485 串口', MODBUS_TCP: 'Modbus TCP' }
const FORMAT_TEXT = { TLV: 'TLV', FIXED: '定长帧', MODBUS: 'Modbus' }
const statusText = { ONLINE: '在线', OFFLINE: '离线', IDLE: '空闲', FLYING: '飞行中', MAINTENANCE: '维护' }
const RAW_PLACEHOLDER = {
  hex: 'AA 55 01 02(两位一组,可含 0x 前缀)',
  dec: '170 85 1 2(每字节 0..255)',
  oct: '252 125 1 2(每字节 0..377)',
  bin: '10101010 01010101(每字节 8 位)'
}

const loading = ref(false)
const protocols = ref([])
const devices = ref([])
const dialog = reactive({
  visible: false, saving: false, form: {},
  rules: [], tlv: { tagLen: 1, lenLen: 2, littleEndian: false },
  fixed: [], modbus: { unitId: 1, regMap: [] }
})
const send = reactive({
  visible: false, saving: false, protocol: null, rules: [], values: {},
  mode: 'tlv', deviceId: null, rawBase: 'hex', rawText: '', result: ''
})
const test = reactive({ visible: false, loading: false, protocol: null, base: 'hex', text: '', result: null, error: '' })

onMounted(load)
async function load() {
  loading.value = true
  try { protocols.value = await http.get('/protocols') }
  finally { loading.value = false }
}

function parseJson(json, fallback) {
  try { return JSON.parse(json) ?? fallback } catch (e) { return fallback }
}

/** 表格「解析规则」列:三种帧格式统一拍平为 {tag,field,type,scale,unit,rad} */
function ruleChips(row) {
  if (row.frameFormat === 'FIXED') {
    return (parseJson(row.configJson, {}).fields || []).map((f) => ({
      tag: `+${f.offset}/${f.len}B`, field: f.field, type: f.type, scale: f.scale, unit: f.unit,
      rad: RADIX_TAG[f.radix] || ''
    }))
  }
  if (row.frameFormat === 'MODBUS') {
    return (parseJson(row.configJson, {}).regMap || []).map((m) => ({
      tag: `R${m.reg}${m.count > 1 ? '×' + m.count : ''}`, field: m.field, type: m.type, scale: m.scale, unit: m.unit
    }))
  }
  return parseRules(row.rulesJson).map((r) => ({
    tag: '#' + r.tag, field: r.field, type: r.type, scale: r.scale, unit: r.unit,
    rad: RADIX_TAG[r.radix] || ''
  }))
}

function fmtTagType(fmt) {
  return { TLV: '', FIXED: 'warning', MODBUS: 'success' }[fmt] || 'info'
}

function parseRules(json) {
  try { return JSON.parse(json || '[]') } catch (e) { return [] }
}

function radixDisabled(type) {
  return type === 'string' || type === 'hex' || type === 'float32'
}

function nextTag() {
  const used = dialog.rules.map((r) => r.tag)
  for (let t = 1; t <= 255; t++) if (!used.includes(t)) return t
  return 1
}

function nextOffset() {
  const last = [...dialog.fixed].sort((a, b) => (a.offset + a.len) - (b.offset + b.len)).pop()
  return last ? last.offset + last.len : 0
}

function nextReg() {
  const last = [...dialog.modbus.regMap].sort((a, b) => (a.reg + a.count) - (b.reg + b.count)).pop()
  return last ? last.reg + last.count : 0
}

/** 帧格式切换:MODBUS 强制配 MODBUS_TCP 接入,离开时还原 TCP */
function onFormatChange(fmt) {
  if (fmt === 'MODBUS') {
    if (dialog.form.transport !== 'MODBUS_TCP') dialog.form.transport = 'MODBUS_TCP'
  } else if (dialog.form.transport === 'MODBUS_TCP') {
    dialog.form.transport = fmt === 'FIXED' ? 'RS485' : 'TCP'
  }
}

function openDialog(row) {
  if (row) {
    dialog.form = {
      id: row.id, name: row.name, description: row.description,
      transport: row.transport || 'TCP', frameFormat: row.frameFormat || 'TLV'
    }
    dialog.rules = parseRules(row.rulesJson).map((r) => ({
      tag: r.tag, field: r.field, type: r.type || 'uint16', scale: r.scale ?? 1,
      radix: radixDisabled(r.type) ? 10 : (r.radix ?? 10), unit: r.unit || ''
    }))
    const cfg = parseJson(row.configJson, {})
    dialog.tlv = {
      tagLen: cfg.tagLen || 1, lenLen: cfg.lenLen || 2, littleEndian: !!cfg.littleEndian
    }
    dialog.fixed = (cfg.fields || []).map((f) => ({
      offset: f.offset ?? 0, len: f.len ?? 2, field: f.field || '', type: f.type || 'uint16',
      scale: f.scale ?? 1, radix: radixDisabled(f.type) ? 10 : (f.radix ?? 10), unit: f.unit || ''
    }))
    dialog.modbus = { unitId: cfg.unitId || 1, regMap: (cfg.regMap || []).map((m) => ({
      reg: m.reg ?? 0, count: m.count || 1, field: m.field || '', type: m.type || 'uint16',
      scale: m.scale ?? 1, unit: m.unit || ''
    })) }
  } else {
    dialog.form = { id: null, name: '', description: '', transport: 'TCP', frameFormat: 'TLV' }
    dialog.rules = []
    dialog.tlv = { tagLen: 1, lenLen: 2, littleEndian: false }
    dialog.fixed = []
    dialog.modbus = { unitId: 1, regMap: [] }
  }
  dialog.visible = true
}

async function save() {
  const f = dialog.form
  if (!f.name) return ElMessage.warning('协议名称不能为空')
  let rulesJson = '[]'
  let configJson = '{}'
  if (f.frameFormat === 'TLV') {
    const rules = dialog.rules.filter((r) => r.field)
    const tags = rules.map((r) => r.tag)
    if (new Set(tags).size !== tags.length) return ElMessage.warning('tag 不能重复')
    if (rules.some((r) => !r.tag || !r.field)) return ElMessage.warning('每行需填写 tag 与字段名')
    if (rules.some((r) => r.radix !== 10 && radixDisabled(r.type))) {
      return ElMessage.warning('string / hex / float32 类型不支持非十进制')
    }
    rulesJson = JSON.stringify(rules)
    configJson = JSON.stringify({ ...dialog.tlv })
  } else if (f.frameFormat === 'FIXED') {
    const fields = dialog.fixed.filter((x) => x.field)
    if (!fields.length) return ElMessage.warning('定长帧至少需要一个字段切分')
    if (fields.some((x) => x.offset == null || !x.len || !x.field)) return ElMessage.warning('每行需填写偏移/长度/字段名')
    if (fields.some((x) => x.radix !== 10 && radixDisabled(x.type))) {
      return ElMessage.warning('string / hex / float32 类型不支持非十进制')
    }
    configJson = JSON.stringify({ fields })
  } else {
    const regMap = dialog.modbus.regMap.filter((m) => m.field)
    if (!regMap.length) return ElMessage.warning('Modbus 帧格式需要至少一条寄存器映射')
    if (regMap.some((m) => m.reg == null || !m.count || !m.field)) return ElMessage.warning('每行需填写寄存器/数量/字段名')
    configJson = JSON.stringify({ unitId: dialog.modbus.unitId || 1, regMap })
  }
  dialog.saving = true
  try {
    const body = { ...f, rulesJson, configJson }
    if (f.id) await http.put(`/protocols/${f.id}`, body)
    else await http.post('/protocols', body)
    ElMessage.success('已保存')
    dialog.visible = false
    load()
  } finally { dialog.saving = false }
}

async function remove(id) {
  await http.delete(`/protocols/${id}`)
  ElMessage.success('已删除')
  load()
}

/* ---------- 解析测试 ---------- */

const TEST_PLACEHOLDER = {
  TLV: '01 00 04 00 06E3 28(消息体 TLV 单元流,不含 AA55 帧头/CRC)',
  FIXED: '00 D2 02 58 00 2A 01 A4 01 7C(整段定长帧字节,按 offset/len 切片)',
  MODBUS: '00 01 00 00 00 0F 01 10 00 00 00 04 08 00 AA 02 2C 03 E8 01 C8(完整 MBAP+FC16 报文)'
}

const testPlaceholder = computed(() => TEST_PLACEHOLDER[test.protocol?.frameFormat] || TEST_PLACEHOLDER.TLV)
const testHint = computed(() => {
  const fmt = test.protocol?.frameFormat
  if (fmt === 'MODBUS') return 'Modbus 帧须输入完整 ADU(MBAP 头 + FC16 功能码 + 寄存器数据),服务端剥离头后按 regMap 拆分'
  if (fmt === 'FIXED') return '支持二/八/十/十六进制输入,如 8 进制每字节 0..377;多字节字段大端解析'
  return '支持二/八/十/十六进制输入;按帧头参数(tag 长度 / length 长度 / 端序)逐单元拆分'
})

const testRows = computed(() => {
  const f = test.result?.fields || {}
  return Object.entries(f).map(([field, value]) => ({ field, value: String(value) }))
})

function openTest(row) {
  test.protocol = row
  test.base = 'hex'
  test.text = ''
  test.result = null
  test.error = ''
  test.visible = true
}

async function runParse() {
  if (!test.text.trim()) return ElMessage.warning('请输入原始报文')
  test.loading = true
  test.error = ''
  test.result = null
  try {
    test.result = await http.post(`/protocols/${test.protocol.id}/parse`, { base: test.base, frame: test.text })
    if (!Object.keys(test.result.fields || {}).length) {
      test.error = '未解析出任何字段:请检查报文与帧格式/切分配置是否匹配'
    }
  } catch (e) {
    test.error = e.message || '解析失败'
  } finally {
    test.loading = false
  }
}

/* ---------- 编码发送 ---------- */

const sendDevices = computed(() =>
  [...devices.value].sort((a, b) => Number(b.status === 'ONLINE') - Number(a.status === 'ONLINE'))
)

const rawPlaceholder = computed(() => RAW_PLACEHOLDER[send.rawBase] || '')

/** 当前编码结果(payload 字节 + 四进制预览),出错时给 error 提示 */
const payloadPreview = computed(() => {
  try {
    const bytes = send.mode === 'tlv'
      ? buildTlvPayload(send.rules, send.values)
      : decodeBytes(send.rawText, send.rawBase)
    if (!bytes.length) {
      return { error: '尚无报文内容:填写字段值或原始报文后自动编码' }
    }
    const text = {}
    for (const b of BASES) text[b.value] = encodeBytes(bytes, b.value)
    return { error: '', text, bytes }
  } catch (e) {
    return { error: e.message }
  }
})

async function openSend(row) {
  send.protocol = row
  send.rules = parseRules(row.rulesJson)
  send.values = {}
  send.mode = send.rules.length ? 'tlv' : 'raw'
  send.deviceId = null
  send.rawBase = 'hex'
  send.rawText = ''
  send.result = ''
  send.visible = true
  if (!devices.value.length) {
    devices.value = await http.get('/devices')
  }
}

async function doSend() {
  if (!send.deviceId) return ElMessage.warning('请选择目标设备')
  const { bytes, error } = payloadPreview.value
  if (error || !bytes?.length) return
  send.saving = true
  try {
    const res = await http.post(`/devices/${send.deviceId}/messages`, {
      base: 'hex',
      content: encodeBytes(bytes, 'hex')
    })
    send.result = `已下发 ${res.length}B 整帧: ${res.contentHex}`
    ElMessage.success('指令已通过 Netty 网关下发')
  } finally { send.sending = false }
}
</script>

<style scoped>
.table-panel { height: calc(100% - 50px); padding: 8px; }
.name { font-weight: 600; }
.rule-chips { display: flex; flex-wrap: wrap; gap: 4px; }
.rule-chip .dim { color: #98a2b3; font-size: 11px; margin-left: 3px; }
.rule-chips .dim { color: #98a2b3; }
.rule-chip .rad {
  margin-left: 4px; padding: 0 4px; border-radius: 4px;
  background: #eef4ff; color: #155eef; font-size: 10px; font-weight: 700;
}

.rules-editor { width: 100%; border: 1px solid var(--border); border-radius: 8px; padding: 10px; }
.rules-head, .rule-row { display: flex; gap: 8px; align-items: center; margin-bottom: 8px; }
.rules-head { font-size: 12px; color: #98a2b3; padding: 0 0 4px; }
.rules-tip { font-size: 12px; color: var(--text-dim); margin-top: 8px; line-height: 1.7; }

.tlv-head { display: flex; align-items: center; gap: 10px; }
.tlv-head .th-label { font-size: 13px; color: #344054; }

.field-tip { width: 100%; margin-top: 6px; font-size: 12px; color: var(--text-dim); line-height: 1.7; }

.parse-result { width: 100%; }
.pr-meta {
  display: flex; gap: 16px; align-items: baseline; margin-bottom: 8px;
  font-size: 12.5px; color: #475467;
}
.pr-meta code { font-family: Consolas, monospace; color: #155eef; word-break: break-all; }

.send-rules { width: 100%; display: flex; flex-direction: column; gap: 8px; }
.send-rule { display: flex; align-items: center; gap: 10px; }
.send-rule .sr-tag { width: 140px; flex-shrink: 0; font-size: 13px; color: #344054; font-weight: 600; }
.send-rule .sr-type { width: 90px; flex-shrink: 0; font-size: 12px; color: #98a2b3; text-align: right; }

.preview { width: 100%; border: 1px solid var(--border); border-radius: 8px; padding: 10px 12px; }
.preview-line { display: flex; gap: 10px; align-items: baseline; margin-bottom: 4px; }
.preview-line:last-child { margin-bottom: 0; }
.pl-label { width: 60px; flex-shrink: 0; font-size: 12px; color: #98a2b3; }
.preview code, .result-frame {
  font-family: Consolas, monospace; font-size: 12px; color: #344054;
  word-break: break-all; line-height: 1.7;
}
.preview-err { font-size: 12.5px; color: #f04438; }
.result-frame {
  display: block; width: 100%; padding: 8px 10px; border-radius: 6px;
  background: #ecfdf3; border: 1px solid #d1fadf; color: #12b76a;
}
</style>
