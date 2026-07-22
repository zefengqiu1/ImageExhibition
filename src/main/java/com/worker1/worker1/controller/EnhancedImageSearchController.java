package com.worker1.worker1.controller;

import com.worker1.worker1.model.ImageSearchResult;
import com.worker1.worker1.model.SearchPage;
import com.worker1.worker1.service.ImageSearchService;
import com.worker1.worker1.service.SearchLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 图片搜索控制器
 * 职责：处理搜索请求，不包含索引管理功能（已移至 ElasticsearchAdminController）
 */
@RestController
@RequestMapping("/api/images")
@CrossOrigin("*")
@Slf4j
@RequiredArgsConstructor
public class EnhancedImageSearchController {

    private final ImageSearchService imageSearchService;
    private final SearchLogService searchLogService;

    @GetMapping("/search")
    public SearchPage<ImageSearchResult> searchImages(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "all") String country,
            @RequestParam(required = false) String time) {

        log.info("search request: keyword='{}', page={}, size={}, country='{}', time='{}'",
                keyword, page, size, country, time);

        try {
            searchLogService.logSearch(keyword);
            return imageSearchService.searchImagesByPage(keyword, page, size, country, time);

        } catch (Exception e) {
            log.error("search failed: keyword='{}', page={}, size={}, country='{}', time='{}'",
                    keyword, page, size, country, time, e);
            return new SearchPage<>(0, Collections.emptyList());
        }
    }

    /**
     * 健康检查
     * GET /api/images/health
     */
    @GetMapping("/health")
    public Map<String, Object> healthCheck() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("service", "Image Search Service");
        response.put("timestamp", System.currentTimeMillis());
        return response;
    }
}
