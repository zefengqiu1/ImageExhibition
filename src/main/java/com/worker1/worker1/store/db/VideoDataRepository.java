package com.worker1.worker1.store.db;

import com.worker1.worker1.store.model.VideoManageData;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VideoDataRepository extends MongoRepository<VideoManageData, String> {
    List<VideoManageData> findByAuditStatusAndPublishStatus(String auditStatus, String publishStatus);
}
