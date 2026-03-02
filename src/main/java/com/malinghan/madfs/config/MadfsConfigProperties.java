package com.malinghan.madfs.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "madfs")
//关键点： @ConfigurationProperties 比 @Value 更适合管理一组相关配置，
// 后续版本会在这里持续添加新配置项（downloadUrl、syncBackup 等），集中管理不散乱。
@Data
public class MadfsConfigProperties {

    // 对应 application.yml 中的 madfs.path
    // ${user.home} 会被替换为当前用户主目录
    private String path;

}