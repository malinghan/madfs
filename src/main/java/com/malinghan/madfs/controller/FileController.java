package com.malinghan.madfs.controller;

import com.malinghan.madfs.model.BlockMeta;
import com.malinghan.madfs.model.FileMeta;
import com.malinghan.madfs.config.MadfsConfigProperties;
import com.malinghan.madfs.service.BlockStorageService;
import com.malinghan.madfs.sync.HttpSyncer;
import com.malinghan.madfs.sync.MQSyncer;
import com.malinghan.madfs.util.FileUtils;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@Slf4j
public class FileController {

    @Autowired
    private MadfsConfigProperties config;

    @Autowired
    private MQSyncer mqSyncer;

    @Autowired
    private HttpSyncer httpSyncer;

    @Autowired
    private BlockStorageService blockStorageService;

    @PostMapping("/upload")
    public String upload(@RequestParam MultipartFile file,
                         @RequestHeader(value = "X-Filename", required = false) String xFilename,
                         @RequestHeader(value = "X-Orig-Filename", required = false) String xOrigFilename)
            throws IOException {

        log.info("[UPLOAD] ===== 收到上传请求 =====");
        log.info("[UPLOAD] 原始文件名: {}, 文件大小: {} bytes",
                file.getOriginalFilename(), file.getSize());

        // [v6.0 新增] 检测是否为同步请求
        String filename;
        String originalFilename;
        boolean needSync;

        if (xFilename != null && !xFilename.isEmpty()) {
            // 同步请求：使用请求头中的文件名
            filename = xFilename;
            originalFilename = xOrigFilename != null ? xOrigFilename : file.getOriginalFilename();
            needSync = false;  // 不再触发二次同步
            log.info("[UPLOAD] 检测到 X-Filename 头: {}", xFilename);
            log.info("[UPLOAD] 主从同步上传 => X-Orig-Filename: {}", originalFilename);
            log.info("[UPLOAD] 主从同步请求, 无需再次同步");
        } else {
            // 普通上传：生成 UUID 文件名
            filename = FileUtils.getUUIDFile(file.getOriginalFilename());
            originalFilename = file.getOriginalFilename();
            needSync = true;
            log.info("[UPLOAD] 普通上传 => 生成UUID文件名: {}, needSync=true", filename);
        }

        // [v2.0] 计算子目录、写入文件
        String subdir = FileUtils.getSubdir(filename);
        File dest = new File(config.getUploadPath() + "/" + subdir + "/" + filename);
        file.transferTo(dest);
        log.info("[UPLOAD] 文件写入完成: {}", dest.getAbsolutePath());

        // [v4.0] 构建 FileMeta、计算 MD5、写入 .meta
        FileMeta meta = new FileMeta();
        meta.setName(filename);
        meta.setOriginalFilename(originalFilename);
        meta.setSize(file.getSize());
        meta.setDownloadUrl(config.getDownloadUrl());

        if (config.isAutoMd5()) {
            String md5 = DigestUtils.md5Hex(new FileInputStream(dest));
            meta.getTags().put("md5", md5);
            log.info("[UPLOAD] 计算MD5: {}", md5);
        }

        FileUtils.writeMeta(dest, meta);

        // [v6.0 新增] 根据配置选择同步策略
        if (needSync) {
            if (config.isSyncBackup()) {
                // 尝试 HTTP 同步
                log.info("[UPLOAD] 需要同步, syncBackup=true");
                log.info("[UPLOAD] 尝试HTTP同步 => backupUrl: {}", config.getBackupUrl());

                boolean success = httpSyncer.sync(dest, config.getBackupUrl(), originalFilename);

                if (success) {
                    log.info("[UPLOAD] HTTP同步成功");
                } else {
                    // HTTP 失败，降级为 MQ
                    log.warn("[UPLOAD] HTTP同步失败, 降级为MQ异步补偿");
                    mqSyncer.sync(meta);
                }
            } else {
                // 直接走 MQ 异步同步
                log.info("[UPLOAD] 需要同步, syncBackup=false");
                log.info("[UPLOAD] 直接走MQ异步同步");
                mqSyncer.sync(meta);
            }
        }

        log.info("[UPLOAD] ===== 上传完成, 返回文件名: {} =====", filename);
        return filename;
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

    /**
     * 健康检查接口
     * GET /health
     */
    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "UP");
        result.put("node", config.getDownloadUrl());
        result.put("storagePath", config.getUploadPath());
        result.put("fileCount", FileUtils.countFiles(config.getUploadPath()));
        result.put("syncBackup", config.isSyncBackup());
        result.put("autoMd5", config.isAutoMd5());
        return result;
    }

    /**
     * 文件列表接口
     * GET /list?page=0&size=20
     */
    @GetMapping("/list")
    public Map<String, Object> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        List<String> files = FileUtils.listFiles(config.getUploadPath(), page, size);
        long total = FileUtils.countFiles(config.getUploadPath());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        result.put("files", files);
        return result;
    }

    // 1. 初始化上传
    @PostMapping("/upload/init")
    public Map<String, Object> initUpload(@RequestParam String filename,
                                          @RequestParam long size) {
        return blockStorageService.initUpload(filename, size);
    }

    // 2. 上传单块
    @PostMapping("/upload/chunk")
    public BlockMeta uploadChunk(@RequestParam String uploadId,
                                 @RequestParam int index,
                                 @RequestParam String filename,
                                 @RequestBody byte[] data) throws IOException {
        return blockStorageService.uploadChunk(uploadId, index, data, filename);
    }

    // 3. 完成上传
    @PostMapping("/upload/complete")
    public FileMeta completeUpload(@RequestParam String uploadId,
                                   @RequestParam String filename,
                                   @RequestParam String originalFilename,
                                   @RequestParam long size) throws IOException {
        return blockStorageService.completeUpload(uploadId, filename, originalFilename, size);
    }
}