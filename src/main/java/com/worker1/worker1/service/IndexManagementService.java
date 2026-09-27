package com.worker1.worker1.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.indices.CreateIndexResponse;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Slf4j
@Service
@ConditionalOnProperty(name = "feature.elasticsearch.enabled", havingValue = "true", matchIfMissing = true)
public class IndexManagementService {

    private static final String INDEX_NAME = "videos";

    @Autowired
    private ElasticsearchClient elasticsearchClient;

    public boolean createVideoIndex() {
        try {
            boolean indexExists = indexExists();
            if (indexExists) {
                log.info("视频索引已存在，先删除旧索引");
                elasticsearchClient.indices().delete(d -> d.index(INDEX_NAME));
            }

            CreateIndexResponse response = elasticsearchClient.indices().create(c -> c
                    .index(INDEX_NAME)
                    .mappings(m -> m
                            .properties("title", p -> p
                                    .text(t -> t
                                            .fields("keyword", f -> f.keyword(k -> k.ignoreAbove(256)))))
                            .properties("description", p -> p.text(t -> t))
                            .properties("imageUrl", p -> p.keyword(k -> k))));

            boolean acknowledged = response.acknowledged();
            if (acknowledged) {
                log.info("成功创建 {} 索引", INDEX_NAME);
            } else {
                log.warn("创建视频索引未被确认");
            }

            return acknowledged;
        } catch (IOException e) {
            log.error("创建视频索引失败", e);
            return false;
        }
    }

    public boolean ensureIndexExists() {
        try {
            boolean indexExists = indexExists();
            if (!indexExists) {
                log.warn("视频索引不存在，正在创建");
                return createVideoIndex();
            }

            log.info("视频索引已存在");
            return true;
        } catch (IOException e) {
            log.error("检查视频索引失败", e);
            return false;
        }
    }

    private boolean indexExists() throws IOException {
        return elasticsearchClient.indices()
                .exists(ExistsRequest.of(e -> e.index(INDEX_NAME)))
                .value();
    }
}
