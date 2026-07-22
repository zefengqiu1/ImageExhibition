package com.worker1.worker1.model;

import lombok.Data;

@Data
public class SearchLog {
    private String keyword; // 用户搜索词
    private long timestamp; // 搜索时间戳
}

