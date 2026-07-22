package com.worker1.worker1.service;

import com.worker1.worker1.store.model.DbImageUrl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Slf4j
@Service
public class BatchIndexService {

    @Autowired
    private ImageSearchService imageSearchService;

    @Autowired
    private IndexManagementService indexManagementService;

    @Autowired
    private DbImageUrlService dbImageUrlService;

    private final AtomicInteger batchSize = new AtomicInteger(200);

    /**
     * 异步重建 Elasticsearch 索引（防重复版本）
     */
    public CompletableFuture<String> rebuildIndex() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.warn("⚠️ 开始重建 Elasticsearch 索引...");

                // 1. 删除旧索引
                log.info("步骤 1/4: 删除旧索引");
                imageSearchService.deleteAllIndexes();

                // 等待一秒确保删除完成
                Thread.sleep(1000);

                // 2. 创建新索引
                log.info("步骤 2/4: 创建新索引");
                if (!indexManagementService.ensureIndexExists()) {
                    return "创建索引失败，无法继续重建";
                }

                // 3. 从 MongoDB 获取所有数据
                log.info("步骤 3/4: 从 MongoDB 获取数据");
                List<DbImageUrl> allImages = dbImageUrlService.getAllDbImageUrls();

                // ✅ 去重：按 title 去重（因为 title 是主键）
                List<DbImageUrl> deduplicatedImages = deduplicateByTitle(allImages);
                int totalOriginal = allImages.size();
                int totalUnique = deduplicatedImages.size();
                int duplicatesRemoved = totalOriginal - totalUnique;

                log.info("数据去重完成: 原始 {} 条, 去重后 {} 条, 移除重复 {} 条",
                        totalOriginal, totalUnique, duplicatesRemoved);

                // 4. 批量索引
                log.info("步骤 4/4: 批量索引到 Elasticsearch");
                int size = batchSize.get();
                int batches = (int) Math.ceil((double) totalUnique / size);

                int successCount = 0;
                int failCount = 0;

                List<Integer> counts = indexInBatches(deduplicatedImages, size, totalUnique);
                successCount = counts.get(0);
                failCount = counts.get(1);

                String result = String.format(
                        "索引重建完成！原始数据: %d 条, 去重后: %d 条, 成功索引: %d 条, 失败: %d 条",
                        totalOriginal, totalUnique, successCount, failCount
                );

                log.info(result);

                return result;

            } catch (Exception e) {
                log.error("重建索引失败: {}", e.getMessage(), e);
                return "重建索引失败：" + e.getMessage();
            }
        });
    }

    /**
     * 清理重复文档（手动触发）
     */
    public Map<String, Object> deduplicateIndex() {
        try {
            log.info("开始清理重复文档...");

            // 1. 查找重复的 title
            Map<String, Long> duplicates = imageSearchService.findDuplicateTitles();

            if (duplicates.isEmpty()) {
                return Map.of(
                        "success", true,
                        "message", "没有发现重复文档",
                        "duplicatesFound", 0,
                        "duplicatesRemoved", 0
                );
            }

            log.warn("发现 {} 个重复的标题", duplicates.size());

            // 2. 重建索引来清理重复（最简单的方法）
            CompletableFuture<String> rebuildFuture = rebuildIndex();
            String rebuildResult = rebuildFuture.get();

            return Map.of(
                    "success", true,
                    "message", "重复文档清理完成",
                    "duplicatesFound", duplicates.size(),
                    "rebuildResult", rebuildResult
            );

        } catch (Exception e) {
            log.error("清理重复文档失败", e);
            return Map.of(
                    "success", false,
                    "message", "清理失败: " + e.getMessage()
            );
        }
    }

    /**
     * 设置批次大小
     */
    public void setBatchSize(int size) {
        if (size > 0 && size <= 1000) {
            batchSize.set(size);
            log.info("批次大小设置为: {}", size);
        } else {
            log.warn("批次大小必须在 1-1000 之间");
        }
    }

    /**
     * 获取索引统计信息
     */
    public Map<String, Object> getIndexStats() {
        Map<String, Object> stats = new HashMap<>();

        try {
            // 1. 安全获取 ES 总数
            long esCount = 0;
            try {
                if (imageSearchService != null) {
                    esCount = imageSearchService.getTotalRecordCount();
                } else {
                    log.error("ImageSearchService 未注入");
                    stats.put("elasticsearchError", "Service not initialized");
                }
            } catch (Exception e) {
                log.error("获取 ES 记录数失败", e);
                stats.put("elasticsearchError", e.getMessage());
            }
            stats.put("elasticsearchCount", esCount);

            // 2. 安全获取 MongoDB 总数
            long mongoCount = 0;
            try {
                if (dbImageUrlService != null) {
                    List<DbImageUrl> allImages = dbImageUrlService.getAllDbImageUrls();
                    mongoCount = allImages != null ? allImages.size() : 0;
                } else {
                    log.error("DbImageUrlService 未注入");
                    stats.put("mongodbError", "Service not initialized");
                }
            } catch (Exception e) {
                log.error("获取 MongoDB 记录数失败", e);
                stats.put("mongodbError", e.getMessage());
            }
            stats.put("mongodbCount", mongoCount);

            // 3. 计算差异
            stats.put("difference", Math.abs(esCount - mongoCount));

            // 4. 安全获取重复信息
            Map<String, Long> duplicates = new HashMap<>();
            try {
                if (imageSearchService != null) {
                    duplicates = imageSearchService.findDuplicateTitles();
                    if (duplicates == null) {
                        duplicates = new HashMap<>();
                    }
                }
            } catch (Exception e) {
                log.error("查找重复文档失败", e);
                stats.put("duplicatesError", e.getMessage());
            }
            stats.put("duplicateTitles", duplicates.size());
            stats.put("duplicateDetails", duplicates);

            stats.put("timestamp", System.currentTimeMillis());
            stats.put("success", true);

            return stats;

        } catch (Exception e) {
            log.error("获取索引统计信息时发生异常", e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            errorResponse.put("errorType", e.getClass().getSimpleName());
            errorResponse.put("timestamp", System.currentTimeMillis());

            return errorResponse;
        }
    }

    private List<DbImageUrl> deduplicateByTitle(List<DbImageUrl> images) {
        Map<String, DbImageUrl> uniqueImages = images.stream()
                .filter(img -> img.getTitle() != null && !img.getTitle().trim().isEmpty())
                .collect(Collectors.toMap(
                        DbImageUrl::getTitle,
                        img -> img,
                        (existing, replacement) -> {
                            if (replacement.getCreatedAt() != null && existing.getCreatedAt() != null) {
                                return replacement.getCreatedAt() > existing.getCreatedAt()
                                        ? replacement : existing;
                            }
                            return existing;
                        }
                ));
        return new ArrayList<>(uniqueImages.values());
    }

    private List<Integer> indexInBatches(List<DbImageUrl> images, int size, int totalUnique) throws InterruptedException {
        int batches = (int) Math.ceil((double) totalUnique / size);
        int successCount = 0;
        int failCount = 0;

        for (int i = 0; i < batches; i++) {
            int start = i * size;
            int end = Math.min(start + size, totalUnique);
            List<DbImageUrl> batch = images.subList(start, end);

            boolean batchSuccess = imageSearchService.addImagesToIndex(batch);

            if (batchSuccess) {
                successCount += batch.size();
                log.info("批次 {}/{} 已完成: {}/{} (成功率: {}/{} = {}%)",
                        i + 1, batches, end, totalUnique,
                        successCount, end,
                        String.format("%.2f", (double) successCount / end * 100));
            } else {
                failCount += batch.size();
                log.error("批次 {}/{} 处理失败", i + 1, batches);
            }

            if (i < batches - 1) {
                Thread.sleep(100);
            }
        }

        return List.of(successCount, failCount);
    }

}
