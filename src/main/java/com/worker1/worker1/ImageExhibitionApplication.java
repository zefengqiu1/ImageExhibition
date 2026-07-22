package com.worker1.worker1;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;


@SpringBootApplication(
        exclude = DataSourceAutoConfiguration.class
)
@EnableScheduling  // 启用定时任务支持
@EnableAsync  // 启用异步支持
@EnableSpringDataWebSupport(pageSerializationMode = EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO)

public class ImageExhibitionApplication {

    public static void main(String[] args) {
        SpringApplication.run(ImageExhibitionApplication.class, args);
    }

}
