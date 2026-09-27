package com.worker1.worker1.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.JsonData;
import com.worker1.worker1.model.SearchLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "feature.elasticsearch.enabled", havingValue = "true", matchIfMissing = true)
public class SearchLogService {

    private static final String INDEX = "search_logs";
    private static final String HOT_KEYWORDS_CACHE_PREFIX = "hot_search_keywords:";
    private static final int HOT_KEYWORDS_WINDOW_DAYS = 7;
    private static final int DEFAULT_CACHE_MINUTES = 10;

    private final ElasticsearchClient esClient;
    private final StringRedisTemplate redisTemplate;

    /**
     * 记录搜索关键词到 ES。
     * 这里先做关键词归一化，避免同一个词因为大小写或空格被拆成多个桶。
     */
    public void logSearch(String keyword) {
        String normalizedKeyword = normalizeKeyword(keyword);
        if (normalizedKeyword == null) {
            return;
        }

        SearchLog searchlog = new SearchLog();
        searchlog.setKeyword(normalizedKeyword);
        searchlog.setTimestamp(System.currentTimeMillis());

        try {
            esClient.index(i -> i
                    .index(INDEX)
                    .document(searchlog)
            );
            invalidateHotKeywordsCache();
        } catch (IOException e) {
            log.error("记录搜索日志失败", e);
        }
    }

    /**
     * 获取近 7 天热词。
     * 先读 Redis 缓存；缓存未命中时再回源 ES 做聚合。
     */
    public List<String> getHotKeywords(int topN) {
        int safeTopN = normalizeTopN(topN);
        String cacheKey = buildHotKeywordsCacheKey(safeTopN);
        List<String> cachedKeywords = getHotKeywordsFromCache(cacheKey);
        if (cachedKeywords != null) {
            return cachedKeywords;
        }

        long windowStart = System.currentTimeMillis() - Duration.ofDays(HOT_KEYWORDS_WINDOW_DAYS).toMillis();
        try {
            var response = esClient.search(s -> s
                    .index(INDEX)
                    .size(0) // 不需要返回文档
                    .query(q -> q
                            .range(r -> r
                                    .field("timestamp")
                                    .gte(JsonData.of(windowStart))
                            )
                    )
                    .aggregations("hot_keywords", a -> a
                            .terms(t -> t
                                    .field("keyword.keyword") // 使用 keyword 类型
                                    .size(safeTopN)
                            )
                    ), Map.class);

            var agg = response.aggregations().get("hot_keywords").sterms();
            if (agg != null && agg.buckets() != null) {
                List<String> keywords = agg.buckets().array().stream()
                        .map(b -> b.key().stringValue())
                        .collect(Collectors.toList());
                cacheHotKeywords(cacheKey, keywords);
                return keywords;
            }

        } catch (IOException e) {
            log.error("获取热门搜索失败", e);
        }

        return Collections.emptyList();
    }

    /**
     * 统一关键词格式，减少脏数据。
     */
    private String normalizeKeyword(String keyword) {
        if (keyword == null) {
            return null;
        }

        String normalized = keyword.trim().toLowerCase(Locale.ROOT);
        return normalized.isEmpty() ? null : normalized;
    }

    private int normalizeTopN(int topN) {
        if (topN <= 0) {
            return 10;
        }
        return Math.min(topN, 50);
    }

    private String buildHotKeywordsCacheKey(int topN) {
        return HOT_KEYWORDS_CACHE_PREFIX + HOT_KEYWORDS_WINDOW_DAYS + "d:top:" + topN;
    }

    /**
     * 用 Redis List 缓存榜单结果，避免每次都实时聚合 ES。
     */
    private List<String> getHotKeywordsFromCache(String cacheKey) {
        Long size = redisTemplate.opsForList().size(cacheKey);
        if (size == null) {
            return null;
        }
        if (size == 0) {
            return Collections.emptyList();
        }

        List<String> cachedKeywords = redisTemplate.opsForList().range(cacheKey, 0, -1);
        if (cachedKeywords == null) {
            return null;
        }

        log.info("读取热词缓存: key={}, size={}", cacheKey, cachedKeywords.size());
        return cachedKeywords;
    }

    private void cacheHotKeywords(String cacheKey, List<String> keywords) {
        redisTemplate.delete(cacheKey);
        if (!keywords.isEmpty()) {
            redisTemplate.opsForList().rightPushAll(cacheKey, keywords);
        }
        redisTemplate.expire(cacheKey, Duration.ofMinutes(DEFAULT_CACHE_MINUTES));
        log.info("写入热词缓存: key={}, size={}", cacheKey, keywords.size());
    }

    private void invalidateHotKeywordsCache() {
        Set<String> cacheKeys = redisTemplate.keys(HOT_KEYWORDS_CACHE_PREFIX + "*");
        if (cacheKeys == null || cacheKeys.isEmpty()) {
            return;
        }
        redisTemplate.delete(cacheKeys);
        log.info("清理热词缓存: size={}", cacheKeys.size());
    }
}
