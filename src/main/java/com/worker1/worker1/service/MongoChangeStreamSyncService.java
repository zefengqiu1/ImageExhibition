package com.worker1.worker1.service;

import com.mongodb.client.model.changestream.ChangeStreamDocument;
import com.mongodb.client.model.changestream.FullDocument;
import com.mongodb.client.model.changestream.OperationType;
import com.worker1.worker1.store.model.DbImageUrl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.messaging.ChangeStreamRequest;
import org.springframework.data.mongodb.core.messaging.DefaultMessageListenerContainer;
import org.springframework.data.mongodb.core.messaging.Message;
import org.springframework.data.mongodb.core.messaging.Subscription;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
/*
*
* SmartLifecycle 是 Spring 提供的生命周期回调接口。实现它的 bean 会在应用启动/停止时被自动调用：
•
start()：Spring 启动完成后自动调用，用来启动你的 Change Stream 监听。
•
stop()：Spring 关闭时自动调用，用来取消订阅、停止容器、flush 缓冲。
•
isRunning()：告诉 Spring 你当前是否在运行。
•
getPhase()：控制启动/停止顺序，值越大越晚启动、越早停止。
所以你不需要手动启动监听器，Spring
* */
@Slf4j
@Service
@RequiredArgsConstructor
public class MongoChangeStreamSyncService implements SmartLifecycle {

    private final MongoTemplate mongoTemplate;
    private final ImageSearchService imageSearchService;
    private final IndexManagementService indexManagementService;

    @Value("${mongo.sync.collection:imageurl}")
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
    private final List<DbImageUrl> bufferedUpserts = new ArrayList<>();

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
            Thread t = new Thread(r, "mongo-change-stream-flush");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleWithFixedDelay(this::flushBufferSafe,
                flushIntervalMs, flushIntervalMs, TimeUnit.MILLISECONDS);

        running = true;
        log.info("Mongo Change Stream 已启动: collection={}", collection);
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
        log.info("Mongo Change Stream 已停止");
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
        if (raw == null) {
            return;
        }

        OperationType op = raw.getOperationType();
        if (op == null) {
            return;
        }

        try {
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
                    log.debug("忽略操作类型: {}", op);
            }
        } catch (Exception e) {
            log.error("处理 Change Stream 失败: op={}, error={}", op, e.getMessage(), e);
        }
    }

    private void upsertDocument(Document fullDocument) {
        if (fullDocument == null) {
            log.warn("fullDocument 为空，跳过更新");
            return;
        }

        DbImageUrl doc = mapToDbImageUrl(fullDocument);
        if (doc == null || doc.getTitle() == null || doc.getTitle().isBlank()) {
            log.warn("无效 fullDocument，缺少 title，跳过");
            return;
        }

        boolean shouldFlush = false;
        synchronized (bufferLock) {
            bufferedUpserts.add(doc);
            shouldFlush = bufferedUpserts.size() >= Math.max(1, bufferMaxSize);
        }

        if (shouldFlush) {
            flushBufferSafe();
        }
    }

    private void deleteDocument(BsonDocument documentKey) {
        String id = extractId(documentKey);
        if (id == null || id.isBlank()) {
            log.warn("无法解析 documentKey，跳过删除");
            return;
        }

        flushBufferSafe();

        boolean ok = imageSearchService.deleteDocument(id);
        if (!ok) {
            log.warn("ES delete 失败: title={}", id);
        }
    }

    private void flushBufferSafe() {
        List<DbImageUrl> batch;
        synchronized (bufferLock) {
            if (bufferedUpserts.isEmpty()) {
                return;
            }
            batch = new ArrayList<>(bufferedUpserts);
            bufferedUpserts.clear();
        }

        try {
            boolean ok = imageSearchService.addImagesToIndex(batch);
            if (!ok) {
                log.warn("ES 批量 upsert 部分失败: batchSize={}", batch.size());
            }
        } catch (Exception e) {
            log.error("ES 批量 upsert 失败: batchSize={}, error={}", batch.size(), e.getMessage(), e);
            synchronized (bufferLock) {
                bufferedUpserts.addAll(batch);
            }
        }
    }

    private DbImageUrl mapToDbImageUrl(Document full) {
        String title = full.getString("title");
        if (title == null) {
            title = getString(full.get("_id"));
        }
        List<String> imageUrls = getStringList(full, "imageUrl", "imageUrls");
        List<String> labels = getStringList(full, "labels");
        List<String> keywords = getStringList(full, "keywords");

        Long createdAt = getLong(full.get("createdAt"));
        if (createdAt == null) {
            createdAt = System.currentTimeMillis();
        }

        return DbImageUrl.builder()
                .title(title)
                .imageUrl(imageUrls)
                .website(getString(full.get("website")))
                .labels(labels)
                .country(getString(full.get("country")))
                .createdAt(createdAt)
                .description(getString(full.get("description")))
                .keywords(keywords)
                .build();
    }

    private String extractId(BsonDocument key) {
        if (key == null) {
            return null;
        }
        if (key.containsKey("title")) {
            return getString(key.get("title"));
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
            return bson.toString();
        }
        return value.toString();
    }

    private Long getLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Long l) {
            return l;
        }
        if (value instanceof Integer i) {
            return i.longValue();
        }
        if (value instanceof Date d) {
            return d.getTime();
        }
        if (value instanceof String s) {
            try {
                return Long.parseLong(s);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private List<String> getStringList(Document full, String... keys) {
        for (String key : keys) {
            Object value = full.get(key);
            List<String> list = toStringList(value);
            if (!list.isEmpty()) {
                return list;
            }
        }
        return new ArrayList<>();
    }

    private List<String> toStringList(Object value) {
        if (value == null) {
            return new ArrayList<>();
        }
        if (value instanceof List<?> list) {
            List<String> out = new ArrayList<>();
            for (Object item : list) {
                if (item != null) {
                    out.add(item.toString());
                }
            }
            return out;
        }
        if (value instanceof String s) {
            List<String> out = new ArrayList<>();
            out.add(s);
            return out;
        }
        return new ArrayList<>();
    }
}
/*
*
*
* 会有提升，尤其是吞吐量高时。批量写 ES 的主要好处是减少网络往返和 ES 写入开销，代价是延迟增加和缓冲失败的复杂性增加。
优点：
•
写入吞吐显著更高（bulk API 更适合 ES）。
•
降低 ES 压力和连接数。
缺点：
•
数据从 Mongo 到 ES 的可见性会有延迟（取决于批大小/flush 间隔）。
•
失败处理更复杂（部分失败需要重试或拆分）。
•
需要内存缓冲和后台 flush 机制。
适用场景：
•
变更流 QPS 较高、允许几百毫秒到几秒的同步延迟。
如果你能接受延迟，我可以加一个简单的 buffer：
•
每 N 条或每 T 毫秒 flush 一次。
•
flush 用 addImagesToIndex(List<DbImageUrl>) 批量写*/
