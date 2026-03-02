package com.malinghan.madfs.controller;

import com.malinghan.madfs.config.MadfsConfigProperties;
import com.malinghan.madfs.util.FileUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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
}