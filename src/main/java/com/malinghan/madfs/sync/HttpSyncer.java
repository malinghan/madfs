package com.malinghan.madfs.sync;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpHeaders;

import java.io.File;

@Component
@Slf4j
public class HttpSyncer {

    @Autowired
    private RestTemplate restTemplate;

    /**
     * 通过 HTTP 将文件同步推送到备份节点
     *
     * @param file              本地文件
     * @param backupUrl         备份节点上传地址
     * @param originalFilename  原始文件名
     * @return 是否成功
     */
    public boolean sync(File file, String backupUrl, String originalFilename) {
        log.info("[HTTP-SYNC] ===== 开始HTTP同步 =====");
        log.info("[HTTP-SYNC] 文件: {}, 目标URL: {}, 原始文件名: {}",
                file.getAbsolutePath(), backupUrl, originalFilename);

        try {
            // 1. 构建 multipart 请求
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            // 2. 设置自定义请求头，标识这是同步请求
            headers.set("X-Filename", file.getName());           // UUID 文件名
            headers.set("X-Orig-Filename", originalFilename);    // 原始文件名

            // 3. 构建文件资源
            FileSystemResource fileResource = new FileSystemResource(file);
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", fileResource);

            HttpEntity<MultiValueMap<String, Object>> requestEntity =
                    new HttpEntity<>(body, headers);

            // 4. 发送 POST 请求
            log.info("[HTTP-SYNC] 发送POST请求到备份节点...");
            String response = restTemplate.postForObject(backupUrl, requestEntity, String.class);
            log.info("[HTTP-SYNC] 同步完成, 备份节点返回: {}", response);

            return true;

        } catch (Exception e) {
            log.error("[HTTP-SYNC] 同步失败: {}", e.getMessage());
            return false;
        }
    }
}