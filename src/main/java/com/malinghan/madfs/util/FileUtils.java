package com.malinghan.madfs.util;

import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.util.UUID;

@Slf4j
public class FileUtils {

    // v1.0 的 init() 方法保持不变...

    /**
     * 根据原始文件名生成 UUID 文件名，保留原始扩展名
     *
     * 示例:
     *   "test.jpg"    => "3a9c1b2d-4e5f-....jpg"
     *   "photo.PNG"   => "3a9c1b2d-4e5f-....PNG"
     *   "noext"       => "3a9c1b2d-4e5f-...."
     */
    public static String getUUIDFile(String originalFilename) {
        String uuid = UUID.randomUUID().toString();

        // 提取扩展名（最后一个点之后的部分）
        int dotIndex = originalFilename.lastIndexOf('.');
        String ext = (dotIndex >= 0) ? originalFilename.substring(dotIndex) : "";

        return uuid + ext;
    }

    /**
     * 取文件名前两位作为子目录名
     *
     * 示例:
     *   "3a9c1b2d-....jpg" => "3a"
     *   "ff001234-....txt" => "ff"
     */
    public static String getSubdir(String filename) {
        return filename.substring(0, 2);
    }


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
