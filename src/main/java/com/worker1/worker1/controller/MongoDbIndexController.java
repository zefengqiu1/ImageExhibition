package com.worker1.worker1.controller;

import com.worker1.worker1.store.model.DbImageUrl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/*
* 用来创建索引的
* */
@RestController
@RequestMapping("/api/index")
public class MongoDbIndexController {

    @Autowired
    private MongoTemplate mongoTemplate;

    /**
     * 创建索引（单字段或复合索引）
     *
     * @param indexFields 索引字段信息
     * @param isCompound 是否是复合索引
     * @return 创建结果
     */
    @PostMapping("/create")
    public String createIndex(@RequestBody List<String> indexFields, @RequestParam boolean isCompound) {
        try {
            IndexOperations indexOperations = mongoTemplate.indexOps(DbImageUrl.class);

            if (isCompound) {
                // 创建复合索引
                Index compoundIndex = new Index();
                for (String field : indexFields) {
                    compoundIndex.on(field, Sort.Direction.ASC);
                }
                indexOperations.ensureIndex(compoundIndex);
                return "Compound index created successfully.";
            } else {
                // 创建单字段索引
                for (String field : indexFields) {
                    Index index = new Index().on(field, Sort.Direction.ASC);
                    indexOperations.ensureIndex(index);
                }
                return "Index created successfully.";
            }
        } catch (Exception e) {
            return "Error creating index: " + e.getMessage();
        }
    }

    /**
     * 删除索引
     *
     * @param indexName 索引名称
     * @return 删除结果
     */
    @DeleteMapping("/delete")
    public String deleteIndex(@RequestParam String indexName) {
        try {
            IndexOperations indexOperations = mongoTemplate.indexOps(DbImageUrl.class);
            indexOperations.dropIndex(indexName);
            return "Index " + indexName + " deleted successfully.";
        } catch (Exception e) {
            return "Error deleting index: " + e.getMessage();
        }
    }

    /**
     * 获取集合的所有索引
     *
     * @return 索引列表
     */
    @GetMapping("/list")
    public List<String> listIndexes() {
        try {
            IndexOperations indexOperations = mongoTemplate.indexOps(DbImageUrl.class);
            List<IndexInfo> indexInfos = indexOperations.getIndexInfo();
            return indexInfos.stream()
                    .map(IndexInfo::getName)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw new RuntimeException("Error fetching indexes: " + e.getMessage());
        }
    }
}
