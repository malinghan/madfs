package com.malinghan.madfs.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BlockMeta {
    private int index;       // 块序号，从 0 开始
    private String hash;     // 块内容 MD5（用于去重和校验）
    private long size;       // 块实际大小（最后一块可能小于 blockSize）
    private String path;     // 块文件相对路径
}
