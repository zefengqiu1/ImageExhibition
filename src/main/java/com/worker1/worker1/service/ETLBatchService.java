package com.worker1.worker1.service;

import com.worker1.worker1.store.model.DbImageUrl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.List;


/*
ODS (Operational Data Store) - 原始数据层
  ↓
DWS (Data Warehouse Summary) - 汇总数据层
  ↓
ADS (Application Data Service) - 应用数据层
 */
/**
// * 完整 ETL 批处理服务（优化版 - 避免 JDBC 解析器警告）
 * 1️⃣ MongoDB -> DIM (dim_image)
 * 2️⃣ DWS 聚合
 * 3️⃣ DWS -> ADS 报表
 */
@Slf4j
@Service
public class ETLBatchService {

    @Autowired
    private DbImageUrlService dbImageUrlService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired(required = true)
    private JdbcTemplate clickHouseJdbc;  // ClickHouse JDBC

    private static final int BOUNCE_DURATION_SECONDS = 5;
    private static final LocalDate MAX_EXPIRATION_DATE = LocalDate.of(2149, 6, 6);
    private static final LocalDate MIN_VALID_DATE = LocalDate.of(1970, 1, 1);
    private static final List<String> RANK_COUNTRIES = List.of("asia", "domestic", "european", "all");
    private static final int RANK_TOP_N = 200;

    @Scheduled(cron = "0 0 1 * * ?")
    public void runDailyETL() {
        log.info("========== 开始每日 ETL 任务 ==========");
        LocalDate etlDate = LocalDate.now().minusDays(1); // 昨天的数据

        try {
            extractMongoImagesToDim(etlDate);
            aggregateDWDToDWS(etlDate);
            generateWeeklyMonthlyRanksToRedis(etlDate);
            generateADSReports(etlDate);

            log.info("========== ETL 任务完成: date={} ==========", etlDate);
        } catch (Exception e) {
            log.error("ETL 任务执行失败: date={}", etlDate, e);
        }
    }

    // ======================
    // Step 1a: MongoDB -> dim_image
    // ======================
    public void extractMongoImagesToDim(LocalDate etlDate) {
        log.info("Step 1a: MongoDB -> dim_image");

        if (!clickHouseAvailable("Step 1a")) {
            return;
        }

        try {
            List<DbImageUrl> images = dbImageUrlService.getAllDbImageUrls();
            log.info("MongoDB 图片数量: {}", images.size());

            String sql = "INSERT INTO dw_dim.dim_image " +
                    "(image_key, image_id, title, country, website, labels, label_count, image_count, " +
                    "created_date, age_days, effective_date, expiration_date, is_current, etl_timestamp) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

            List<Object[]> batch = new ArrayList<>();
            int successCount = 0, errorCount = 0;

            for (DbImageUrl img : images) {
                try {
                    String title = img.getTitle();
                    if (title == null || title.trim().isEmpty()) {
                        log.warn("跳过无效图片: title 为空");
                        errorCount++;
                        continue;
                    }

                    LocalDate createdDate = parseCreatedDate(img.getCreatedAt(), etlDate);
                    long ageDays = ChronoUnit.DAYS.between(createdDate, etlDate);

                    List<String> labels = img.getLabels();
                    List<String> imageUrls = img.getImageUrl();

                    Object[] params = new Object[]{
                            (long) title.hashCode(),                   // image_key
                            title,                                     // image_id
                            title,                                     // title
                            img.getCountry(),                           // country
                            img.getWebsite(),                           // website
                            labels != null ? String.join(",", labels) : "", // labels
                            labels != null ? labels.size() : 0,        // label_count
                            imageUrls != null ? imageUrls.size() : 0,  // image_count
                            Timestamp.valueOf(createdDate.atStartOfDay()), // created_date
                            (long) ageDays,                             // age_days
                            Timestamp.valueOf(etlDate.atStartOfDay()),  // effective_date
                            Timestamp.valueOf(MAX_EXPIRATION_DATE.atStartOfDay()), // expiration_date
                            1,                                          // is_current
                            Timestamp.valueOf(LocalDateTime.now())      // etl_timestamp
                    };

                    batch.add(params);
                    successCount++;

                    if (batch.size() >= 1000) {
                        flushBatch(sql, batch);
                    }
                } catch (Exception e) {
                    log.error("处理图片失败: {}", img.getTitle(), e);
                    errorCount++;
                }
            }

            flushBatch(sql, batch);
            log.info("MongoDB 图片抽取完成: 成功 {} 条, 失败 {} 条", successCount, errorCount);
        } catch (Exception e) {
            log.error("MongoDB 抽取失败", e);
            throw new RuntimeException("Step 1a 失败", e);
        }
    }

    private LocalDate parseCreatedDate(Object createdAtObj, LocalDate defaultDate) {
        try {
            LocalDate date = defaultDate;
            if (createdAtObj instanceof Long) {
                long ts = (Long) createdAtObj;
                date = ts > 10000000000L ?
                        Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).toLocalDate() :
                        Instant.ofEpochSecond(ts).atZone(ZoneId.systemDefault()).toLocalDate();
            } else if (createdAtObj instanceof Integer) {
                date = Instant.ofEpochSecond((Integer) createdAtObj).atZone(ZoneId.systemDefault()).toLocalDate();
            } else if (createdAtObj instanceof String) {
                date = parseCreatedDate(Long.parseLong((String) createdAtObj), defaultDate);
            }
            if (date.isBefore(MIN_VALID_DATE) || date.isAfter(MAX_EXPIRATION_DATE)) return defaultDate;
            return date;
        } catch (Exception e) {
            return defaultDate;
        }
    }

    // ======================
    // Step 2: DWS 聚合
    // ======================
    private void aggregateDWDToDWS(LocalDate etlDate) {
        log.info("Step 2: DWS 聚合");
        if (!clickHouseAvailable("Step 2")) {
            return;
        }

        try {
            String sql = "INSERT INTO dw_dws.dws_image_daily_stats " +
                    "(stat_date, image_id, image_title, country, pv, uv, avg_view_duration, bounce_rate, avg_scroll_depth, mobile_rate) " +
                    "SELECT '" + etlDate + "', image_id, any(image_title), any(country), count(*), uniq(user_id), avg(view_duration), " +
                    "countIf(view_duration <= " + BOUNCE_DURATION_SECONDS + ")*100.0/count(*), avg(scroll_depth), " +
                    "countIf(device_type='mobile')*100.0/count(*) " +
                    "FROM dw_ods.ods_image_view_log WHERE toDate(event_timestamp, 'America/Los_Angeles')='" + etlDate + "' GROUP BY image_id";
            clickHouseJdbc.update(sql);
        } catch (Exception e) {
            log.error("DWS 聚合失败", e);
            throw new RuntimeException("Step 2 失败", e);
        }
    }

    // ======================
    // Step 5: DWS -> ADS 报表（优化版 - 避免 CTE + INSERT 解析警告）
    // ======================
    private void generateADSReports(LocalDate reportDate) {
        try {
            log.info("Step 5: 生成 ADS 层报表，日期: {}", reportDate);
            if (!clickHouseAvailable("Step 5")) {
                return;
            }

            // 方案：分两步执行，先聚合再插入
            // Step 1: 聚合数据到临时表
            String dropTempTableSql = "DROP TABLE IF EXISTS temp_aggregated_data";
            clickHouseJdbc.execute(dropTempTableSql);

            String createTempTableSql = """
                CREATE TABLE temp_aggregated_data
                ENGINE = Memory AS
                SELECT
                    sum(pv) AS total_pv,
                    sum(uv) AS total_uv,
                    avg(avg_view_duration) AS avg_session_duration,
                    avg(bounce_rate) AS bounce_rate,
                    count(DISTINCT image_id) AS active_images,
                    avg(mobile_rate) AS mobile_rate
                FROM dw_dws.dws_image_daily_stats
                WHERE stat_date = ?
                """;

            clickHouseJdbc.update(createTempTableSql, reportDate);

            // Step 2: 从临时表插入到 ADS
            String insertAdsSql = """
                INSERT INTO dw_ads.ads_operation_overview
                (
                    report_date,
                    total_pv, total_uv, avg_session_duration, bounce_rate,
                    total_images, active_images,
                    total_searches, unique_keywords, search_ctr,
                    mobile_rate, desktop_rate
                )
                SELECT
                    ? AS report_date,
                    total_pv,
                    total_uv,
                    avg_session_duration,
                    bounce_rate,
                    (SELECT count(*) FROM dw_dim.dim_image WHERE is_current = 1) AS total_images,
                    active_images,
                    (SELECT sum(search_count) FROM dw_dws.dws_search_keyword_daily WHERE stat_date = ?) AS total_searches,
                    (SELECT count(*) FROM dw_dws.dws_search_keyword_daily WHERE stat_date = ?) AS unique_keywords,
                    (SELECT avg(ctr) FROM dw_dws.dws_search_keyword_daily WHERE stat_date = ?) AS search_ctr,
                    mobile_rate,
                    100 - mobile_rate AS desktop_rate
                FROM temp_aggregated_data
                """;

            clickHouseJdbc.update(insertAdsSql,
                    reportDate,  // report_date column
                    reportDate,  // total_searches subquery
                    reportDate,  // unique_keywords subquery
                    reportDate   // search_ctr subquery
            );

            // Step 3: 清理临时表
            clickHouseJdbc.execute(dropTempTableSql);

            log.info("ADS 运营概览报表生成完成");

        } catch (Exception e) {
            log.error("生成 ADS 报表失败: {}", e.getMessage(), e);
            throw new RuntimeException("Step 5 失败", e);
        }
    }

    private boolean clickHouseAvailable(String stepName) {
        if (clickHouseJdbc == null) {
            log.warn("ClickHouse JDBC 未配置，跳过 {}", stepName);
            return false;
        }
        return true;
    }

    private void flushBatch(String sql, List<Object[]> batch) {
        if (batch.isEmpty()) {
            return;
        }
        clickHouseJdbc.batchUpdate(sql, batch);
        batch.clear();
    }

    // ======================
    // Step 4: 周榜/月榜离线汇总写入 Redis（包含历史榜）
    // ======================
    private void generateWeeklyMonthlyRanksToRedis(LocalDate etlDate) {
        log.info("Step 4: 生成周榜/月榜并写入 Redis");
        if (!clickHouseAvailable("Step 4")) {
            return;
        }

        LocalDate weekStart = etlDate.with(WeekFields.ISO.dayOfWeek(), 1);
        LocalDate weekEnd = weekStart.plusDays(6);
        String weekKey = getIsoWeekKey(etlDate);

        YearMonth yearMonth = YearMonth.from(etlDate);
        LocalDate monthStart = yearMonth.atDay(1);
        LocalDate monthEnd = yearMonth.atEndOfMonth();
        String monthKey = yearMonth.format(DateTimeFormatter.ofPattern("yyyyMM"));

        for (String country : RANK_COUNTRIES) {
            List<RankRow> weekly = queryTopFromDws(weekStart, weekEnd, country, RANK_TOP_N);
            writeRankToRedis("rank:weekly:" + country, weekly, null);
            writeRankToRedis("rank:weekly:" + country + ":" + weekKey, weekly, Duration.ofDays(180));

            List<RankRow> monthly = queryTopFromDws(monthStart, monthEnd, country, RANK_TOP_N);
            writeRankToRedis("rank:monthly:" + country, monthly, null);
            writeRankToRedis("rank:monthly:" + country + ":" + monthKey, monthly, Duration.ofDays(540));
        }
    }

    private List<RankRow> queryTopFromDws(LocalDate start, LocalDate end, String country, int topN) {
        String baseSql;
        Object[] params;
        if ("all".equalsIgnoreCase(country)) {
            baseSql = """
                SELECT image_id, sum(pv) AS view_count
                FROM dw_dws.dws_image_daily_stats
                WHERE stat_date BETWEEN ? AND ?
                GROUP BY image_id
                ORDER BY view_count DESC
                LIMIT ?
                """;
            params = new Object[]{start, end, topN};
        } else {
            baseSql = """
                SELECT image_id, sum(pv) AS view_count
                FROM dw_dws.dws_image_daily_stats
                WHERE stat_date BETWEEN ? AND ? AND country = ?
                GROUP BY image_id
                ORDER BY view_count DESC
                LIMIT ?
                """;
            params = new Object[]{start, end, country, topN};
        }

        return clickHouseJdbc.query(baseSql, (rs, rowNum) ->
                new RankRow(rs.getString("image_id"), rs.getLong("view_count")), params);
    }

    private void writeRankToRedis(String key, List<RankRow> rows, Duration ttl) {
        redisTemplate.delete(key);
        if (rows.isEmpty()) {
            log.info("榜单写入: key={}, empty", key);
            return;
        }
        for (RankRow row : rows) {
            redisTemplate.opsForZSet().add(key, row.imageId(), row.viewCount());
        }
        if (ttl != null) {
            redisTemplate.expire(key, ttl);
        }
        log.info("榜单写入: key={}, size={}", key, rows.size());
    }

    private String getIsoWeekKey(LocalDate date) {
        WeekFields weekFields = WeekFields.ISO;
        int week = date.get(weekFields.weekOfWeekBasedYear());
        int year = date.get(weekFields.weekBasedYear());
        return String.format("%d-W%02d", year, week);
    }

    private record RankRow(String imageId, long viewCount) {}
}
