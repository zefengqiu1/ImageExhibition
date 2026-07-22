package com.worker1.worker1.controller;


import com.worker1.worker1.model.ImageDTO;
import com.worker1.worker1.store.model.DbImageUrl;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Builder
@Data
public class ResponseRes {
    private Long total;
    private List<ImageDTO> imageUrlList;
}
