package com.worker1.worker1.controller;

import com.worker1.worker1.service.VideoDataService;
import com.worker1.worker1.store.model.VideoManageData;
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
@RequestMapping("/api/admin/videos")
@Slf4j
public class VideoAdminController {

    @Autowired
    private VideoDataService videoDataService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getVideos(
            @RequestParam(name = "page", defaultValue = "0") Integer page,
            @RequestParam(name = "size", defaultValue = "10") Integer size,
            @RequestParam(name = "sortBy", defaultValue = "createdAt") String sortBy,
            @RequestParam(name = "sortDir", defaultValue = "desc") String direction,
            @RequestParam(name = "category", required = false) String category,
            @RequestParam(name = "type", required = false) String type,
            @RequestParam(name = "region", required = false) String region,
            @RequestParam(name = "language", required = false) String language,
            @RequestParam(name = "year", required = false) String year,
            @RequestParam(name = "quality", required = false) String quality,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "auditStatus", required = false) String auditStatus,
            @RequestParam(name = "publishStatus", required = false) String publishStatus) {
        Pageable pageable = buildPageable(page, size, sortBy, direction);
        Page<VideoManageData> dataPage = videoDataService.searchAdmin(
                category,
                type,
                region,
                language,
                year,
                quality,
                status,
                auditStatus,
                publishStatus,
                pageable);
        log.info("dataPage = {}", dataPage);
        return new ResponseEntity<>(buildListResponse(dataPage), HttpStatus.OK);
    }

    @PostMapping
    public ResponseEntity<VideoManageData> createVideo(@RequestBody VideoManageData data) {
        return new ResponseEntity<>(videoDataService.create(data), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<VideoManageData> updateVideo(@PathVariable String id, @RequestBody VideoManageData data) {
        VideoManageData updated = videoDataService.update(id, data);
        if (updated == null) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        return new ResponseEntity<>(updated, HttpStatus.OK);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteVideo(@PathVariable String id) {
        if (!videoDataService.delete(id)) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @PatchMapping("/{id}/audit")
    public ResponseEntity<VideoManageData> auditVideo(@PathVariable String id, @RequestBody Map<String, String> request) {
        VideoManageData updated = videoDataService.audit(id, request);
        if (updated == null) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        return new ResponseEntity<>(updated, HttpStatus.OK);
    }

    @PatchMapping("/{id}/publish")
    public ResponseEntity<VideoManageData> publishVideo(@PathVariable String id, @RequestBody Map<String, String> request) {
        VideoManageData updated = videoDataService.publish(id, request);
        if (updated == null) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        return new ResponseEntity<>(updated, HttpStatus.OK);
    }

    private Pageable buildPageable(Integer page, Integer size, String sortBy, String direction) {
        return PageRequest.of(page, size, Sort.by(Sort.Direction.fromString(direction), sortBy));
    }

    private Map<String, Object> buildListResponse(Page<VideoManageData> dataPage) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("imageUrlList", dataPage.getContent());
        response.put("total", dataPage.getTotalElements());
        return response;
    }
}
