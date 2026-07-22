package com.worker1.worker1.controller;

import com.worker1.worker1.service.ETLBatchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/etl")
@Slf4j
public class ETLController {

    @Autowired
    private ETLBatchService etlService;

    @PostMapping("/run-daily")
    public String runDailyETL() {
        try {
            etlService.runDailyETL();
            return "ETL 任务执行成功";
        } catch (Exception e) {
            log.error("ETL 失败", e);
            return "ETL 失败: " + e.getMessage();
        }
    }

    // ✅ 测试单个步骤
    @PostMapping("/teststep1")
    public String testStep1() {
        try {
            // 调用私有方法测试（或改为 public）
            etlService.extractMongoImagesToDim(LocalDate.now().minusDays(1));
            return "Step 1 测试成功";
        } catch (Exception e) {
            return "Step 1 失败: " + e.getMessage();
        }
    }
}
