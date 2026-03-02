# madfs 从 0 到 1 开发计划

> 类比：把 madfs 想象成一个**快递仓储系统**。
> - 文件 = 包裹
> - 上传 = 收件入库
> - 下载 = 取件出库
> - 元数据 = 快递单（记录包裹信息）
> - 子目录分桶 = 仓库货架编号（按编号前两位分区，避免一个区域堆满）
> - MQ 同步 = 仓库间调货通知（异步广播，各仓库自行取货）
> - HTTP 同步 = 直接送货上门（同步推送，实时到达）

---

## 版本总览

| 版本 | 核心功能 | 依赖 |
|------|----------|------|
| v1.0 | 项目骨架 + 本地文件存储 | Spring Boot Web |
| v2.0 | 文件上传接口 | v1.0 |
| v3.0 | 文件下载接口 | v2.0 |
| v4.0 | 元数据管理（FileMeta + .meta 文件） | v3.0 |
| v5.0 | MQ 异步同步（多节点广播） | v4.0 + RocketMQ |
| v6.0 | HTTP 同步备份（主从直连） | v5.0 |
| v7.0 | 配置中心化 + 健康检查接口 | v6.0 |
| v8.0 | 块存储改造（文件分块存储） | v7.0 |
| v9.0 | 对象存储接口（S3 兼容） | v8.0 |
| v10.0 | 集群管理 + 自动节点发现 | v9.0 |

---

## v1.0 — 项目骨架 + 本地文件存储

### 目标
搭建 Spring Boot 项目骨架，实现存储目录初始化（256 个哈希桶）。

> 类比：建仓库，把仓库划分成 256 个货架区（00~ff），每个区放一类包裹。

### 功能点
- Spring Boot 项目初始化（Web + Maven）
- `FileUtils.init(path)` — 创建根目录 + 256 个子目录（`00`~`ff`）
- `MadfsApplication` 启动时调用 `init`
- `MadfsConfigProperties` — 读取 `mafs.path` 配置

### 流程图
```
SpringApplication.run()
        │
        ▼
ApplicationRunner.run()
        │
        ▼
FileUtils.init(uploadPath)
        │
        ├─ mkdir ~/madfs/
        │
        └─ for i in 0..255
               └─ mkdir ~/madfs/00/ ~ ~/madfs/ff/

结果：256 个空目录就绪
```

### 关键代码结构
```
src/main/java/com/malinghan/madfs/
├── MadfsApplication.java          # 启动类 + ApplicationRunner
├── config/
│   └── MadfsConfigProperties.java # @ConfigurationProperties(prefix="madfs")
└── util/
    └── FileUtils.java             # init() 方法
```

### 测试流程
```bash
# 1. 启动应用
mvn spring-boot:run

# 2. 验证目录创建
ls ~/madfs/ | wc -l
# 预期输出: 256

ls ~/madfs/ | head -5
# 预期输出:
# 00
# 01
# 02
# 03
# 04
```

---

## v2.0 — 文件上传接口

### 目标
实现 `POST /upload`，将文件存储到本地哈希桶目录，返回 UUID 文件名。

> 类比：快递员送来包裹，系统自动生成快递单号（UUID），按单号前两位分配货架，放入对应区域。

### 功能点
- `FileController.upload()` — 接收 multipart 文件
- `FileUtils.getUUIDFile(originalName)` — 生成 UUID 文件名，保留扩展名
- `FileUtils.getSubdir(name)` — 取文件名前两位作为子目录
- 文件写入 `~/madfs/{subdir}/{uuid}.ext`

### 流程图
```
POST /upload (multipart/form-data, file=xxx.jpg)
        │
        ▼
FileController.upload(file)
        │
        ├─ originalFilename = file.getOriginalFilename()  // "test.jpg"
        │
        ├─ uuidName = FileUtils.getUUIDFile("test.jpg")   // "3a9c1b2d-....jpg"
        │
        ├─ subdir = FileUtils.getSubdir("3a9c1b2d-...")   // "3a"
        │
        ├─ dest = ~/madfs/3a/3a9c1b2d-....jpg
        │
        └─ file.transferTo(dest)
                │
                ▼
        返回 "3a9c1b2d-....jpg"
```

### 测试流程
```bash
# 准备测试文件
echo "Hello madfs v2" > /tmp/test.txt

# 上传文件
curl -X POST http://localhost:8090/upload \
  -F "file=@/tmp/test.txt"
# 预期返回: 类似 3a9c1b2d-4e5f-6789-abcd-ef0123456789.txt

# 验证文件存在
FILENAME="<上面返回的文件名>"
SUBDIR="${FILENAME:0:2}"
ls ~/madfs/$SUBDIR/
# 预期看到该文件
```

---

## v3.0 — 文件下载接口

### 目标
实现 `GET /download?name=xxx`，按文件名从本地存储读取并流式输出。

> 类比：客户凭快递单号取件，仓库员根据单号前两位找到货架区，取出包裹交给客户。

### 功能点
- `FileController.download(name, response)` — 按名称查找并输出文件
- `FileUtils.getMimeType(name)` — 根据扩展名推断 Content-Type
- `FileUtils.output(file, outputStream)` — 16KB 缓冲区流式输出

### 流程图
```
GET /download?name=3a9c1b2d-....jpg
        │
        ▼
FileController.download()
        │
        ├─ subdir = getSubdir("3a9c...") => "3a"
        ├─ file = ~/madfs/3a/3a9c1b2d-....jpg
        │
        ├─ response.setContentType("image/jpeg")
        ├─ response.setContentLength(file.length())
        │
        └─ FileUtils.output(file, response.getOutputStream())
               │
               └─ while (read 16KB) { write to stream }
```

### 测试流程
```bash
# 先上传一个文件（参考 v2.0）
FILENAME=$(curl -s -X POST http://localhost:8090/upload -F "file=@/tmp/test.txt")
echo "上传成功: $FILENAME"

# 下载文件
curl -o /tmp/downloaded.txt "http://localhost:8090/download?name=$FILENAME"

# 验证内容一致
diff /tmp/test.txt /tmp/downloaded.txt && echo "文件一致 ✓" || echo "文件不一致 ✗"

# 测试图片下载（验证 MIME 类型）
curl -I "http://localhost:8090/download?name=$FILENAME"
# 预期 Content-Type: text/plain
```

---

## v4.0 — 元数据管理

### 目标
上传时生成 `.meta` 文件，提供 `GET /meta?name=xxx` 接口查询元数据，支持 MD5 自动计算。

> 类比：每个包裹除了实物，还有一张快递单（.meta 文件），记录：原始包裹名、重量（size）、来源地址、防伪码（MD5）。

### 功能点
- `FileMeta` — 元数据模型（name, originalFilename, size, downloadUrl, tags）
- `FileUtils.writeMeta(file, meta)` — 序列化 FileMeta 为 JSON 写入 `.meta` 文件
- 上传时自动生成 `.meta` 文件
- `autoMd5=true` 时计算 MD5 存入 `tags.md5`
- `FileController.meta(name)` — 读取并返回 `.meta` 文件内容

### 流程图
```
上传流程（新增 meta 部分）:
        │
        ├─ file.transferTo(dest)          // 写数据文件
        │
        ├─ meta = new FileMeta()
        │   meta.name = "3a9c....jpg"
        │   meta.originalFilename = "test.jpg"
        │   meta.size = 102400
        │   meta.downloadUrl = "http://localhost:8090/download"
        │
        ├─ if autoMd5:
        │       md5 = DigestUtils.md5Hex(file)
        │       meta.tags.put("md5", md5)
        │
        └─ FileUtils.writeMeta(dest, meta)
               └─ 写入 ~/madfs/3a/3a9c....jpg.meta

查询元数据:
GET /meta?name=3a9c....jpg
        │
        └─ 读取 ~/madfs/3a/3a9c....jpg.meta
               └─ 返回 JSON
```

### 存储结构
```
~/madfs/
└── 3a/
    ├── 3a9c1b2d-....jpg        # 数据文件
    └── 3a9c1b2d-....jpg.meta   # 元数据文件（JSON）
```

### 测试流程
```bash
# 上传文件
FILENAME=$(curl -s -X POST http://localhost:8090/upload -F "file=@/tmp/test.txt")

# 查询元数据
curl -s "http://localhost:8090/meta?name=$FILENAME" | python3 -m json.tool
# 预期输出:
# {
#   "name": "3a9c....txt",
#   "originalFilename": "test.txt",
#   "size": 15,
#   "downloadUrl": "http://localhost:8090/download",
#   "tags": {
#     "md5": "d41d8cd98f00b204e9800998ecf8427e"
#   }
# }

# 验证 .meta 文件存在
SUBDIR="${FILENAME:0:2}"
ls ~/madfs/$SUBDIR/
# 预期看到 xxx.txt 和 xxx.txt.meta 两个文件
```

---

## v5.0 — MQ 异步同步（多节点广播）

### 目标
上传完成后，通过 RocketMQ 广播文件元数据，其他节点收到消息后自动从源节点下载文件，实现最终一致性。

> 类比：A 仓库入库一个包裹后，通过广播电台（RocketMQ）通知所有仓库："我这里有个新包裹，单号是 xxx，需要的来取"。各仓库收到广播后，自己去 A 仓库取货（HTTP 下载）。A 仓库自己也收到广播，但发现是自己发的，直接忽略。

### 功能点
- 引入 `rocketmq-spring-boot-starter` 依赖
- `MQSyncer.sync(meta)` — 序列化 FileMeta 发送到 Topic `madfs`
- `FileMQSyncer`（内部消费者）— 消费消息，下载文件
- 去重：`localUrl.equals(meta.downloadUrl)` 跳过本机消息
- 幂等：`.meta` 文件已存在则跳过；文件存在且 size 一致则跳过下载
- `FileUtils.download(url, file)` — 从远端 URL 下载文件

### 流程图
```
节点A 上传完成
        │
        ▼
MQSyncer.sync(meta)
        │
        └─ rocketMQTemplate.send("madfs", JSON(meta))
                │
                ▼
        RocketMQ Topic: madfs
                │
        ┌───────┴────────┐
        ▼                ▼
   节点A 消费者      节点B 消费者
        │                │
        ├─ 解析 meta      ├─ 解析 meta
        ├─ localUrl       ├─ localUrl
        │  == downloadUrl │  != downloadUrl
        └─ 忽略（防自同步）└─ 处理同步
                               │
                               ├─ 写 .meta（幂等）
                               ├─ 检查文件（幂等）
                               └─ GET /download?name=xxx
                                      └─ 写入本地存储
```

### 测试流程
```bash
# 前提：启动 RocketMQ
docker run -d --name rmqnamesrv -p 9876:9876 apache/rocketmq:5.1.0 sh mqnamesrv
docker run -d --name rmqbroker -p 10911:10911 --link rmqnamesrv:namesrv \
  -e "NAMESRV_ADDR=namesrv:9876" apache/rocketmq:5.1.0 sh mqbroker

# 启动节点A（端口 8090）
java -jar target/madfs.jar \
  --server.port=8090 \
  --madfs.downloadUrl=http://localhost:8090/download \
  --madfs.group=C8090

# 启动节点B（端口 8091，另一个终端）
java -jar target/madfs.jar \
  --server.port=8091 \
  --madfs.downloadUrl=http://localhost:8091/download \
  --madfs.group=C8091 \
  --madfs.path=/tmp/madfs-b

# 向节点A上传文件
FILENAME=$(curl -s -X POST http://localhost:8090/upload -F "file=@/tmp/test.txt")
echo "上传到节点A: $FILENAME"

# 等待 MQ 同步（约 1-2 秒）
sleep 3

# 验证节点B已同步
SUBDIR="${FILENAME:0:2}"
ls /tmp/madfs-b/$SUBDIR/
# 预期看到同名文件

# 从节点B下载验证
curl -o /tmp/from-b.txt "http://localhost:8091/download?name=$FILENAME"
diff /tmp/test.txt /tmp/from-b.txt && echo "节点B同步成功 ✓" || echo "同步失败 ✗"
```

---

## v6.0 — HTTP 同步备份（主从直连）

### 目标
`syncBackup=true` 时，上传完成后同步 HTTP 推送到备份节点，失败则降级为 MQ 异步补偿。

> 类比：VIP 包裹走专线直送（HTTP 同步），普通包裹走广播调货（MQ 异步）。专线送达失败时，自动切换广播模式兜底。

### 功能点
- `HttpSyncer.sync(file, backupUrl, originalFilename)` — 用 RestTemplate 推送文件
- 请求头 `X-Filename`（UUID 文件名）+ `X-Orig-Filename`（原始文件名）
- 上传接口检测 `X-Filename` 头：存在则为同步请求，`needSync=false` 防止循环
- HTTP 失败时降级调用 `MQSyncer.sync()`

### 流程图
```
节点A 上传完成（syncBackup=true）
        │
        ▼
HttpSyncer.sync(file, "http://localhost:8091/upload", "test.jpg")
        │
        ├─ 构建 MultipartBody
        │   Header: X-Filename = "3a9c....jpg"
        │   Header: X-Orig-Filename = "test.jpg"
        │
        ├─ POST http://localhost:8091/upload
        │       │
        │       ▼
        │  节点B: FileController.upload()
        │       │
        │       ├─ 检测到 X-Filename 不为空
        │       ├─ needSync = false（不再触发同步，防循环）
        │       └─ 直接存储文件
        │
        ├─ 成功 ──► 返回
        │
        └─ 失败 ──► MQSyncer.sync(meta)（降级补偿）
```

### 测试流程
```bash
# 启动两个节点（同 v5.0）

# 节点A 开启 syncBackup
java -jar target/madfs.jar \
  --server.port=8090 \
  --madfs.syncBackup=true \
  --madfs.backupUrl=http://localhost:8091/upload \
  --madfs.downloadUrl=http://localhost:8090/download \
  --madfs.group=C8090

# 上传文件
FILENAME=$(curl -s -X POST http://localhost:8090/upload -F "file=@/tmp/test.txt")

# 立即验证节点B（HTTP 同步是同步的，无需等待）
SUBDIR="${FILENAME:0:2}"
ls /tmp/madfs-b/$SUBDIR/
# 预期立即看到文件（不需要等 MQ）

# 测试降级：停掉节点B，再上传
# 节点A 日志应显示 HTTP 失败，降级走 MQ
```

---

## v7.0 — 配置中心化 + 健康检查接口

### 目标
完善配置体系，新增 `/health` 接口，支持运行时查看节点状态。

> 类比：仓库管理系统增加"仓库状态看板"，随时查看仓库容量、连接状态、配置信息。

### 功能点
- `MadfsConfigProperties` 完整属性（path, downloadUrl, backupUrl, group, autoMd5, syncBackup）
- `GET /health` — 返回节点状态 JSON（存储路径、文件数量、配置信息）
- `GET /list` — 列出所有文件（分页）
- 启动日志打印完整配置

### 健康检查响应示例
```json
{
  "status": "UP",
  "node": "http://localhost:8090/download",
  "storagePath": "/Users/xxx/madfs",
  "fileCount": 42,
  "syncBackup": false,
  "autoMd5": true
}
```

### 测试流程
```bash
# 启动应用后
curl -s http://localhost:8090/health | python3 -m json.tool

# 上传几个文件后再查
for i in 1 2 3; do
  echo "test $i" > /tmp/test$i.txt
  curl -s -X POST http://localhost:8090/upload -F "file=@/tmp/test$i.txt"
done

curl -s http://localhost:8090/health | python3 -m json.tool
# 预期 fileCount 变为 3

# 列出文件
curl -s "http://localhost:8090/list?page=0&size=10" | python3 -m json.tool
```

---

## 未来开发计划

以下版本为规划中的功能，尚未实现，按优先级排列。

---

### v8.0 — 块存储改造

**目标**：将文件拆分为固定大小的块（如 4MB）分别存储，支持大文件断点续传。

**核心概念**：
> 类比：把一个大包裹拆成多个小箱子（块），每个小箱子独立编号存储。取件时按顺序拼回原包裹。

**功能**：
- 文件按 4MB 分块，每块独立存储为 `{uuid}.block.{index}`
- `.meta` 文件记录块数量、每块 hash、总大小
- 上传接口支持分块上传（`POST /upload/chunk`）
- 下载接口支持 Range 请求（断点续传）
- 块级去重：相同内容的块只存一份（内容寻址）

**实现要点**：
- 引入 `blockSize` 配置（默认 4MB）
- `BlockMeta` 模型：blockIndex, blockHash, blockSize
- `FileUtils.splitToBlocks()` / `FileUtils.mergeBlocks()`
- HTTP Range 头处理：`Content-Range: bytes 0-4194303/10485760`

---

### v9.0 — 对象存储接口（S3 兼容）

**目标**：提供兼容 AWS S3 协议的 REST 接口，支持 Bucket 概念和标准 S3 操作。

**核心概念**：
> 类比：在仓库系统上套一层标准化的"快递柜"接口，任何支持标准快递柜协议的客户端都能直接使用。

**功能**：
- `PUT /{bucket}/{key}` — 上传对象
- `GET /{bucket}/{key}` — 下载对象
- `DELETE /{bucket}/{key}` — 删除对象
- `GET /{bucket}?list-type=2` — 列出 Bucket 内对象
- Bucket 映射到存储路径的命名空间
- 支持 AWS SDK 直接对接（通过自定义 endpoint）

**实现要点**：
- `BucketController` 处理 S3 风格路由
- `BucketService` 管理 Bucket 元数据（存储在 `.bucket.meta`）
- 兼容 S3 XML 响应格式（`ListBucketResult` 等）
- 支持 Presigned URL（预签名下载链接，带过期时间）

---

### v10.0 — 集群管理 + 自动节点发现

**目标**：节点自动注册、发现，支持动态扩缩容，无需手动配置 backupUrl。

**核心概念**：
> 类比：仓库加入联盟后，自动在联盟总部（注册中心）登记，其他仓库自动知道新成员的地址，无需人工通知。

**功能**：
- 节点启动时向注册中心（ZooKeeper 或 Nacos）注册自身信息
- 节点列表动态维护，支持节点上下线感知
- 上传时自动选择同步目标节点（轮询 / 一致性哈希）
- 节点故障自动剔除，恢复后重新加入
- 集群状态接口：`GET /cluster/nodes`

**实现要点**：
- 引入 `spring-cloud-starter-zookeeper-discovery` 或 Nacos
- `ClusterManager` — 维护节点列表，监听节点变化
- `NodeSelector` — 选择同步目标节点的策略接口
- 一致性哈希环：同一文件始终路由到相同节点集合

---

### v11.0 — 存储配额 + 访问控制

**目标**：支持多租户存储配额管理和基于 Token 的访问控制。

**功能**：
- API Token 认证（`Authorization: Bearer xxx`）
- 租户隔离：每个 Token 对应独立存储空间
- 存储配额：限制每个租户最大存储量
- 访问日志：记录每次上传/下载操作
- 文件过期：支持设置文件 TTL，自动清理

---

### v12.0 — 监控 + 告警

**目标**：接入 Prometheus + Grafana，提供存储系统可观测性。

**功能**：
- Micrometer 指标暴露（上传次数、下载次数、存储用量、同步延迟）
- `GET /actuator/prometheus` — Prometheus 抓取端点
- 告警规则：磁盘使用率 > 80%、同步失败率 > 5%
- Grafana Dashboard 模板

---

## 开发节奏建议

```
v1.0 ──► v2.0 ──► v3.0 ──► v4.0   （核心存储，无外部依赖，可独立运行）
                                │
                                ▼
                           v5.0 ──► v6.0   （引入 RocketMQ，实现多节点同步）
                                        │
                                        ▼
                                   v7.0   （完善运维能力）
                                        │
                                        ▼
                              v8.0 ──► v9.0 ──► v10.0   （存储能力升级）
```

每个版本完成后：
1. 运行对应测试流程，确认功能正常
2. 提交 git tag（如 `git tag v1.0`）
3. 再开始下一个版本