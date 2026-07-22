package com.worker1.worker1.store.model;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Builder
@Document(collection = "imageurl")
@Data
@CompoundIndexes({
        @CompoundIndex(name = "labels_country_idx", def = "{'labels': 1, 'country': 1}")  // 复合索引：labels + country
})
public class DbImageUrl {
    @Id
    private String title;
    private List<String> imageUrl;
    private String website;
//    private List<String> urls;
    @Indexed
    private List<String> labels;
    @Indexed
    private String country;
    private Long createdAt;
    private String description;    // 描述信息（用于全文检索）
    private List<String> keywords; // 关键词列表（从标题提取
}
