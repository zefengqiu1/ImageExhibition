package com.worker1.worker1.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;

/**
 * 数据报表 API Controller（生产增强版）
 */
@RestController
@RequestMapping("/api/reports")
@CrossOrigin("*")
@Slf4j
public class ReportsController {

    @Autowired(required = true)
    private JdbcTemplate clickHouseJdbc;

    // ==================== 1. 运营概览 ====================

    @GetMapping("/overview/daily")
    public Map<String, Object> getDailyOverview(
            @RequestParam(required = false) String date) {

        LocalDate queryDate = parseDate(date);

        if (clickHouseJdbc == null) {
            log.info("clickHouseJdbc is null");
            return withSource(mockDailyOverview(), "mock");
        }

        try {
            String sql = """
                SELECT 
                    report_date,
                    total_pv,
                    total_uv,
                    avg_session_duration,
                    bounce_rate,
                    total_images,
                    active_images,
                    total_searches,
                    unique_keywords,
                    search_ctr,
                    mobile_rate,
                    desktop_rate
                FROM dw_ads.ads_operation_overview
                WHERE toDate(report_date) = ?
                """;

            List<Map<String, Object>> result =
                    clickHouseJdbc.queryForList(sql, queryDate);

            if (result.isEmpty()) {
                log.info("result is empty");
                return withSource(mockDailyOverview(), "mock");
            }
        log.info("has result");
            return withSource(result.get(0), "real");

        } catch (Exception e) {
            log.error("查询每日概览失败", e);
            return withSource(mockDailyOverview(), "mock");
        }
    }

    @GetMapping("/overview/compare")
    public Map<String, Object> compareOverview() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        LocalDate dayBefore = LocalDate.now().minusDays(2);

        Map<String, Object> y = getDailyOverview(yesterday.toString());
        Map<String, Object> d = getDailyOverview(dayBefore.toString());

        return Map.of(
                "yesterday", y,
                "dayBefore", d,
                "growth", calculateGrowth(y, d),
                "dataSource", y.get("dataSource")
        );
    }

    // ==================== 2. 流量趋势 ====================

    @GetMapping("/trend/traffic")
    public List<Map<String, Object>> getTrafficTrend(
            @RequestParam(defaultValue = "30") int days) {

        if (clickHouseJdbc == null) {
            return mockTrafficTrend(days);
        }

        try {
            String sql = """
                SELECT 
                    toDate(report_date) as date,
                    total_pv as pv,
                    total_uv as uv,
                    bounce_rate as bounceRate,
                    mobile_rate as mobileRate
                FROM dw_ads.ads_operation_overview
                WHERE report_date >= today() - ?
                ORDER BY date
                """;

            List<Map<String, Object>> result =
                    clickHouseJdbc.queryForList(sql, days);

            if (result.isEmpty()) {
                return mockTrafficTrend(days);
            }

            return result;

        } catch (Exception e) {
            log.error("查询流量趋势失败", e);
            return mockTrafficTrend(days);
        }
    }

    @GetMapping("/trend/hourly")
    public List<Map<String, Object>> getHourlyTrend(
            @RequestParam(required = false) String date) {

        LocalDate queryDate = parseDate(date);

        if (clickHouseJdbc == null) {
            return mockHourlyTrend();
        }

        try {
            String sql = """
                SELECT 
                    view_hour as hour,
                    count(*) as views,
                    uniq(user_id) as users
                FROM dw_dwd.dwd_image_view_detail
                WHERE toDate(view_date) = ?
                GROUP BY view_hour
                ORDER BY view_hour
                """;

            List<Map<String, Object>> result =
                    clickHouseJdbc.queryForList(sql, queryDate);

            if (result.isEmpty()) {
                return mockHourlyTrend();
            }

            return result;

        } catch (Exception e) {
            log.error("查询分时段流量失败", e);
            return mockHourlyTrend();
        }
    }

    // ==================== 3. 热门内容 ====================

    @GetMapping("/top/images")
    public List<Map<String, Object>> getTopImages(
            @RequestParam(required = false) String date,
            @RequestParam(defaultValue = "20") int limit) {

        LocalDate queryDate = parseDate(date);

        if (clickHouseJdbc == null) {
            return mockTopImages(limit);
        }

        try {
            String sql = """
                SELECT 
                    image_id as imageId,
                    image_title as title,
                    country,
                    pv,
                    uv,
                    avg_view_duration as avgDuration,
                    bounce_rate as bounceRate,
                    avg_scroll_depth as avgScrollDepth,
                    mobile_rate as mobileRate
                FROM dw_dws.dws_image_daily_stats
                WHERE toDate(stat_date) = ?
                ORDER BY pv DESC
                LIMIT ?
                """;

            List<Map<String, Object>> result =
                    clickHouseJdbc.queryForList(sql, queryDate, limit);

            if (result.isEmpty()) {
                return mockTopImages(limit);
            }

            return result;

        } catch (Exception e) {
            log.error("查询热门图片失败", e);
            return mockTopImages(limit);
        }
    }

    @GetMapping("/top/keywords")
    public List<Map<String, Object>> getTopKeywords(
            @RequestParam(required = false) String date,
            @RequestParam(defaultValue = "50") int limit) {

        LocalDate queryDate = parseDate(date);

        if (clickHouseJdbc == null) {
            return mockTopKeywords(limit);
        }

        try {
            String sql = """
                SELECT 
                    keyword,
                    search_count as searchCount,
                    click_count as clickCount,
                    ctr,
                    avg_result_count as avgResultCount,
                    avg_click_position as avgClickPosition,
                    unique_users as uniqueUsers
                FROM dw_dws.dws_search_keyword_daily
                WHERE toDate(stat_date) = ?
                ORDER BY search_count DESC
                LIMIT ?
                """;

            List<Map<String, Object>> result =
                    clickHouseJdbc.queryForList(sql, queryDate, limit);

            if (result.isEmpty()) {
                return mockTopKeywords(limit);
            }

            return result;

        } catch (Exception e) {
            log.error("查询热词失败", e);
            return mockTopKeywords(limit);
        }
    }

    // ==================== 4. 分布 ====================

    @GetMapping("/distribution/country")
    public List<Map<String, Object>> getCountryDistribution(
            @RequestParam(required = false) String date) {

        LocalDate queryDate = parseDate(date);

        if (clickHouseJdbc == null) {
            return mockCountryDistribution();
        }

        try {
            String sql = """
                SELECT 
                    country,
                    sum(pv) as totalPv,
                    sum(uv) as totalUv,
                    avg(avg_view_duration) as avgDuration,
                    avg(bounce_rate) as avgBounceRate,
                    avg(mobile_rate) as mobileRate
                FROM dw_dws.dws_image_daily_stats
                WHERE toDate(stat_date) = ?
                GROUP BY country
                ORDER BY totalPv DESC
                """;

            List<Map<String, Object>> result =
                    clickHouseJdbc.queryForList(sql, queryDate);

            if (result.isEmpty()) {
                return mockCountryDistribution();
            }

            return result;

        } catch (Exception e) {
            log.error("国家分布失败", e);
            return mockCountryDistribution();
        }
    }

    @GetMapping("/distribution/device")
    public Map<String, Object> getDeviceDistribution(
            @RequestParam(required = false) String date) {

        LocalDate queryDate = parseDate(date);

        if (clickHouseJdbc == null) {
            return Map.of("mobile", 65.5, "desktop", 34.5);
        }

        try {
            String sql = """
                SELECT 
                    mobile_rate,
                    desktop_rate
                FROM dw_ads.ads_operation_overview
                WHERE toDate(report_date) = ?
                """;

            List<Map<String, Object>> result =
                    clickHouseJdbc.queryForList(sql, queryDate);

            if (result.isEmpty()) {
                return Map.of("mobile", 65.5, "desktop", 34.5);
            }

            return result.get(0);

        } catch (Exception e) {
            log.error("设备分布失败", e);
            return Map.of("mobile", 65.5, "desktop", 34.5);
        }
    }

    // ==================== 工具方法 ====================

    private Map<String, Object> withSource(Map<String, Object> data, String source) {
        data.put("dataSource", source);
        return data;
    }

    private Map<String, Object> calculateGrowth(
            Map<String, Object> current,
            Map<String, Object> previous) {

        Map<String, Object> growth = new HashMap<>();

        for (String key : current.keySet()) {
            if (current.get(key) instanceof Number &&
                    previous.get(key) instanceof Number) {

                double cur = ((Number) current.get(key)).doubleValue();
                double pre = ((Number) previous.get(key)).doubleValue();

                if (pre == 0 && cur > 0) {
                    growth.put(key + "_growth", "NEW");
                } else if (pre != 0) {
                    double rate = ((cur - pre) / pre) * 100;
                    growth.put(key + "_growth",
                            String.format("%.2f%%", rate));
                    growth.put(key + "_diff", cur - pre);
                }
            }
        }
        return growth;
    }

    private LocalDate parseDate(String date) {
        if (date == null || date.isBlank()) {
            return LocalDate.now().minusDays(1);
        }
        try {
            return LocalDate.parse(date);
        } catch (Exception e) {
            return LocalDate.now().minusDays(1);
        }
    }

    // ==================== MOCK ====================

    // （下面 mock 方法你原来的可以原封不动粘过来继续用）

    private Map<String, Object> mockDailyOverview() {
        Map<String, Object> data = new HashMap<>();
        data.put("report_date", LocalDate.now().minusDays(1).toString());
        data.put("total_pv", 125000);
        data.put("total_uv", 45000);
        data.put("avg_session_duration", 180);
        data.put("bounce_rate", 35.5);
        data.put("total_images", 5000);
        data.put("active_images", 3200);
        data.put("total_searches", 8500);
        data.put("unique_keywords", 450);
        data.put("search_ctr", 68.5);
        data.put("mobile_rate", 65.5);
        data.put("desktop_rate", 34.5);
        return data;
    }

    private List<Map<String, Object>> mockTrafficTrend(int days) {
        List<Map<String, Object>> data = new ArrayList<>();
        for (int i = days; i >= 0; i--) {
            Map<String, Object> day = new HashMap<>();
            day.put("date", LocalDate.now().minusDays(i).toString());
            day.put("pv", 100000 + (int)(Math.random() * 50000));
            day.put("uv", 40000 + (int)(Math.random() * 10000));
            day.put("bounceRate", 30 + Math.random() * 10);
            day.put("mobileRate", 60 + Math.random() * 15);
            data.add(day);
        }
        return data;
    }

    private List<Map<String, Object>> mockHourlyTrend() {
        List<Map<String, Object>> data = new ArrayList<>();
        for (int hour = 0; hour < 24; hour++) {
            Map<String, Object> hourData = new HashMap<>();
            hourData.put("hour", hour);
            // 模拟流量高峰（晚上8-11点）
            int baseViews = hour >= 20 && hour <= 23 ? 8000 : 4000;
            hourData.put("views", baseViews + (int)(Math.random() * 2000));
            hourData.put("users", (int)(((Number)hourData.get("views")).intValue() * 0.4));
            data.add(hourData);
        }
        return data;
    }

    private List<Map<String, Object>> mockTopImages(int limit) {
        List<Map<String, Object>> data = new ArrayList<>();
        String[] countries = {"国产", "亚洲", "欧美"};
        String[] titles = {
                "精彩瞬间合集", "经典回顾", "热门推荐", "最新发布", "人气之选",
                "精选内容", "独家资源", "高清画质", "震撼视觉", "珍藏版"
        };

        for (int i = 0; i < limit; i++) {
            Map<String, Object> image = new HashMap<>();
            image.put("imageId", "img_" + (i + 1));
            image.put("title", titles[i % titles.length] + " " + (i + 1));
            image.put("country", countries[i % 3]);
            image.put("pv", 5000 - i * 100);
            image.put("uv", 2000 - i * 50);
            image.put("avgDuration", 45 + Math.random() * 30);
            image.put("bounceRate", 25 + Math.random() * 20);
            image.put("avgScrollDepth", 60 + Math.random() * 30);
            image.put("mobileRate", 60 + Math.random() * 20);
            data.add(image);
        }
        return data;
    }

    private List<Map<String, Object>> mockTopKeywords(int limit) {
        String[] keywords = {
                "动作", "喜剧", "悬疑", "爱情", "科幻", "恐怖", "剧情", "犯罪", "惊悚", "冒险",
                "战争", "历史", "传记", "音乐", "运动", "西部", "奇幻", "动画", "家庭", "纪录片"
        };

        List<Map<String, Object>> data = new ArrayList<>();
        for (int i = 0; i < Math.min(limit, keywords.length); i++) {
            Map<String, Object> kw = new HashMap<>();
            kw.put("keyword", keywords[i]);
            kw.put("searchCount", 1000 - i * 30);
            kw.put("clickCount", 800 - i * 25);
            kw.put("ctr", 70 + Math.random() * 20);
            kw.put("avgResultCount", 50 + (int)(Math.random() * 100));
            kw.put("avgClickPosition", 2 + Math.random() * 3);
            kw.put("uniqueUsers", 500 - i * 15);
            data.add(kw);
        }
        return data;
    }

    private List<Map<String, Object>> mockCountryDistribution() {
        return List.of(
                Map.of("country", "国产", "totalPv", 60000, "totalUv", 25000,
                        "avgDuration", 48.5, "avgBounceRate", 32.5, "mobileRate", 68.0),
                Map.of("country", "亚洲", "totalPv", 40000, "totalUv", 18000,
                        "avgDuration", 42.3, "avgBounceRate", 35.8, "mobileRate", 65.0),
                Map.of("country", "欧美", "totalPv", 25000, "totalUv", 12000,
                        "avgDuration", 55.8, "avgBounceRate", 28.2, "mobileRate", 58.5)
        );
    }

    private List<Map<String, Object>> mockRetentionData() {
        return List.of(
                Map.of("cohortDate", LocalDate.now().minusDays(30).toString(), "retentionDay", 1,
                        "initialUsers", 1000, "retainedUsers", 450, "retentionRate", 45.0),
                Map.of("cohortDate", LocalDate.now().minusDays(30).toString(), "retentionDay", 7,
                        "initialUsers", 1000, "retainedUsers", 280, "retentionRate", 28.0),
                Map.of("cohortDate", LocalDate.now().minusDays(30).toString(), "retentionDay", 30,
                        "initialUsers", 1000, "retainedUsers", 150, "retentionRate", 15.0)
        );
    }

    private List<Map<String, Object>> mockDurationTrend(int days) {
        List<Map<String, Object>> data = new ArrayList<>();
        String[] countries = {"国产", "亚洲", "欧美"};

        for (int i = days; i >= 0; i--) {
            for (String country : countries) {
                Map<String, Object> point = new HashMap<>();
                point.put("date", LocalDate.now().minusDays(i).toString());
                point.put("country", country);
                point.put("avgDuration", 40 + Math.random() * 20);
                data.add(point);
            }
        }
        return data;
    }
}
