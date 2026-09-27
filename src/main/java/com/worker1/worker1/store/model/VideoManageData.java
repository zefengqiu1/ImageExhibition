package com.worker1.worker1.store.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Document(collection = "videoData")
@CompoundIndexes({
        @CompoundIndex(name = "category_filters_idx", def = "{'category': 1, 'type': 1, 'region': 1, 'language': 1, 'year': 1, 'quality': 1, 'status': 1}"),
        @CompoundIndex(name = "category_created_at_idx", def = "{'category': 1, 'createdAt': -1}"),
        @CompoundIndex(name = "public_video_idx", def = "{'auditStatus': 1, 'publishStatus': 1, 'category': 1, 'createdAt': -1}")
})
public class VideoManageData extends VideoData {
    public static final String AUDIT_PENDING = "pending";
    public static final String AUDIT_APPROVED = "approved";
    public static final String AUDIT_REJECTED = "rejected";

    public static final String PUBLISH_DRAFT = "draft";
    public static final String PUBLISH_PUBLISHED = "published";
    public static final String PUBLISH_OFFLINE = "offline";

    @Indexed
    private String auditStatus;

    @Indexed
    private String publishStatus;

    private String videoUrl;
    private String rejectReason;
    private String reviewer;
    private Long reviewedAt;
    private Long updatedAt;
}
