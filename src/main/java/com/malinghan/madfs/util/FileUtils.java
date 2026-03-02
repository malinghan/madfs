package com.malinghan.madfs.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.malinghan.madfs.FileMeta.FileMeta;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
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

    /**
     * 根据文件名推断 MIME 类型
     * 取文件扩展名，映射到对应的 Content-Type
     */
    public static String getMimeType(String filename) {
        if (filename == null) return "application/octet-stream";

        String lower = filename.toLowerCase();

        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".png"))  return "image/png";
        if (lower.endsWith(".gif"))  return "image/gif";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".txt"))  return "text/plain";
        if (lower.endsWith(".html") || lower.endsWith(".htm")) return "text/html";
        if (lower.endsWith(".css"))  return "text/css";
        if (lower.endsWith(".js"))   return "application/javascript";
        if (lower.endsWith(".json")) return "application/json";
        if (lower.endsWith(".xml"))  return "application/xml";
        if (lower.endsWith(".pdf"))  return "application/pdf";
        if (lower.endsWith(".zip"))  return "application/zip";
        if (lower.endsWith(".mp4"))  return "video/mp4";
        if (lower.endsWith(".mp3"))  return "audio/mpeg";

        // 未知类型：通用二进制流，浏览器会触发下载
        return "application/octet-stream";
    }

    /**
     * 流式输出文件内容到 OutputStream
     * 使用 16KB 缓冲区，避免大文件占用大量内存
     *
     * @param file  要输出的文件
     * @param out   目标输出流（通常是 HttpServletResponse.getOutputStream()）
     */
    public static void output(File file, OutputStream out) throws IOException {
        byte[] buffer = new byte[16 * 1024];  // 16KB 缓冲区
        int bytesRead;

        try (FileInputStream fis = new FileInputStream(file)) {
            while ((bytesRead = fis.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
        }
        // out 由调用方（Spring）负责关闭，这里不关闭
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 将 FileMeta 序列化为 JSON，写入 {dataFile}.meta 文件
     *
     * 示例: dataFile = ~/madfs/3a/3a9c....jpg
     *       写入    = ~/madfs/3a/3a9c....jpg.meta
     */
    public static void writeMeta(File dataFile, FileMeta meta) throws IOException {
        File metaFile = new File(dataFile.getAbsolutePath() + ".meta");
        String json = MAPPER.writeValueAsString(meta);
        Files.writeString(metaFile.toPath(), json);
        log.info("[FileUtils] 写入Meta文件: {}", metaFile.getAbsolutePath());
    }

    /**
     * 从 .meta 文件读取 FileMeta 对象
     */
    public static FileMeta readMeta(File dataFile) throws IOException {
        File metaFile = new File(dataFile.getAbsolutePath() + ".meta");
        String json = Files.readString(metaFile.toPath());
        return MAPPER.readValue(json, FileMeta.class);
    }
}
