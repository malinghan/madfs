package com.malinghan.madfs.util;

import lombok.extern.slf4j.Slf4j;

import java.io.File;

@Slf4j
public class FileUtils {

    /**
     * 初始化存储目录结构
     * 创建根目录 + 256 个十六进制子目录（00~ff）
     */
    public static void init(String uploadPath) {
        File root = new File(uploadPath);

        // 创建根目录
        if (!root.exists()) {
            root.mkdirs();
            log.info("[FileUtils] 创建根目录: {}", uploadPath);
        }

        int created = 0;
        // 循环 0~255，格式化为两位十六进制
        for (int i = 0; i < 256; i++) {
            String subdir = String.format("%02x", i);  // 0 => "00", 255 => "ff"
            File dir = new File(uploadPath, subdir);
            if (!dir.exists()) {
                dir.mkdir();
                created++;
            }
        }

        log.info("[FileUtils] 子目录初始化完成: 共256个桶, 新建{}个", created);
    }
}
