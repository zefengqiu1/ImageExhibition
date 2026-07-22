package com.worker1.worker1.controller;

import com.worker1.worker1.service.DbImageUrlService;
import com.worker1.worker1.service.ImageHotRankService;
import com.worker1.worker1.service.ImageHotRankService.HotImage;
import com.worker1.worker1.store.model.DbImageUrl;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 图片热度排行榜控制器
 */
@RestController
@RequestMapping("/api/images/rank")
@CrossOrigin("*")
@Slf4j
public class HotRankController {

    @Autowired
    private ImageHotRankService hotRankService;

    @Autowired
    private DbImageUrlService dbImageUrlService;

    /**
     * 获取实时热榜（过去5分钟）
     */
    @GetMapping("/realtime")
    public ResponseEntity<RankResponse> getRealtimeRank(
            @RequestParam(defaultValue = "10") int top,
            @RequestParam(defaultValue = "all") String country) {
        try {
            List<HotImage> hotImages = hotRankService.getRealtimeTop(top, country);
            List<RankItem> items = enrichRankData(hotImages);

            return ResponseEntity.ok(RankResponse.builder()
                    .success(true)
                    .type("realtime")
                    .title("实时热榜（5分钟）")
                    .data(items)
                    .total(items.size())
                    .build());
        } catch (Exception e) {
            log.error("获取实时热榜失败", e);
            return ResponseEntity.ok(RankResponse.builder()
                    .success(false)
                    .message("获取失败: " + e.getMessage())
                    .build());
        }
    }

    /**
     * 获取日榜
     */
    @GetMapping("/daily")
    public ResponseEntity<RankResponse> getDailyRank(
            @RequestParam(defaultValue = "10") int top,
            @RequestParam(defaultValue = "all") String country) {
        try {
            List<HotImage> hotImages = hotRankService.getDailyTop(top, country);
            List<RankItem> items = enrichRankData(hotImages);

            return ResponseEntity.ok(RankResponse.builder()
                    .success(true)
                    .type("daily")
                    .title("今日热榜")
                    .data(items)
                    .total(items.size())
                    .build());
        } catch (Exception e) {
            log.error("获取日榜失败", e);
            return ResponseEntity.ok(RankResponse.builder()
                    .success(false)
                    .message("获取失败: " + e.getMessage())
                    .build());
        }
    }

    /**
     * 获取周榜
     */
    @GetMapping("/weekly")
    public ResponseEntity<RankResponse> getWeeklyRank(
            @RequestParam(defaultValue = "10") int top,
            @RequestParam(defaultValue = "all") String country) {
        try {
            List<HotImage> hotImages = hotRankService.getWeeklyTop(top, country);
            List<RankItem> items = enrichRankData(hotImages);

            return ResponseEntity.ok(RankResponse.builder()
                    .success(true)
                    .type("weekly")
                    .title("本周热榜")
                    .data(items)
                    .total(items.size())
                    .build());
        } catch (Exception e) {
            log.error("获取周榜失败", e);
            return ResponseEntity.ok(RankResponse.builder()
                    .success(false)
                    .message("获取失败: " + e.getMessage())
                    .build());
        }
    }

    /**
     * 获取历史周榜
     */
    @GetMapping("/weekly/history")
    public ResponseEntity<RankResponse> getWeeklyHistoryRank(
            @RequestParam(defaultValue = "10") int top,
            @RequestParam(defaultValue = "all") String country,
            @RequestParam String week) {
        try {
            List<HotImage> hotImages = hotRankService.getWeeklyTopByWeek(top, country, week);
            List<RankItem> items = enrichRankData(hotImages);

            return ResponseEntity.ok(RankResponse.builder()
                    .success(true)
                    .type("weekly-history")
                    .title("历史周榜")
                    .data(items)
                    .total(items.size())
                    .build());
        } catch (Exception e) {
            log.error("获取历史周榜失败: week={}", week, e);
            return ResponseEntity.ok(RankResponse.builder()
                    .success(false)
                    .message("获取失败: " + e.getMessage())
                    .build());
        }
    }

    /**
     * 获取月榜
     */
    @GetMapping("/monthly")
    public ResponseEntity<RankResponse> getMonthlyRank(
            @RequestParam(defaultValue = "10") int top,
            @RequestParam(defaultValue = "all") String country) {
        try {
            List<HotImage> hotImages = hotRankService.getMonthlyTop(top, country);
            List<RankItem> items = enrichRankData(hotImages);

            return ResponseEntity.ok(RankResponse.builder()
                    .success(true)
                    .type("monthly")
                    .title("本月热榜")
                    .data(items)
                    .total(items.size())
                    .build());
        } catch (Exception e) {
            log.error("获取月榜失败", e);
            return ResponseEntity.ok(RankResponse.builder()
                    .success(false)
                    .message("获取失败: " + e.getMessage())
                    .build());
        }
    }

    /**
     * 获取历史月榜
     */
    @GetMapping("/monthly/history")
    public ResponseEntity<RankResponse> getMonthlyHistoryRank(
            @RequestParam(defaultValue = "10") int top,
            @RequestParam(defaultValue = "all") String country,
            @RequestParam String month) {
        try {
            List<HotImage> hotImages = hotRankService.getMonthlyTopByMonth(top, country, month);
            List<RankItem> items = enrichRankData(hotImages);

            return ResponseEntity.ok(RankResponse.builder()
                    .success(true)
                    .type("monthly-history")
                    .title("历史月榜")
                    .data(items)
                    .total(items.size())
                    .build());
        } catch (Exception e) {
            log.error("获取历史月榜失败: month={}", month, e);
            return ResponseEntity.ok(RankResponse.builder()
                    .success(false)
                    .message("获取失败: " + e.getMessage())
                    .build());
        }
    }

    /**
     * 获取所有榜单（一次性返回）
     */
    @GetMapping("/all")
    public ResponseEntity<Map<String, Object>> getAllRanks(
            @RequestParam(defaultValue = "10") int top,
            @RequestParam(defaultValue = "all") String country) {
        try {
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("realtime", enrichRankData(hotRankService.getRealtimeTop(top, country)));
            result.put("daily", enrichRankData(hotRankService.getDailyTop(top, country)));
            result.put("weekly", enrichRankData(hotRankService.getWeeklyTop(top, country)));
            result.put("monthly", enrichRankData(hotRankService.getMonthlyTop(top, country)));

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("获取所有榜单失败", e);
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "message", "获取失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 补充图片详细信息
     * ⚠️ 关键修改：imageId 改为使用 title 字段
     */
    private List<RankItem> enrichRankData(List<HotImage> hotImages) {
        List<RankItem> items = new ArrayList<>();

        for (int i = 0; i < hotImages.size(); i++) {
            HotImage hot = hotImages.get(i);
            RankItem item = new RankItem();
            item.setRank(i + 1);
            item.setImageId(hot.getImageId());
            item.setViewCount(hot.getViewCount());

            // 从数据库获取图片详细信息
            try {
                // ✅ 关键：imageId 实际上就是 title（因为 @Id 是 title）
                DbImageUrl dbImage = dbImageUrlService.getDbImageUrlById(hot.getImageId());
                if (dbImage != null) {
                    item.setTitle(dbImage.getTitle());  // ✅ 设置 title 供前端使用
                    item.setWebsite(dbImage.getWebsite());
                    item.setCountry(dbImage.getCountry());

                    // 只返回第一张图片URL（缩略图）
                    if (dbImage.getImageUrl() != null && !dbImage.getImageUrl().isEmpty()) {
                        item.setThumbnail(dbImage.getImageUrl().get(0));
                    }

                    item.setLabels(dbImage.getLabels());
                }
            } catch (Exception e) {
                log.warn("获取图片详情失败: imageId={}", hot.getImageId(), e);
            }

            items.add(item);
        }

        return items;
    }

    // DTO 类
    @Data
    public static class ViewRequest {
        private String imageId;
        private String country;
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
        private String imageId;      // imageId（实际是 title）
        private String title;        // ✅ 明确的 title 字段供前端使用
        private String website;
        private String country;
        private String thumbnail;
        private List<String> labels;
        private Long viewCount;
    }
}
