package com.worker1.worker1.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.indices.CreateIndexResponse;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Slf4j
@Service
public class IndexManagementService {

    @Autowired
    private ElasticsearchClient elasticsearchClient;

    private static final String INDEX_NAME = "images";

    /**
     * 创建图片索引和映射
     */
    public boolean createImageIndex() {
        try {
            // 检查索引是否存在
            boolean indexExists = indexExists();

            if (indexExists) {
                log.info("索引已存在，先删除旧索引");
                elasticsearchClient.indices().delete(d -> d.index(INDEX_NAME));
            }

            // 创建索引并定义映射
            CreateIndexResponse response = elasticsearchClient.indices().create(c -> c
                    .index(INDEX_NAME)
                    .mappings(m -> m
                            .properties("id", p -> p.keyword(k -> k))
                            .properties("title", p -> p
                                    .text(t -> t
                                            .fields("keyword", f -> f.keyword(k -> k.ignoreAbove(256)))
                                    )
                            )
                            .properties("country", p -> p.keyword(k -> k))
                            .properties("website", p -> p.keyword(k -> k))
                            .properties("imageUrls", p -> p.keyword(k -> k))
                            .properties("labels", p -> p.keyword(k -> k))
                            .properties("createdAt", p -> p.long_(l -> l))
                    )
            );

            boolean acknowledged = response.acknowledged();
            if (acknowledged) {
                log.info("成功创建 {} 索引", INDEX_NAME);
            } else {
                log.warn("创建索引未被确认");
            }

            return acknowledged;

        } catch (IOException e) {
            log.error("创建索引失败", e);
            return false;
        }
    }

    /**
     * 检查并确保索引存在
     */
    public boolean ensureIndexExists() {
        try {
            boolean indexExists = indexExists();

            if (!indexExists) {
                log.warn("索引不存在，正在创建...");
                return createImageIndex();
            }

            log.info("索引已存在");
            return true;

        } catch (IOException e) {
            log.error("检查索引失败", e);
            return false;
        }
    }

    private boolean indexExists() throws IOException {
        return elasticsearchClient.indices()
                .exists(ExistsRequest.of(e -> e.index(INDEX_NAME)))
                .value();
    }
}
