package com.malinghan.madfs.controller;

import com.malinghan.madfs.FileMeta.FileMeta;
import com.malinghan.madfs.config.MadfsConfigProperties;
import com.malinghan.madfs.util.FileUtils;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;

@RestController
@Slf4j
public class FileController {

    @Autowired
    private MadfsConfigProperties config;

    @PostMapping("/upload")
    public String upload(@RequestParam MultipartFile file) throws IOException {
        log.info("[UPLOAD] ===== 收到上传请求 =====");
        log.info("[UPLOAD] 原始文件名: {}, 大小: {} bytes",
                file.getOriginalFilename(), file.getSize());

        // [v2.0] 生成 UUID 文件名、计算子目录、写入文件
        String uuidName = FileUtils.getUUIDFile(file.getOriginalFilename());
        String subdir = FileUtils.getSubdir(uuidName);
        File dest = new File(config.getUploadPath() + "/" + subdir + "/" + uuidName);
        file.transferTo(dest);
        log.info("[UPLOAD] 文件写入完成: {}", dest.getAbsolutePath());

        // [v4.0 新增] 构建 FileMeta
        FileMeta meta = new FileMeta();
        meta.setName(uuidName);
        meta.setOriginalFilename(file.getOriginalFilename());
        meta.setSize(file.getSize());
        meta.setDownloadUrl(config.getDownloadUrl());

        // [v4.0 新增] 自动计算 MD5
        if (config.isAutoMd5()) {
            String md5 = DigestUtils.md5Hex(new FileInputStream(dest));
            meta.getTags().put("md5", md5);
            log.info("[UPLOAD] 计算MD5: {}", md5);
        }

        // [v4.0 新增] 写入 .meta 文件
        FileUtils.writeMeta(dest, meta);

        log.info("[UPLOAD] ===== 上传完成, 返回文件名: {} =====", uuidName);
        return uuidName;
    }

    // download() 方法保持不变...

    /**
     * 元数据查询接口
     * GET /meta?name=xxx
     * 返回对应的 .meta 文件内容（JSON 字符串）
     */
    @GetMapping("/meta")
    public String meta(@RequestParam String name) throws IOException {
        log.info("[META] 查询元数据, name={}", name);

        String subdir = FileUtils.getSubdir(name);
        File dataFile = new File(config.getUploadPath() + "/" + subdir + "/" + name);
        File metaFile = new File(dataFile.getAbsolutePath() + ".meta");

        log.info("[META] Meta文件路径: {}", metaFile.getAbsolutePath());

        if (!metaFile.exists()) {
            return "{}";  // 文件不存在返回空 JSON
        }

        String content = Files.readString(metaFile.toPath());
        log.info("[META] 返回内容: {}", content);
        return content;
    }

    // upload() 方法保持不变...

    /**
     * 文件下载接口
     * GET /download?name=xxx
     * 直接向 HttpServletResponse 写入文件内容
     */
    @GetMapping("/download")
    public void download(@RequestParam String name,
                         HttpServletResponse response) throws IOException {
        log.info("[DOWNLOAD] ===== 收到下载请求, name={} =====", name);

        // 1. 定位文件
        String subdir = FileUtils.getSubdir(name);
        File file = new File(config.getUploadPath() + "/" + subdir + "/" + name);
        log.info("[DOWNLOAD] 文件路径: {}, 存在: {}, 大小: {} bytes",
                file.getAbsolutePath(), file.exists(), file.length());

        // 2. 文件不存在时返回 404
        if (!file.exists()) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        // 3. 设置响应头
        String mimeType = FileUtils.getMimeType(name);
        response.setContentType(mimeType);
        response.setContentLengthLong(file.length());
        log.info("[DOWNLOAD] MIME类型: {}", mimeType);

        // 4. 流式输出文件内容
        FileUtils.output(file, response.getOutputStream());
        log.info("[DOWNLOAD] ===== 文件输出完成 =====");
    }
}