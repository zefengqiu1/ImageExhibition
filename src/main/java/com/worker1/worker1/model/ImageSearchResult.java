package com.worker1.worker1.model;

import lombok.Data;
import java.util.List;

@Data
public class ImageSearchResult {
    private String id;
    private String title;
    private String highlightTitle;     // 高亮标题
    private String website;
    private List<String> imageUrls;
    private String country;
    private Long createdAt;
    private Double score;               // 搜索相关性得分
    private List<String> matchedKeywords; // 匹配的关键词
}
