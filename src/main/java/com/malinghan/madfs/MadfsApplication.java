package com.malinghan.madfs;

import com.malinghan.madfs.config.MadfsConfigProperties;
import com.malinghan.madfs.util.FileUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

@SpringBootApplication
@EnableConfigurationProperties(MadfsConfigProperties.class)
@Slf4j
public class MadfsApplication {

    public static void main(String[] args) {
        SpringApplication.run(MadfsApplication.class, args);
    }

    /**
     * ApplicationRunner: Spring Boot 启动完成后自动执行
     * 相比 @PostConstruct，此时 Spring 容器已完全就绪
     */
    @Bean
    public ApplicationRunner runner(MadfsConfigProperties config) {
        return args -> {
            log.info("[MadfsApplication] 初始化存储目录: {}", config.getPath());
            FileUtils.init(config.getPath());
            log.info("[MadfsApplication] madfs started, storage: {}", config.getPath());
        };
    }

    // 在 MadfsApplication 或单独的配置类中添加
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
