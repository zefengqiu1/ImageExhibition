package com.worker1.worker1.service;

import com.worker1.worker1.store.db.VideoDataRepository;
import com.worker1.worker1.model.SearchPage;
import com.worker1.worker1.model.VideoSearchResult;
import com.worker1.worker1.store.model.VideoData;
import com.worker1.worker1.store.model.VideoManageData;
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
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.regex.Pattern;

@Service
@Slf4j
public class VideoDataService {

    @Autowired
    private VideoDataRepository videoDataRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    public Page<VideoData> searchPublic(
            String category,
            String type,
            String region,
            String language,
            String year,
            String quality,
            String status,
            Pageable pageable) {

        List<Criteria> criteriaList = new ArrayList<>();
        addEqualsCriteria(criteriaList, "auditStatus", VideoManageData.AUDIT_APPROVED);
        addEqualsCriteria(criteriaList, "publishStatus", VideoManageData.PUBLISH_PUBLISHED);
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

        long collectionTotal = mongoTemplate.count(new Query(), VideoManageData.class);
        log.info("VideoData collection total: {}", collectionTotal);
        log.info("VideoData query: {}", query.getQueryObject().toJson());
        long total = mongoTemplate.count(query, VideoManageData.class);
        log.info("Total video data count : {}", total);
        List<VideoData> dataList = mongoTemplate.find(query.with(pageable), VideoManageData.class)
                .stream()
                .map(this::toPublicData)
                .collect(Collectors.toList());
        log.info("Total video data count : {}", dataList.size());
        return new PageImpl<>(dataList, pageable, total);
    }

    public Page<VideoManageData> searchAdmin(
            String category,
            String type,
            String region,
            String language,
            String year,
            String quality,
            String status,
            String auditStatus,
            String publishStatus,
            Pageable pageable) {

        List<Criteria> criteriaList = new ArrayList<>();
        addEqualsCriteria(criteriaList, "category", category);
        addEqualsCriteria(criteriaList, "type", type);
        addEqualsCriteria(criteriaList, "region", region);
        addEqualsCriteria(criteriaList, "language", language);
        addEqualsCriteria(criteriaList, "year", year);
        addEqualsCriteria(criteriaList, "quality", quality);
        addEqualsCriteria(criteriaList, "status", status);
        addEqualsCriteria(criteriaList, "auditStatus", auditStatus);
        addEqualsCriteria(criteriaList, "publishStatus", publishStatus);

        Query query = new Query();
        if (!criteriaList.isEmpty()) {
            query.addCriteria(new Criteria().andOperator(criteriaList.toArray(new Criteria[0])));
        }

        long total = mongoTemplate.count(query, VideoManageData.class);
        List<VideoManageData> dataList = mongoTemplate.find(query.with(pageable), VideoManageData.class);
        return new PageImpl<>(dataList, pageable, total);
    }

    public VideoManageData create(VideoManageData data) {
        long now = System.currentTimeMillis();
        if (data.getCreatedAt() == null) {
            data.setCreatedAt(now);
        }
        data.setUpdatedAt(now);
        if (!hasText(data.getAuditStatus())) {
            data.setAuditStatus(VideoManageData.AUDIT_PENDING);
        }
        if (!hasText(data.getPublishStatus())) {
            data.setPublishStatus(VideoManageData.PUBLISH_DRAFT);
        }
        return videoDataRepository.save(data);
    }

    public VideoData getPublicById(String id) {
        Optional<VideoManageData> data = videoDataRepository.findById(id);
        return data.filter(this::isPublicVisible)
                .map(this::toPublicData)
                .orElse(null);
    }

    public SearchPage<VideoSearchResult> searchPublicByKeyword(String keyword, Pageable pageable) {
        List<Criteria> criteriaList = new ArrayList<>();
        addEqualsCriteria(criteriaList, "auditStatus", VideoManageData.AUDIT_APPROVED);
        addEqualsCriteria(criteriaList, "publishStatus", VideoManageData.PUBLISH_PUBLISHED);

        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        if (!normalizedKeyword.isEmpty()) {
            Pattern keywordPattern = Pattern.compile(Pattern.quote(normalizedKeyword), Pattern.CASE_INSENSITIVE);
            criteriaList.add(new Criteria().orOperator(
                    Criteria.where("title").regex(keywordPattern),
                    Criteria.where("description").regex(keywordPattern)));
        }

        Query query = new Query();
        query.addCriteria(new Criteria().andOperator(criteriaList.toArray(new Criteria[0])));

        long total = mongoTemplate.count(query, VideoManageData.class);
        List<VideoSearchResult> list = mongoTemplate.find(query.with(pageable), VideoManageData.class)
                .stream()
                .map(this::toSearchResult)
                .collect(Collectors.toList());

        return new SearchPage<>(total, list);
    }

    public VideoManageData getAdminById(String id) {
        Optional<VideoManageData> data = videoDataRepository.findById(id);
        return data.orElse(null);
    }

    public VideoManageData update(String id, VideoManageData input) {
        VideoManageData existing = getAdminById(id);
        if (existing == null) {
            return null;
        }
        copyEditableFields(input, existing);
        existing.setUpdatedAt(System.currentTimeMillis());
        return videoDataRepository.save(existing);
    }

    public boolean delete(String id) {
        if (!videoDataRepository.existsById(id)) {
            return false;
        }
        videoDataRepository.deleteById(id);
        return true;
    }

    public VideoManageData audit(String id, Map<String, String> request) {
        VideoManageData existing = getAdminById(id);
        if (existing == null) {
            return null;
        }
        String auditStatus = request.get("auditStatus");
        if (hasText(auditStatus)) {
            existing.setAuditStatus(auditStatus.trim());
        }
        existing.setRejectReason(trimToNull(request.get("rejectReason")));
        existing.setReviewer(trimToNull(request.get("reviewer")));
        long now = System.currentTimeMillis();
        existing.setReviewedAt(now);
        existing.setUpdatedAt(now);
        return videoDataRepository.save(existing);
    }

    public VideoManageData publish(String id, Map<String, String> request) {
        VideoManageData existing = getAdminById(id);
        if (existing == null) {
            return null;
        }
        String publishStatus = request.get("publishStatus");
        if (hasText(publishStatus)) {
            existing.setPublishStatus(publishStatus.trim());
        }
        existing.setUpdatedAt(System.currentTimeMillis());
        return videoDataRepository.save(existing);
    }

    private boolean isPublicVisible(VideoManageData data) {
        return VideoManageData.AUDIT_APPROVED.equals(data.getAuditStatus())
                && VideoManageData.PUBLISH_PUBLISHED.equals(data.getPublishStatus());
    }

    private VideoData toPublicData(VideoManageData data) {
        return VideoData.builder()
                .id(data.getId())
                .title(data.getTitle())
                .description(data.getDescription())
                .imageUrl(data.getImageUrl())
                .category(data.getCategory())
                .type(data.getType())
                .region(data.getRegion())
                .language(data.getLanguage())
                .year(data.getYear())
                .quality(data.getQuality())
                .status(data.getStatus())
                .createdAt(data.getCreatedAt())
                .build();
    }

    private VideoSearchResult toSearchResult(VideoManageData data) {
        VideoSearchResult result = new VideoSearchResult();
        result.setId(data.getId());
        result.setTitle(data.getTitle());
        result.setDescription(data.getDescription());
        result.setImageUrl(data.getImageUrl());
        result.setHighlightTitle(data.getTitle());
        return result;
    }

    private void copyEditableFields(VideoManageData source, VideoManageData target) {
        target.setTitle(source.getTitle());
        target.setDescription(source.getDescription());
        target.setImageUrl(source.getImageUrl());
        target.setCategory(source.getCategory());
        target.setType(source.getType());
        target.setRegion(source.getRegion());
        target.setLanguage(source.getLanguage());
        target.setYear(source.getYear());
        target.setQuality(source.getQuality());
        target.setStatus(source.getStatus());
        target.setVideoUrl(source.getVideoUrl());
        target.setAuditStatus(source.getAuditStatus());
        target.setPublishStatus(source.getPublishStatus());
        target.setRejectReason(source.getRejectReason());
        target.setReviewer(source.getReviewer());
        target.setReviewedAt(source.getReviewedAt());
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

    private String trimToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }
}
