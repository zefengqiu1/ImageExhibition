package com.worker1.worker1.controller;

import com.worker1.worker1.service.BatchIndexService;
import com.worker1.worker1.service.ImageSearchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Elasticsearch 管理接口
 */
@RestController
@RequestMapping("/api/admin/elasticsearch")
@CrossOrigin("*")
@Slf4j
public class ElasticsearchAdminController {

    @Autowired
    private BatchIndexService batchIndexService;

    private HashMap<Integer,Integer> map;
    @Autowired
    private ImageSearchService imageSearchService;

    /**
     * 重建索引
     * POST /api/admin/elasticsearch/rebuild
     */
    @PostMapping("/rebuild")
    public ResponseEntity<Map<String, Object>> rebuildIndex() {
        try {
            log.info("收到重建索引请求");

            CompletableFuture<String> future = batchIndexService.rebuildIndex();

            // 立即返回，不等待完成
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "索引重建任务已启动，请稍后查看日志");
            response.put("taskStatus", "RUNNING");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("启动索引重建失败", e);
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * 删除所有索引
     * DELETE /api/admin/elasticsearch/index
     */
    @DeleteMapping("/index")
    public ResponseEntity<Map<String, Object>> deleteAllIndexes() {
        try {
            log.warn("收到删除索引请求");

            boolean success = imageSearchService.deleteAllIndexes();

            return ResponseEntity.ok(Map.of(
                    "success", success,
                    "message", success ? "索引删除成功" : "索引删除失败"
            ));

        } catch (Exception e) {
            log.error("删除索引失败", e);
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * 清理重复文档
     * POST /api/admin/elasticsearch/deduplicate
     */
    @PostMapping("/deduplicate")
    public ResponseEntity<Map<String, Object>> deduplicateIndex() {
        try {
            log.info("收到清理重复文档请求");

            Map<String, Object> result = batchIndexService.deduplicateIndex();
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("清理重复文档失败", e);
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * 获取索引统计信息
     * GET /api/admin/elasticsearch/stats
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getIndexStats() {
        try {
            Map<String, Object> stats = batchIndexService.getIndexStats();
            stats.put("success", true);
            return ResponseEntity.ok(stats);

        } catch (Exception e) {
            log.error("获取统计信息失败", e);
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * 查找重复文档
     * GET /api/admin/elasticsearch/duplicates
     */
    @GetMapping("/duplicates")
    public ResponseEntity<Map<String, Object>> findDuplicates() {
        try {
            Map<String, Long> duplicates = imageSearchService.findDuplicateTitles();

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "duplicateCount", duplicates.size(),
                    "duplicates", duplicates
            ));

        } catch (Exception e) {
            log.error("查找重复文档失败", e);
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * 设置批次大小
     * PUT /api/admin/elasticsearch/batch-size?size=200
     */
    @PutMapping("/batch-size")
    public ResponseEntity<Map<String, Object>> setBatchSize(@RequestParam int size) {
        try {
            batchIndexService.setBatchSize(size);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "批次大小设置为: " + size
            ));

        } catch (Exception e) {
            log.error("设置批次大小失败", e);
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * 健康检查
     * GET /api/admin/elasticsearch/health
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        try {
            long totalCount = imageSearchService.getTotalRecordCount();

            return ResponseEntity.ok(Map.of(
                    "status", "UP",
                    "service", "Elasticsearch",
                    "totalDocuments", totalCount,
                    "timestamp", System.currentTimeMillis()
            ));

        } catch (Exception e) {
            log.error("健康检查失败", e);
            return ResponseEntity.ok(Map.of(
                    "status", "DOWN",
                    "error", e.getMessage()
            ));
        }
    }
}
