package com.malinghan.madfs.service;

import com.malinghan.madfs.config.MadfsConfigProperties;
import com.malinghan.madfs.model.BlockMeta;
import com.malinghan.madfs.model.FileMeta;
import com.malinghan.madfs.util.FileUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class BlockStorageService {

    @Autowired
    private MadfsConfigProperties config;

    // 临时存储上传中的块：uploadId -> 已上传块列表
    private final Map<String, List<BlockMeta>> uploadSessions = new ConcurrentHashMap<>();

    /**
     * 初始化分块上传，返回 uploadId 和预期块数
     */
    public Map<String, Object> initUpload(String filename, long totalSize) {
        String uploadId = UUID.randomUUID().toString();
        int blockCount = (int) Math.ceil((double) totalSize / config.getBlockSize());
        uploadSessions.put(uploadId, new ArrayList<>());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("uploadId", uploadId);
        result.put("blockCount", blockCount);
        result.put("blockSize", config.getBlockSize());
        return result;
    }

    /**
     * 上传单个块
     */
    public BlockMeta uploadChunk(String uploadId, int index, byte[] data, String uuidName) throws IOException {
        String hash = DigestUtils.md5Hex(data);

        // 内容寻址：相同 hash 的块只存一份
        String subdir = FileUtils.getSubdir(uuidName);
        String blockPath = subdir + "/" + uuidName + ".block." + index;
        File blockFile = new File(config.getUploadPath() + "/" + blockPath);

        if (!blockFile.exists()) {
            Files.write(blockFile.toPath(), data);
            log.info("[BLOCK] 写入块: index={}, hash={}, size={}", index, hash, data.length);
        } else {
            log.info("[BLOCK] 块已存在（去重）: index={}, hash={}", index, hash);
        }

        BlockMeta blockMeta = new BlockMeta(index, hash, data.length, blockPath);
        uploadSessions.get(uploadId).add(blockMeta);
        return blockMeta;
    }

    /**
     * 完成上传：写 .meta 文件，清理 session
     */
    public FileMeta completeUpload(String uploadId, String uuidName,
                                   String originalFilename, long totalSize) throws IOException {
        List<BlockMeta> blocks = uploadSessions.remove(uploadId);
        // 按 index 排序，防止乱序上传
        blocks.sort(Comparator.comparingInt(BlockMeta::getIndex));

        FileMeta meta = new FileMeta();
        meta.setName(uuidName);
        meta.setOriginalFilename(originalFilename);
        meta.setSize(totalSize);
        meta.setDownloadUrl(config.getDownloadUrl());
        meta.setBlocks(blocks);

        String subdir = FileUtils.getSubdir(uuidName);
        File dataFile = new File(config.getUploadPath() + "/" + subdir + "/" + uuidName);
        FileUtils.writeMeta(dataFile, meta);

        log.info("[BLOCK] 上传完成: name={}, blocks={}", uuidName, blocks.size());
        return meta;
    }

    /**
     * 合并所有块，输出到 OutputStream（用于完整下载）
     */
    public void mergeToStream(FileMeta meta, OutputStream out) throws IOException {
        for (BlockMeta block : meta.getBlocks()) {
            File blockFile = new File(config.getUploadPath() + "/" + block.getPath());
            Files.copy(blockFile.toPath(), out);
        }
    }

    /**
     * Range 下载：定位到对应块，输出指定字节范围
     */
    public void rangeOutput(FileMeta meta, long start, long end, OutputStream out) throws IOException {
        long blockSize = config.getBlockSize();
        int startBlock = (int) (start / blockSize);
        int endBlock = (int) (end / blockSize);

        long written = 0;
        long globalOffset = (long) startBlock * blockSize;

        for (int i = startBlock; i <= endBlock; i++) {
            BlockMeta block = meta.getBlocks().get(i);
            File blockFile = new File(config.getUploadPath() + "/" + block.getPath());
            byte[] data = Files.readAllBytes(blockFile.toPath());

            long blockStart = Math.max(0, start - globalOffset);
            long blockEnd = Math.min(data.length, end - globalOffset + 1);

            out.write(data, (int) blockStart, (int) (blockEnd - blockStart));
            written += blockEnd - blockStart;
            globalOffset += data.length;
        }
    }
}