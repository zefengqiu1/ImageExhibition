package com.worker1.worker1.controller;

import com.worker1.worker1.service.SearchLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Collections;

@RestController
@RequestMapping("/api/videos")
@CrossOrigin("*")
@Slf4j
public class HotSearchController {

    private final ObjectProvider<SearchLogService> searchLogServiceProvider;

    public HotSearchController(ObjectProvider<SearchLogService> searchLogServiceProvider) {
        this.searchLogServiceProvider = searchLogServiceProvider;
    }

    /**
     * 获取近 7 天热门搜索词。
     * 默认返回前 10 个，给前端下拉框直接使用。
     */
    @GetMapping("/hotKeywords")
    public List<String> hotKeywords(@RequestParam(defaultValue = "10") int topN) {
        SearchLogService searchLogService = searchLogServiceProvider.getIfAvailable();
        if (searchLogService == null) {
            return Collections.emptyList();
        }
        return searchLogService.getHotKeywords(topN);
    }
}
