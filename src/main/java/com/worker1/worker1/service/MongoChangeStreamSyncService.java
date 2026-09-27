package com.worker1.worker1.service;

import com.mongodb.client.model.changestream.ChangeStreamDocument;
import com.mongodb.client.model.changestream.FullDocument;
import com.mongodb.client.model.changestream.OperationType;
import com.worker1.worker1.store.model.VideoManageData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.messaging.ChangeStreamRequest;
import org.springframework.data.mongodb.core.messaging.DefaultMessageListenerContainer;
import org.springframework.data.mongodb.core.messaging.Message;
import org.springframework.data.mongodb.core.messaging.Subscription;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "feature.elasticsearch.enabled", havingValue = "true", matchIfMissing = true)
public class MongoChangeStreamSyncService implements SmartLifecycle {

    private final MongoTemplate mongoTemplate;
    private final VideoSearchService videoSearchService;
    private final IndexManagementService indexManagementService;

    @Value("${mongo.sync.collection:videoData}")
    private String collection;

    @Value("${mongo.sync.buffer.max-size:50}")
    private int bufferMaxSize;

    @Value("${mongo.sync.buffer.flush-interval-ms:500}")
    private long flushIntervalMs;

    private volatile boolean running = false;
    private DefaultMessageListenerContainer container;
    private Subscription subscription;
    private ScheduledExecutorService scheduler;

    private final Object bufferLock = new Object();
    private final List<VideoManageData> bufferedUpserts = new ArrayList<>();

    @Override
    public void start() {
        if (running) {
            return;
        }
        indexManagementService.ensureIndexExists();

        container = new DefaultMessageListenerContainer(mongoTemplate);

        ChangeStreamRequest<Document> request = ChangeStreamRequest.builder(this::handleMessage)
                .collection(collection)
                .fullDocumentLookup(FullDocument.UPDATE_LOOKUP)
                .build();

        subscription = container.register(request, Document.class);
        container.start();

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "mongo-video-change-stream-flush");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleWithFixedDelay(this::flushBufferSafe,
                flushIntervalMs, flushIntervalMs, TimeUnit.MILLISECONDS);

        running = true;
        log.info("Mongo video Change Stream 已启动: collection={}", collection);
    }

    @Override
    public void stop() {
        if (!running) {
            return;
        }
        if (subscription != null) {
            subscription.cancel();
        }
        if (container != null) {
            container.stop();
        }
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
        flushBufferSafe();
        running = false;
        log.info("Mongo video Change Stream 已停止");
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }

    private void handleMessage(Message<ChangeStreamDocument<Document>, Document> message) {
        ChangeStreamDocument<Document> raw = message.getRaw();
        if (raw == null || raw.getOperationType() == null) {
            return;
        }

        try {
            OperationType op = raw.getOperationType();
            switch (op) {
                case INSERT:
                case REPLACE:
                case UPDATE:
                    upsertDocument(raw.getFullDocument());
                    break;
                case DELETE:
                    deleteDocument(raw.getDocumentKey());
                    break;
                default:
                    log.debug("忽略 Mongo video 操作类型: {}", op);
            }
        } catch (Exception e) {
            log.error("处理 Mongo video Change Stream 失败: op={}, error={}",
                    raw.getOperationType(), e.getMessage(), e);
        }
    }

    private void upsertDocument(Document fullDocument) {
        if (fullDocument == null) {
            log.warn("video fullDocument 为空，跳过更新");
            return;
        }

        VideoManageData video = mapToVideoManageData(fullDocument);
        if (!isPublicIndexable(video)) {
            deleteFromIndex(video);
            return;
        }

        boolean shouldFlush = false;
        synchronized (bufferLock) {
            bufferedUpserts.add(video);
            shouldFlush = bufferedUpserts.size() >= Math.max(1, bufferMaxSize);
        }

        if (shouldFlush) {
            flushBufferSafe();
        }
    }

    private void deleteDocument(BsonDocument documentKey) {
        String id = extractId(documentKey);
        if (id == null || id.isBlank()) {
            log.warn("无法解析 video documentKey，跳过删除");
            return;
        }

        flushBufferSafe();
        if (!videoSearchService.deleteDocument(id)) {
            log.warn("ES video delete 失败: id={}", id);
        }
    }

    private void flushBufferSafe() {
        List<VideoManageData> batch;
        synchronized (bufferLock) {
            if (bufferedUpserts.isEmpty()) {
                return;
            }
            batch = new ArrayList<>(bufferedUpserts);
            bufferedUpserts.clear();
        }

        try {
            boolean ok = videoSearchService.addVideosToIndex(batch);
            if (!ok) {
                log.warn("ES video 批量 upsert 部分失败: batchSize={}", batch.size());
            }
        } catch (Exception e) {
            log.error("ES video 批量 upsert 失败: batchSize={}, error={}", batch.size(), e.getMessage(), e);
            synchronized (bufferLock) {
                bufferedUpserts.addAll(batch);
            }
        }
    }

    private VideoManageData mapToVideoManageData(Document full) {
        VideoManageData video = new VideoManageData();
        video.setId(getString(full.get("_id")));
        video.setTitle(getString(full.get("title")));
        video.setDescription(getString(full.get("description")));
        video.setImageUrl(getString(full.get("imageUrl")));
        video.setAuditStatus(getString(full.get("auditStatus")));
        video.setPublishStatus(getString(full.get("publishStatus")));
        return video;
    }

    private boolean isPublicIndexable(VideoManageData video) {
        return video != null
                && video.getId() != null
                && !video.getId().isBlank()
                && video.getTitle() != null
                && !video.getTitle().isBlank()
                && VideoManageData.AUDIT_APPROVED.equals(video.getAuditStatus())
                && VideoManageData.PUBLISH_PUBLISHED.equals(video.getPublishStatus());
    }

    private void deleteFromIndex(VideoManageData video) {
        if (video == null || video.getId() == null || video.getId().isBlank()) {
            return;
        }
        videoSearchService.deleteDocument(video.getId());
    }

    private String extractId(BsonDocument key) {
        if (key == null) {
            return null;
        }
        if (key.containsKey("_id")) {
            return getString(key.get("_id"));
        }
        return null;
    }

    private String getString(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String s) {
            return s;
        }
        if (value instanceof BsonValue bson) {
            if (bson.isString()) {
                return bson.asString().getValue();
            }
            if (bson.isObjectId()) {
                return bson.asObjectId().getValue().toHexString();
            }
            return bson.toString();
        }
        return value.toString();
    }
}
