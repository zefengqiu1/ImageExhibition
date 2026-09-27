package com.worker1.worker1.model;

import lombok.Data;

@Data
public class VideoSearchResult {
    private String id;
    private String title;
    private String description;
    private String imageUrl;
    private String highlightTitle;
}
