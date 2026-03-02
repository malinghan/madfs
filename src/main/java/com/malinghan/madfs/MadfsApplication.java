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
            FileUtils.init(config.getPath());
            log.info("===== madfs 启动完成 =====");
            log.info("存储路径:   {}", config.getUploadPath());
            log.info("下载地址:   {}", config.getDownloadUrl());
            log.info("备份地址:   {}", config.getBackupUrl());
            log.info("消费者组:   {}", config.getGroup());
            log.info("自动MD5:    {}", config.isAutoMd5());
            log.info("HTTP备份:   {}", config.isSyncBackup());
            log.info("=========================");
        };
    }

    // 在 MadfsApplication 或单独的配置类中添加
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
