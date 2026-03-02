package com.malinghan.madfs.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

//关键点： downloadUrl 存的是"下载地址前缀"而非完整 URL。 完整下载 URL = downloadUrl + "?name=" + name，
// 这样设计是为了让 v5.0 的 MQ 消费者能用 downloadUrl 识别消息来自哪个节点。
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class FileMeta {

    private String name;               // UUID 文件名: "3a9c....jpg"
    private String originalFilename;   // 原始文件名: "photo.jpg"
    private long size;                 // 文件大小（字节）
    private String downloadUrl;        // 本节点下载地址前缀: "http://localhost:8090/download"
    private Map<String, String> tags = new HashMap<>();  // 扩展标签，如 md5

    // v8.0 新增：块信息（null 表示小文件，未分块）
    private List<BlockMeta> blocks;

    // 是否为分块存储
    public boolean isChunked() {
        return blocks != null && !blocks.isEmpty();
    }

    // 构造方法、getter/setter 省略
}