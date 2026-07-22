package com.worker1.worker1;


import com.worker1.worker1.service.DbImageUrlService;
import com.worker1.worker1.store.db.DbImageUrlRepository;
import com.worker1.worker1.store.model.DbImageUrl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class ImageValidatorServiceTest {

    @Autowired
    private DbImageUrlService dbImageUrlService;
    private final ImageValidatorService service = new ImageValidatorService();



    /**
     * 并发测试：检测无效图片 URL 并删除
     */
//    @Test@Disabled
    void testInvalidImageUrlConcurrently() throws InterruptedException {
        List<DbImageUrl> list = dbImageUrlService.getAllDbImageUrls();
//        Collections.reverse(list); // 从最新往旧遍历

        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Future<?>> futures = new ArrayList<>();

        for (DbImageUrl dbImageUrl : list) {
            futures.add(executor.submit(() -> {
                try {
                    List<String> urls = dbImageUrl.getImageUrl();
                    if (urls == null || urls.isEmpty()) {
                        System.out.println("Deleting (empty URL): " + dbImageUrl.getTitle());
                        dbImageUrlService.deleteDbImageUrl(dbImageUrl.getTitle());
                        return;
                    }

                    String url = urls.get(0);
                    if (!service.isImageUrlValid(url)) {
                        System.out.println("Deleting (invalid URL): " + url + " → ID: " + dbImageUrl.getTitle());
                        dbImageUrlService.deleteDbImageUrl(dbImageUrl.getTitle());
                    }

                } catch (Exception e) {
                    System.err.println("Error checking URL: " + dbImageUrl.getTitle());
                    e.printStackTrace();
                }
            }));
        }

        // 等待所有任务完成
        for (Future<?> future : futures) {
            try {
                future.get(); // 阻塞等待完成
            } catch (ExecutionException e) {
                e.printStackTrace();
            }
        }

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.MINUTES);

        Assertions.assertFalse(list.isEmpty());
    }

}

