package com.worker1.worker1.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.mapping.FieldType;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.DeleteResponse;
import co.elastic.clients.elasticsearch.core.ExistsRequest;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.indices.DeleteIndexRequest;
import co.elastic.clients.elasticsearch.indices.DeleteIndexResponse;
import com.worker1.worker1.model.SearchPage;
import com.worker1.worker1.model.VideoSearchResult;
import com.worker1.worker1.store.model.VideoManageData;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "feature.elasticsearch.enabled", havingValue = "true", matchIfMissing = true)
public class VideoSearchService {

    private static final String INDEX_NAME = "videos";

    private final ElasticsearchClient elasticsearchClient;

    @PostConstruct
    public void init() {
        if (elasticsearchClient == null) {
            log.error("ElasticsearchClient 未初始化");
        }
    }

    public SearchPage<VideoSearchResult> searchVideosByPage(String keyword, int page, int size) {
        int pageSize = size > 0 ? size : 10;
        int pageIndex = Math.max(page, 0);
        int from = pageIndex * pageSize;

        try {
            var response = elasticsearchClient.search(s -> s
                            .index(INDEX_NAME)
                            .from(from)
                            .size(pageSize)
                            .sort(so -> so.score(sc -> sc.order(SortOrder.Desc)))
                            .sort(so -> so.field(f -> f.field("title.keyword")
                                    .order(SortOrder.Asc)
                                    .unmappedType(FieldType.Keyword)))
                            .query(q -> {
                                if (keyword == null || keyword.trim().isEmpty()) {
                                    return q.matchAll(m -> m);
                                }
                                return q.match(m -> m.field("title").query(keyword.trim()));
                            }),
                    Map.class);

            List<VideoSearchResult> list = response.hits().hits().stream()
                    .map(this::mapHitToSearchResult)
                    .collect(Collectors.toList());
            long total = response.hits().total() != null ? response.hits().total().value() : list.size();
            return new SearchPage<>(total, list);
        } catch (Exception e) {
            log.error("视频分页搜索失败: keyword={}, page={}, size={}, error={}",
                    keyword, pageIndex, pageSize, e.getMessage(), e);
            return new SearchPage<>(0, List.of());
        }
    }

    public boolean addVideosToIndex(List<VideoManageData> videos) {
        if (videos == null || videos.isEmpty()) {
            log.warn("添加的视频列表为空");
            return true;
        }

        try {
            BulkRequest.Builder bulkBuilder = new BulkRequest.Builder();
            int addedCount = 0;

            for (VideoManageData video : videos) {
                String documentId = safeString(video.getId());
                String title = safeString(video.getTitle());
                if (documentId.isBlank() || title.isBlank()) {
                    log.warn("跳过无效视频索引: id={}, title={}", documentId, title);
                    continue;
                }

                bulkBuilder.operations(op -> op
                        .index(idx -> idx
                                .index(INDEX_NAME)
                                .id(documentId)
                                .document(createIndexDocument(video))));
                addedCount++;
            }

            if (addedCount == 0) {
                log.warn("没有有效的视频可添加到索引");
                return true;
            }

            BulkResponse bulkResponse = elasticsearchClient.bulk(bulkBuilder.build());
            if (bulkResponse.errors()) {
                int errorCount = 0;
                for (BulkResponseItem item : bulkResponse.items()) {
                    if (item.error() != null) {
                        log.error("视频文档 {} 索引失败: {}", item.id(), item.error().reason());
                        errorCount++;
                    }
                }
                log.warn("批量视频索引完成，但有 {} 个错误", errorCount);
                return errorCount < addedCount;
            }

            log.info("成功批量添加/更新 {} 个视频到索引", addedCount);
            return true;
        } catch (IOException e) {
            log.error("批量添加视频到索引时发生 IO 异常", e);
            return false;
        } catch (Exception e) {
            log.error("批量添加视频到索引失败", e);
            return false;
        }
    }

    public boolean addVideoToIndex(VideoManageData video) {
        try {
            String documentId = safeString(video.getId());
            String title = safeString(video.getTitle());
            if (documentId.isBlank() || title.isBlank()) {
                log.warn("无法添加视频索引: id={}, title={}", documentId, title);
                return false;
            }

            IndexResponse response = elasticsearchClient.index(i -> i
                    .index(INDEX_NAME)
                    .id(documentId)
                    .document(createIndexDocument(video)));

            log.info("视频索引成功: id={}, result={}", documentId, response.result());
            return true;
        } catch (Exception e) {
            log.error("添加单个视频到索引失败: id={}", video != null ? video.getId() : null, e);
            return false;
        }
    }

    public boolean documentExists(String id) {
        try {
            ExistsRequest request = ExistsRequest.of(e -> e
                    .index(INDEX_NAME)
                    .id(id));
            return elasticsearchClient.exists(request).value();
        } catch (Exception e) {
            log.error("检查视频文档是否存在失败: id={}", id, e);
            return false;
        }
    }

    public boolean deleteDocument(String id) {
        try {
            DeleteResponse response = elasticsearchClient.delete(d -> d
                    .index(INDEX_NAME)
                    .id(id));
            log.info("删除视频文档: id={}, result={}", id, response.result());
            return true;
        } catch (Exception e) {
            log.error("删除视频文档失败: id={}", id, e);
            return false;
        }
    }

    public boolean deleteAllIndexes() {
        try {
            DeleteIndexRequest deleteRequest = DeleteIndexRequest.of(d -> d.index(INDEX_NAME));
            DeleteIndexResponse deleteResponse = elasticsearchClient.indices().delete(deleteRequest);

            boolean acknowledged = deleteResponse.acknowledged();
            if (acknowledged) {
                log.info("成功删除 {} 索引", INDEX_NAME);
            } else {
                log.warn("删除 {} 索引未被确认", INDEX_NAME);
            }
            return acknowledged;
        } catch (IOException e) {
            log.error("删除视频索引时发生错误", e);
            return false;
        } catch (Exception e) {
            log.warn("删除视频索引时发生异常，可能索引不存在: {}", e.getMessage());
            return false;
        }
    }

    public long getTotalRecordCount() {
        try {
            var response = elasticsearchClient.search(s -> s
                            .index(INDEX_NAME)
                            .size(0)
                            .query(q -> q.matchAll(m -> m)),
                    Map.class);

            return response.hits().total() != null ? response.hits().total().value() : 0;
        } catch (Exception e) {
            log.error("获取视频索引总数失败: error={}", e.getMessage());
            return 0;
        }
    }

    public Map<String, Long> findDuplicateTitles() {
        try {
            var response = elasticsearchClient.search(s -> s
                            .index(INDEX_NAME)
                            .size(0)
                            .aggregations("duplicate_titles", a -> a
                                    .terms(t -> t
                                            .field("title.keyword")
                                            .minDocCount(2)
                                            .size(100))),
                    Map.class);

            Map<String, Long> duplicates = new HashMap<>();
            var agg = response.aggregations().get("duplicate_titles");
            if (agg != null && agg.isSterms()) {
                agg.sterms().buckets().array().forEach(bucket ->
                        duplicates.put(bucket.key().stringValue(), bucket.docCount()));
            }
            return duplicates;
        } catch (Exception e) {
            log.error("查找重复视频标题失败", e);
            return Collections.emptyMap();
        }
    }

    private Map<String, Object> createIndexDocument(VideoManageData video) {
        Map<String, Object> doc = new HashMap<>();
        doc.put("title", safeString(video.getTitle()));
        doc.put("description", safeString(video.getDescription()));
        doc.put("imageUrl", safeString(video.getImageUrl()));
        return doc;
    }

    private VideoSearchResult mapHitToSearchResult(Hit<Map> hit) {
        Map<String, Object> src = hit.source();

        VideoSearchResult result = new VideoSearchResult();
        result.setId(hit.id());

        String title = src != null ? safeString(src.get("title")) : "";
        result.setTitle(title);
        result.setDescription(src != null ? safeString(src.get("description")) : "");
        result.setImageUrl(src != null ? safeString(src.get("imageUrl")) : "");
        result.setHighlightTitle(getHighlightTitle(hit, title));
        return result;
    }

    private String getHighlightTitle(Hit<Map> hit, String fallback) {
        if (hit.highlight() == null) {
            return fallback;
        }
        List<String> titles = hit.highlight().get("title");
        if (titles == null || titles.isEmpty()) {
            return fallback;
        }
        return titles.get(0);
    }

    private String safeString(Object obj) {
        return obj != null ? obj.toString() : "";
    }
}
