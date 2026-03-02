package com.malinghan.madfs.controller;

import com.malinghan.madfs.config.MadfsConfigProperties;
import com.malinghan.madfs.util.FileUtils;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;

@RestController
@Slf4j
public class FileController {

    @Autowired
    private MadfsConfigProperties config;

    /**
     * 文件上传接口
     * POST /upload
     * Content-Type: multipart/form-data
     * 参数: file (MultipartFile)
     * 返回: UUID 文件名字符串
     */
    @PostMapping("/upload")
    public String upload(@RequestParam MultipartFile file) throws IOException {
        log.info("[UPLOAD] ===== 收到上传请求 =====");
        log.info("[UPLOAD] 原始文件名: {}, 大小: {} bytes",
                file.getOriginalFilename(), file.getSize());

        // 1. 生成 UUID 文件名
        String uuidName = FileUtils.getUUIDFile(file.getOriginalFilename());
        log.info("[UPLOAD] 生成UUID文件名: {}", uuidName);

        // 2. 计算子目录
        String subdir = FileUtils.getSubdir(uuidName);

        // 3. 构建目标文件路径
        File dest = new File(config.getUploadPath() + "/" + subdir + "/" + uuidName);
        log.info("[UPLOAD] 存储路径: subdir={}, dest={}", subdir, dest.getAbsolutePath());

        // 4. 写入文件
        file.transferTo(dest);
        log.info("[UPLOAD] 文件写入完成: {}", dest.getAbsolutePath());

        log.info("[UPLOAD] ===== 上传完成, 返回文件名: {} =====", uuidName);
        return uuidName;
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