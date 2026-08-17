package com.wrj.platform.config;

import com.wrj.platform.entity.*;
import com.wrj.platform.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 首次启动初始化演示数据(北京场景) */
@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final DroneRepository droneRepository;
    private final PilotRepository pilotRepository;
    private final FlightTaskRepository taskRepository;
    private final GeoFenceRepository fenceRepository;
    private final AlertRepository alertRepository;

    public DataSeeder(DroneRepository droneRepository, PilotRepository pilotRepository,
                      FlightTaskRepository taskRepository, GeoFenceRepository fenceRepository,
                      AlertRepository alertRepository) {
        this.droneRepository = droneRepository;
        this.pilotRepository = pilotRepository;
        this.taskRepository = taskRepository;
        this.fenceRepository = fenceRepository;
        this.alertRepository = alertRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (droneRepository.count() > 0) {
            log.info("Data already seeded, skip");
            return;
        }
        log.info("Seeding demo data ...");

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

        // ---------- 无人机 ----------
        Drone d1 = new Drone("UAV-2024-0001", "DJI M350 RTK", "大疆创新", "巡检",
                116.397128, 39.916527);
        d1.setPilot(p1); d1.setMaxAltitude(500.0); d1.setMaxEndurance(55.0);
        d1.setTotalFlightHours(210.5); d1.setPurchaseDate(LocalDateTime.of(2024, 3, 15, 0, 0));

        Drone d2 = new Drone("UAV-2024-0002", "DJI Mavic 3E", "大疆创新", "航拍",
                116.407526, 39.904030);
        d2.setPilot(p2); d2.setMaxAltitude(500.0); d2.setMaxEndurance(45.0);
        d2.setTotalFlightHours(156.0); d2.setPurchaseDate(LocalDateTime.of(2024, 5, 20, 0, 0));

        Drone d3 = new Drone("UAV-2023-0003", "CW-15 垂起固定翼", "纵横股份", "测绘",
                116.480, 39.910);
        d3.setPilot(p3); d3.setMaxAltitude(1000.0); d3.setMaxEndurance(180.0);
        d3.setTotalFlightHours(98.0); d3.setPurchaseDate(LocalDateTime.of(2023, 11, 2, 0, 0));

        Drone d4 = new Drone("UAV-2024-0004", "DJI Matrice 30T", "大疆创新", "巡检",
                116.35, 39.95);
        d4.setPilot(p4); d4.setMaxAltitude(500.0); d4.setMaxEndurance(41.0);
        d4.setTotalFlightHours(73.0); d4.setPurchaseDate(LocalDateTime.of(2024, 8, 8, 0, 0));

        Drone d5 = new Drone("UAV-2025-0005", "FH-98 大载重", "峰飞航空", "物流",
                116.28, 39.98);
        d5.setPilot(p5); d5.setMaxAltitude(300.0); d5.setMaxEndurance(25.0);
        d5.setTotalFlightHours(12.0); d5.setPurchaseDate(LocalDateTime.of(2025, 4, 18, 0, 0));

        Drone d6 = new Drone("UAV-2024-0006", "DJI Mavic 3T", "大疆创新", "巡检",
                116.52, 39.87);
        d6.setPilot(p1); d6.setMaxAltitude(500.0); d6.setMaxEndurance(45.0);
        d6.setTotalFlightHours(88.0); d6.setPurchaseDate(LocalDateTime.of(2024, 10, 1, 0, 0));
        d6.setStatus(Drone.Status.MAINTENANCE);

        droneRepository.saveAll(List.of(d1, d2, d3, d4, d5, d6));

        // ---------- 电子围栏 ----------
        GeoFence f1 = new GeoFence();
        f1.setName("首都功能核心区禁飞区");
        f1.setType(GeoFence.Type.NO_FLY); f1.setShape(GeoFence.Shape.CIRCLE);
        f1.setPointsJson("[{\"lng\":116.397,\"lat\":39.910}]");
        f1.setRadius(6000.0); f1.setMaxAltitude(0.0);
        f1.setRemark("东城/西城核心区,全天候禁飞");
        fenceRepository.save(f1);

        GeoFence f2 = new GeoFence();
        f2.setName("首都机场净空保护区");
        f2.setType(GeoFence.Type.NO_FLY); f2.setShape(GeoFence.Shape.CIRCLE);
        f2.setPointsJson("[{\"lng\":116.603,\"lat\":40.080}]");
        f2.setRadius(10000.0); f2.setMaxAltitude(0.0);
        f2.setRemark("机场净空区,严禁无人机飞行");
        fenceRepository.save(f2);

        GeoFence f3 = new GeoFence();
        f3.setName("海淀五环外限飞区");
        f3.setType(GeoFence.Type.LIMIT); f3.setShape(GeoFence.Shape.POLYGON);
        f3.setPointsJson("[{\"lng\":116.28,\"lat\":40.00},{\"lng\":116.42,\"lat\":40.00},"
                + "{\"lng\":116.42,\"lat\":39.96},{\"lng\":116.28,\"lat\":39.96}]");
        f3.setMaxAltitude(120.0);
        f3.setRemark("限高 120m,需报备");
        fenceRepository.save(f3);

        GeoFence f4 = new GeoFence();
        f4.setName("亦庄作业示范区");
        f4.setType(GeoFence.Type.WORK); f4.setShape(GeoFence.Shape.POLYGON);
        f4.setPointsJson("[{\"lng\":116.48,\"lat\":39.82},{\"lng\":116.56,\"lat\":39.82},"
                + "{\"lng\":116.56,\"lat\":39.76},{\"lng\":116.48,\"lat\":39.76}]");
        f4.setMaxAltitude(300.0);
        f4.setRemark("低空经济示范区,物流配送作业区");
        fenceRepository.save(f4);

        // ---------- 飞行任务 ----------
        FlightTask t1 = new FlightTask();
        t1.setName("东城区河道日常巡检");
        t1.setDescription("巡河排污口核查,拍摄对比影像");
        t1.setDrone(d1); t1.setPilot(p1);
        t1.setRouteJson("[{\"lng\":116.404,\"lat\":39.884,\"alt\":100},{\"lng\":116.418,\"lat\":39.888,\"alt\":100},"
                + "{\"lng\":116.425,\"lat\":39.901,\"alt\":110},{\"lng\":116.409,\"lat\":39.905,\"alt\":100},"
                + "{\"lng\":116.404,\"lat\":39.884,\"alt\":100}]");
        t1.setPlannedAltitude(100.0); t1.setPlannedDuration(18.0);
        t1.setStatus(FlightTask.Status.PENDING); t1.setApproval(FlightTask.Approval.APPROVED);

        FlightTask t2 = new FlightTask();
        t2.setName("朝阳公园航拍取证");
        t2.setDescription("绿地侵占情况航拍取证");
        t2.setDrone(d2); t2.setPilot(p2);
        t2.setRouteJson("[{\"lng\":116.475,\"lat\":39.935,\"alt\":80},{\"lng\":116.485,\"lat\":39.935,\"alt\":80},"
                + "{\"lng\":116.485,\"lat\":39.945,\"alt\":90},{\"lng\":116.475,\"lat\":39.945,\"alt\":80},"
                + "{\"lng\":116.475,\"lat\":39.935,\"alt\":80}]");
        t2.setPlannedAltitude(80.0); t2.setPlannedDuration(15.0);
        t2.setStatus(FlightTask.Status.PENDING); t2.setApproval(FlightTask.Approval.APPROVED);

        FlightTask t3 = new FlightTask();
        t3.setName("通州副中心正射测绘");
        t3.setDescription("1:2000 正射影像采集");
        t3.setDrone(d3); t3.setPilot(p3);
        t3.setRouteJson("[{\"lng\":116.65,\"lat\":39.90,\"alt\":260},{\"lng\":116.70,\"lat\":39.90,\"alt\":260},"
                + "{\"lng\":116.70,\"lat\":39.86,\"alt\":260},{\"lng\":116.65,\"lat\":39.86,\"alt\":260},"
                + "{\"lng\":116.65,\"lat\":39.90,\"alt\":260}]");
        t3.setPlannedAltitude(260.0); t3.setPlannedDuration(60.0);
        t3.setStatus(FlightTask.Status.PENDING); t3.setApproval(FlightTask.Approval.PENDING);

        FlightTask t4 = new FlightTask();
        t4.setName("亦庄物流配送试飞");
        t4.setDescription("低空物流航线验证飞行");
        t4.setDrone(d5); t4.setPilot(p5);
        t4.setRouteJson("[{\"lng\":116.50,\"lat\":39.79,\"alt\":90},{\"lng\":116.53,\"lat\":39.79,\"alt\":90},"
                + "{\"lng\":116.53,\"lat\":39.77,\"alt\":90},{\"lng\":116.50,\"lat\":39.77,\"alt\":90},"
                + "{\"lng\":116.50,\"lat\":39.79,\"alt\":90}]");
        t4.setPlannedAltitude(90.0); t4.setPlannedDuration(12.0);
        t4.setStatus(FlightTask.Status.PENDING); t4.setApproval(FlightTask.Approval.APPROVED);

        FlightTask t5 = new FlightTask();
        t5.setName("西城重点区域黑飞核查(历史)");
        t5.setDescription("已完成的核查任务");
        t5.setDrone(d4); t5.setPilot(p4);
        t5.setPlannedAltitude(100.0); t5.setPlannedDuration(20.0);
        t5.setStartTime(LocalDateTime.now().minusDays(1));
        t5.setEndTime(LocalDateTime.now().minusDays(1).plusMinutes(19));
        t5.setStatus(FlightTask.Status.COMPLETED); t5.setApproval(FlightTask.Approval.APPROVED);

        FlightTask t6 = new FlightTask();
        t6.setName("房山电力线路巡检(历史)");
        t6.setDescription("220kV 线路通道巡检");
        t6.setDrone(d4); t6.setPilot(p4);
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

        Alert a4 = new Alert(Alert.Type.NO_LICENSE, Alert.Level.CRITICAL, d5, null,
                "[UAV-2025-0005] 检测到未报备飞行(黑飞嫌疑)", 116.51, 39.78, 110.0);
        a4.setCreatedAt(LocalDateTime.now().minusHours(30));

        alertRepository.saveAll(List.of(a1, a2, a3, a4));

        log.info("Seed done: {} pilots, {} drones, {} fences, {} tasks, {} alerts",
                pilotRepository.count(), droneRepository.count(), fenceRepository.count(),
                taskRepository.count(), alertRepository.count());
    }
}
