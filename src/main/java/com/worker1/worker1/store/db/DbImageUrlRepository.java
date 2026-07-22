package com.worker1.worker1.store.db;

import com.worker1.worker1.store.model.DbImageUrl;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface DbImageUrlRepository extends MongoRepository<DbImageUrl, String> {

    List<DbImageUrl> findAll();
    // 已有：根据标签模糊匹配（不分页）
    List<DbImageUrl> findByLabelsContaining(String label);

    // 已有：根据国家查询（不分页）
    List<DbImageUrl> findByCountry(String country);

    // ✅ 新增：根据国家分页查询
    Page<DbImageUrl> findByCountry(String country, Pageable pageable);

    // ✅ 新增：根据国家and label 分页查询
    Page<DbImageUrl> findByLabelsContainingAndCountry(String label, String country, Pageable pageable);



    // ✅ 新增：根据标签分页查询
    Page<DbImageUrl> findByLabelsContaining(String label, Pageable pageable);

    // ✅ 新增：关键字搜索（例如title、labels、website等字段中模糊搜索）
    @Query("{ '$or': [ {'labels': { $regex: ?0, $options: 'i' } }, {'website': { $regex: ?0, $options: 'i' } } ] }")
    Page<DbImageUrl> searchByKeyword(String keyword, Pageable pageable);

    // ✅ 新增：提取所有标签（去重）
    @Query(value = "{}", fields = "{ 'labels' : 1 }")
    List<DbImageUrl> findAllLabelsOnly();
}