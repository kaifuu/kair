/** mapProviders.js registry/CRUD 逻辑测试(通过 vite ssrLoadModule 处理 import.meta.env) */
import { createServer } from 'vite'

globalThis.localStorage = {
  _s: {},
  getItem(k) { return this._s[k] ?? null },
  setItem(k, v) { this._s[k] = String(v) },
  removeItem(k) { delete this._s[k] }
}

const server = await createServer({
  root: process.cwd(),
  server: { middlewareMode: true },
  logLevel: 'error'
})

let failed = 0
const ok = (name, cond) => {
  console.log(`${cond ? '✓' : '✗ FAIL'} ${name}`)
  if (!cond) failed++
}

try {
  const m = await server.ssrLoadModule('/src/utils/mapProviders.js')

  // 1. 初始 registry:三家内置,builtIn 标记
  let reg = m.getRegistry()
  ok('初始注册表 3 家内置', reg.length === 3 && reg.every((p) => p.builtIn))

  // 2. 新增自定义 XYZ 提供商
  const saved = m.saveCustomProvider({ name: '内网影像底图', desc: '局域网瓦片', engine: 'tdt', tileUrl: 'https://tiles.lan/{z}/{x}/{y}.png' })
  reg = m.getRegistry()
  ok('新增后注册表 4 家', reg.length === 4)
  ok('自定义项 short 取名称首字且 builtIn=false', saved.short === '内' && saved.builtIn === false)
  ok('自定义项 tags 含引擎标记', reg[3].tags.some((t) => t === '引擎:天地图'))
  ok('getProviderMeta 可查到自定义项', m.getProviderMeta(saved.id)?.name === '内网影像底图')

  // 3. 就绪判断:无 tdt 密钥 → 未就绪;配置后 → 就绪(.env 已预配高德密钥,恒就绪)
  ok('无引擎密钥时未就绪', m.providerKeyReady(saved.id) === false)
  ok('tdt 引擎密钥缺失(.env 未配)未就绪', m.providerKeyReady('tdt') === false)
  m.saveMapKeys({ tdt: 'demo-tk' })
  ok('配 tdt 密钥 + tileUrl 后就绪', m.providerKeyReady(saved.id) === true)
  ok('高德密钥由 .env 预配 → 就绪', m.providerKeyReady('amap') === true)

  // 4. 选中自定义项 + 引擎解析
  m.setProviderId(saved.id)
  ok('getProviderId 返回自定义 id', m.getProviderId() === saved.id)
  ok('resolveEngine 自定义→engine', m.resolveEngine(m.getProviderMeta(saved.id)) === 'tdt')
  ok('resolveEngine 内置→自身', m.resolveEngine(m.getProviderMeta('baidu')) === 'baidu')

  // 5. 更新(按 id)保留 createdAt,数量不变
  const createdAt = saved.createdAt
  m.saveCustomProvider({ id: saved.id, name: '内网影像底图2', engine: 'amap', tileUrl: 'https://tiles.lan/{z}/{x}/{y}.png?key={key}', key: 'kk', grad: 'linear-gradient(135deg,#111111,#222222)' })
  reg = m.getRegistry()
  ok('更新后仍 4 家', reg.length === 4)
  const updated = m.getProviderMeta(saved.id)
  ok('更新生效且 createdAt 保留', updated.name === '内网影像底图2' && updated.createdAt === createdAt)
  ok('amap 引擎密钥已配 → 就绪', m.providerKeyReady(saved.id) === true)

  // 6. 删除使用中的:函数仍可删,但 UI 层先拦截;这里验证删除 + getProviderId 回落
  ok('removeCustomProvider 删除成功', m.removeCustomProvider(saved.id) === true)
  ok('删除后注册表回 3 家', m.getRegistry().length === 3)
  ok('选中项被删后 getProviderId 回落 baidu', m.getProviderId() === 'baidu')
  ok('删除不存在项返回 false', m.removeCustomProvider('custom_nope') === false)

  // 7. 内置覆盖:改名/恢复
  m.saveBuiltinOverride('baidu', { name: '百度底图', desc: '自定义描述' })
  ok('内置覆盖生效', m.getRegistry()[0].name === '百度底图' && m.getRegistry()[0].desc === '自定义描述')
  m.saveBuiltinOverride('baidu', { name: '  ' }) // 空名应被忽略
  ok('空白覆盖被忽略', m.getRegistry()[0].name === '百度底图')
  m.resetBuiltinOverride('baidu')
  ok('恢复默认', m.getRegistry()[0].name === '百度地图')

  // 8. localStorage JSON 损坏兜底
  localStorage.setItem('wrj.map.customProviders', '{bad json')
  ok('损坏 JSON 下注册表回落内置', m.getRegistry().length === 3)
  localStorage.setItem('wrj.map.providerOverrides', '[1,2]')
  ok('overrides 类型异常回落内置', m.getRegistry()[0].name === '百度地图')
  localStorage.setItem('wrj.map.keys', 'not-json')
  ok('keys 损坏返回空对象', typeof m.getMapKeys() === 'object')
} finally {
  await server.close()
}

console.log(failed ? `\n${failed} 个用例失败` : '\n全部通过')
process.exit(failed ? 1 : 0)
