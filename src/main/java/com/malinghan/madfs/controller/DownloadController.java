package com.malinghan.madfs.controller;

import com.malinghan.madfs.config.MadfsConfigProperties;
import com.malinghan.madfs.model.FileMeta;
import com.malinghan.madfs.service.BlockStorageService;
import com.malinghan.madfs.util.FileUtils;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.io.File;
import java.io.IOException;

@RestController
@Slf4j
public class DownloadController {

    @Autowired
    private BlockStorageService blockStorageService;

    @Autowired
    private MadfsConfigProperties config;

    @GetMapping("/download")
    public void download(@RequestParam String name,
                         @RequestHeader(value = "Range", required = false) String range,
                         HttpServletResponse response) throws IOException {

        String subdir = FileUtils.getSubdir(name);
        File dataFile = new File(config.getUploadPath() + "/" + subdir + "/" + name);
        FileMeta meta = FileUtils.readMeta(dataFile);

        if (meta.isChunked()) {
            if (range != null) {
                // 解析 Range: bytes=start-end
                String[] parts = range.replace("bytes=", "").split("-");
                long start = Long.parseLong(parts[0]);
                long end = parts.length > 1 ? Long.parseLong(parts[1]) : meta.getSize() - 1;

                response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
                response.setHeader("Content-Range",
                        "bytes " + start + "-" + end + "/" + meta.getSize());
                response.setContentLengthLong(end - start + 1);
                blockStorageService.rangeOutput(meta, start, end, response.getOutputStream());
            } else {
                response.setContentLengthLong(meta.getSize());
                blockStorageService.mergeToStream(meta, response.getOutputStream());
            }
        } else {
            // 兼容 v7.0 及以前的非分块文件
            response.setContentLengthLong(dataFile.length());
            FileUtils.output(dataFile, response.getOutputStream());
        }
    }
}
