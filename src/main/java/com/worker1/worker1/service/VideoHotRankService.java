package com.worker1.worker1.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@ConditionalOnProperty(name = "feature.redis.enabled", havingValue = "true", matchIfMissing = true)
public class VideoHotRankService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired(required = false)
    private VideoViewEventProducer eventProducer;

    private static final String REALTIME_KEY = "video:rank:realtime:";
    private static final String DAILY_KEY = "video:rank:daily:";
    private static final String WEEKLY_KEY = "video:rank:weekly:";
    private static final String MONTHLY_KEY = "video:rank:monthly:";
    private static final DateTimeFormatter DAY_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyyMM");
    private static final long WEEKLY_HISTORY_TTL_DAYS = 180;
    private static final long MONTHLY_HISTORY_TTL_DAYS = 540;
    private static final Set<String> ALLOWED_CATEGORIES =
            Set.of("movie", "drama", "variety", "all");

    public void recordView(String videoId, String category) {
        if (videoId == null || videoId.isBlank()) {
            log.warn("跳过视频浏览记录: videoId 为空");
            return;
        }

        long now = System.currentTimeMillis();
        String timestamp = String.valueOf(now / 60000);
        String normalizedCategory = normalizeCategory(category);

        if (eventProducer != null) {
            eventProducer.sendViewEvent(videoId);
        }

        recordRealtime(videoId, normalizedCategory, timestamp);
        recordRealtime(videoId, "all", timestamp);

        LocalDate today = LocalDate.now();
        String dayKey = today.format(DAY_FORMATTER);
        recordDaily(videoId, normalizedCategory, dayKey);
        recordDaily(videoId, "all", dayKey);

        String weekKey = getIsoWeekKey(today);
        recordWeekly(videoId, normalizedCategory, weekKey);
        recordWeekly(videoId, "all", weekKey);

        String monthKey = YearMonth.from(today).format(MONTH_FORMATTER);
        recordMonthly(videoId, normalizedCategory, monthKey);
        recordMonthly(videoId, "all", monthKey);

        log.info("记录视频浏览: videoId={}, category={}, timestamp={}", videoId, normalizedCategory, timestamp);
    }

    public List<HotVideo> getRealtimeTop(int topN, String category) {
        int safeTopN = normalizeTopN(topN);
        if (safeTopN == 0) {
            return Collections.emptyList();
        }

        String normalizedCategory = normalizeCategory(category);
        long now = System.currentTimeMillis() / 60000;
        Map<String, Double> scoreMap = new HashMap<>();

        for (int i = 0; i < 5; i++) {
            String key = buildCategoryKey(REALTIME_KEY, normalizedCategory, String.valueOf(now - i));
            Set<ZSetOperations.TypedTuple<String>> tuples =
                    redisTemplate.opsForZSet().reverseRangeWithScores(key, 0, -1);

            if (tuples != null) {
                for (ZSetOperations.TypedTuple<String> tuple : tuples) {
                    String videoId = tuple.getValue();
                    Double score = tuple.getScore();
                    if (videoId != null && score != null) {
                        scoreMap.merge(videoId, score, Double::sum);
                    }
                }
            }
        }

        return scoreMap.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(safeTopN)
                .map(e -> new HotVideo(e.getKey(), e.getValue().longValue()))
                .collect(Collectors.toList());
    }

    public List<HotVideo> getDailyTop(int topN, String category) {
        String today = LocalDate.now().format(DAY_FORMATTER);
        String key = buildCategoryKey(DAILY_KEY, normalizeCategory(category), today);
        return getTopFromZSet(key, topN, "daily");
    }

    public List<HotVideo> getWeeklyTop(int topN, String category) {
        String weekKey = getIsoWeekKey(LocalDate.now());
        String key = buildCategoryKey(WEEKLY_KEY, normalizeCategory(category), weekKey);
        return getTopFromZSet(key, topN, "weekly");
    }

    public List<HotVideo> getWeeklyTopByWeek(int topN, String category, String weekKey) {
        String key = buildCategoryKey(WEEKLY_KEY, normalizeCategory(category), weekKey);
        return getTopFromZSet(key, topN, "weekly-history");
    }

    public List<HotVideo> getMonthlyTop(int topN, String category) {
        String monthKey = YearMonth.now().format(MONTH_FORMATTER);
        String key = buildCategoryKey(MONTHLY_KEY, normalizeCategory(category), monthKey);
        return getTopFromZSet(key, topN, "monthly");
    }

    public List<HotVideo> getMonthlyTopByMonth(int topN, String category, String monthKey) {
        String key = buildCategoryKey(MONTHLY_KEY, normalizeCategory(category), monthKey);
        return getTopFromZSet(key, topN, "monthly-history");
    }

    private List<HotVideo> getTopFromZSet(String key, int topN, String type) {
        int safeTopN = normalizeTopN(topN);
        if (safeTopN == 0) {
            return Collections.emptyList();
        }

        Set<ZSetOperations.TypedTuple<String>> tuples =
                redisTemplate.opsForZSet().reverseRangeWithScores(key, 0, safeTopN - 1);

        if (tuples == null || tuples.isEmpty()) {
            log.info("视频榜单读取: key={}, type={}, empty", key, type);
            return Collections.emptyList();
        }

        List<HotVideo> result = tuples.stream()
                .filter(t -> t.getValue() != null && t.getScore() != null)
                .map(t -> new HotVideo(t.getValue(), t.getScore().longValue()))
                .collect(Collectors.toList());

        log.info("视频榜单读取: key={}, type={}, size={}", key, type, result.size());
        return result;
    }

    private void recordRealtime(String videoId, String category, String timestamp) {
        String windowKey = buildCategoryKey(REALTIME_KEY, category, timestamp);
        redisTemplate.opsForZSet().incrementScore(windowKey, videoId, 1);
        redisTemplate.expire(windowKey, 10, TimeUnit.MINUTES);
        log.info("视频实时榜写入: key={}, videoId={}, category={}, minute={}", windowKey, videoId, category, timestamp);
    }

    private void recordDaily(String videoId, String category, String today) {
        String key = buildCategoryKey(DAILY_KEY, category, today);
        redisTemplate.opsForZSet().incrementScore(key, videoId, 1);
        redisTemplate.expire(key, 7, TimeUnit.DAYS);
        log.info("视频日榜写入: key={}, videoId={}, category={}, date={}", key, videoId, category, today);
    }

    private void recordWeekly(String videoId, String category, String weekKey) {
        String key = buildCategoryKey(WEEKLY_KEY, category, weekKey);
        redisTemplate.opsForZSet().incrementScore(key, videoId, 1);
        redisTemplate.expire(key, WEEKLY_HISTORY_TTL_DAYS, TimeUnit.DAYS);
        log.info("视频周榜写入: key={}, videoId={}, category={}, week={}", key, videoId, category, weekKey);
    }

    private void recordMonthly(String videoId, String category, String monthKey) {
        String key = buildCategoryKey(MONTHLY_KEY, category, monthKey);
        redisTemplate.opsForZSet().incrementScore(key, videoId, 1);
        redisTemplate.expire(key, MONTHLY_HISTORY_TTL_DAYS, TimeUnit.DAYS);
        log.info("视频月榜写入: key={}, videoId={}, category={}, month={}", key, videoId, category, monthKey);
    }

    private String normalizeCategory(String category) {
        if (category == null || category.isBlank()) {
            return "all";
        }
        String normalized = category.trim().toLowerCase();
        return ALLOWED_CATEGORIES.contains(normalized) ? normalized : "all";
    }

    private int normalizeTopN(int topN) {
        return Math.max(topN, 0);
    }

    private String buildCategoryKey(String prefix, String category, String suffix) {
        return prefix + normalizeCategory(category) + ":" + suffix;
    }

    @Scheduled(cron = "0 0 * * * ?")
    public void cleanExpiredRealtimeKeys() {
        long now = System.currentTimeMillis() / 60000;
        long threshold = now - 10;

        Set<String> keys = redisTemplate.keys(REALTIME_KEY + "*");
        if (keys != null) {
            for (String key : keys) {
                try {
                    String timestamp = key.substring(key.lastIndexOf(':') + 1);
                    if (Long.parseLong(timestamp) < threshold) {
                        redisTemplate.delete(key);
                        log.info("清理过期视频实时窗口: {}", key);
                    }
                } catch (NumberFormatException e) {
                    log.warn("无效的视频实时榜 key: {}", key);
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

    public static class HotVideo {
        private String videoId;
        private Long viewCount;

        public HotVideo(String videoId, Long viewCount) {
            this.videoId = videoId;
            this.viewCount = viewCount;
        }

        public String getVideoId() {
            return videoId;
        }

        public void setVideoId(String videoId) {
            this.videoId = videoId;
        }

        public Long getViewCount() {
            return viewCount;
        }

        public void setViewCount(Long viewCount) {
            this.viewCount = viewCount;
        }
    }
}
