package com.worker1.worker1.service;

import com.worker1.worker1.store.db.DbImageUrlRepository;
import com.worker1.worker1.store.model.DbImageUrl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

@Service
public class DbImageUrlService {

    @Autowired
    private DbImageUrlRepository repository;

    public void saveDbImageUrls(DbImageUrl dbImageUrl) {
         repository.save(dbImageUrl);
    }

    public List<DbImageUrl> getAllDbImageUrls() {
        return repository.findAll();
    }

    public Page<DbImageUrl> getAllDbImageUrlsPaged(Pageable pageable) {
        return repository.findAll(pageable);
    }

    public DbImageUrl getDbImageUrlById(String id) {
        Optional<DbImageUrl> dbImageUrl = repository.findById(id);
        return dbImageUrl.orElse(null);
    }

    public Page<DbImageUrl> getDbImageUrlsByLabel(String label, Pageable pageable) {
        return repository.findByLabelsContaining(label, pageable);
    }

    public List<DbImageUrl> getDbImageUrlsByCountry(String country) {
        return repository.findByCountry(country);
    }

    public Page<DbImageUrl> getDbImageUrlsByCountry(String country, Pageable pageable) {
        return repository.findByCountry(country, pageable);
    }

    public Page<DbImageUrl> getByLabelAndCountry(String label, String country, Pageable pageable) {
        return repository.findByLabelsContainingAndCountry(label, country, pageable);
    }

    public List<String> getAllLabels() {
        List<DbImageUrl> dbImageUrls = repository.findAllLabelsOnly();
        return dbImageUrls.stream()
                .filter(dbImageUrl -> dbImageUrl.getLabels() != null && !dbImageUrl.getLabels().isEmpty())
                .flatMap(dbImageUrl -> dbImageUrl.getLabels().stream())
                .distinct()
                .toList();
    }



//    public List<String> getAllLabels() {
//        // 获取所有标签并缓存
//        String cacheKey = CACHE_KEY_PREFIX + "allLabels";
//        List<String> labels = (List<String>) redisTemplate.opsForValue().get(cacheKey);
//        if (labels != null) {
//            return labels;
//        }
//
//        List<DbImageUrl> dbImageUrls = repository.findAllLabelsOnly();
//        labels = dbImageUrls.stream()
//                .filter(dbImageUrl -> dbImageUrl.getLabels() != null && !dbImageUrl.getLabels().isEmpty())
//                .flatMap(dbImageUrl -> dbImageUrl.getLabels().stream())
//                .distinct()
//                .toList();
//
//        // 缓存所有标签
//        redisTemplate.opsForValue().set(cacheKey, labels, 10, TimeUnit.MINUTES);
//
//        return labels;
//    }

    public DbImageUrl createDbImageUrl(DbImageUrl dbImageUrl) {
        if (dbImageUrl.getCreatedAt() == null) {
            dbImageUrl.setCreatedAt(System.currentTimeMillis());
        }
        repository.save(dbImageUrl);
        return dbImageUrl;
    }

    public DbImageUrl updateDbImageUrl(DbImageUrl dbImageUrl) {
        repository.save(dbImageUrl);
        return dbImageUrl;
    }

    public void deleteDbImageUrl(String id) {
        repository.deleteById(id);
    }
}
