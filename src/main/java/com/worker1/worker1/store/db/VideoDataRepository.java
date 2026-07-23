package com.worker1.worker1.store.db;

import com.worker1.worker1.store.model.VideoData;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface VideoDataRepository extends MongoRepository<VideoData, String> {
}
