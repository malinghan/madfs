package com.malinghan.madfs.sync;

import com.malinghan.madfs.FileMeta.FileMeta;
import com.malinghan.madfs.config.MadfsConfigProperties;
import com.malinghan.madfs.util.FileUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.nio.file.Files;

@Component
@Slf4j
public class MQSyncer {

    private static final String TOPIC = "madfs";

    @Autowired
    private RocketMQTemplate rocketMQTemplate;

    @Autowired
    private MadfsConfigProperties config;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 发送文件元数据到 MQ，通知其他节点同步
     */
    public void sync(FileMeta meta) {
        try {
            String json = MAPPER.writeValueAsString(meta);
            log.info("[MQ-SYNC] ===== 发送MQ消息 =====");
            log.info("[MQ-SYNC] Topic: {}, Payload: {}", TOPIC, json);

            rocketMQTemplate.send(TOPIC, MessageBuilder.withPayload(json).build());

            log.info("[MQ-SYNC] 消息发送完成");
        } catch (Exception e) {
            log.error("[MQ-SYNC] 消息发送失败", e);
        }
    }

    /**
     * MQ 消费者：接收其他节点发来的文件元数据，下载文件到本地
     *
     * 注意: @RocketMQMessageListener 的 consumerGroup 需要动态配置
     * 这里用 SpEL 表达式从配置文件读取
     */
    @Component
    @RocketMQMessageListener(
        topic = TOPIC,
        consumerGroup = "${madfs.group}"   // 每个节点配置不同的 group
    )
    public class FileMQSyncer implements RocketMQListener<String> {

        @Override
        public void onMessage(String message) {
            log.info("[MQ-CONSUMER] ===== 收到MQ消息 =====");
            log.info("[MQ-CONSUMER] 消息体: {}", message);

            try {
                // 1. 反序列化元数据
                FileMeta meta = MAPPER.readValue(message, FileMeta.class);
                log.info("[MQ-CONSUMER] 解析FileMeta: name={}, size={}, downloadUrl={}",
                        meta.getName(), meta.getSize(), meta.getDownloadUrl());

                // 2. 去重：跳过本机发出的消息
                String localUrl = config.getDownloadUrl();
                if (localUrl.equals(meta.getDownloadUrl())) {
                    log.info("[MQ-CONSUMER] 消息来自本机, 忽略 (防止自同步)");
                    return;
                }
                log.info("[MQ-CONSUMER] 消息来自其他节点, 开始处理同步");

                // 3. 计算本地存储路径
                String subdir = FileUtils.getSubdir(meta.getName());
                File dataFile = new File(config.getUploadPath() + "/" + subdir + "/" + meta.getName());
                File metaFile = new File(dataFile.getAbsolutePath() + ".meta");

                // 4. 幂等写入 .meta 文件
                if (!metaFile.exists()) {
                    String metaJson = MAPPER.writeValueAsString(meta);
                    Files.writeString(metaFile.toPath(), metaJson);
                    log.info("[MQ-CONSUMER] Meta文件写入完成: {}", metaFile.getAbsolutePath());
                } else {
                    log.info("[MQ-CONSUMER] Meta文件已存在, 跳过");
                }

                // 5. 幂等下载文件
                if (dataFile.exists() && dataFile.length() == meta.getSize()) {
                    log.info("[MQ-CONSUMER] 文件已存在且大小一致, 跳过下载");
                    return;
                }

                // 6. 从源节点下载文件
                String downloadUrl = meta.getDownloadUrl() + "?name=" + meta.getName();
                log.info("[MQ-CONSUMER] 开始从源节点下载: {}", downloadUrl);
                FileUtils.download(downloadUrl, dataFile);
                log.info("[MQ-CONSUMER] ===== 文件同步完成: {} =====", dataFile.getAbsolutePath());

            } catch (Exception e) {
                log.error("[MQ-CONSUMER] 消息处理失败", e);
            }
        }
    }
}