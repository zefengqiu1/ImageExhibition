package com.worker1.worker1.store.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VideoData {
    @Id
    private String id;
    private String title;
    private String description;
    private String imageUrl;
    @Indexed
    private String category;
    @Indexed
    private String type;
    @Indexed
    private String region;
    @Indexed
    private String language;
    @Indexed
    private String year;
    @Indexed
    private String quality;
    @Indexed
    private String status;
    private Long createdAt;
}
