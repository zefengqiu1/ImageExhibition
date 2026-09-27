package com.worker1.worker1.service;

import com.worker1.worker1.store.db.VideoDataRepository;
import com.worker1.worker1.store.model.VideoManageData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@ConditionalOnProperty(name = "feature.elasticsearch.enabled", havingValue = "true", matchIfMissing = true)
public class BatchIndexService {

    @Autowired
    private VideoSearchService videoSearchService;

    @Autowired
    private IndexManagementService indexManagementService;

    @Autowired
    private VideoDataRepository videoDataRepository;

    private final AtomicInteger batchSize = new AtomicInteger(200);

    public CompletableFuture<String> rebuildIndex() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.warn("开始重建视频 Elasticsearch 索引");

                log.info("步骤 1/4: 删除旧视频索引");
                videoSearchService.deleteAllIndexes();

                Thread.sleep(1000);

                log.info("步骤 2/4: 创建视频索引");
                if (!indexManagementService.ensureIndexExists()) {
                    return "创建视频索引失败，无法继续重建";
                }

                log.info("步骤 3/4: 从 MongoDB 获取已发布视频");
                List<VideoManageData> allVideos = videoDataRepository.findByAuditStatusAndPublishStatus(
                        VideoManageData.AUDIT_APPROVED,
                        VideoManageData.PUBLISH_PUBLISHED);

                List<VideoManageData> indexableVideos = filterAndDeduplicate(allVideos);
                int totalOriginal = allVideos.size();
                int totalUnique = indexableVideos.size();
                int skipped = totalOriginal - totalUnique;

                log.info("视频数据过滤完成: 原始 {} 条, 可索引 {} 条, 跳过 {} 条",
                        totalOriginal, totalUnique, skipped);

                log.info("步骤 4/4: 批量索引视频标题到 Elasticsearch");
                int size = batchSize.get();
                List<Integer> counts = indexInBatches(indexableVideos, size, totalUnique);
                int successCount = counts.get(0);
                int failCount = counts.get(1);

                String result = String.format(
                        "视频索引重建完成。原始数据: %d 条, 可索引: %d 条, 成功索引: %d 条, 失败: %d 条",
                        totalOriginal, totalUnique, successCount, failCount);

                log.info(result);
                return result;
            } catch (Exception e) {
                log.error("重建视频索引失败: {}", e.getMessage(), e);
                return "重建视频索引失败：" + e.getMessage();
            }
        });
    }

    public Map<String, Object> deduplicateIndex() {
        try {
            log.info("开始清理重复视频标题");
            Map<String, Long> duplicates = videoSearchService.findDuplicateTitles();

            if (duplicates.isEmpty()) {
                return Map.of(
                        "success", true,
                        "message", "没有发现重复视频标题",
                        "duplicatesFound", 0,
                        "duplicatesRemoved", 0);
            }

            CompletableFuture<String> rebuildFuture = rebuildIndex();
            String rebuildResult = rebuildFuture.get();

            return Map.of(
                    "success", true,
                    "message", "重复视频标题清理完成",
                    "duplicatesFound", duplicates.size(),
                    "rebuildResult", rebuildResult);
        } catch (Exception e) {
            log.error("清理重复视频标题失败", e);
            return Map.of(
                    "success", false,
                    "message", "清理失败: " + e.getMessage());
        }
    }

    public void setBatchSize(int size) {
        if (size > 0 && size <= 1000) {
            batchSize.set(size);
            log.info("视频索引批次大小设置为: {}", size);
        } else {
            log.warn("批次大小必须在 1-1000 之间");
        }
    }

    public Map<String, Object> getIndexStats() {
        Map<String, Object> stats = new HashMap<>();

        try {
            long esCount = videoSearchService.getTotalRecordCount();
            long mongoCount = videoDataRepository.findByAuditStatusAndPublishStatus(
                    VideoManageData.AUDIT_APPROVED,
                    VideoManageData.PUBLISH_PUBLISHED).stream()
                    .filter(this::isIndexable)
                    .count();

            Map<String, Long> duplicates = videoSearchService.findDuplicateTitles();

            stats.put("elasticsearchCount", esCount);
            stats.put("mongodbCount", mongoCount);
            stats.put("difference", Math.abs(esCount - mongoCount));
            stats.put("duplicateTitles", duplicates.size());
            stats.put("duplicateDetails", duplicates);
            stats.put("timestamp", System.currentTimeMillis());
            stats.put("success", true);
            return stats;
        } catch (Exception e) {
            log.error("获取视频索引统计信息失败", e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            errorResponse.put("errorType", e.getClass().getSimpleName());
            errorResponse.put("timestamp", System.currentTimeMillis());
            return errorResponse;
        }
    }

    private List<VideoManageData> filterAndDeduplicate(List<VideoManageData> videos) {
        Map<String, VideoManageData> uniqueVideos = new LinkedHashMap<>();
        for (VideoManageData video : videos) {
            if (!isIndexable(video)) {
                continue;
            }
            uniqueVideos.put(video.getId(), video);
        }
        return new ArrayList<>(uniqueVideos.values());
    }

    private boolean isIndexable(VideoManageData video) {
        return video != null
                && video.getId() != null
                && !video.getId().trim().isEmpty()
                && video.getTitle() != null
                && !video.getTitle().trim().isEmpty();
    }

    private List<Integer> indexInBatches(List<VideoManageData> videos, int size, int totalUnique) throws InterruptedException {
        int batches = (int) Math.ceil((double) totalUnique / size);
        int successCount = 0;
        int failCount = 0;

        for (int i = 0; i < batches; i++) {
            int start = i * size;
            int end = Math.min(start + size, totalUnique);
            List<VideoManageData> batch = videos.subList(start, end);

            boolean batchSuccess = videoSearchService.addVideosToIndex(batch);
            if (batchSuccess) {
                successCount += batch.size();
                log.info("视频索引批次 {}/{} 已完成: {}/{}", i + 1, batches, end, totalUnique);
            } else {
                failCount += batch.size();
                log.error("视频索引批次 {}/{} 处理失败", i + 1, batches);
            }

            if (i < batches - 1) {
                Thread.sleep(100);
            }
        }

        return List.of(successCount, failCount);
    }
}
