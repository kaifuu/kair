package com.wrj.platform.config;

import com.wrj.platform.entity.*;
import com.wrj.platform.repository.*;
import com.wrj.platform.service.GeoUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** 首次启动初始化演示数据(北京场景):RBAC + 协议 + 设备 + 飞手/围栏/任务/告警 */
@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final PilotRepository pilotRepository;
    private final FlightTaskRepository taskRepository;
    private final GeoFenceRepository fenceRepository;
    private final AlertRepository alertRepository;
    private final DeviceRepository deviceRepository;
    private final ProtocolRepository protocolRepository;
    private final DeviceDataHistoryRepository historyRepository;
    private final SysUserRepository userRepository;
    private final SysRoleRepository roleRepository;
    private final SysMenuRepository menuRepository;
    private final SysOrgRepository orgRepository;
    private final SysTenantRepository tenantRepository;
    private final SysMapProviderRepository mapProviderRepository;
    private final MsgChannelRepository msgChannelRepository;
    private final LlmModelRepository llmModelRepository;

    private final String baiduAk;
    private final String amapKey;
    private final String amapSec;
    private final String tdtKey;

    public DataSeeder(PilotRepository pilotRepository, FlightTaskRepository taskRepository,
                      GeoFenceRepository fenceRepository, AlertRepository alertRepository,
                      DeviceRepository deviceRepository, ProtocolRepository protocolRepository,
                      DeviceDataHistoryRepository historyRepository,
                      SysUserRepository userRepository, SysRoleRepository roleRepository,
                      SysMenuRepository menuRepository, SysOrgRepository orgRepository,
                      SysTenantRepository tenantRepository, SysMapProviderRepository mapProviderRepository,
                      MsgChannelRepository msgChannelRepository, LlmModelRepository llmModelRepository,
                      @Value("${map-keys.baidu-ak:}") String baiduAk,
                      @Value("${map-keys.amap-key:}") String amapKey,
                      @Value("${map-keys.amap-sec:}") String amapSec,
                      @Value("${map-keys.tdt-key:}") String tdtKey) {
        this.pilotRepository = pilotRepository;
        this.taskRepository = taskRepository;
        this.fenceRepository = fenceRepository;
        this.alertRepository = alertRepository;
        this.deviceRepository = deviceRepository;
        this.protocolRepository = protocolRepository;
        this.historyRepository = historyRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.menuRepository = menuRepository;
        this.orgRepository = orgRepository;
        this.tenantRepository = tenantRepository;
        this.mapProviderRepository = mapProviderRepository;
        this.msgChannelRepository = msgChannelRepository;
        this.llmModelRepository = llmModelRepository;
        this.baiduAk = baiduAk;
        this.amapKey = amapKey;
        this.amapSec = amapSec;
        this.tdtKey = tdtKey;
    }

    @Override
    @Transactional
    public void run(String... args) {
        seedMapProviders();    // 幂等:存量库也补底图厂商配置
        seedMsgChannels();     // 幂等:存量库也补消息通道
        seedLlmModels();       // 幂等:存量库也补大模型预设
        if (userRepository.count() > 0) {
            ensureMenus();    // 幂等:存量库也修菜单(路径纠偏/补新菜单)
            log.info("Data already seeded, skip");
            return;
        }
        log.info("Seeding demo data ...");
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

        // ---------- 租户 ----------
        SysTenant tenant = new SysTenant();
        tenant.setName("默认示范租户");
        tenant.setCode("DEFAULT");
        tenant.setRemark("演示用内置租户");
        tenantRepository.save(tenant);

        // ---------- 组织 ----------
        SysOrg root = new SysOrg();
        root.setName("市低空监管局");
        root.setOrgCode("WRJ"); root.setSort(1);
        orgRepository.save(root);

        SysOrg orgFly = childOrg("飞行监管部", "WRJ-01", root.getId(), 1);
        SysOrg orgDev = childOrg("设备管理部", "WRJ-02", root.getId(), 2);
        SysOrg orgGen = childOrg("综合事务部", "WRJ-03", root.getId(), 3);
        orgRepository.saveAll(List.of(orgFly, orgDev, orgGen));

        // ---------- 菜单(BIZ 8 + SYS 8) ----------
        List<SysMenu> menus = new ArrayList<>(List.of(
                new SysMenu("实时监控", "/monitor", "Monitor", SysMenu.Group.BIZ, 1),
                new SysMenu("设备管理", "/devices", "Cpu", SysMenu.Group.BIZ, 2),
                new SysMenu("飞手管理", "/pilots", "User", SysMenu.Group.BIZ, 3),
                new SysMenu("飞行任务", "/tasks", "Aim", SysMenu.Group.BIZ, 4),
                new SysMenu("电子围栏", "/fences", "Location", SysMenu.Group.BIZ, 5),
                new SysMenu("告警中心", "/alerts", "Bell", SysMenu.Group.BIZ, 6),
                new SysMenu("统计分析", "/stats", "TrendCharts", SysMenu.Group.BIZ, 7),
                new SysMenu("地图管理", "/mapadmin", "MapLocation", SysMenu.Group.BIZ, 8),
                new SysMenu("协议管理", "/protocols", "Connection", SysMenu.Group.SYS, 1),
                new SysMenu("报文管理", "/messages", "ChatLineRound", SysMenu.Group.SYS, 2),
                new SysMenu("人员管理", "/sys/users", "UserFilled", SysMenu.Group.SYS, 3),
                new SysMenu("角色管理", "/sys/roles", "Key", SysMenu.Group.SYS, 4),
                new SysMenu("菜单管理", "/sys/menus", "Menu", SysMenu.Group.SYS, 5),
                new SysMenu("组织管理", "/sys/orgs", "OfficeBuilding", SysMenu.Group.SYS, 6),
                new SysMenu("租户管理", "/sys/tenants", "Files", SysMenu.Group.SYS, 7),
                new SysMenu("日志管理", "/sys/logs", "Document", SysMenu.Group.SYS, 8),
                new SysMenu("消息管理", "/msgadmin", "Promotion", SysMenu.Group.SYS, 9),
                new SysMenu("模型配置", "/models", "Cpu", SysMenu.Group.SYS, 10)
        ));
        menuRepository.saveAll(menus);

        // ---------- 角色 ----------
        SysRole adminRole = new SysRole();
        adminRole.setName("系统管理员"); adminRole.setCode("ADMIN");
        adminRole.setRemark("拥有全部菜单权限");
        adminRole.setMenuIdsJson(menuIds(menus, m -> true));
        roleRepository.save(adminRole);

        SysRole opRole = new SysRole();
        opRole.setName("业务操作员"); opRole.setCode("OPERATOR");
        opRole.setRemark("仅业务菜单,无系统管理权限");
        opRole.setMenuIdsJson(menuIds(menus, m -> m.getGroup() == SysMenu.Group.BIZ));
        roleRepository.save(opRole);

        // ---------- 用户 ----------
        SysUser admin = new SysUser();
        admin.setUsername("admin"); admin.setPassword(encoder.encode("admin123"));
        admin.setNickname("系统管理员"); admin.setPhone("13800000001");
        admin.setRole(adminRole); admin.setOrgId(root.getId()); admin.setTenantId(tenant.getId());
        userRepository.save(admin);

        SysUser operator = new SysUser();
        operator.setUsername("operator"); operator.setPassword(encoder.encode("operator123"));
        operator.setNickname("运维操作员"); operator.setPhone("13800000002");
        operator.setRole(opRole); operator.setOrgId(orgFly.getId()); operator.setTenantId(tenant.getId());
        userRepository.save(operator);

        // ---------- TLV 协议模板 ----------
        ProtocolTemplate droneProto = new ProtocolTemplate();
        droneProto.setName("无人机标准 TLV 协议");
        droneProto.setDescription("经纬度 uint32×1e-6,高度/速度/航向 uint16×0.1,电量/卫星 uint8");
        droneProto.setRulesJson("[{\"tag\":1,\"field\":\"lng\",\"type\":\"uint32\",\"scale\":1.0E-6,\"unit\":\"deg\"},"
                + "{\"tag\":2,\"field\":\"lat\",\"type\":\"uint32\",\"scale\":1.0E-6,\"unit\":\"deg\"},"
                + "{\"tag\":3,\"field\":\"altitude\",\"type\":\"uint16\",\"scale\":0.1,\"unit\":\"m\"},"
                + "{\"tag\":4,\"field\":\"battery\",\"type\":\"uint8\",\"scale\":1,\"unit\":\"%\"},"
                + "{\"tag\":5,\"field\":\"speed\",\"type\":\"uint16\",\"scale\":0.1,\"unit\":\"m/s\"},"
                + "{\"tag\":6,\"field\":\"heading\",\"type\":\"uint16\",\"scale\":0.1,\"unit\":\"deg\"},"
                + "{\"tag\":7,\"field\":\"satellites\",\"type\":\"uint8\",\"scale\":1,\"unit\":\"颗\"}]");
        protocolRepository.save(droneProto);

        ProtocolTemplate weatherProto = new ProtocolTemplate();
        weatherProto.setName("气象站 TLV 协议");
        weatherProto.setDescription("温度 int16×0.1,湿度/风速/气压 uint16×0.1");
        weatherProto.setRulesJson("[{\"tag\":1,\"field\":\"temperature\",\"type\":\"int16\",\"scale\":0.1,\"unit\":\"℃\"},"
                + "{\"tag\":2,\"field\":\"humidity\",\"type\":\"uint16\",\"scale\":0.1,\"unit\":\"%RH\"},"
                + "{\"tag\":3,\"field\":\"windSpeed\",\"type\":\"uint16\",\"scale\":0.1,\"unit\":\"m/s\"},"
                + "{\"tag\":4,\"field\":\"pressure\",\"type\":\"uint16\",\"scale\":0.1,\"unit\":\"hPa\"}]");
        protocolRepository.save(weatherProto);

        // RS232/RS485 串口设备经 DTU 透传的定长帧协议(环境微站,10 字节按偏移切分)
        ProtocolTemplate rs485Proto = new ProtocolTemplate();
        rs485Proto.setName("串口透传定长帧-环境微站");
        rs485Proto.setTransport(ProtocolTemplate.Transport.RS485);
        rs485Proto.setFrameFormat(ProtocolTemplate.FrameFormat.FIXED);
        rs485Proto.setDescription("RS232/RS485 串口经 DTU 透传接入(网关 9528),10 字节定长帧:温度/湿度 int16×0.1,"
                + "PM2.5/CO2 uint16,噪声 uint16×0.1,大端字节序");
        rs485Proto.setConfigJson("{\"fields\":["
                + "{\"offset\":0,\"len\":2,\"field\":\"temperature\",\"type\":\"int16\",\"scale\":0.1,\"unit\":\"℃\"},"
                + "{\"offset\":2,\"len\":2,\"field\":\"humidity\",\"type\":\"uint16\",\"scale\":0.1,\"unit\":\"%RH\"},"
                + "{\"offset\":4,\"len\":2,\"field\":\"pm25\",\"type\":\"uint16\",\"scale\":1,\"unit\":\"μg/m³\"},"
                + "{\"offset\":6,\"len\":2,\"field\":\"co2\",\"type\":\"uint16\",\"scale\":1,\"unit\":\"ppm\"},"
                + "{\"offset\":8,\"len\":2,\"field\":\"noiseDb\",\"type\":\"uint16\",\"scale\":0.1,\"unit\":\"dB\"}]}");
        protocolRepository.save(rs485Proto);

        // PLC/RTU 的 Modbus TCP 寄存器映射协议(网关 9529,FC16 写保持寄存器)
        ProtocolTemplate modbusProto = new ProtocolTemplate();
        modbusProto.setName("Modbus TCP-PLC 数据采集");
        modbusProto.setTransport(ProtocolTemplate.Transport.MODBUS_TCP);
        modbusProto.setFrameFormat(ProtocolTemplate.FrameFormat.MODBUS);
        modbusProto.setDescription("PLC/RTU 经 Modbus TCP 接入(网关 9529),FC16 写保持寄存器 0-3 "
                + "映射温度/湿度/压力/流量,FC3/4 可回读寄存器缓存");
        modbusProto.setConfigJson("{\"unitId\":1,\"regMap\":["
                + "{\"reg\":0,\"count\":1,\"field\":\"temperature\",\"type\":\"int16\",\"scale\":0.1,\"unit\":\"℃\"},"
                + "{\"reg\":1,\"count\":1,\"field\":\"humidity\",\"type\":\"uint16\",\"scale\":0.1,\"unit\":\"%RH\"},"
                + "{\"reg\":2,\"count\":1,\"field\":\"pressure\",\"type\":\"uint16\",\"scale\":0.1,\"unit\":\"kPa\"},"
                + "{\"reg\":3,\"count\":1,\"field\":\"flowRate\",\"type\":\"uint16\",\"scale\":0.1,\"unit\":\"m³/h\"}]}");
        protocolRepository.save(modbusProto);

        // ---------- 飞手 ----------
        Pilot p1 = pilotRepository.save(new Pilot("UAS-110245", "张伟", "13800138001", "市局警务航空队"));
        p1.setLicenseType("多旋翼"); p1.setLicenseGrade(3); p1.setTotalFlightHours(326.5); p1.setTotalFlights(210);
        p1.setLicenseIssue(LocalDate.of(2021, 6, 15)); p1.setLicenseExpiry(LocalDate.of(2027, 6, 14));

        Pilot p2 = pilotRepository.save(new Pilot("UAS-110378", "李娜", "13800138002", "城管巡查大队"));
        p2.setLicenseType("多旋翼"); p2.setLicenseGrade(4); p2.setTotalFlightHours(198.0); p2.setTotalFlights(156);
        p2.setLicenseIssue(LocalDate.of(2022, 3, 10)); p2.setLicenseExpiry(LocalDate.of(2026, 3, 9));

        Pilot p3 = pilotRepository.save(new Pilot("UAS-110512", "王强", "13800138003", "测绘勘察院"));
        p3.setLicenseType("垂直起降固定翼"); p3.setLicenseGrade(3); p3.setTotalFlightHours(512.0); p3.setTotalFlights(98);
        p3.setLicenseIssue(LocalDate.of(2020, 9, 1)); p3.setLicenseExpiry(LocalDate.of(2026, 8, 31));

        Pilot p4 = pilotRepository.save(new Pilot("UAS-110633", "赵敏", "13800138004", "电力运检中心"));
        p4.setLicenseType("多旋翼"); p4.setLicenseGrade(4); p4.setTotalFlightHours(87.5); p4.setTotalFlights(73);
        p4.setLicenseIssue(LocalDate.of(2023, 1, 20)); p4.setLicenseExpiry(LocalDate.of(2029, 1, 19));

        Pilot p5 = pilotRepository.save(new Pilot("UAS-110780", "刘洋", "13800138005", "应急管理局"));
        p5.setLicenseType("多旋翼"); p5.setLicenseGrade(4); p5.setTotalFlightHours(64.0); p5.setTotalFlights(45);
        p5.setLicenseIssue(LocalDate.of(2024, 5, 5)); p5.setLicenseExpiry(LocalDate.of(2028, 5, 4));

        pilotRepository.saveAll(List.of(p1, p2, p3, p4, p5));

        // ---------- 无人机(category=DRONE,virtual=true 走模拟器) ----------
        Device d1 = drone("UAV-2024-0001", "1号巡检机", "DJI M350 RTK", "大疆创新", "巡检",
                116.397128, 39.916527, p1, droneProto, "secret-0001");
        d1.setMaxAltitude(500.0); d1.setMaxEndurance(55.0);
        d1.setTotalFlightHours(210.5); d1.setPurchaseDate(LocalDateTime.of(2024, 3, 15, 0, 0));

        Device d2 = drone("UAV-2024-0002", "2号航拍机", "DJI Mavic 3E", "大疆创新", "航拍",
                116.407526, 39.904030, p2, droneProto, "secret-0002");
        d2.setMaxAltitude(500.0); d2.setMaxEndurance(45.0);
        d2.setTotalFlightHours(156.0); d2.setPurchaseDate(LocalDateTime.of(2024, 5, 20, 0, 0));

        Device d3 = drone("UAV-2023-0003", "3号测绘机", "CW-15 垂起固定翼", "纵横股份", "测绘",
                116.480, 39.910, p3, droneProto, "secret-0003");
        d3.setMaxAltitude(1000.0); d3.setMaxEndurance(180.0);
        d3.setTotalFlightHours(98.0); d3.setPurchaseDate(LocalDateTime.of(2023, 11, 2, 0, 0));
        d3.setStatus(Device.Status.OFFLINE);   // 离线:仅可回放历史轨迹

        Device d4 = drone("UAV-2024-0004", "4号巡检机", "DJI Matrice 30T", "大疆创新", "巡检",
                116.35, 39.95, p4, droneProto, "secret-0004");
        d4.setMaxAltitude(500.0); d4.setMaxEndurance(41.0);
        d4.setTotalFlightHours(73.0); d4.setPurchaseDate(LocalDateTime.of(2024, 8, 8, 0, 0));
        d4.setStatus(Device.Status.OFFLINE);   // 离线:仅可回放历史轨迹

        // 在线 2 架(d1/d2)+ 离线 2 架(d3/d4),均带历史轨迹回放数据
        deviceRepository.saveAll(List.of(d1, d2, d3, d4));

        // ---------- 物联网设备(virtual=true 由 IoTSimulator 驱动,home 坐标用于地图 ICON) ----------
        // 演示规模收敛:2 气象站 + 3 摄像头 + 2 环境传感,共 7 台 + 4 架无人机
        Device ws1 = iot("WS-0001", "亦庄气象站", Device.Category.WEATHER, "FT-780", "富奥通",
                116.505, 39.795, "地面气象监测");
        ws1.setProtocol(weatherProto); ws1.setSecret("secret-ws01"); ws1.setOrgId(orgDev.getId());
        deviceRepository.save(ws1);

        Device ws2 = iot("WS-0002", "通州气象站", Device.Category.WEATHER, "FT-780", "富奥通",
                116.658, 39.909, "地面气象监测");
        ws2.setProtocol(weatherProto); ws2.setSecret("secret-ws02"); ws2.setOrgId(orgDev.getId());
        deviceRepository.save(ws2);

        // 摄像头接入央视网熊猫频道公开慢直播(ipanda 真机位 24h 直播,免鉴权,经 /api/video/proxy 代理播放)
        Device cam = iot("CAM-0001", "熊猫基地·1号机位摄像头", Device.Category.CAMERA, "POE-HD", "央视网慢直播",
                104.146, 30.739, "熊猫基地公开慢直播接入");
        cam.setVideoUrl("https://gcwbndcnchw.v.cdn20.com/gcwbnd/xiongmao01_2/index.m3u8");
        deviceRepository.save(cam);

        Device cam2 = iot("CAM-0002", "熊猫基地·10号机位摄像头", Device.Category.CAMERA, "POE-HD", "央视网慢直播",
                104.142, 30.736, "熊猫基地公开慢直播接入");
        cam2.setVideoUrl("https://gcwbndcnchw.v.cdn20.com/gcwbnd/xiongmao10_2/index.m3u8");
        deviceRepository.save(cam2);

        // 环境传感微站(SENSOR):RS485 定长帧协议,AQ-0001 可用 DTU 模拟器实联
        Device ns = iot("NS-0001", "什刹海噪声监测仪", Device.Category.SENSOR, "NS-360", "声学感知",
                116.383, 39.940, "核心区噪声监测");
        deviceRepository.save(ns);

        Device aq = iot("AQ-0001", "亦庄空气质量微站", Device.Category.SENSOR, "AQ-M6", "先河环保",
                116.515, 39.805, "六参数空气质量监测");
        aq.setProtocol(rs485Proto); aq.setSecret("secret-aq01"); aq.setOrgId(orgDev.getId());
        deviceRepository.save(aq);

        // ---------- 电子围栏 ----------
        GeoFence f1 = new GeoFence();
        f1.setName("首都功能核心区禁飞区");
        f1.setType(GeoFence.Type.NO_FLY); f1.setShape(GeoFence.Shape.CIRCLE);
        f1.setPointsJson("[{\"lng\":116.397,\"lat\":39.910}]");
        f1.setRadius(6000.0); f1.setMaxAltitude(0.0);
        f1.setEnabled(true); f1.setRemark("东城/西城核心区,全天候禁飞");
        fenceRepository.save(f1);

        GeoFence f2 = new GeoFence();
        f2.setName("首都机场净空保护区");
        f2.setType(GeoFence.Type.NO_FLY); f2.setShape(GeoFence.Shape.CIRCLE);
        f2.setPointsJson("[{\"lng\":116.603,\"lat\":40.080}]");
        f2.setRadius(10000.0); f2.setMaxAltitude(0.0);
        f2.setEnabled(true); f2.setRemark("机场净空区,严禁无人机飞行");
        fenceRepository.save(f2);

        GeoFence f3 = new GeoFence();
        f3.setName("海淀五环外限飞区");
        f3.setType(GeoFence.Type.LIMIT); f3.setShape(GeoFence.Shape.POLYGON);
        f3.setPointsJson("[{\"lng\":116.28,\"lat\":40.00},{\"lng\":116.42,\"lat\":40.00},"
                + "{\"lng\":116.42,\"lat\":39.96},{\"lng\":116.28,\"lat\":39.96}]");
        f3.setMaxAltitude(120.0);
        f3.setEnabled(true); f3.setRemark("限高 120m,需报备");
        fenceRepository.save(f3);

        GeoFence f4 = new GeoFence();
        f4.setName("亦庄作业示范区");
        f4.setType(GeoFence.Type.WORK); f4.setShape(GeoFence.Shape.POLYGON);
        f4.setPointsJson("[{\"lng\":116.48,\"lat\":39.82},{\"lng\":116.56,\"lat\":39.82},"
                + "{\"lng\":116.56,\"lat\":39.76},{\"lng\":116.48,\"lat\":39.76}]");
        f4.setMaxAltitude(300.0);
        f4.setEnabled(true); f4.setRemark("低空经济示范区,物流配送作业区");
        fenceRepository.save(f4);

        GeoFence f5 = new GeoFence();
        f5.setName("京沪高速物流走廊");
        f5.setType(GeoFence.Type.WORK); f5.setShape(GeoFence.Shape.LINE);
        f5.setPointsJson("[{\"lng\":116.48,\"lat\":39.79},{\"lng\":116.58,\"lat\":39.80},{\"lng\":116.68,\"lat\":39.81}]");
        f5.setRadius(500.0); f5.setMaxAltitude(120.0);
        f5.setEnabled(true); f5.setRemark("线状航线缓冲走廊,半径 500m,限高 120m");
        fenceRepository.save(f5);

        // ---------- 飞行任务 ----------
        FlightTask t1 = new FlightTask();
        t1.setName("东城区河道日常巡检");
        t1.setDescription("巡河排污口核查,拍摄对比影像");
        t1.setDevice(d1); t1.setPilot(p1);
        t1.setRouteJson("[{\"lng\":116.404,\"lat\":39.884,\"alt\":100},{\"lng\":116.418,\"lat\":39.888,\"alt\":100},"
                + "{\"lng\":116.425,\"lat\":39.901,\"alt\":110},{\"lng\":116.409,\"lat\":39.905,\"alt\":100},"
                + "{\"lng\":116.404,\"lat\":39.884,\"alt\":100}]");
        t1.setPlannedAltitude(100.0); t1.setPlannedDuration(18.0);
        t1.setStatus(FlightTask.Status.PENDING); t1.setApproval(FlightTask.Approval.APPROVED);

        FlightTask t2 = new FlightTask();
        t2.setName("朝阳公园航拍取证");
        t2.setDescription("绿地侵占情况航拍取证");
        t2.setDevice(d2); t2.setPilot(p2);
        t2.setRouteJson("[{\"lng\":116.475,\"lat\":39.935,\"alt\":80},{\"lng\":116.485,\"lat\":39.935,\"alt\":80},"
                + "{\"lng\":116.485,\"lat\":39.945,\"alt\":90},{\"lng\":116.475,\"lat\":39.945,\"alt\":80},"
                + "{\"lng\":116.475,\"lat\":39.935,\"alt\":80}]");
        t2.setPlannedAltitude(80.0); t2.setPlannedDuration(15.0);
        t2.setStatus(FlightTask.Status.PENDING); t2.setApproval(FlightTask.Approval.APPROVED);

        FlightTask t3 = new FlightTask();
        t3.setName("通州副中心正射测绘");
        t3.setDescription("1:2000 正射影像采集");
        t3.setDevice(d3); t3.setPilot(p3);
        t3.setRouteJson("[{\"lng\":116.65,\"lat\":39.90,\"alt\":260},{\"lng\":116.70,\"lat\":39.90,\"alt\":260},"
                + "{\"lng\":116.70,\"lat\":39.86,\"alt\":260},{\"lng\":116.65,\"lat\":39.86,\"alt\":260},"
                + "{\"lng\":116.65,\"lat\":39.90,\"alt\":260}]");
        t3.setPlannedAltitude(260.0); t3.setPlannedDuration(60.0);
        t3.setStatus(FlightTask.Status.PENDING); t3.setApproval(FlightTask.Approval.PENDING);

        FlightTask t4 = new FlightTask();
        t4.setName("亦庄物流配送试飞(历史)");
        t4.setDescription("已完成的低空物流航线验证飞行");
        t4.setDevice(d3); t4.setPilot(p3);
        t4.setRouteJson("[{\"lng\":116.50,\"lat\":39.79,\"alt\":90},{\"lng\":116.53,\"lat\":39.79,\"alt\":90},"
                + "{\"lng\":116.53,\"lat\":39.77,\"alt\":90},{\"lng\":116.50,\"lat\":39.77,\"alt\":90},"
                + "{\"lng\":116.50,\"lat\":39.79,\"alt\":90}]");
        t4.setPlannedAltitude(90.0); t4.setPlannedDuration(14.0);
        t4.setStartTime(LocalDateTime.now().minusDays(1).minusHours(4));
        t4.setEndTime(LocalDateTime.now().minusDays(1).minusHours(4).plusMinutes(13));
        t4.setStatus(FlightTask.Status.COMPLETED); t4.setApproval(FlightTask.Approval.APPROVED);

        FlightTask t5 = new FlightTask();
        t5.setName("西城重点区域黑飞核查(历史)");
        t5.setDescription("已完成的核查任务");
        t5.setDevice(d4); t5.setPilot(p4);
        t5.setRouteJson("[{\"lng\":116.342,\"lat\":39.912,\"alt\":100},{\"lng\":116.356,\"lat\":39.915,\"alt\":100},"
                + "{\"lng\":116.360,\"lat\":39.928,\"alt\":110},{\"lng\":116.346,\"lat\":39.930,\"alt\":100},"
                + "{\"lng\":116.342,\"lat\":39.912,\"alt\":100}]");
        t5.setPlannedAltitude(100.0); t5.setPlannedDuration(20.0);
        t5.setStartTime(LocalDateTime.now().minusDays(1));
        t5.setEndTime(LocalDateTime.now().minusDays(1).plusMinutes(19));
        t5.setStatus(FlightTask.Status.COMPLETED); t5.setApproval(FlightTask.Approval.APPROVED);

        FlightTask t6 = new FlightTask();
        t6.setName("房山电力线路巡检(历史)");
        t6.setDescription("220kV 线路通道巡检");
        t6.setDevice(d4); t6.setPilot(p4);
        t6.setRouteJson("[{\"lng\":116.08,\"lat\":39.80,\"alt\":80},{\"lng\":116.12,\"lat\":39.77,\"alt\":85},"
                + "{\"lng\":116.16,\"lat\":39.74,\"alt\":80},{\"lng\":116.20,\"lat\":39.71,\"alt\":80},"
                + "{\"lng\":116.24,\"lat\":39.68,\"alt\":80}]");
        t6.setPlannedAltitude(80.0); t6.setPlannedDuration(25.0);
        t6.setStartTime(LocalDateTime.now().minusDays(2));
        t6.setEndTime(LocalDateTime.now().minusDays(2).plusMinutes(24));
        t6.setStatus(FlightTask.Status.COMPLETED); t6.setApproval(FlightTask.Approval.APPROVED);

        taskRepository.saveAll(List.of(t1, t2, t3, t4, t5, t6));

        // ---------- 历史告警 ----------
        Alert a1 = new Alert(Alert.Type.GEOFENCE_BREACH, Alert.Level.CRITICAL, d2, null,
                "[UAV-2024-0002] 疑似闯入首都功能核心区禁飞区!", 116.401, 39.913, 95.0);
        a1.setCreatedAt(LocalDateTime.now().minusHours(5));
        a1.setHandled(true); a1.setHandler("admin"); a1.setHandleTime(LocalDateTime.now().minusHours(4));

        Alert a2 = new Alert(Alert.Type.ALTITUDE_EXCEED, Alert.Level.WARNING, d3, null,
                "[UAV-2023-0003] 海淀限飞区内高度超限: 150m > 120m", 116.35, 39.98, 150.0);
        a2.setCreatedAt(LocalDateTime.now().minusHours(9));

        Alert a3 = new Alert(Alert.Type.LOW_BATTERY, Alert.Level.WARNING, d4, t5,
                "[UAV-2024-0004] 电量不足 19%,已自动返航", 116.32, 39.94, 60.0);
        a3.setCreatedAt(LocalDateTime.now().minusDays(1));

        Alert a4 = new Alert(Alert.Type.NO_LICENSE, Alert.Level.CRITICAL, d3, null,
                "[UAV-2023-0003] 检测到未报备飞行(黑飞嫌疑)", 116.51, 39.78, 110.0);
        a4.setCreatedAt(LocalDateTime.now().minusHours(30));

        alertRepository.saveAll(List.of(a1, a2, a3, a4));

        // ---------- 无人机历史轨迹(供监控页「轨迹回放」,4 架全覆盖,时间覆盖近 3 天) ----------
        // 在线机:昨日已完成飞行(待命状态下也能回放;起飞后模拟器会追加实时轨迹)
        seedTrackHistory(d1, t1.getRouteJson(), LocalDateTime.now().minusDays(1).withHour(9).withMinute(30), 10, 16);
        seedTrackHistory(d2, t2.getRouteJson(), LocalDateTime.now().minusDays(1).withHour(15).withMinute(0), 10, 12);
        // 离线机:近 2 天历史任务轨迹
        seedTrackHistory(d3, t3.getRouteJson(), LocalDateTime.now().minusDays(2).withHour(11).withMinute(0), 10, 22);
        seedTrackHistory(d3, t4.getRouteJson(), LocalDateTime.now().minusDays(1).minusHours(4), 10, 12);
        seedTrackHistory(d4, t6.getRouteJson(), LocalDateTime.now().minusDays(2).withHour(14).withMinute(10), 10, 14);
        seedTrackHistory(d4, t5.getRouteJson(), LocalDateTime.now().minusDays(1).withHour(10).withMinute(0), 10, 10);

        log.info("Seed done: {} users, {} menus, {} devices, {} protocols, {} pilots, {} fences, {} tasks, {} alerts, {} map-providers",
                userRepository.count(), menuRepository.count(), deviceRepository.count(),
                protocolRepository.count(), pilotRepository.count(), fenceRepository.count(),
                taskRepository.count(), alertRepository.count(), mapProviderRepository.count());
    }

    /** 幂等菜单维护:纠正存量库 /map → /mapadmin(路由实际路径),补插报文管理菜单 */
    private void ensureMenus() {
        menuRepository.findFirstByPath("/map").ifPresent(m -> {
            m.setPath("/mapadmin");
            menuRepository.save(m);
            log.info("Fixed menu path /map -> /mapadmin");
        });
        if (menuRepository.findFirstByPath("/messages").isEmpty()) {
            menuRepository.save(new SysMenu("报文管理", "/messages", "ChatLineRound", SysMenu.Group.SYS, 2));
            log.info("Added menu /messages (报文管理)");
        }
        if (menuRepository.findFirstByPath("/msgadmin").isEmpty()) {
            menuRepository.save(new SysMenu("消息管理", "/msgadmin", "Promotion", SysMenu.Group.SYS, 9));
            log.info("Added menu /msgadmin (消息管理)");
        }
        if (menuRepository.findFirstByPath("/models").isEmpty()) {
            menuRepository.save(new SysMenu("模型配置", "/models", "Cpu", SysMenu.Group.SYS, 10));
            log.info("Added menu /models (模型配置)");
        }
    }

    /** 幂等:内置五个消息通道(默认停用,密钥留空由「消息管理」页维护) */
    private void seedMsgChannels() {
        if (msgChannelRepository.findByCode("inapp").isEmpty()) {
            MsgChannel inapp = new MsgChannel("inapp", MsgChannel.TYPE_INAPP, "站内消息", 5);
            inapp.setRemark("平台内实时消息,经 WebSocket 推送与收件箱呈现");
            inapp.setEnabled(true);
            msgChannelRepository.save(inapp);
        }
        if (msgChannelRepository.findByCode("jpush").isEmpty()) {
            MsgChannel jpush = new MsgChannel("jpush", MsgChannel.TYPE_JPUSH, "极光推送(APP)", 1);
            jpush.setConfigJson("{\"appKey\":\"\",\"masterSecret\":\"\"}");
            jpush.setRemark("APP 端通知,填 AppKey 与 MasterSecret 后启用");
            msgChannelRepository.save(jpush);
        }
        if (msgChannelRepository.findByCode("umeng").isEmpty()) {
            MsgChannel umeng = new MsgChannel("umeng", MsgChannel.TYPE_UMENG, "友盟推送(APP)", 2);
            umeng.setConfigJson("{\"appKey\":\"\",\"appMasterSecret\":\"\",\"production\":false}");
            umeng.setRemark("友盟 U-Push,填 AppKey 与 App Master Secret 后启用");
            msgChannelRepository.save(umeng);
        }
        if (msgChannelRepository.findByCode("email").isEmpty()) {
            MsgChannel email = new MsgChannel("email", MsgChannel.TYPE_EMAIL, "邮件(SMTP)", 3);
            email.setConfigJson("{\"host\":\"\",\"port\":465,\"username\":\"\",\"password\":\"\",\"from\":\"\",\"ssl\":true,\"testTo\":\"\"}");
            email.setRemark("填 SMTP 主机/账号/授权码,留 testTo 供通道测试");
            msgChannelRepository.save(email);
        }
        if (msgChannelRepository.findByCode("sms").isEmpty()) {
            MsgChannel sms = new MsgChannel("sms", MsgChannel.TYPE_SMS, "短信(HTTP网关)", 4);
            sms.setConfigJson("{\"apiUrl\":\"\",\"method\":\"POST\",\"bodyTemplate\":\"{\\\"phone\\\":\\\"${phone}\\\",\\\"content\\\":\\\"${content}\\\"}\",\"successContains\":\"\",\"testPhone\":\"\"}");
            sms.setRemark("通用 HTTP 短信网关适配,模板支持 ${phone}/${content} 占位");
            msgChannelRepository.save(sms);
        }
    }

    /** 幂等:预置主流大模型(默认停用,密钥留空由「模型配置」页维护) */
    private void seedLlmModels() {
        if (llmModelRepository.count() > 0) return;
        LlmModel glm = new LlmModel("智谱 GLM", LlmModel.PROVIDER_GLM,
                "https://open.bigmodel.cn/api/paas/v4", "glm-4.5");
        glm.setRemark("智谱开放平台,OpenAI 兼容;填 API Key 后启用");
        llmModelRepository.save(glm);

        LlmModel qwen = new LlmModel("通义千问", LlmModel.PROVIDER_QWEN,
                "https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen-plus");
        qwen.setRemark("阿里百炼兼容模式;填 API Key 后启用");
        llmModelRepository.save(qwen);

        LlmModel deepseek = new LlmModel("DeepSeek", LlmModel.PROVIDER_DEEPSEEK,
                "https://api.deepseek.com/v1", "deepseek-chat");
        deepseek.setRemark("DeepSeek 开放平台;填 API Key 后启用");
        llmModelRepository.save(deepseek);

        LlmModel local = new LlmModel("本地模型(Ollama)", LlmModel.PROVIDER_LOCAL,
                "http://localhost:11434/v1", "qwen2.5:7b");
        local.setRemark("本地部署免密钥;按 ollama pull 的模型名修改模型标识");
        local.setEnabled(true);
        local.setIsDefault(true);
        llmModelRepository.save(local);
    }

    private SysOrg childOrg(String name, String code, Long parentId, int sort) {
        SysOrg org = new SysOrg();
        org.setName(name);
        org.setOrgCode(code);
        org.setParentId(parentId);
        org.setSort(sort);
        return org;
    }

    /**
     * 沿航线合成历史轨迹并写入 device_data_history(离线机「轨迹回放」数据源):
     * 每 intervalSec 一帧,含经纬度/高度/速度/航向/电量/卫星,电量随进度线性衰减。
     */
    private void seedTrackHistory(Device device, String routeJson, LocalDateTime start,
                                  int intervalSec, double speed) {
        List<double[]> route = parseSeedRoute(routeJson);
        if (route.size() < 2) {
            return;
        }
        double total = 0;
        List<Double> segLen = new ArrayList<>();
        for (int i = 0; i + 1 < route.size(); i++) {
            double len = GeoUtils.distance(route.get(i)[0], route.get(i)[1],
                    route.get(i + 1)[0], route.get(i + 1)[1]);
            segLen.add(len);
            total += len;
        }
        int n = Math.max(2, (int) (total / (speed * intervalSec)));
        ObjectMapper mapper = new ObjectMapper();
        List<DeviceDataHistory> rows = new ArrayList<>(n + 1);
        for (int i = 0; i <= n; i++) {
            double done = total * i / n;
            int seg = 0;
            while (seg < segLen.size() - 1 && done > segLen.get(seg)) {
                done -= segLen.get(seg);
                seg++;
            }
            double[] from = route.get(seg);
            double[] to = route.get(seg + 1);
            double t = segLen.get(seg) <= 0 ? 1 : Math.min(1, done / segLen.get(seg));
            double[] pos = GeoUtils.interpolate(from[0], from[1], to[0], to[1], t);
            java.util.Map<String, Object> fields = new java.util.LinkedHashMap<>();
            fields.put("lng", Math.round(pos[0] * 1e6) / 1e6);
            fields.put("lat", Math.round(pos[1] * 1e6) / 1e6);
            fields.put("altitude", Math.round((from[2] + (to[2] - from[2]) * t) * 10) / 10.0);
            fields.put("speed", Math.round((speed + Math.sin(i / 5.0) * 1.5) * 10) / 10.0);
            fields.put("heading", Math.round(GeoUtils.bearing(from[0], from[1], to[0], to[1]) * 10) / 10.0);
            fields.put("battery", Math.round((98 - 60.0 * i / n) * 10) / 10.0);
            fields.put("satellites", 14 + (i % 5));
            try {
                DeviceDataHistory h = new DeviceDataHistory(device.getId(), device.getCode(),
                        device.getCategory(), mapper.writeValueAsString(fields));
                h.setTs(start.plusSeconds((long) i * intervalSec));
                rows.add(h);
            } catch (Exception ignore) {
                // json 序列化不会失败,保守兜底
            }
        }
        historyRepository.saveAll(rows);
        log.info("Seeded {} track points for offline drone {}", rows.size(), device.getCode());
    }

    /** 解析航线 JSON:[{"lng":..,"lat":..,"alt":..}, ...] */
    private List<double[]> parseSeedRoute(String json) {
        List<double[]> result = new ArrayList<>();
        if (json == null || json.isBlank()) {
            return result;
        }
        try {
            ObjectMapper mapper = new ObjectMapper();
            List<java.util.Map<String, Object>> raw = mapper.readValue(json,
                    mapper.getTypeFactory().constructCollectionType(List.class, java.util.Map.class));
            for (java.util.Map<String, Object> p : raw) {
                result.add(new double[]{
                        ((Number) p.get("lng")).doubleValue(),
                        ((Number) p.get("lat")).doubleValue(),
                        p.get("alt") == null ? 0 : ((Number) p.get("alt")).doubleValue()
                });
            }
        } catch (Exception e) {
            log.warn("Seed route parse failed: {}", e.getMessage());
        }
        return result;
    }

    private Device drone(String code, String name, String model, String manufacturer, String usage,
                         double homeLng, double homeLat, Pilot pilot,
                         ProtocolTemplate protocol, String secret) {
        Device d = new Device(code, name, Device.Category.DRONE, model, manufacturer);
        d.setUsage(usage);
        d.setHomeLng(homeLng);
        d.setHomeLat(homeLat);
        d.setPilot(pilot);
        d.setProtocol(protocol);
        d.setSecret(secret);
        d.setVirtual(true);          // 虚拟设备:由 FlightSimulator 驱动
        d.setStatus(Device.Status.IDLE);
        return d;
    }

    /** 物联网虚拟设备:有 home 坐标(地图 ICON/弹窗),由 IoTSimulator 驱动 */
    private Device iot(String code, String name, Device.Category category, String model, String manufacturer,
                       double homeLng, double homeLat, String usage) {
        Device d = new Device(code, name, category, model, manufacturer);
        d.setUsage(usage);
        d.setHomeLng(homeLng);
        d.setHomeLat(homeLat);
        d.setVirtual(true);
        d.setStatus(Device.Status.OFFLINE);
        return d;
    }

    /** 幂等补种内置底图厂商配置:凭证优先取环境变量 map-keys.*(缺省空,前端按「未配置」提示) */
    private void seedMapProviders() {
        if (mapProviderRepository.count() > 0) {
            return;
        }
        SysMapProvider baidu = new SysMapProvider("baidu", SysMapProvider.VENDOR_BAIDU, "百度地图");
        baidu.setDescription("百度地图 JS API,默认底图");
        baidu.setCredentialsJson(credJson("ak", baiduAk));
        baidu.setGrad("linear-gradient(135deg,#337cff,#00c8ff)");
        baidu.setSort(1);
        baidu.setEnabled(true);
        baidu.setIsDefault(true);
        mapProviderRepository.save(baidu);

        SysMapProvider amap = new SysMapProvider("amap", SysMapProvider.VENDOR_AMAP, "高德地图");
        amap.setDescription("高德地图 JS API 2.0,支持安全密钥");
        amap.setCredentialsJson(credJson("key", amapKey, "secret", amapSec));
        amap.setGrad("linear-gradient(135deg,#00b96b,#7be6b0)");
        amap.setSort(2);
        amap.setEnabled(true);
        mapProviderRepository.save(amap);

        SysMapProvider tdt = new SysMapProvider("tdt", SysMapProvider.VENDOR_TDT, "天地图");
        tdt.setDescription("国家地理信息公共服务,政务场景首选");
        tdt.setCredentialsJson(credJson("tk", tdtKey));
        tdt.setGrad("linear-gradient(135deg,#1a7f6e,#8fd26b)");
        tdt.setSort(3);
        tdt.setEnabled(true);
        mapProviderRepository.save(tdt);
        log.info("Seeded {} map providers (baidu default)", 3);
    }

    /** 极简凭证 JSON 组装(k-v 交替入参,值做 JSON 转义;null/空均输出空串) */
    private String credJson(String... kv) {
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i + 1 < kv.length; i += 2) {
            if (i > 0) {
                sb.append(',');
            }
            String v = kv[i + 1] == null ? "" : kv[i + 1].replace("\\", "\\\\").replace("\"", "\\\"");
            sb.append('"').append(kv[i]).append("\":\"").append(v).append('"');
        }
        return sb.append('}').toString();
    }

    private String menuIds(List<SysMenu> menus, java.util.function.Predicate<SysMenu> filter) {
        return menus.stream().filter(filter).map(SysMenu::getId).toList().toString();
    }
}
