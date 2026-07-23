package com.worker1.worker1.controller;

import com.worker1.worker1.service.VideoDataService;
import com.worker1.worker1.store.model.VideoData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@CrossOrigin("*")
@Slf4j
@RequestMapping("/api/videos")
public class VideoDataController {

    @Autowired
    private VideoDataService videoDataService;

    @GetMapping("/list/{category}")
    public ResponseEntity<Map<String, Object>> getDataList(
            @PathVariable(name = "category", required = false) String category,
            @RequestParam(name = "page", defaultValue = "0") Integer page,
            @RequestParam(name = "size", defaultValue = "10") Integer size,
            @RequestParam(name = "sortBy", defaultValue = "createdAt") String sortBy,
            @RequestParam(name = "sortDir", defaultValue = "desc") String direction,
            @RequestParam(name = "type", required = false) String type,
            @RequestParam(name = "region", required = false) String region,
            @RequestParam(name = "language", required = false) String language,
            @RequestParam(name = "year", required = false) String year,
            @RequestParam(name = "quality", required = false) String quality,
        @RequestParam(name = "status", required = false) String status) {
        log.info(category);
        Pageable pageable = buildPageable(page, size, sortBy, direction);
        Page<VideoData> dataPage = videoDataService.search(category, type, region, language, year, quality, status, pageable);
        return new ResponseEntity<>(buildListResponse(dataPage), HttpStatus.OK);
    }

    @GetMapping("/{id}")
    public ResponseEntity<VideoData> getDataById(@PathVariable String id) {
        VideoData data = videoDataService.getById(id);
        if (data == null) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        return new ResponseEntity<>(data, HttpStatus.OK);
    }

    @PostMapping({"/video", "/api/videos"})
    public ResponseEntity<VideoData> createData(@RequestBody VideoData data) {
        return new ResponseEntity<>(videoDataService.create(data), HttpStatus.CREATED);
    }

    private Pageable buildPageable(Integer page, Integer size, String sortBy, String direction) {
        return PageRequest.of(page, size, Sort.by(Sort.Direction.fromString(direction), sortBy));
    }

    private Map<String, Object> buildListResponse(Page<VideoData> dataPage) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("imageUrlList", dataPage.getContent());
        response.put("total", dataPage.getTotalElements());
        log.info("response dataPage = {}", response);
        return response;
    }
}
