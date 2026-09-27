package com.worker1.worker1.controller;

import com.worker1.worker1.service.VideoDataService;
import com.worker1.worker1.service.VideoHotRankService;
import com.worker1.worker1.service.VideoHotRankService.HotVideo;
import com.worker1.worker1.store.model.VideoData;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/videos/rank")
@CrossOrigin("*")
@Slf4j
@ConditionalOnProperty(name = "feature.redis.enabled", havingValue = "true", matchIfMissing = true)
public class HotRankController {

    @Autowired
    private VideoHotRankService hotRankService;

    @Autowired
    private VideoDataService videoDataService;

    @GetMapping("/realtime")
    public ResponseEntity<RankResponse> getRealtimeRank(
            @RequestParam(defaultValue = "10") int top,
            @RequestParam(defaultValue = "all") String category) {
        try {
            List<RankItem> items = enrichRankData(hotRankService.getRealtimeTop(top, category));

            return ResponseEntity.ok(RankResponse.builder()
                    .success(true)
                    .type("realtime")
                    .title("实时热榜（5分钟）")
                    .data(items)
                    .total(items.size())
                    .build());
        } catch (Exception e) {
            log.error("获取视频实时热榜失败", e);
            return buildFailureResponse(e);
        }
    }

    @GetMapping("/daily")
    public ResponseEntity<RankResponse> getDailyRank(
            @RequestParam(defaultValue = "10") int top,
            @RequestParam(defaultValue = "all") String category) {
        try {
            List<RankItem> items = enrichRankData(hotRankService.getDailyTop(top, category));

            return ResponseEntity.ok(RankResponse.builder()
                    .success(true)
                    .type("daily")
                    .title("今日热榜")
                    .data(items)
                    .total(items.size())
                    .build());
        } catch (Exception e) {
            log.error("获取视频日榜失败", e);
            return buildFailureResponse(e);
        }
    }

    @GetMapping("/weekly")
    public ResponseEntity<RankResponse> getWeeklyRank(
            @RequestParam(defaultValue = "10") int top,
            @RequestParam(defaultValue = "all") String category) {
        try {
            List<RankItem> items = enrichRankData(hotRankService.getWeeklyTop(top, category));

            return ResponseEntity.ok(RankResponse.builder()
                    .success(true)
                    .type("weekly")
                    .title("本周热榜")
                    .data(items)
                    .total(items.size())
                    .build());
        } catch (Exception e) {
            log.error("获取视频周榜失败", e);
            return buildFailureResponse(e);
        }
    }

    @GetMapping("/weekly/history")
    public ResponseEntity<RankResponse> getWeeklyHistoryRank(
            @RequestParam(defaultValue = "10") int top,
            @RequestParam(defaultValue = "all") String category,
            @RequestParam String week) {
        try {
            List<RankItem> items = enrichRankData(hotRankService.getWeeklyTopByWeek(top, category, week));

            return ResponseEntity.ok(RankResponse.builder()
                    .success(true)
                    .type("weekly-history")
                    .title("历史周榜")
                    .data(items)
                    .total(items.size())
                    .build());
        } catch (Exception e) {
            log.error("获取视频历史周榜失败: week={}", week, e);
            return buildFailureResponse(e);
        }
    }

    @GetMapping("/monthly")
    public ResponseEntity<RankResponse> getMonthlyRank(
            @RequestParam(defaultValue = "10") int top,
            @RequestParam(defaultValue = "all") String category) {
        try {
            List<RankItem> items = enrichRankData(hotRankService.getMonthlyTop(top, category));

            return ResponseEntity.ok(RankResponse.builder()
                    .success(true)
                    .type("monthly")
                    .title("本月热榜")
                    .data(items)
                    .total(items.size())
                    .build());
        } catch (Exception e) {
            log.error("获取视频月榜失败", e);
            return buildFailureResponse(e);
        }
    }

    @GetMapping("/monthly/history")
    public ResponseEntity<RankResponse> getMonthlyHistoryRank(
            @RequestParam(defaultValue = "10") int top,
            @RequestParam(defaultValue = "all") String category,
            @RequestParam String month) {
        try {
            List<RankItem> items = enrichRankData(hotRankService.getMonthlyTopByMonth(top, category, month));

            return ResponseEntity.ok(RankResponse.builder()
                    .success(true)
                    .type("monthly-history")
                    .title("历史月榜")
                    .data(items)
                    .total(items.size())
                    .build());
        } catch (Exception e) {
            log.error("获取视频历史月榜失败: month={}", month, e);
            return buildFailureResponse(e);
        }
    }

    @GetMapping("/all")
    public ResponseEntity<Map<String, Object>> getAllRanks(
            @RequestParam(defaultValue = "10") int top,
            @RequestParam(defaultValue = "all") String category) {
        try {
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("realtime", enrichRankData(hotRankService.getRealtimeTop(top, category)));
            result.put("daily", enrichRankData(hotRankService.getDailyTop(top, category)));
            result.put("weekly", enrichRankData(hotRankService.getWeeklyTop(top, category)));
            result.put("monthly", enrichRankData(hotRankService.getMonthlyTop(top, category)));

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("获取所有视频榜单失败", e);
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "message", "获取失败: " + e.getMessage()));
        }
    }

    private List<RankItem> enrichRankData(List<HotVideo> hotVideos) {
        List<RankItem> items = new ArrayList<>();

        for (int i = 0; i < hotVideos.size(); i++) {
            HotVideo hot = hotVideos.get(i);
            RankItem item = new RankItem();
            item.setRank(i + 1);
            item.setVideoId(hot.getVideoId());
            item.setViewCount(hot.getViewCount());

            try {
                VideoData video = videoDataService.getPublicById(hot.getVideoId());
                if (video != null) {
                    item.setTitle(video.getTitle());
                    item.setDescription(video.getDescription());
                    item.setImageUrl(video.getImageUrl());
                    item.setCategory(video.getCategory());
                    item.setType(video.getType());
                    item.setStatus(video.getStatus());
                }
            } catch (Exception e) {
                log.warn("获取视频热榜详情失败: videoId={}", hot.getVideoId(), e);
            }

            items.add(item);
        }

        return items;
    }

    private ResponseEntity<RankResponse> buildFailureResponse(Exception e) {
        return ResponseEntity.ok(RankResponse.builder()
                .success(false)
                .message("获取失败: " + e.getMessage())
                .build());
    }

    @Data
    public static class ViewRequest {
        private String videoId;
        private String category;
    }

    @Data
    @lombok.Builder
    public static class RankResponse {
        private Boolean success;
        private String type;
        private String title;
        private List<RankItem> data;
        private Integer total;
        private String message;
    }

    @Data
    public static class RankItem {
        private Integer rank;
        private String videoId;
        private String title;
        private String description;
        private String imageUrl;
        private String category;
        private String type;
        private String status;
        private Long viewCount;
    }
}
