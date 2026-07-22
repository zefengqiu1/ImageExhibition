package com.worker1.worker1.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.worker1.worker1.service.ImageHotRankService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 埋点数据接收 Controller
 * 接收前端发送的用户行为数据，发送到 Kafka
 */
@RestController
@RequestMapping("/api/analytics")
@CrossOrigin("*")
@Slf4j
public class AnalyticsController {

    @Autowired(required = false)
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ImageHotRankService hotRankService;

    // Kafka Topics
//    private static final String PAGE_VIEW_TOPIC = "page-view-events";
    private static final String IMAGE_VIEW_TOPIC = "image-view-events";
    private static final String SEARCH_TOPIC = "search-events";

    /**
     * 接收批量埋点事件
     */
    @PostMapping("/track")
    public ResponseEntity<Map<String, Object>> trackEvents(@RequestBody TrackRequest request) {
        try {
            log.info("收到埋点数据: userId={}, sessionId={}, events={}",
                    request.getUserInfo().getUserId(),
                    request.getUserInfo().getSessionId(),
                    request.getEvents().size());



            int successCount = 0;
            int failCount = 0;

            for (EventData event : request.getEvents()) {
                try {
                    // 组装完整事件（包含用户信息）

                    Map<String, Object> fullEvent = new HashMap<>();
                    fullEvent.put("userInfo", request.getUserInfo());
                    fullEvent.put("event", event);
                    hotRankService.recordView(event.getImageId(), event.getCountry());

                    String eventJson = objectMapper.writeValueAsString(fullEvent);

                    // 根据事件类型发送到不同的 Kafka Topic
                    String topic = getTopicByEventType(event.getEventType());
                    String key = request.getUserInfo().getSessionId(); // 用 sessionId 作为 key

                    if (kafkaTemplate != null) {
                        kafkaTemplate.send(topic, key, eventJson);
                        successCount++;
                        log.debug("事件已发送到 Kafka: topic={}, key={}", topic, key);
                    } else {
                        // Kafka 未配置，只记录日志
                        log.warn("Kafka 未配置，事件仅记录日志: {}", eventJson);
                        successCount++;
                    }

                } catch (Exception e) {
                    log.error("处理单个事件失败: eventType={}", event.getEventType(), e);
                    failCount++;
                }
            }

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "事件接收成功",
                    "successCount", successCount,
                    "failCount", failCount
            ));

        } catch (Exception e) {
            log.error("处理埋点数据失败", e);
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "message", "服务异常: " + e.getMessage()
            ));
        }
    }

    /**
     * 根据事件类型获取对应的 Kafka Topic
     */
    private String getTopicByEventType(String eventType) {
        return switch (eventType) {
            case "image_view" -> IMAGE_VIEW_TOPIC;
            case "search" -> SEARCH_TOPIC;
            default -> "unknown-events";
        };
    }

    // ==================== DTO 类 ====================

    @Data
    public static class TrackRequest {
        private UserInfo userInfo;
        private List<EventData> events;
    }

    @Data
    public static class UserInfo {
        private String userId;
        private String sessionId;
        private String deviceType;
        private String browser;
        private String os;
        private String screenResolution;
    }

    @Data
    public static class EventData {
        private String eventType;
        private Long timestamp;

        // page_view 字段
        private String pageType;
        private String pageUrl;
        private String referrer;

        // image_view 字段
        private String imageId;
        private String imageTitle;
        private String country;
        private List<String> labels;
        private Integer viewDuration;
        private Double scrollDepth;

        // search 字段
        private String keyword;
        private Integer resultCount;
        private List<String> clickedResults;
        private List<Integer> clickPositions;
        private String sortType;
        private Map<String, Object> filters;
    }
}