package com.worker1.worker1.model;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class SearchPage<T> {
    private long total;
    private List<T> list;
}
