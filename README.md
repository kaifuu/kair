# UAV Low-Altitude Supervision Platform

**English** | [简体中文](README.zh-CN.md)

A full-stack **drone / low-altitude airspace supervision platform** for government-style regulators: real-time map monitoring, geofencing, flight-task approval, alerting, IoT device access — plus an **AI duty assistant** (streaming chat with platform tool-calling, alert assessment, daily situation reports) and **predictive threat perception**.

Built with **Vue 3 + Spring Boot 3 + PostgreSQL/PostGIS**, a **switchable multi-engine map layer** (Baidu / AMap / Tianditu / custom XYZ tiles), a built-in **flight simulator**, and a **Netty device gateway** (binary TLV frames / DTU serial passthrough / Modbus TCP). Out of the box you can experience the full loop: *task approval → take-off → live situation → alert handling*, and *IoT device access → protocol parsing → history*.

---

## Screenshots

| | |
|---|---|
| ![Live Monitoring + AI Copilot](docs/screenshots/monitor-ai.png) | ![Live Monitoring](docs/screenshots/monitor.png) |
| **Live monitoring with the AI duty assistant** — drone marker, track, geofences, right-side panels (alerts / drones / IoT sensors / video), and the drone-styled AI floating ball with a streaming reply | **Live monitoring** — multi-engine basemap, drone icons with heading, tracks, fences, collapsible panels, map toolbox (measure / overview / 3D compass / fullscreen) |
| ![Statistics](docs/screenshots/stats.png) | ![Alert Center](docs/screenshots/alerts.png) |
| **Statistics** — 7-day trends, alert-type distribution, model mix, pilot ranking (ECharts, light-blue gov style) | **Alert center** — paging, severity levels, handling workflow, AI assessment column |
| ![Flight Tasks](docs/screenshots/tasks.png) | ![Geofences](docs/screenshots/fences.png) |
| **Flight tasks** — creation, approval flow, launch / abort, map route picking | **Geofences** — no-fly / restricted / operation zones, circle / line / polygon drawn on the map |
| ![Devices](docs/screenshots/devices.png) | ![Map Providers](docs/screenshots/mapadmin.png) |
| **Device management** — 7 device categories, custom map icons, history curves | **Map provider admin** — server-side key management for all four basemap engines |
| ![Model Config](docs/screenshots/models.png) | ![Login](docs/screenshots/login.png) |
| **LLM model config** — OpenAI-compatible endpoints, key stored server-side (masked in API responses) | **Login** — captcha + token auth |

## Feature Overview

| Module | Highlights |
|--------|-----------|
| Live monitoring | Multi-engine basemap; live drone position/heading, track lines, geofence rendering (circle/line/polygon); right column with **alerts (paged) / drones (online·offline tabs) / IoT sensors / video** — each panel collapsible, whole column collapsible, map fullscreen (Esc to exit); **track replay** with timeline & speed control for every drone (online = live, offline = last 3 days); all devices rendered as icons (custom icons configurable per device); click an icon for info + 60-min history curves; map toolbox: scale, basemap switcher, pan pad, eagle-eye minimap, 3D compass, distance & area measure — consistent across engines, state remembered |
| Map admin | Server-side management of basemap providers: keys/styles/ordering per vendor (Baidu / AMap / Tianditu / custom XYZ), default source, enable/disable |
| Drone management | Profiles CRUD, status machine (standby/flying/charging/maintenance/offline), pilot binding, home point |
| Pilot management | License profiles, expiry tracking, flight-hour stats |
| Flight tasks | Creation, approval flow (approve/reject), launch, abort, route picking on map |
| Geofences | No-fly / restricted / operation zones; **circle / line / polygon drawn interactively on a fullscreen map** (click to add, move preview, double-click to finish; center+radius for circles; undo/clear), existing fences faintly shown for reference — no vendor DrawingManager, identical across all four engines; stored as **WGS-84 (4326) geometry** (Point/LineString/Polygon) with GiST index, BD-09 conversion at the API layer; point-in-fence API |
| Protocol admin | Device protocol CRUD + **parse testing**: TLV (configurable length/endianness), fixed-offset slices, Modbus register mapping; values in bin/oct/dec/hex with sign/scale/endianness conversion |
| Device access | 7 device categories (drone/nest/camera/weather/ADS-B/gateway/sensor), **custom map icons** (PNG/SVG upload); virtual devices fed by the built-in simulator, real devices via the **Netty gateway** (3 ports, see below); data persisted + pushed over WebSocket; flight telemetry stored every 4 s |
| Video | HLS (m3u8) live playback for camera devices: server-side `/api/video/proxy` follows 302 temp tokens and rewrites playlist segment URLs (solves CORS); hls.js decode with native-HLS fallback; 4 public demo streams seeded |
| Alerts | No-fly breach, altitude exceed, low battery, signal loss, unlicensed flight, overdue task **+ predictive/threat types below**; severity levels; handling workflow; paging |
| Statistics | 7-day trends, model mix, alert types, pilot ranking (ECharts) |
| **AI assistant** | See next section |
| Auth & admin | Captcha login, Bearer token, operation-log aspect; users/roles/menus/orgs/tenants/logs admin pages |

## AI Capabilities

LLM features are powered by any **OpenAI-compatible chat API** (developed & tested with Qwen via DashScope compatible-mode). The API key is configured on the **Model Config** page and stored in the database only — it never enters the repo, and is masked (`******`) in API responses.

| Feature | Description |
|---------|-------------|
| **AI duty copilot** | A drone-styled floating ball on the monitoring page opens a draggable/resizable chat window. Replies **stream token-by-token (SSE)** and the model can call **6 platform tools** (`get_overview`, `list_flying`, `list_alerts`, `list_tasks`, `get_device`, `list_fences`) over multi-round function calling — answers are grounded in live platform data, with tool status shown while querying |
| **Alert AI assessment** | Each new alert is asynchronously assessed after commit (or on demand from the alert page); risk analysis + handling advice is written back to the alert record |
| **Daily situation report** | Cron-generated (default 07:36) 24 h situation report in markdown, pushed to in-app messages; can also be generated manually |
| **Predictive threat perception** | Traditional algorithms (LLM never in the real-time loop): 60 s trajectory extrapolation → **PREDICTED_BREACH** with ETA; CPA/DCPA/TCPA math → **CONFLICT_ALERT** between drones; anomaly windows → **BATTERY_ANOMALY / ALTITUDE_JUMP / SIGNAL_WEAK**. All thresholds configurable in `application.yml` |

```yaml
# backend/src/main/resources/application.yml
ai:
  enabled: true
  alert-assess: true            # async AI assessment on every new alert
  report-cron: "0 36 7 * * *"   # daily situation report
threat:
  horizon-seconds: 60           # trajectory extrapolation horizon
  conflict-dcpa-meters: 100     # closest-point-of-approach threshold
  conflict-tcpa-seconds: 60
  battery-drop-percent: 15      # within battery-window-seconds
  altitude-jump-meters: 40
  min-satellites: 6
```

## Tech Stack & Architecture

- **Frontend** `frontend/` — Vue 3 (script setup) · Vite 5 · Element Plus · ECharts · multi-engine map adapter (dev proxy for `/api` `/ws`)
- **Backend** `backend/` — Spring Boot 3.5 · Java 17+ · Spring Data JPA · **PostgreSQL 16 + PostGIS 3.4 (Docker)** · hibernate-spatial (JTS) · WebSocket (telemetry/alert push) · **Netty TCP gateway** · SSE streaming
- **Map adapter** — `mapAdapter.js` unifies Baidu GL / AMap 2.0 / Tianditu 4.0 / custom XYZ tiles; `coord.js` handles BD-09 / GCJ-02 / WGS-84 conversion. Business code targets one API in BD-09 — switching basemaps requires zero changes
- **Flight simulator** — 2 s tick: drones fly routes, battery drain, satellite jitter, fence collision detection (ray casting / great-circle distance), alert cooldown dedup (60 s per type), WS broadcast; separate IoT simulator (5 s tick) feeds virtual nests/cameras/weather stations/ADS-B/gateways/sensors

```
┌─ Vue 3 SPA ───────────────────────────────────────────────┐
│  Monitor / Tasks / Fences / Alerts / Stats / Admin pages  │
│  Map adapter (Baidu·AMap·Tianditu·XYZ)   AI copilot ball  │
└──────┬───────────────┬────────────────┬───────────────────┘
       │ REST /api     │ WS /ws         │ SSE /api/ai/*
┌──────▼───────────────▼────────────────▼───────────────────┐
│                Spring Boot 3 (port 8180)                   │
│  Auth (captcha+token) · Tasks · Fences · Alerts · Stats    │
│  Device/Protocol admin · Video proxy · Map provider admin  │
│  AiAssistantService ── LlmService ──► OpenAI-compatible API│
│  ThreatService (prediction/conflict/anomaly, ms-level)     │
│  FlightSimulator / IoT simulator (virtual devices)         │
│  Netty gateway: 9527 TLV · 9528 DTU · 9529 Modbus TCP      │
└──────┬─────────────────────────────────────────────────────┘
       │ JPA / hibernate-spatial
┌──────▼─────────────────────────────────────────────────────┐
│         PostgreSQL 16 + PostGIS 3.4 (geometry, GiST)       │
└────────────────────────────────────────────────────────────┘
```

## Quick Start

```bash
# 1. PostgreSQL + PostGIS (must be ready first; tables & seed data auto-created)
docker run -d --name wrj-postgres \
  -e POSTGRES_DB=wrj -e POSTGRES_USER=wrj -e POSTGRES_PASSWORD=wrj123 \
  -p 5432:5432 postgis/postgis:16-3.4

# 2. Backend (port 8180; JDK 17+ & Maven; gateway ports 9527/9528/9529)
cd backend
mvn spring-boot:run

# 3. Frontend (port 5174)
cd frontend
npm install
npm run dev
```

Open **http://localhost:5174** and log in with **admin / admin123**.

> Map keys: recommended to configure on the **Map Admin** page after login (persisted server-side).
> Fallback env vars for the backend: `MAP_BAIDU_AK / MAP_AMAP_KEY / MAP_AMAP_SEC / MAP_TDT_KEY`.
> LLM: configure on the **Model Config** page (base URL / model code / API key). No key, no problem — everything except the AI features works without it.

### Map provider keys

| Provider | Apply at | Configure |
|----------|----------|-----------|
| Baidu Map | https://lbsyun.baidu.com | Map Admin → edit vendor, paste AK (default source) |
| AMap | https://console.amap.com | Map Admin → KEY + security secret |
| Tianditu | https://console.tianditu.gov.cn | Map Admin → TK |
| Custom tiles | your own tile server | Map Admin → add CUSTOM vendor with URL template + engine |

Keys live in the `sys_map_provider` table and browser localStorage fallback — **never in the repo**.

## Device Gateway (Netty)

| Port | Channel | Use case |
|------|---------|----------|
| **9527** | Standard AA55 magic + CRC16 + **TLV** binary frames | Drones, nests and other standard devices |
| **9528** | DTU serial passthrough (RS232/RS485), `REG:/PING:/DATA:<radix>:` text lines | Serial sensors; payload in bin/oct/dec/hex |
| **9529** | **Modbus TCP** (MBAP + FC03/04/10) | PLCs etc.; FC16 writes persist immediately |

All three channels parse payloads through the protocol rules configured in **Protocol Admin** (TLV / fixed-offset / register mapping), persist data (last 2 000 rows per device) and push over WS.

### Device simulator (backend/scripts/)

```bash
# Standard frames → 9527 (drone telemetry TLV)
node device-simulator.mjs --code UAV-2024-0002 --secret secret-0002 --protocol drone

# DTU serial passthrough → 9528 (RS485 micro weather station, fixed-frame hex)
node device-simulator.mjs --mode transparent --code AQ-0001 --secret secret-aq01

# Modbus TCP PLC → 9529 (FC16 cyclic write to registers 0-3)
node device-simulator.mjs --mode modbus --unit 1
```

Options: `--interval ms` (report period, default 3000), `--host`, `--port`.

## API Overview

```
POST /api/auth/login                     login (username/password/captcha)
GET  /api/auth/captcha                   SVG captcha
GET  /api/drones|pilots|tasks|devices|protocols        lists
POST /api/tasks/{id}/approve             approval (approved/rejected)
POST /api/tasks/{id}/launch              launch (starts simulation)
POST /api/tasks/{id}/abort               abort
GET  /api/alerts?page&size&unhandled     alert paging
POST /api/alerts/{id}/handle             handle alert
GET  /api/stats/{overview|trend|alert-type|drone-model|pilot-rank}
GET|POST|PUT|DELETE /api/map-providers   basemap provider admin
PUT  /api/map-providers/{id}/default     set default source
GET  /api/fences/contains?lng&lat        point-in-fence check (WGS-84)
GET  /api/devices/{id}/history?minutes&limit   device history (for replay)
GET  /api/devices/latest-data            latest frame of every device
GET  /api/video/proxy?url=<m3u8>         HLS proxy (redirect+rewrite)
POST /api/protocols/{id}/parse           protocol parse test
POST /api/ai/copilot                     AI copilot (blocking)
POST /api/ai/copilot/stream              AI copilot (SSE streaming)
POST /api/ai/alert/{id}/assess           on-demand alert assessment
POST /api/ai/report/generate             generate daily situation report
WS   /ws/telemetry                       live push {type, payload, ts}
                                         # telemetry | alert | deviceData | deviceStatus
```

## Project Structure

```
├── backend/                        # Spring Boot backend
│   ├── scripts/                    # zero-dependency tools
│   │   ├── device-simulator.mjs    # device access simulator (3 modes)
│   │   ├── probe-register.mjs      # registration probe
│   │   └── smoke-test.sh           # smoke test (login → APIs → WS)
│   ├── sql/init/                   # PostGIS extensions bootstrap
│   └── src/main/java/com/wrj/platform/
│       ├── controller/             # REST (devices/protocols/map/fences/alerts/ai/...)
│       ├── service/                # business logic, flight simulator, protocol
│       │                           # parsing, AiAssistant/Llm/Threat services
│       ├── gateway/                # Netty gateway (ports 9527/9528/9529)
│       ├── entity|repository/      # JPA entities (PostGIS geometry in fences)
│       └── config/                 # seed data, WebSocket, schema init
└── frontend/                       # Vue 3 frontend
    └── src/
        ├── views/                  # pages (monitor/tasks/fences/alerts/stats/...)
        ├── components/             # monitor panels, AI copilot ball, ...
        ├── utils/
        │   ├── mapAdapter.js       # unified map adapter (4 engines)
        │   ├── mapProviders.js     # provider registry + SDK lazy-load
        │   ├── coord.js            # BD-09/GCJ-02/WGS-84 conversion
        │   └── map.js              # device icon SVG generation
        └── api/                    # axios wrapper
```

## Demo Walkthrough

1. Log in → **Live Monitoring**: fences, nests and online device icons on the map; click icons for details & history curves; collapse panels / the whole right column; fullscreen the map (Esc to exit)
2. **Flight Tasks** → find an approved task → **Launch** (demo drones 1/2 are online & flyable)
3. Back to **Live Monitoring**: the drone icon moves along its route, track fades in; open the **AI floating ball** (bottom-right) and ask “当前态势如何” — the copilot queries live data and streams an answer; replay any drone from the drones panel
4. Task 1 (river inspection) crosses a core no-fly zone — expect a **critical breach alert** within ~1 min (re-reminded every 60 s) plus a **PREDICTED_BREACH** warning ahead of it; the alert gets an AI assessment within seconds
5. **Protocol Admin** → “parse test” any protocol: paste a bin/oct/hex payload and see the decoded fields
6. **Device Management** → 7 IoT categories; run the simulator scripts above to experience real TCP access
7. Handle alerts in **Alert Center**; browse charts in **Statistics**; switch between the four basemaps in **Map Admin**
