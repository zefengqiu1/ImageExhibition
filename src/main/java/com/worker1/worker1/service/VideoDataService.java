package com.worker1.worker1.service;

import com.worker1.worker1.store.db.VideoDataRepository;
import com.worker1.worker1.store.model.VideoData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class VideoDataService {

    @Autowired
    private VideoDataRepository videoDataRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    public Page<VideoData> search(
            String category,
            String type,
            String region,
            String language,
            String year,
            String quality,
            String status,
            Pageable pageable) {

        List<Criteria> criteriaList = new ArrayList<>();
        addEqualsCriteria(criteriaList, "category", category);
        addEqualsCriteria(criteriaList, "type", type);
        addEqualsCriteria(criteriaList, "region", region);
        addEqualsCriteria(criteriaList, "language", language);
        addEqualsCriteria(criteriaList, "year", year);
        addEqualsCriteria(criteriaList, "quality", quality);
        addEqualsCriteria(criteriaList, "status", status);

        Query query = new Query();
        if (!criteriaList.isEmpty()) {
            query.addCriteria(new Criteria().andOperator(criteriaList.toArray(new Criteria[0])));
        }

        long collectionTotal = mongoTemplate.count(new Query(), VideoData.class);
        log.info("VideoData collection total: {}", collectionTotal);
        log.info("VideoData query: {}", query.getQueryObject().toJson());
        long total = mongoTemplate.count(query, VideoData.class);
        log.info("Total video data count : {}", total);
        List<VideoData> dataList = mongoTemplate.find(query.with(pageable), VideoData.class);
        log.info("Total video data count : {}", dataList.size());
        return new PageImpl<>(dataList, pageable, total);
    }

    public VideoData create(VideoData data) {
        if (data.getCreatedAt() == null) {
            data.setCreatedAt(System.currentTimeMillis());
        }
        return videoDataRepository.save(data);
    }

    public VideoData getById(String id) {
        Optional<VideoData> data = videoDataRepository.findById(id);
        return data.orElse(null);
    }

    private void addEqualsCriteria(List<Criteria> criteriaList, String field, String value) {
        if (hasText(value)) {
            criteriaList.add(Criteria.where(field).is(value.trim()));
        }
    }

    private boolean hasText(String value) {
        if (value == null || value.trim().isEmpty()) {
            return false;
        }
        String normalizedValue = value.trim();
        return !"all".equalsIgnoreCase(normalizedValue)
                && !"any".equalsIgnoreCase(normalizedValue)
                && !"全部".equals(normalizedValue)
                && !"不限".equals(normalizedValue);
    }
}
