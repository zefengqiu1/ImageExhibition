package com.worker1.worker1.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.mapping.FieldType;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.*;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.indices.DeleteIndexRequest;
import co.elastic.clients.elasticsearch.indices.DeleteIndexResponse;
import com.worker1.worker1.model.ImageSearchResult;
import com.worker1.worker1.model.SearchPage;
import com.worker1.worker1.store.model.DbImageUrl;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ImageSearchService {

    private final ElasticsearchClient elasticsearchClient;

    private static final String INDEX_NAME = "images";


    @PostConstruct
    public void init() {
        if (elasticsearchClient == null) {
            log.error("ElasticsearchClient 未初始化！");
        }
    }

    public SearchPage<ImageSearchResult> searchImagesByPage(
            String keyword,
            int page,
            int size,
            String country,
            String time) {
        int pageSize = size > 0 ? size : 10;
        int pageIndex = Math.max(page, 0);
        int from = pageIndex * pageSize;

        try {
            boolean useRelevance = isBlank(time);
            boolean hasKeyword = !isBlank(keyword);
            SortOrder timeOrder = resolveTimeOrder(time);
            var response = elasticsearchClient.search(s -> {
                        var sb = s.index(INDEX_NAME)
                                .from(from)
                                .size(pageSize);
                        if (useRelevance && hasKeyword) {
                            sb = sb.sort(so -> so.score(sc -> sc.order(SortOrder.Desc)));
                        }
                        sb = sb.sort(so -> so.field(f -> f.field("createdAt")
                                        .order(timeOrder)
                                        .unmappedType(FieldType.Long)))
                                .sort(so -> so.field(f -> f.field("id.keyword")
                                        .order(SortOrder.Asc)
                                        .unmappedType(FieldType.Keyword)))
                                .query(q -> buildQuery(keyword, country));
                        return sb;
                    },
                    Map.class);

            List<ImageSearchResult> list = response.hits().hits().stream()
                    .map(this::mapHitToSearchResult)
                    .collect(Collectors.toList());
            long total = response.hits().total() != null ? response.hits().total().value() : list.size();
            return new SearchPage<>(total, list);
        } catch (Exception e) {
            log.error("分页搜索失败: keyword={}, page={}, size={}, country={}, time={}, error={}",
                    keyword, pageIndex, pageSize, country, time, e.getMessage(), e);
            return new SearchPage<>(0, List.of());
        }
    }

    /**
     * 批量添加图片到索引 - 使用 title 作为唯一 ID，防止重复
     */
    public boolean addImagesToIndex(List<DbImageUrl> images) {
        if (images == null || images.isEmpty()) {
            log.warn("添加的图片列表为空");
            return true;
        }

        try {
            BulkRequest.Builder bulkBuilder = new BulkRequest.Builder();
            int addedCount = 0;

            for (DbImageUrl image : images) {
                String documentId = image.getTitle();

                if (documentId == null || documentId.trim().isEmpty()) {
                    log.warn("跳过无效图片：title 为空");
                    continue;
                }

                Map<String, Object> document = createIndexDocument(image);

                bulkBuilder.operations(op -> op
                        .index(idx -> idx
                                .index(INDEX_NAME)
                                .id(documentId)
                                .document(document)
                        )
                );
                addedCount++;
            }

            if (addedCount == 0) {
                log.warn("没有有效的图片可添加");
                return true;
            }

            BulkResponse bulkResponse = elasticsearchClient.bulk(bulkBuilder.build());

            // 检查错误
            if (bulkResponse.errors()) {
                int errorCount = 0;
                for (BulkResponseItem item : bulkResponse.items()) {
                    if (item.error() != null) {
                        log.error("文档 {} 索引失败: {}", item.id(), item.error().reason());
                        errorCount++;
                    }
                }
                log.warn("批量索引完成，但有 {} 个错误", errorCount);
                return errorCount < addedCount; // 只要有部分成功就返回 true
            } else {
                log.info("成功批量添加/更新 {} 个图片到索引", addedCount);
                return true;
            }

        } catch (IOException e) {
            log.error("批量添加图片到索引时发生IO异常", e);
            return false;
        } catch (Exception e) {
            log.error("批量添加图片到索引时发生未知异常", e);
            return false;
        }
    }

    /**
     * 添加单个图片到索引
     */
    public boolean addImageToIndex(DbImageUrl image) {
        try {
            String documentId = image.getTitle();

            if (documentId == null || documentId.trim().isEmpty()) {
                log.warn("无法添加图片：title 为空");
                return false;
            }

            Map<String, Object> document = createIndexDocument(image);

            // 使用 index API（如果文档存在则更新）
            IndexResponse response = elasticsearchClient.index(i -> i
                    .index(INDEX_NAME)
                    .id(documentId)
                    .document(document)
            );

            log.info("图片索引成功: id={}, result={}", documentId, response.result());
            return true;

        } catch (Exception e) {
            log.error("添加单个图片到索引失败: title={}", image.getTitle(), e);
            return false;
        }
    }

    /**
     * 检查文档是否存在
     */
    public boolean documentExists(String title) {
        try {
            ExistsRequest request = ExistsRequest.of(e -> e
                    .index(INDEX_NAME)
                    .id(title)
            );
            return elasticsearchClient.exists(request).value();
        } catch (Exception e) {
            log.error("检查文档是否存在失败: title={}", title, e);
            return false;
        }
    }

    /**
     * 删除单个文档
     */
    public boolean deleteDocument(String title) {
        try {
            DeleteResponse response = elasticsearchClient.delete(d -> d
                    .index(INDEX_NAME)
                    .id(title)
            );
            log.info("删除文档: id={}, result={}", title, response.result());
            return true;
        } catch (Exception e) {
            log.error("删除文档失败: title={}", title, e);
            return false;
        }
    }

    /**
     * 删除所有索引
     */
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
            log.error("删除索引时发生错误", e);
            return false;
        } catch (Exception e) {
            log.warn("删除索引时发生异常，可能索引不存在: {}", e.getMessage());
            return false;
        }
    }

    public long getTotalCount(String keyword) {
        try {
            var response = elasticsearchClient.search(s -> s
                            .index(INDEX_NAME)
                            .size(0)
                            .query(q -> buildQuery(keyword, null)),
                    Map.class
            );

            return response.hits().total() != null ? response.hits().total().value() : 0;
        } catch (Exception e) {
            log.error("获取总数失败: keyword={}, error={}", keyword, e.getMessage());
            return 0;
        }
    }

    public long getTotalRecordCount() {
        try {
            var response = elasticsearchClient.search(s -> s
                            .index(INDEX_NAME)
                            .size(0)
                            .query(q -> q.matchAll(m -> m)),
                    Map.class
            );

            return response.hits().total() != null ? response.hits().total().value() : 0;
        } catch (Exception e) {
            log.error("获取总记录数失败: error={}", e.getMessage());
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
                                            .minDocCount(2)  // 只返回出现2次以上的
                                            .size(100)
                                    )
                            ),
                    Map.class
            );

            Map<String, Long> duplicates = new HashMap<>();
            var agg = response.aggregations().get("duplicate_titles");

            if (agg != null && agg.isSterms()) {
                agg.sterms().buckets().array().forEach(bucket -> {
                    duplicates.put(bucket.key().stringValue(), bucket.docCount());
                });
            }

            return duplicates;

        } catch (Exception e) {
            log.error("查找重复文档失败", e);
            return Collections.emptyMap();
        }
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 将 DbImageUrl 转换为索引文档
     */
    private Map<String, Object> createIndexDocument(DbImageUrl dbImageUrl) {
        Map<String, Object> doc = new HashMap<>();

        String title = safeString(dbImageUrl.getTitle());
        doc.put("id", title);
        doc.put("title", title);
        doc.put("website", safeString(dbImageUrl.getWebsite()));
        doc.put("country", safeString(dbImageUrl.getCountry()));
        doc.put("createdAt", dbImageUrl.getCreatedAt() != null ? dbImageUrl.getCreatedAt() : System.currentTimeMillis());
        doc.put("imageUrls", safeList(dbImageUrl.getImageUrl()));
        doc.put("labels", safeList(dbImageUrl.getLabels()));

        return doc;
    }

    /**
     * 将搜索结果映射为 ImageSearchResult
     */
    private ImageSearchResult mapHitToSearchResult(Hit<Map> hit) {
        Map<String, Object> src = hit.source();
        if (src == null) {
            return createEmptyResult(hit);
        }

        ImageSearchResult r = new ImageSearchResult();
        r.setId(safeString(src.get("id")));
        r.setTitle(safeString(src.get("title")));
        r.setWebsite(safeString(src.get("website")));
        r.setCountry(safeString(src.get("country")));
        r.setScore(hit.score());

        r.setCreatedAt(getLongValue(src.get("createdAt")));

        r.setImageUrls(getStringList(src.get("imageUrls")));

        r.setHighlightTitle(getHighlightTitle(hit, r.getTitle()));

        Object labels = src.get("labels");
        r.setMatchedKeywords(getStringList(labels));

        return r;
    }

    private String safeString(Object obj) {
        return obj != null ? obj.toString() : "";
    }

    private Long getLongValue(Object obj) {
        if (obj == null) {
            return System.currentTimeMillis();
        }
        if (obj instanceof Long) {
            return (Long) obj;
        }
        if (obj instanceof Integer) {
            return ((Integer) obj).longValue();
        }
        if (obj instanceof String) {
            try {
                return Long.parseLong((String) obj);
            } catch (NumberFormatException e) {
                return System.currentTimeMillis();
            }
        }
        return System.currentTimeMillis();
    }

    private ImageSearchResult createEmptyResult(Hit<Map> hit) {
        ImageSearchResult result = new ImageSearchResult();
        result.setId("");
        result.setTitle("");
        result.setWebsite("");
        result.setCountry("");
        result.setScore(hit.score());
        result.setImageUrls(List.of());
        result.setHighlightTitle("");
        result.setCreatedAt(System.currentTimeMillis());
        result.setMatchedKeywords(List.of());
        return result;
    }

    private Query.Builder buildQuery(String keyword, String country) {
        boolean hasKeyword = !isBlank(keyword);
        boolean hasCountry = !isBlank(country);

        if (!hasKeyword && !hasCountry) {
            Query.Builder queryBuilder = new Query.Builder();
            queryBuilder.matchAll(m -> m);
            return queryBuilder;
        }
        Query.Builder queryBuilder = new Query.Builder();
        queryBuilder.bool(b -> {
            if (hasKeyword) {
                b.must(m -> m.match(t -> t.field("title").query(keyword)));
            }
            if (hasCountry && !"all".equals(country)) {
                b.filter(f -> f.term(t -> t.field("country.keyword").value(country)));
            }
            return b;
        });
        return queryBuilder;
    }

    private SortOrder resolveTimeOrder(String time) {
        if ("oldest".equalsIgnoreCase(time)) {
            return SortOrder.Asc;
        }
        return SortOrder.Desc;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
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

    private List<String> getStringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream()
                    .map(Object::toString)
                    .collect(Collectors.toList());
        }
        return List.of();
    }

    private <T> List<T> safeList(List<T> value) {
        return value != null ? value : new ArrayList<>();
    }
}
