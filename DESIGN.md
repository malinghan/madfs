# MAFS 分布式文件系统设计架构文档

## 1. 项目概述

MAFS（KimmKing File System）是一个基于 Spring Boot 构建的轻量级分布式文件存储系统，支持文件的上传、下载、元数据管理，以及多节点间的文件同步。

- **技术栈**：Java 17 + Spring Boot 3.3.1 + RocketMQ
- **构建工具**：Maven
- **包名**：`com.malinghan.mafs`

---

## 2. 整体架构

```
┌─────────────────────────────────────────────────────────┐
│                        Client                           │
│              (Browser / HTTP Client)                    │
└───────────────────────┬─────────────────────────────────┘
                        │ HTTP
                        ▼
┌─────────────────────────────────────────────────────────┐
│                   FileController                        │
│         POST /upload  GET /download  GET /meta          │
└──────┬──────────────────────────────────────────────────┘
       │
       ├──────────────────────────────────────────────────┐
       │                                                  │
       ▼                                                  ▼
┌─────────────┐                               ┌──────────────────┐
│  HttpSyncer │  同步（主从直连）              │    MQSyncer      │
│  (同步备份) │ ─────────────────────────►    │  (异步消息同步)  │
└─────────────┘                               └────────┬─────────┘
                                                       │
                                              ┌────────▼─────────┐
                                              │    RocketMQ      │
                                              │  Topic: mafs     │
                                              └────────┬─────────┘
                                                       │
                                              ┌────────▼─────────┐
                                              │  FileMQSyncer    │
                                              │ (消息消费/下载)  │
                                              └──────────────────┘
       │
       ▼
┌─────────────────────────────────────────────────────────┐
│                    本地文件存储                          │
│   ~/mafs/{xx}/{uuid}.ext                                │
│   ~/mafs/{xx}/{uuid}.ext.meta                           │
└─────────────────────────────────────────────────────────┘
```

---

## 3. 模块说明

### 3.1 入口层

**`MafsApplication`**
- Spring Boot 启动类
- 启动时调用 `FileUtils.init(uploadPath)`，预创建 256 个子目录（`00`~`ff`）

### 3.2 接口层

**`FileController`** — REST 控制器，提供三个接口：

| 接口 | 方法 | 说明 |
|------|------|------|
| `/upload` | POST | 文件上传，支持普通上传和主从同步上传 |
| `/download` | GET/POST | 按文件名下载文件 |
| `/meta` | GET/POST | 获取文件元数据（JSON） |

**上传流程：**

```
接收文件
   │
   ├─ 有 X-Filename 头？ ──Yes──► 主从同步写入，不再触发同步
   │
   └─ No ──► 生成 UUID 文件名
              │
              ├─ 写文件到本地存储
              ├─ 生成 .meta 文件
              │
              └─ syncBackup=true？
                    ├─ Yes ──► HttpSyncer 同步备份节点
                    │              └─ 失败 ──► MQSyncer 异步补偿
                    └─ No  ──► MQSyncer 异步广播
```

### 3.3 同步层

**`HttpSyncer`** — 同步 HTTP 文件推送
- 使用 `RestTemplate` 将文件以 multipart/form-data 格式 POST 到备份节点的 `/upload`
- 通过自定义请求头传递文件名信息：
  - `X-Filename`：存储文件名（UUID 格式）
  - `X-Orig-Filename`：原始文件名

**`MQSyncer`** — 基于 RocketMQ 的异步同步
- **生产者**：将 `FileMeta` 序列化为 JSON 发送到 Topic `mafs`
- **消费者**（内部类 `FileMQSyncer`）：
  1. 反序列化消息获取 `FileMeta`
  2. 跳过本机消息（通过 `downloadUrl` 去重）
  3. 写入 `.meta` 文件（幂等，已存在则跳过）
  4. 下载文件（按 size 校验，已存在且大小一致则跳过）

### 3.4 元数据层

**`FileMeta`** — 文件元数据模型

| 字段 | 类型 | 说明 |
|------|------|------|
| `name` | String | 存储文件名（UUID + 扩展名） |
| `originalFilename` | String | 原始文件名 |
| `size` | long | 文件大小（字节） |
| `downloadUrl` | String | 来源节点下载地址（用于去重） |
| `tags` | Map<String,String> | 扩展标签，如 `md5` |

元数据以 JSON 格式持久化为 `{filename}.meta` 文件，与数据文件同目录存放。

### 3.5 工具层

**`FileUtils`** — 文件操作工具类

| 方法 | 说明 |
|------|------|
| `init(path)` | 初始化 256 个子目录（`00`~`ff`） |
| `getUUIDFile(name)` | 生成 UUID 文件名，保留原扩展名 |
| `getSubdir(name)` | 取文件名前两位作为子目录名 |
| `getMimeType(name)` | 根据文件名推断 MIME 类型 |
| `writeMeta(file, meta)` | 序列化 FileMeta 写入磁盘 |
| `download(url, file)` | 从远端 URL 下载文件到本地 |
| `output(file, out)` | 16KB 缓冲流式输出文件 |

### 3.6 配置层

**`MafsConfigProperties`** — 配置属性（前缀 `mafs`）

| 属性 | 说明 |
|------|------|
| `path` / `uploadPath` | 本地文件存储根目录 |
| `downloadUrl` | 本节点对外下载地址 |
| `backupUrl` | 备份节点上传地址 |
| `group` | RocketMQ 消费者组 |
| `autoMd5` | 是否自动计算并存储 MD5 |
| `syncBackup` | 是否启用同步 HTTP 备份（否则走 MQ） |

**`MafsConfig`** — 配置 Multipart 临时目录为 `/private/tmp/tomcat`

---

## 4. 文件存储结构

```
~/mafs/
├── 00/
│   ├── 00a3f2...jpg
│   └── 00a3f2...jpg.meta
├── 01/
├── ...
└── ff/
```

- 文件名格式：`{UUID}.{ext}`，例如 `3a9c1b2d-...-4e5f.jpg`
- 子目录由文件名前两位十六进制字符决定，共 256 个桶，均匀分散文件
- 每个文件对应一个同名 `.meta` 文件存储元数据

---

## 5. 同步策略

| 场景 | 策略 |
|------|------|
| `syncBackup=true` 且备份节点可达 | HttpSyncer 同步推送 |
| `syncBackup=true` 但推送失败 | 降级为 MQSyncer 异步补偿 |
| `syncBackup=false` | 直接走 MQSyncer 异步广播 |
| 消费端收到自身发出的消息 | 通过 `downloadUrl` 比对跳过，避免重复下载 |
| 文件已存在且大小一致 | 跳过下载，保证幂等 |

---

## 6. 默认配置（application.yml）

```yaml
server:
  port: 8090

mafs:
  path: ${user.home}/mafs
  syncBackup: false
  autoMd5: true
  group: C8090
  backupUrl: http://localhost:8091/upload
  downloadUrl: http://localhost:8090/download

rocketmq:
  name-server: localhost:9876
  producer:
    group: mafs-producer
```

---

## 7. 扩展方向（代码注释中提及）

代码注释中提到三种存储模式的演进路径：

1. **文件存储**（当前实现）— 基于本地文件系统
2. **块存储** — 效率最高，计划改造方向
3. **对象存储** — 类 S3 模式