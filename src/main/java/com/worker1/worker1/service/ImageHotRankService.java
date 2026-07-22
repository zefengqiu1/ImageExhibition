package com.worker1.worker1.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 图片热度排行服务。
 *
 * Redis key:
 * - 实时榜：rank:realtime:{country}:{minuteTimestamp}
 * - 日榜：rank:daily:{country}:{yyyyMMdd}
 * - 周榜：rank:weekly:{country}:{YYYY-Www}
 * - 月榜：rank:monthly:{country}:{yyyyMM}
 */
@Slf4j
@Service
public class ImageHotRankService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired(required = false)
    private ImageViewEventProducer eventProducer;

    // Redis Key 前缀
    // 不同地区的榜单: asia, domestic, european, all
    private static final String REALTIME_KEY = "rank:realtime:"; // 实时窗口
    private static final String DAILY_KEY = "rank:daily:";       // 日榜
    private static final String WEEKLY_KEY = "rank:weekly:";     // 周榜
    private static final String MONTHLY_KEY = "rank:monthly:";   // 月榜
    private static final DateTimeFormatter DAY_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyyMM");
    private static final long WEEKLY_HISTORY_TTL_DAYS = 180;
    private static final long MONTHLY_HISTORY_TTL_DAYS = 540;
    // no cache for rank lists
    private static final Set<String> ALLOWED_COUNTRIES =
            Set.of("asia", "domestic", "european", "all");

    /**
     * 记录图片浏览（PV +1）
     * @param imageId 图片ID
     */
    public void recordView(String imageId, String country) {
        long now = System.currentTimeMillis();
        String timestamp = String.valueOf(now / 60000); // 按分钟分桶
        String normalizedCountry = normalizeCountry(country);

        // 0. 发送事件到 Kafka（供异步消费）
        if (eventProducer != null) {
            eventProducer.sendViewEvent(imageId);
        }

        // 1. 更新实时窗口（只更新当前分钟桶）
        recordRealtime(imageId, normalizedCountry, timestamp);
        recordRealtime(imageId, "all", timestamp);

        // 2. 更新日榜、周榜、月榜
        LocalDate today = LocalDate.now();
        recordDaily(imageId, normalizedCountry, today.format(DAY_FORMATTER));
        recordDaily(imageId, "all", today.format(DAY_FORMATTER));

        String weekKey = getIsoWeekKey(today);
        recordWeekly(imageId, normalizedCountry, weekKey);
        recordWeekly(imageId, "all", weekKey);

        String monthKey = YearMonth.from(today).format(MONTH_FORMATTER);
        recordMonthly(imageId, normalizedCountry, monthKey);
        recordMonthly(imageId, "all", monthKey);

        log.info("记录浏览: imageId={}, country={}, timestamp={}", imageId, normalizedCountry, timestamp);
    }

    /**
     * 获取实时热榜（过去5分钟）
     */
    public List<HotImage> getRealtimeTop(int topN, String country) {
        String normalizedCountry = normalizeCountry(country);

        // 合并过去5分钟的数据
        long now = System.currentTimeMillis() / 60000;
        Map<String, Double> scoreMap = new HashMap<>();

        for (int i = 0; i < 5; i++) {
            String key = buildCountryKey(REALTIME_KEY, normalizedCountry, String.valueOf(now - i));
            Set<ZSetOperations.TypedTuple<String>> tuples =
                    redisTemplate.opsForZSet().reverseRangeWithScores(key, 0, -1);

            if (tuples != null) {
                for (ZSetOperations.TypedTuple<String> tuple : tuples) {
                    String imageId = tuple.getValue();
                    Double score = tuple.getScore();
                    scoreMap.merge(imageId, score, Double::sum);
                }
            }
        }

        // 排序并转换
        List<HotImage> result = scoreMap.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(topN)
                .map(e -> new HotImage(e.getKey(), e.getValue().longValue()))
                .collect(Collectors.toList());
        return result;
    }

    /**
     * 获取日榜 TOP N
     */
    public List<HotImage> getDailyTop(int topN, String country) {
        String today = LocalDate.now().format(DAY_FORMATTER);
        String key = buildCountryKey(DAILY_KEY, normalizeCountry(country), today);
        List<HotImage> result = getTopFromZSet(key, topN, "daily");
        return result;
    }

    /**
     * 获取周榜 TOP N
     */
    public List<HotImage> getWeeklyTop(int topN, String country) {
        String weekKey = getIsoWeekKey(LocalDate.now());
        String key = buildCountryKey(WEEKLY_KEY, normalizeCountry(country), weekKey);
        return getTopFromZSet(key, topN, "weekly");
    }

    /**
     * 获取指定周的周榜（历史榜）
     * weekKey 格式: YYYY-Www
     */
    public List<HotImage> getWeeklyTopByWeek(int topN, String country, String weekKey) {
        String normalizedCountry = normalizeCountry(country);
        String key = buildCountryKey(WEEKLY_KEY, normalizedCountry, weekKey);
        return getTopFromZSet(key, topN, "weekly-history");
    }

    /**
     * 获取月榜 TOP N
     */
    public List<HotImage> getMonthlyTop(int topN, String country) {
        String monthKey = YearMonth.now().format(MONTH_FORMATTER);
        String key = buildCountryKey(MONTHLY_KEY, normalizeCountry(country), monthKey);
        return getTopFromZSet(key, topN, "monthly");
    }

    /**
     * 获取指定月的月榜（历史榜）
     * monthKey 格式: yyyyMM
     */
    public List<HotImage> getMonthlyTopByMonth(int topN, String country, String monthKey) {
        String normalizedCountry = normalizeCountry(country);
        String key = buildCountryKey(MONTHLY_KEY, normalizedCountry, monthKey);
        return getTopFromZSet(key, topN, "monthly-history");
    }

    /**
     * 从 ZSet 获取 TOP N（带缓存）
     */
    private List<HotImage> getTopFromZSet(String key, int topN, String type) {
        // no cache for any rank type

        Set<ZSetOperations.TypedTuple<String>> tuples =
                redisTemplate.opsForZSet().reverseRangeWithScores(key, 0, topN - 1);

        if (tuples == null || tuples.isEmpty()) {
            log.info("榜单读取: key={}, type={}, empty", key, type);
            return Collections.emptyList();
        }

        List<HotImage> result = tuples.stream()
                .map(t -> new HotImage(t.getValue(), t.getScore().longValue()))
                .collect(Collectors.toList());

        log.info("榜单读取: key={}, type={}, size={}", key, type, result.size());
        return result;
    }

    private void recordRealtime(String imageId, String country, String timestamp) {
        String windowKey = buildCountryKey(REALTIME_KEY, country, timestamp);
        redisTemplate.opsForZSet().incrementScore(windowKey, imageId, 1);
        redisTemplate.expire(windowKey, 10, TimeUnit.MINUTES); // 保留10分钟
        log.info("实时榜写入: key={}, imageId={}, country={}, minute={}", windowKey, imageId, country, timestamp);
    }

    private void recordDaily(String imageId, String country, String today) {
        String key = buildCountryKey(DAILY_KEY, country, today);
        redisTemplate.opsForZSet().incrementScore(key, imageId, 1);
        redisTemplate.expire(key, 7, TimeUnit.DAYS);
        log.info("日榜写入: key={}, imageId={}, country={}, date={}", key, imageId, country, today);
    }

    private void recordWeekly(String imageId, String country, String weekKey) {
        String key = buildCountryKey(WEEKLY_KEY, country, weekKey);
        redisTemplate.opsForZSet().incrementScore(key, imageId, 1);
        redisTemplate.expire(key, WEEKLY_HISTORY_TTL_DAYS, TimeUnit.DAYS);
        log.info("周榜写入: key={}, imageId={}, country={}, week={}", key, imageId, country, weekKey);
    }

    private void recordMonthly(String imageId, String country, String monthKey) {
        String key = buildCountryKey(MONTHLY_KEY, country, monthKey);
        redisTemplate.opsForZSet().incrementScore(key, imageId, 1);
        redisTemplate.expire(key, MONTHLY_HISTORY_TTL_DAYS, TimeUnit.DAYS);
        log.info("月榜写入: key={}, imageId={}, country={}, month={}", key, imageId, country, monthKey);
    }

    private String normalizeCountry(String country) {
        if (country == null || country.isBlank()) {
            return "all";
        }
        String normalized = country.trim().toLowerCase();
        return ALLOWED_COUNTRIES.contains(normalized) ? normalized : "all";
    }

    private String buildCountryKey(String prefix, String country, String suffix) {
        return prefix + normalizeCountry(country) + ":" + suffix;
    }

    /**
     * 定时清理过期的实时窗口（每小时执行）
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void cleanExpiredRealtimeKeys() {
        long now = System.currentTimeMillis() / 60000;
        long threshold = now - 10; // 保留10分钟内的数据

        Set<String> keys = redisTemplate.keys(REALTIME_KEY + "*");
        if (keys != null) {
            for (String key : keys) {
                try {
                    long timestamp = Long.parseLong(key.replace(REALTIME_KEY, ""));
                    if (timestamp < threshold) {
                        redisTemplate.delete(key);
                        log.info("清理过期实时窗口: {}", key);
                    }
                } catch (NumberFormatException e) {
                    log.warn("无效的key格式: {}", key);
                }
            }
        }
    }

    private String getIsoWeekKey(LocalDate date) {
        WeekFields weekFields = WeekFields.ISO;
        int week = date.get(weekFields.weekOfWeekBasedYear());
        int year = date.get(weekFields.weekBasedYear());
        return String.format("%d-W%02d", year, week);
    }

    /**
     * 热门图片 DTO
     */
    public static class HotImage {
        private String imageId;
        private Long viewCount;
        private Integer rank;

        public HotImage(String imageId, Long viewCount) {
            this.imageId = imageId;
            this.viewCount = viewCount;
        }

        // Getters & Setters
        public String getImageId() { return imageId; }
        public void setImageId(String imageId) { this.imageId = imageId; }
        public Long getViewCount() { return viewCount; }
        public void setViewCount(Long viewCount) { this.viewCount = viewCount; }
        public Integer getRank() { return rank; }
        public void setRank(Integer rank) { this.rank = rank; }
    }
}
