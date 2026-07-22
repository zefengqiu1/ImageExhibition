package com.worker1.worker1.model;

import lombok.Builder;
import lombok.Data;
import java.util.List;

/**
 * 统一的图片数据传输对象
 * 用于前后端交互，替代 DbImageUrl 和 ImageSearchResult
 */
@Data
@Builder
public class ImageDTO {
    private String id;              // 唯一标识
    private String title;           // 标题
    private String highlightTitle;  // 高亮标题（搜索时使用）
    private String website;         // 来源网站
    private List<String> imageUrls; // 图片URL列表
    private String country;         // 地区（domestic/asia/european）
    private Long createdAt;         // 创建时间
    private Double score;           // 搜索相关性得分（可选）
    private List<String> matchedKeywords; // 匹配的关键词/标签

    /**
     * 从 DbImageUrl 转换
     */
    public static ImageDTO fromDbImageUrl(com.worker1.worker1.store.model.DbImageUrl db) {
        return ImageDTO.builder()
                .id(db.getTitle())
                .title(db.getTitle())
                .highlightTitle(db.getTitle())
                .website(db.getWebsite())
                .imageUrls(db.getImageUrl())
                .country(db.getCountry())
                .createdAt(db.getCreatedAt())
                .matchedKeywords(db.getLabels())
                .build();
    }

    /**
     * 从 ImageSearchResult 转换
     */
    public static ImageDTO fromSearchResult(com.worker1.worker1.model.ImageSearchResult result) {
        return ImageDTO.builder()
                .id(result.getId())
                .title(result.getTitle())
                .highlightTitle(result.getHighlightTitle() != null ?
                        result.getHighlightTitle() : result.getTitle())
                .website(result.getWebsite())
                .imageUrls(result.getImageUrls())
                .country(result.getCountry())
                .createdAt(result.getCreatedAt())
                .score(result.getScore())
                .matchedKeywords(result.getMatchedKeywords())
                .build();
    }
}