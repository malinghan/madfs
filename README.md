# madfs - 轻量级分布式文件存储系统

[![Java](https://img.shields.io/badge/Java-17-blue.svg)](https://openjdk.java.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.3-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

**madfs**（Malinghan Distributed File System）是一个基于 Spring Boot 构建的轻量级分布式文件存储系统，支持文件的上传、下载、元数据管理，以及多节点间的文件同步。

## ✨ 特性

- 🚀 **轻量级架构**：基于 Spring Boot 4.0.3，零依赖启动（可选 RocketMQ）
- 📦 **智能分桶存储**：自动将文件分散到 256 个哈希桶目录，避免单目录文件过多
- 🔗 **多节点同步**：支持 HTTP 同步备份和 RocketMQ 异步广播两种同步策略
- 🏷️ **元数据管理**：每个文件附带 JSON 格式的元数据（原始文件名、大小、MD5 等）
- 🔍 **健康检查**：提供 `/health` 接口实时监控节点状态
- 📊 **文件列表**：支持分页查询所有已存储文件
- 🧩 **块存储支持**：支持大文件分块上传和断点续传（v8.0+）
- 🔐 **MD5 校验**：自动计算并存储文件 MD5 值，确保数据完整性

## 🏗️ 系统架构

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
                                              │  Topic: madfs    │
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
│   ~/madfs/{xx}/{uuid}.ext                               │
│   ~/madfs/{xx}/{uuid}.ext.meta                          │
└─────────────────────────────────────────────────────────┘
```

## 🛠️ 技术栈

- **后端框架**：Spring Boot 4.0.3
- **开发语言**：Java 17
- **构建工具**：Maven
- **消息队列**：RocketMQ（可选，用于异步同步）
- **工具库**：
  - Lombok：简化代码
  - Commons Codec：MD5 计算
  - FastJSON / Jackson：JSON 序列化
  - RocketMQ Spring Boot Starter：MQ 集成

## 🚀 快速开始

### 环境要求

- JDK 17+
- Maven 3.6+
- RocketMQ 5.1.0+（可选，仅多节点同步时需要）

### 安装步骤

1. **克隆项目**
```bash
git clone https://github.com/malinghan/madfs.git
cd madfs
```

2. **编译项目**
```bash
mvn clean package -DskipTests
```

3. **启动应用**
```bash
java -jar target/madfs-0.0.1-SNAPSHOT.jar
```

或使用 Maven 直接运行：
```bash
mvn spring-boot:run
```

### 默认配置

应用默认配置如下（可通过 `application.yml` 或命令行参数覆盖）：

```yaml
server:
  port: 8090

madfs:
  path: ${user.home}/madfs           # 文件存储根目录
  downloadUrl: http://localhost:8090/download
  autoMd5: true                       # 自动计算 MD5
  group: C8090                        # RocketMQ 消费者组
  syncBackup: false                   # 是否启用 HTTP 同步备份
  backupUrl: http://localhost:8091/upload
  blockSize: 4194304                  # 分块大小（4MB）

rocketmq:
  name-server: localhost:9876
  producer:
    group: madfs-producer
```

## 📖 API 接口

### 1. 文件上传

**接口**：`POST /upload`

**请求**：
```bash
curl -X POST http://localhost:8090/upload \
  -F "file=@/path/to/your/file.txt"
```

**响应**：返回 UUID 格式的文件名（如 `3a9c1b2d-4e5f-6789-abcd-ef0123456789.txt`）

**高级用法**（主从同步上传）：
```bash
curl -X POST http://localhost:8090/upload \
  -F "file=@file.txt" \
  -H "X-Filename: custom-uuid.txt" \
  -H "X-Orig-Filename: original.txt"
```

### 2. 文件下载

**接口**：`GET /download?name={filename}`

**请求**：
```bash
curl -O "http://localhost:8090/download?name=3a9c1b2d-4e5f-6789-abcd-ef0123456789.txt"
```

### 3. 元数据查询

**接口**：`GET /meta?name={filename}`

**请求**：
```bash
curl "http://localhost:8090/meta?name=3a9c1b2d-4e5f-6789-abcd-ef0123456789.txt"
```

**响应示例**：
```json
{
  "name": "3a9c1b2d-4e5f-6789-abcd-ef0123456789.txt",
  "originalFilename": "test.txt",
  "size": 15,
  "downloadUrl": "http://localhost:8090/download",
  "tags": {
    "md5": "d41d8cd98f00b204e9800998ecf8427e"
  }
}
```

### 4. 健康检查

**接口**：`GET /health`

**请求**：
```bash
curl http://localhost:8090/health
```

**响应示例**：
```json
{
  "status": "UP",
  "node": "http://localhost:8090/download",
  "storagePath": "/Users/username/madfs",
  "fileCount": 42,
  "syncBackup": false,
  "autoMd5": true
}
```

### 5. 文件列表

**接口**：`GET /list?page={page}&size={size}`

**请求**：
```bash
curl "http://localhost:8090/list?page=0&size=20"
```

**响应示例**：
```json
{
  "total": 100,
  "page": 0,
  "size": 20,
  "files": [
    "3a9c1b2d-4e5f-6789-abcd-ef0123456789.txt",
    "..."
  ]
}
```

### 6. 分块上传（v8.0+）

#### 6.1 初始化上传
```bash
curl -X POST "http://localhost:8090/upload/init?filename=test.zip&size=10485760"
```

#### 6.2 上传分块
```bash
curl -X POST "http://localhost:8090/upload/chunk?uploadId=xxx&index=0&filename=test.zip" \
  --data-binary @chunk0.dat
```

#### 6.3 完成上传
```bash
curl -X POST "http://localhost:8090/upload/complete?uploadId=xxx&filename=test.zip&originalFilename=test.zip&size=10485760"
```

## 🔄 同步策略

madfs 提供两种文件同步策略，适用于不同场景：

| 策略 | 说明 | 适用场景 |
|------|------|----------|
| **HTTP 同步** | 上传完成后同步推送到备份节点 | 主从架构、实时性要求高 |
| **MQ 异步** | 通过 RocketMQ 广播，各节点自行下载 | 多活架构、最终一致性 |

### 同步流程

```
上传完成
    │
    ├─ syncBackup=true？
    │     ├─ Yes ──► HTTP 同步到备份节点
    │     │           ├─ 成功 ──► 结束
    │     │           └─ 失败 ──► 降级为 MQ 异步补偿
    │     └─ No  ──► MQ 异步广播
    │
    └─ 其他节点收到 MQ 消息
          ├─ 跳过自身发出的消息
          ├─ 写入 .meta 文件（幂等）
          └─ 下载文件（已存在则跳过）
```

## 📁 文件存储结构

```
~/madfs/
├── 00/
│   ├── 00a3f2...jpg
│   └── 00a3f2...jpg.meta
├── 01/
├── ...
└── ff/
```

- **文件名格式**：`{UUID}.{ext}`（如 `3a9c1b2d-....jpg`）
- **子目录规则**：取文件名前两位十六进制字符（共 256 个桶）
- **元数据文件**：与数据文件同名，追加 `.meta` 后缀
- **存储内容**：`.meta` 文件为 JSON 格式，包含文件元信息

## 🧪 测试示例

### 完整测试流程

```bash
# 1. 上传文件
FILENAME=$(curl -s -X POST http://localhost:8090/upload \
  -F "file=@/tmp/test.txt")
echo "上传成功：$FILENAME"

# 2. 查询元数据
curl "http://localhost:8090/meta?name=$FILENAME"

# 3. 下载文件
curl -o /tmp/downloaded.txt "http://localhost:8090/download?name=$FILENAME"

# 4. 验证文件一致性
diff /tmp/test.txt /tmp/downloaded.txt && echo "✓ 文件一致" || echo "✗ 文件不一致"

# 5. 查看健康状态
curl http://localhost:8090/health | python3 -m json.tool

# 6. 列出文件
curl "http://localhost:8090/list?page=0&size=10" | python3 -m json.tool
```

### 多节点同步测试

```bash
# 前提：启动 RocketMQ
docker run -d --name rmqnamesrv -p 9876:9876 apache/rocketmq:5.1.0 sh mqnamesrv
docker run -d --name rmqbroker -p 10911:10911 --link rmqnamesrv:namesrv \
  -e "NAMESRV_ADDR=namesrv:9876" apache/rocketmq:5.1.0 sh mqbroker

# 启动节点 A（端口 8090）
java -jar target/madfs.jar \
  --server.port=8090 \
  --madfs.downloadUrl=http://localhost:8090/download \
  --madfs.group=C8090

# 启动节点 B（端口 8091，不同存储路径）
java -jar target/madfs.jar \
  --server.port=8091 \
  --madfs.downloadUrl=http://localhost:8091/download \
  --madfs.group=C8091 \
  --madfs.path=/tmp/madfs-node2

# 向节点 A 上传文件
FILENAME=$(curl -s -X POST http://localhost:8090/upload -F "file=@/tmp/test.txt")

# 等待 MQ 同步（约 1-2 秒）
sleep 3

# 验证节点 B 已同步
curl "http://localhost:8091/download?name=$FILENAME" -o /tmp/from-node2.txt
diff /tmp/test.txt /tmp/from-node2.txt && echo "✓ 同步成功" || echo "✗ 同步失败"
```

## 📋 开发路线图

| 版本 | 核心功能 | 状态 |
|------|----------|------|
| v1.0 | 项目骨架 + 本地文件存储 | ✅ 已完成 |
| v2.0 | 文件上传接口 | ✅ 已完成 |
| v3.0 | 文件下载接口 | ✅ 已完成 |
| v4.0 | 元数据管理（FileMeta + .meta 文件） | ✅ 已完成 |
| v5.0 | MQ 异步同步（多节点广播） | ✅ 已完成 |
| v6.0 | HTTP 同步备份（主从直连） | ✅ 已完成 |
| v7.0 | 配置中心化 + 健康检查接口 | ✅ 已完成 |
| v8.0 | 块存储改造（文件分块存储） | ✅ 已完成 |
| v9.0 | 对象存储接口（S3 兼容） | 🚧 规划中 |
| v10.0 | 集群管理 + 自动节点发现 | 🚧 规划中 |
| v11.0 | 存储配额 + 访问控制 | 🚧 规划中 |
| v12.0 | 监控 + 告警（Prometheus） | 🚧 规划中 |

## 🔧 配置说明

### 核心配置项

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `madfs.path` | String | `${user.home}/madfs` | 文件存储根目录 |
| `madfs.downloadUrl` | String | - | 本节点对外下载地址 |
| `madfs.autoMd5` | Boolean | `true` | 是否自动计算 MD5 |
| `madfs.group` | String | `C8090` | RocketMQ 消费者组 |
| `madfs.syncBackup` | Boolean | `false` | 是否启用 HTTP 同步备份 |
| `madfs.backupUrl` | String | - | 备份节点上传地址 |
| `madfs.blockSize` | Integer | `4194304` | 分块大小（4MB） |

### RocketMQ 配置

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `rocketmq.name-server` | String | `localhost:9876` | NameServer 地址 |
| `rocketmq.producer.group` | String | `madfs-producer` | 生产者组 ID |

## 🤝 贡献指南

欢迎提交 Issue 和 Pull Request！

1. Fork 本仓库
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 开启 Pull Request

## 📄 许可证

本项目采用 MIT 许可证。详见 [LICENSE](LICENSE) 文件。

## 👥 作者

- **malinghan** - [GitHub](https://github.com/malinghan)

## 🙏 致谢

感谢以下开源项目：

- [Spring Boot](https://spring.io/projects/spring-boot)
- [RocketMQ](https://rocketmq.apache.org/)
- [Lombok](https://projectlombok.org/)
- [FastJSON](https://github.com/alibaba/fastjson)

## 📞 联系方式

如有问题或建议，请通过以下方式联系：

- GitHub Issues: [提交 Issue](https://github.com/malinghan/madfs/issues)
- Email: [您的邮箱]

---

**madfs** - 让文件存储更简单！🚀
