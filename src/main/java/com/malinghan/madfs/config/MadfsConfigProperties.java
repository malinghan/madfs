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
    private String downloadUrl;   // 本节点对外下载地址，如 "http://localhost:8090/download"
    private boolean autoMd5 = true;  // 是否自动计算并存储 MD5，默认开启

    /**
     * 上传存储路径，默认与 path 相同
     * 单独暴露是为了后续可能区分"上传临时目录"和"最终存储目录"
     */
    public String getUploadPath() {
        return path;  // 当前版本直接返回 path
    }

}