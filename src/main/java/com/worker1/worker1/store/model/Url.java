package com.worker1.worker1.store.model;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Builder
@Data
@Document(collection = "urldb")
public class Url {
    @Id
    private String id;
    private String status; // pending,done
    private String title;
}
