package com.worker1.worker1.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@ConditionalOnProperty(name = "feature.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class VideoViewEventProducer {

    private static final String TOPIC = "video-view-events";

    @Autowired(required = false)
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    public void sendViewEvent(String videoId) {
        if (kafkaTemplate == null) {
            log.warn("Kafka 未配置，跳过视频事件发送");
            return;
        }

        try {
            ViewEvent event = new ViewEvent();
            event.setVideoId(videoId);
            event.setTimestamp(System.currentTimeMillis());
            event.setEventType("VIEW");

            String json = objectMapper.writeValueAsString(event);

            kafkaTemplate.send(TOPIC, videoId, json)
                    .whenComplete((result, ex) -> {
                        if (ex == null) {
                            log.info("视频事件发送成功: videoId={}, offset={}",
                                    videoId, result.getRecordMetadata().offset());
                        } else {
                            log.error("视频事件发送失败: videoId={}", videoId, ex);
                        }
                    });
        } catch (Exception e) {
            log.error("序列化视频事件失败: videoId={}", videoId, e);
        }
    }

    public void sendBatchEvents(String videoId, int count) {
        if (kafkaTemplate == null) {
            return;
        }

        try {
            ViewEvent event = new ViewEvent();
            event.setVideoId(videoId);
            event.setTimestamp(System.currentTimeMillis());
            event.setEventType("BATCH_VIEW");
            event.setCount(count);

            String json = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(TOPIC, videoId, json);
        } catch (Exception e) {
            log.error("批量发送视频事件失败: videoId={}, count={}", videoId, count, e);
        }
    }

    @Data
    public static class ViewEvent {
        private String videoId;
        private Long timestamp;
        private String eventType;
        private Integer count = 1;
    }
}
