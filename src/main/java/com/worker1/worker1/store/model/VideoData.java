package com.worker1.worker1.store.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "videoData")
@CompoundIndexes({
        @CompoundIndex(name = "category_filters_idx", def = "{'category': 1, 'type': 1, 'region': 1, 'language': 1, 'year': 1, 'quality': 1, 'status': 1}"),
        @CompoundIndex(name = "category_created_at_idx", def = "{'category': 1, 'createdAt': -1}")
})
public class VideoData {
    @Id
    private String id;
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
