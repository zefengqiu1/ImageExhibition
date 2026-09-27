package com.worker1.worker1.controller;

import com.worker1.worker1.model.SearchPage;
import com.worker1.worker1.model.VideoSearchResult;
import com.worker1.worker1.service.SearchLogService;
import com.worker1.worker1.service.VideoDataService;
import com.worker1.worker1.service.VideoSearchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/videos")
@CrossOrigin("*")
@Slf4j
public class VideoSearchController {

    private final ObjectProvider<VideoSearchService> videoSearchServiceProvider;
    private final ObjectProvider<SearchLogService> searchLogServiceProvider;
    private final VideoDataService videoDataService;

    public VideoSearchController(
            ObjectProvider<VideoSearchService> videoSearchServiceProvider,
            ObjectProvider<SearchLogService> searchLogServiceProvider,
            VideoDataService videoDataService) {
        this.videoSearchServiceProvider = videoSearchServiceProvider;
        this.searchLogServiceProvider = searchLogServiceProvider;
        this.videoDataService = videoDataService;
    }

    @GetMapping("/search")
    public SearchPage<VideoSearchResult> searchVideos(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        log.info("video search request: keyword='{}', page={}, size={}", keyword, page, size);

        try {
            SearchLogService searchLogService = searchLogServiceProvider.getIfAvailable();
            if (searchLogService != null) {
                searchLogService.logSearch(keyword);
            }

            VideoSearchService videoSearchService = videoSearchServiceProvider.getIfAvailable();
            if (videoSearchService != null) {
                return videoSearchService.searchVideosByPage(keyword, page, size);
            }

            int pageSize = size > 0 ? size : 10;
            int pageIndex = Math.max(page, 0);
            Pageable pageable = PageRequest.of(pageIndex, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"));
            return videoDataService.searchPublicByKeyword(keyword, pageable);
        } catch (Exception e) {
            log.error("video search failed: keyword='{}', page={}, size={}", keyword, page, size, e);
            return new SearchPage<>(0, Collections.emptyList());
        }
    }

    @GetMapping("/health")
    public Map<String, Object> healthCheck() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("service", "Video Search Service");
        response.put("timestamp", System.currentTimeMillis());
        return response;
    }
}
