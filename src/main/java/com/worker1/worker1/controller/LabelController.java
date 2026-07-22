package com.worker1.worker1.controller;

import com.worker1.worker1.service.DbImageUrlService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;


/*
 * 用来获取label的
 * */
@RestController
@RequestMapping("/api")
@CrossOrigin("*")
public class LabelController {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private DbImageUrlService dbImageUrlService;

    @GetMapping("/labels")
    public ResponseEntity<List<String>> getAllLabels() {
        List<String> labels = dbImageUrlService.getAllLabels();
        return new ResponseEntity<>(labels, HttpStatus.OK);
    }

    @GetMapping("/labels/rankings")
    public ResponseEntity<List<String>> getHotLabels() {
        Set<String> labelSet = redisTemplate.opsForZSet()
                .reverseRange("label_rank", 0, -1);

        List<String> labelList = new ArrayList<>(labelSet);
        return new ResponseEntity<>(labelList, HttpStatus.OK);
    }
}

