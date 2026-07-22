package com.worker1.worker1.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * 图片浏览事件生产者
 * 将浏览事件发送到 Kafka，供 Flink 消费
 */
@Slf4j
@Service
public class ImageViewEventProducer {

    @Autowired(required = false)
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String TOPIC = "image-view-events";

    /**
     * 发送浏览事件到 Kafka
     * @param imageId 图片ID
     */
    public void sendViewEvent(String imageId) {
        if (kafkaTemplate == null) {
            log.warn("Kafka 未配置，跳过事件发送");
            return;
        }

        try {
            ViewEvent event = new ViewEvent();
            event.setImageId(imageId);
            event.setTimestamp(System.currentTimeMillis());
            event.setEventType("VIEW");

            String json = objectMapper.writeValueAsString(event);

            kafkaTemplate.send(TOPIC, imageId, json)
                    .whenComplete((result, ex) -> {
                        if (ex == null) {
                            log.info("事件发送成功: imageId={}, offset={}",
                                    imageId, result.getRecordMetadata().offset());
                        } else {
                            log.error("事件发送失败: imageId={}", imageId, ex);
                        }
                    });

        } catch (Exception e) {
            log.error("序列化事件失败: imageId={}", imageId, e);
        }
    }

    /**
     * 批量发送事件（性能优化）
     */
    public void sendBatchEvents(String imageId, int count) {
        if (kafkaTemplate == null) return;

        try {
            ViewEvent event = new ViewEvent();
            event.setImageId(imageId);
            event.setTimestamp(System.currentTimeMillis());
            event.setEventType("BATCH_VIEW");
            event.setCount(count);

            String json = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(TOPIC, imageId, json);

        } catch (Exception e) {
            log.error("批量发送失败: imageId={}, count={}", imageId, count, e);
        }
    }

    @Data
    public static class ViewEvent {
        private String imageId;
        private Long timestamp;
        private String eventType;
        private Integer count = 1;
    }
}
