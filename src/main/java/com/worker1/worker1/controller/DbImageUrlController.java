package com.worker1.worker1.controller;

import com.worker1.worker1.model.ImageDTO;
import com.worker1.worker1.service.DbImageUrlService;
import com.worker1.worker1.store.model.DbImageUrl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;


@RestController
@RequestMapping("/api/images")
@CrossOrigin("*") // For React frontend
@Slf4j
public class DbImageUrlController {

    @Autowired
    private DbImageUrlService dbImageUrlService;


    // 2. 修改 DbImageUrlController.java 中的方法
    @GetMapping("/list")
    public ResponseEntity<ResponseRes> getAllDbImageUrls(
            @RequestParam(name = "page", defaultValue = "0") Integer page,
            @RequestParam(name = "size", defaultValue = "10") Integer size,
            @RequestParam(name = "sortBy", defaultValue = "createdAt") String sortBy,
            @RequestParam(name = "sortDir", defaultValue = "desc") String direction,
            @RequestParam(name = "label", defaultValue = "") String label) {

        Pageable pageable = buildPageable(page, size, sortBy, direction);
        Page<DbImageUrl> dbImageUrls = fetchImages(label, null, pageable);
        return buildImageResponse(dbImageUrls);
    }

    @GetMapping("/list/domestic")
    public ResponseEntity<ResponseRes> getAllDomesticDbImageUrls(
            @RequestParam(name = "page", defaultValue = "0") Integer page,
            @RequestParam(name = "size", defaultValue = "10") Integer size,
            @RequestParam(name = "sortBy", defaultValue = "createdAt") String sortBy,
            @RequestParam(name = "sortDir", defaultValue = "desc") String direction,
            @RequestParam(name = "label", defaultValue = "") String label) {
        Pageable pageable = buildPageable(page, size, sortBy, direction);
        Page<DbImageUrl> dbImageUrls = fetchImages(label, "domestic", pageable);
        return buildImageResponse(dbImageUrls);
    }


    @GetMapping("/list/asia")
    public ResponseEntity<ResponseRes> getAsiaImages(
            @RequestParam(name = "page", defaultValue = "0") Integer page,
            @RequestParam(name = "size", defaultValue = "10") Integer size,
            @RequestParam(name = "sortBy", defaultValue = "createdAt") String sortBy,
            @RequestParam(name = "sortDir", defaultValue = "desc") String direction,
            @RequestParam(name = "label", defaultValue = "") String label) {

        Pageable pageable = buildPageable(page, size, sortBy, direction);
        Page<DbImageUrl> dbImageUrls = fetchImages(label, "asia", pageable);
        return buildImageResponse(dbImageUrls);
    }


    @GetMapping("/list/european")
    public ResponseEntity<ResponseRes> getEuropeanImages(
            @RequestParam(name = "page", defaultValue = "0") Integer page,
            @RequestParam(name = "size", defaultValue = "10") Integer size,
            @RequestParam(name = "sortBy", defaultValue = "createdAt") String sortBy,
            @RequestParam(name = "sortDir", defaultValue = "desc") String direction,
            @RequestParam(name = "label", defaultValue = "") String label) {

        Pageable pageable = buildPageable(page, size, sortBy, direction);
        Page<DbImageUrl> dbImageUrls = fetchImages(label, "european", pageable);
        return buildImageResponse(dbImageUrls);
    }

    /**
     * 获取图片的详细信息
     * @param id
     * @return
     */
    @GetMapping("/{id}")
    public ResponseEntity<ImageDTO> getDbImageUrlById(@PathVariable String id) {
        System.out.println("Fetching image with ID: " + id);
        DbImageUrl dbImageUrl = dbImageUrlService.getDbImageUrlById(id);
        ImageDTO imageDTO = ImageDTO.fromDbImageUrl(dbImageUrl);
        if (dbImageUrl != null) {
            return new ResponseEntity<>(imageDTO, HttpStatus.OK);
        }

        return new ResponseEntity<>(HttpStatus.OK);
    }

    /**
     * 创建新的图片记录
     * @param dbImageUrl
     * @return
     */
    @PostMapping("/")
    public ResponseEntity<DbImageUrl> createDbImageUrl(@RequestBody DbImageUrl dbImageUrl) {
        DbImageUrl newDbImageUrl = dbImageUrlService.createDbImageUrl(dbImageUrl);
        return new ResponseEntity<>(newDbImageUrl, HttpStatus.CREATED);
    }

    private Pageable buildPageable(Integer page, Integer size, String sortBy, String direction) {
        return PageRequest.of(page, size, Sort.by(Sort.Direction.fromString(direction), sortBy));
    }

    private Page<DbImageUrl> fetchImages(String label, String country, Pageable pageable) {
        boolean hasLabel = label != null && !label.isEmpty();
        if (hasLabel && country != null) {
            return dbImageUrlService.getByLabelAndCountry(label, country, pageable);
        }
        if (hasLabel) {
            return dbImageUrlService.getDbImageUrlsByLabel(label, pageable);
        }
        if (country != null) {
            return dbImageUrlService.getDbImageUrlsByCountry(country, pageable);
        }
        return dbImageUrlService.getAllDbImageUrlsPaged(pageable);
    }

    private ResponseEntity<ResponseRes> buildImageResponse(Page<DbImageUrl> dbImageUrls) {
        List<ImageDTO> dtoList = dbImageUrls.getContent().stream()
                .map(ImageDTO::fromDbImageUrl)
                .collect(Collectors.toList());

        return new ResponseEntity<>(ResponseRes.builder()
                .imageUrlList(dtoList)
                .total(dbImageUrls.getTotalElements())
                .build(), HttpStatus.OK);
    }
}
