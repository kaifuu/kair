# 无人机低空监管平台 (UAV Supervision Platform)

前后端分离的无人机低空监管系统:**Vue 3 + Spring Boot 3 + 多引擎地图(百度 / 高德 / 天地图可切换)**,科技蓝监管大屏风格,内置飞行模拟引擎,开箱即可体验完整的「任务审批 → 起飞 → 实时态势 → 告警处置」闭环。

## 功能总览

| 模块 | 说明 |
|------|------|
| 实时监控大屏 | 多引擎底图,无人机图标实时位置与航向、航迹线、电子围栏渲染、机巢归航点、在飞列表、实时告警滚动、告警位置涟漪、2D/3D 视角切换 |
| **地图管理** | 全平台底图一键切换 **百度地图 / 高德地图 / 天地图**,坐标系统自动互转(业务数据统一 BD-09 存储),高德/天地图密钥支持页面本机配置 |
| 无人机管理 | 档案 CRUD、状态机(待命/飞行/充电/维保/离线)、飞手绑定、归航点 |
| 飞手管理 | 执照档案 CRUD、有效期管理、飞行时长统计 |
| 飞行任务 | 任务创建、审批流(批准/驳回)、下发起飞、中止、地图选点航线 |
| 电子围栏 | 禁飞区/限飞区/作业区,圆形/多边形,限高配置,启停控制 |
| 告警中心 | 禁飞区闯入、超高、低电量、失联等,分级(紧急/警告),处理闭环 |
| 统计分析 | 近7日趋势、机型分布、告警类型、飞手排行(ECharts) |
| 登录 | 科技蓝大屏登录页,验证码 + JWT 风格 token |

## 技术栈

- **前端** `frontend/` — Vue 3(script setup)· Vite 5 · Element Plus · ECharts · 多引擎地图适配层(vite 代理 `/api` `/ws`)
- **后端** `backend/` — Spring Boot 3.5 · Java 25 · Spring Data JPA · H2 文件库(`backend/data/`)· WebSocket
- **地图适配层** — `src/utils/mapAdapter.js` 统一封装百度 GL / 高德 JS API 2.0 / 天地图 4.0,`src/utils/coord.js` 负责 BD-09 / GCJ-02 / WGS-84 坐标互转;业务代码只面向统一 API 与 BD-09 坐标,切换底图零改动
- **模拟引擎** — 2 秒一 tick:无人机沿航线飞行、电量消耗、卫星数抖动、围栏碰撞检测(射线法/大圆距离)、告警冷却去重(同类 60s)、WS 广播遥测与告警

## 项目结构

```
├── backend/                        # Spring Boot 后端
│   └── src/main/java/com/wrj/platform/
│       ├── controller/             # REST 接口
│       ├── service/                # 业务与飞行模拟引擎
│       ├── entity|repository/      # JPA 实体与仓储
│       └── config/                 # 种子数据 / WebSocket 配置
└── frontend/                       # Vue 3 前端
    └── src/
        ├── views/                  # 页面(监控/任务/围栏/地图管理...)
        ├── utils/
        │   ├── mapAdapter.js       # 统一地图适配层(三引擎)
        │   ├── mapProviders.js     # 提供商注册表 + SDK 按需加载 + 密钥管理
        │   ├── coord.js            # 坐标系互转(BD-09/GCJ-02/WGS-84)
        │   └── map.js              # 图标 SVG 生成 / 百度个性化样式
        └── api/                    # axios 封装
```

## 快速启动

```bash
# 1. 配置地图密钥(必填百度,高德/天地图按需)
cp frontend/.env.example frontend/.env
#   编辑 frontend/.env 填入 VITE_BMAP_AK 等

# 2. 后端(先启动,端口 8180,需 JDK 17+ 与 Maven)
cd backend
mvn spring-boot:run

# 3. 前端(端口 5174)
cd frontend
npm install
npm run dev
```

访问 **http://localhost:5174**,登录账号 **admin / admin123**

## 地图密钥说明

| 提供商 | 申请地址 | 配置方式 |
|--------|----------|----------|
| 百度地图 | https://lbsyun.baidu.com | `.env` 的 `VITE_BMAP_AK`(必配) |
| 高德地图 | https://console.amap.com | `.env` 的 `VITE_AMAP_KEY` + `VITE_AMAP_SEC`,或登录后「地图管理」页本机配置 |
| 天地图 | https://console.tianditu.gov.cn | `.env` 的 `VITE_TDT_KEY`,或「地图管理」页本机配置 |

> 密钥只存在于本地 `.env`(已 gitignore)或浏览器 localStorage,**不会进入代码仓库**。
> 切换底图:登录 → **地图管理** → 点选提供商 → 重新进入地图页面即生效。

## 体验路径

1. 登录 → **实时监控**:地图上可见围栏与机巢,右上角为当前图源标签
2. **飞行任务** → 找到已批准任务 → 「下发起飞」
3. 回到 **实时监控**:无人机图标沿航线移动、航迹渐显、右侧在飞列表实时刷新
4. 任务 1(东城河道巡检)航线穿过核心区禁飞区,约 1 分钟内触发「禁飞区闯入」紧急告警(每 60s 重复提醒),地图告警点涟漪扩散
5. **告警中心** 处理告警;**统计分析** 查看图表;**地图管理** 切换三家底图对比体验

## 主要接口

```
POST /api/auth/login              登录(用户名/密码/验证码)
GET  /api/auth/captcha            SVG 验证码
GET  /api/drones|pilots|tasks|fences    列表
POST /api/tasks/{id}/approve      审批(approved/rejected)
POST /api/tasks/{id}/launch       下发起飞(启动模拟)
POST /api/tasks/{id}/abort        中止
GET  /api/alerts?page&size&unhandled   告警分页
POST /api/alerts/{id}/handle      处理告警
GET  /api/stats/{overview|trend|alert-type|drone-model|pilot-rank}
WS   /ws/telemetry                遥测/告警实时推送 {type, payload, ts}
```
