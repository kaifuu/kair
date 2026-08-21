package com.wrj.platform.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * PostgreSQL/PostGIS 初始化兜底:
 * - docker-entrypoint-initdb.d 已启用 postgis 扩展;此处幂等兜底(本地自建库场景)
 * - Hibernate 建表后补建 GiST 空间索引( ddl-auto 不支持 GiST,必须手动补)
 * ApplicationRunner 在上下文就绪(JPA 已完成 schema 导出)后执行,时序安全。
 * HIGHEST_PRECEDENCE:CHECK 约束重建必须先于 DataSeeder 种子插入(其插入新枚举值)。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SchemaInit implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SchemaInit.class);

    private final JdbcTemplate jdbc;

    public SchemaInit(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        tryExec("CREATE EXTENSION IF NOT EXISTS postgis", "PostGIS 扩展已就绪");
        tryExec("CREATE INDEX IF NOT EXISTS idx_geo_fence_geom ON geo_fence USING GIST (geom)",
                "geo_fence.geom GiST 索引已就绪");
        // ST_DWithin(geom::geography) 用的是 geography 表达式索引,普通 geometry 索引帮不上
        tryExec("CREATE INDEX IF NOT EXISTS idx_geo_fence_geog ON geo_fence USING GIST ((geom::geography))",
                "geo_fence geography 表达式索引已就绪");
        tryExec("CREATE INDEX IF NOT EXISTS idx_device_data_history_device_ts ON device_data_history (device_id, ts DESC)",
                "device_data_history 查询索引已就绪");
        // 复合围栏(MULTI)的部件数组 JSON 较长,扩容列宽(Hibernate update 不改既有列类型)
        tryExec("ALTER TABLE geo_fence ALTER COLUMN points_json TYPE varchar(8000)",
                "geo_fence.points_json 已扩容至 varchar(8000)");
        // 旧 schema 的 shape 枚举检查约束不含 MULTI,重建放行
        tryExec("DO $$ BEGIN" +
                " ALTER TABLE geo_fence DROP CONSTRAINT IF EXISTS geo_fence_shape_check;" +
                " ALTER TABLE geo_fence ADD CONSTRAINT geo_fence_shape_check" +
                " CHECK (shape IN ('POLYGON','CIRCLE','LINE','MULTI'));" +
                " END $$",
                "geo_fence.shape 约束已放行 MULTI");
        // 威胁感知新增 5 类告警,旧 schema 的 type 检查约束不含新枚举,重建放行
        tryExec("DO $$ BEGIN" +
                " ALTER TABLE alert DROP CONSTRAINT IF EXISTS alert_type_check;" +
                " ALTER TABLE alert ADD CONSTRAINT alert_type_check" +
                " CHECK (type IN ('GEOFENCE_BREACH','ALTITUDE_EXCEED','LOW_BATTERY','SIGNAL_LOST'," +
                " 'NO_LICENSE','TASK_OVERDUE','PREDICTED_BREACH','CONFLICT_ALERT'," +
                " 'BATTERY_ANOMALY','ALTITUDE_JUMP','SIGNAL_WEAK'));" +
                " END $$",
                "alert.type 约束已放行威胁感知新类型");
        // 反制设备新增 6 类分类:若存量 schema 存在 category 检查约束则重建放行
        // (ddl-auto 不会更新 CHECK;无约束时建一条也无害,防手工建库遗漏)
        tryExec("DO $$ BEGIN" +
                " ALTER TABLE device DROP CONSTRAINT IF EXISTS device_category_check;" +
                " ALTER TABLE device ADD CONSTRAINT device_category_check" +
                " CHECK (category IN ('DRONE','DOCK','CAMERA','WEATHER','ADSB','GATEWAY','SENSOR'," +
                " 'RADAR','RADIO_DETECT','EO_TRACK','RADIO_JAM','LASER','NET_CAPTURE'));" +
                " END $$",
                "device.category 约束已放行反制设备类型");
    }

    private void tryExec(String sql, String okMsg) {
        try {
            jdbc.execute(sql);
            log.info("SchemaInit: {}", okMsg);
        } catch (Exception e) {
            log.warn("SchemaInit 失败(非致命): {} -> {}", sql, e.getMessage());
        }
    }
}
