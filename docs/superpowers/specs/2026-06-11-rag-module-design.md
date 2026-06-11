# RAG 知识库模块设计文档

**版本**: v1.2
**日期**: 2026-06-11
**状态**: 待评审
**修订**: ES 降级为 7.17 + RestHighLevelClient（适配 JDK 8），BGE Embedding 独立服务 + 离线部署方案

---

## 1. 概述

### 1.1 背景

当前系统数据流：前线 → MySQL（`origin_text` 表）→ 前端检索。检索依赖关键词匹配，无法支持语义级别的搜索。

### 1.2 目标

将 MySQL 中原始报文（文字报、图文报）通过切片+Embedding 向量化后存入 Elasticsearch，向前端提供语义检索能力。第一期只做知识库形态，核心功能是语义检索。

### 1.3 边界

- **关注**：MySQL → 切片向量化 → ES；ES 检索接口
- **不关注**：数据推送来源、前端 UI（独立知识库页面后续设计）、DOCX 解析、OCR

---

## 2. 架构设计

### 2.1 合并方式

将 xwRAG 项目核心代码迁移到 xwBackend，作为 `rag` 子模块。xwRAG 中不需要的组件（pgvector、langchain4j、DOCX/OCR）全部去掉。

### 2.2 模块位置

```
xwBackend/src/main/java/com/qy/dch/
├── controller/
│   └── RagController.java               # 知识库检索 + 向量化管理接口
├── service/
│   ├── RagService.java                   # 接口
│   └── impl/
│       └── RagServiceImpl.java           # 编排：切片→向量化→ES写入→检索
├── rag/                                  # RAG 子包（从 xwRAG 迁移）
│   ├── config/
│   │   ├── ElasticsearchConfig.java      # ES 客户端配置
│   │   ├── EmbeddingConfig.java          # WebClient 配置（调用 BGE）
│   │   └── RagProperties.java            # 统一 RAG 参数
│   ├── model/
│   │   ├── DocumentChunk.java            # 切片模型
│   │   └── SearchResult.java             # 检索结果模型
│   ├── chunk/
│   │   └── ChunkService.java             # 混合切片服务
│   ├── embed/
│   │   └── EmbeddingService.java         # BGE Embedding 客户端
│   ├── store/
│   │   └── EsVectorStore.java            # ES 索引管理 + 批量写入
│   └── search/
│       └── HybridSearchService.java      # BM25 + kNN 混合检索
├── task/
│   └── RagIndexingTask.java              # 每日凌晨 3:00 自动向量化
├── mapper/
│   └── RagMapper.java                    # 查询未索引报文
└── entity/
    └── OriginText.java                   # 复用现有实体，只需 is_indexed 字段
```

### 2.3 组件交互

```
┌────────────────────────────────────────────────────┐
│             Spring Boot 后端 (:8081)                │
│                                                     │
│  ┌──────────┐  ┌──────────────┐  ┌──────────────┐  │
│  │ 报文管理  │  │   RAG 模块    │  │ 抽取/融合     │  │
│  │ Uygur*   │  │  ├切片服务   │  │ → 算法服务   │  │
│  │          │  │  ├Embedding │  │   (:5001)    │  │
│  │          │  │  ├ES 写入   │  │              │  │
│  │          │  │  └ES 检索   │  │              │  │
│  └──────────┘  └──────┬───────┘  └──────────────┘  │
│                       │                             │
└───────────────────────┼─────────────────────────────┘
                  HTTP  │
        ┌───────────────┼───────────────┐
        ▼               ▼               ▼
  ┌───────────┐  ┌───────────┐  ┌───────────┐
  │   MySQL   │  │    ES     │  │   BGE     │
  │  :8010    │  │  :9200    │  │ Embedding │
  └───────────┘  └───────────┘  └───────────┘
```

---

## 3. 数据流设计

### 3.1 向量化入库流程

```
定时任务(每日 3:00) / 手动触发
      │
      ▼
RagMapper 查询: SELECT * FROM origin_text
               WHERE is_indexed = 0 AND 时间范围条件
      │
      ▼
ChunkService: 逐条 content 段落分割 + 混合切片
      ├─ ≤128字符 → 不切
      ├─ 128-512字符 → 定长切片(256)
      └─ >512字符 → 滑动窗口(512, overlap=64)
      │
      ▼
EmbeddingService: 批量调用 BGE API (batch=32)
      │
      ▼
EsVectorStore: 批量写入 ES (batch=100)
      │
      ▼
RagMapper: UPDATE origin_text SET is_indexed = 1
           WHERE sid IN (成功列表)
```

### 3.2 检索流程

```
前端 POST /api/rag/search { query, topK, hybrid }
      │
      ▼
EmbeddingService: query → vector
      │
      ▼
HybridSearchService:
  ├─ BM25 检索 (match IK 分词)
  ├─ kNN 向量检索 (script_score + cosineSimilarity，适配 ES 7.17)
  └─ RRF 融合排序
      │
      ▼
返回: [{ chunk_id, content, score, title, publish_time, category, doc_id }]
```

---

## 4. 接口设计

### 4.1 索引状态查询

```
GET /api/rag/index/status
```

请求参数：无

响应：
```json
{
  "code": 1,
  "msg": "success",
  "data": {
    "totalDocs": 63120,
    "indexedDocs": 45000,
    "unindexedDocs": 18120,
    "byDate": [
      { "date": "2026-06-10", "total": 150, "indexed": 120, "unindexed": 30 },
      { "date": "2026-06-11", "total": 80, "indexed": 0, "unindexed": 80 }
    ]
  }
}
```

### 4.2 手动触发向量化

```
POST /api/rag/index/trigger
```

请求体：
```json
{
  "startDate": "2026-06-01",
  "endDate": "2026-06-10"
}
```

响应：
```json
{
  "code": 1,
  "msg": "索引任务已提交",
  "data": {
    "taskId": "rag-20260611-001",
    "status": "RUNNING",
    "totalCount": 230,
    "message": "任务已在后台执行，预计需要 5-10 分钟"
  }
}
```

错误响应（任务已执行中）：
```json
{
  "code": 0,
  "msg": "已有索引任务正在执行中，请等待其完成后再试",
  "data": null
}
```

### 4.3 语义检索

```
POST /api/rag/search
```

请求体：
```json
{
  "query": "美军在南海的军事部署情况",
  "topK": 10,
  "hybrid": true
}
```

响应：
```json
{
  "code": 1,
  "msg": "success",
  "data": {
    "totalHits": 243,
    "results": [
      {
        "chunkId": "abc123-0",
        "docId": "12345",
        "content": "原文切片内容...",
        "title": "原文标题",
        "publishTime": "2026-06-10",
        "category": "军事动态",
        "bm25Score": 12.34,
        "vectorScore": 0.89,
        "finalScore": 15.67
      }
    ],
    "searchMode": "hybrid",
    "took": 145
  }
}
```

### 4.4 索引日志查询

```
GET /api/rag/index/log?pageNum=1&pageSize=20
```

响应：
```json
{
  "code": 1,
  "msg": "success",
  "data": {
    "total": 45,
    "list": [
      {
        "id": 1,
        "triggerType": "SCHEDULED",
        "startTime": "2026-06-11 03:00:00",
        "endTime": "2026-06-11 03:04:32",
        "status": "SUCCESS",
        "processedCount": 120,
        "successCount": 118,
        "skippedCount": 2,
        "failedCount": 0
      }
    ]
  }
}
```

---

## 5. 数据模型

### 5.1 数据库变更

```sql
ALTER TABLE origin_text ADD COLUMN is_indexed TINYINT(1) DEFAULT 0 COMMENT '是否已向量化入ES 0-未索引 1-已索引';
CREATE INDEX idx_is_indexed ON origin_text(is_indexed);
```

### 5.2 ES 索引

```json
{
  "index": "xianwei_docs",
  "settings": {
    "number_of_shards": 3,
    "number_of_replicas": 1,
    "analysis": {
      "analyzer": {
        "ik_max_word_analyzer": {
          "type": "ik_max_word"
        }
      }
    }
  },
  "mappings": {
    "properties": {
      "chunk_id":     { "type": "keyword" },
      "doc_id":       { "type": "keyword" },
      "chunk_index":  { "type": "integer" },
      "content":      { "type": "text", "analyzer": "ik_max_word" },
      "embedding":    { "type": "dense_vector", "dims": 1024 },
      "title":        { "type": "text", "analyzer": "ik_smart" },
      "publish_time": { "type": "date", "format": "yyyy-MM-dd HH:mm:ss||yyyy-MM-dd||epoch_millis" },
      "category":     { "type": "keyword" },
      "indexed_at":   { "type": "date" }
    }
  }
}
```

### 5.3 索引任务日志表（可选，存数据库或文件日志）

```sql
CREATE TABLE IF NOT EXISTS rag_index_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    trigger_type VARCHAR(20) COMMENT 'SCHEDULED / MANUAL',
    start_time DATETIME,
    end_time DATETIME,
    status VARCHAR(20) COMMENT 'SUCCESS / PARTIAL / FAILED',
    processed_count INT DEFAULT 0,
    success_count INT DEFAULT 0,
    skipped_count INT DEFAULT 0,
    failed_count INT DEFAULT 0,
    failed_doc_ids TEXT COMMENT '失败的doc_id列表，JSON数组',
    error_msg TEXT,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);
```

---

## 6. 定时任务

### RagIndexingTask

- **执行时间**：每日凌晨 3:00（Cron: `0 0 3 * * ?`）
- **与 DailyAnalysisTask 关系**：DailyAnalysisTask 凌晨 2:00 执行事件分析，RagIndexingTask 错开一小时
- **执行逻辑**：
  1. 查询 `origin_text WHERE is_indexed = 0 AND create_time >= 前一天 00:00`
  2. 批量向量化写入 ES
  3. 记录执行日志

---

## 7. 错误处理

| 场景 | 策略 |
|------|------|
| **BGE 服务不可用** | 重试 3 次（间隔 5s），仍失败则标记任务失败、记录日志，is_indexed 不回写 |
| **ES 不可用** | 重试 3 次，仍失败则回滚本次批次（不更新 is_indexed），等待下次任务重试 |
| **单条报文内容为空** | 跳过，标记 is_indexed=1（空内容不需要索引），记录跳过日志 |
| **Embedding 返回维度异常** | 单条跳过，记录失败 doc_id，继续处理下一条 |
| **ES 批量写入部分失败** | ES bulk API 返回每项状态，成功项更新 is_indexed，失败项保留待下次 |
| **任务执行超时** | 单次任务最长 30 分钟，超时中断并记录进度（最后处理的 doc_id），下次从该位置继续 |
| **并发触发** | 手动触发时检查是否有正在执行的任务，若有则拒绝并返回"任务执行中" |

---

## 8. 幂等性

- ES 写入使用 `doc_id + chunk_index` 拼接作为 `_id`，重复写入自动覆盖
- `is_indexed` 更新只在 ES 写入成功后执行
- 重复触发同一时间范围不会产生重复数据

---

## 9. 迁移清单

| 来源（xwRAG） | 处理方式 |
|---------------|----------|
| `EmbeddingService.java` | ✅ 直接迁移，WebClient + 重试逻辑完整 |
| `DocumentChunkService.java` | ✅ 迁移为 `ChunkService.java` |
| `ElasticsearchService.java` | ✅ 迁移为 `EsVectorStore.java` |
| `HybridSearchService.java` | ✅ 迁移 |
| `model/DocumentChunk.java` | ✅ 迁移 |
| `model/SearchResult.java` | ✅ 迁移 |
| `DataImportService.java` | ❌ 不迁移 |
| `DocxParserService.java` | ❌ 不迁移 |
| `DocxTableParserService.java` | ❌ 不迁移 |
| `DocxMixedParserService.java` | ❌ 不迁移 |
| `OcrService.java` | ❌ 不迁移 |
| `PgVectorService.java` | ❌ 不迁移，用 ES 替代 |
| `langchain4j` 依赖 | ❌ 去掉，直接用 ES Java Client |
| `pgvector`/`postgresql` 依赖 | ❌ 去掉 |
| `jakarta.*` import | ⚠️ 统一改为 `javax.*`（6 处，5 个文件） |

---

## 10. BGE Embedding 独立服务

### 10.1 设计原则

- **独立进程**：不嵌入算法服务（Flask :5001），避免 Embedding 的 CPU/内存密集计算与 LLM 网络 IO 互相干扰
- **轻量化**：单文件 Flask 应用，加载 sentence-transformers 模型，常驻内存
- **离线可部署**：模型文件随项目打包，不依赖外网下载

### 10.2 服务架构

```
┌──────────────────────────────────────────────────┐
│         BGE Embedding 服务 (:5002)                │
│                                                    │
│  Flask REST API                                    │
│  ├── POST /embed         单条/批量向量化           │
│  ├── GET  /health        健康检查                  │
│  └── GET  /model/info    模型信息                  │
│                                                    │
│  sentence-transformers                             │
│  └── BAAI/bge-large-zh-v1.5                       │
│      ├── 维度: 1024                                 │
│      ├── 最大长度: 512 tokens                       │
│      ├── 内存占用: ~1.3GB                           │
│      └── 启动时间: ~10-15秒（加载模型）              │
└──────────────────────────────────────────────────┘
```

### 10.3 目录结构

```
xwSystem0611/
├── xwBackend/                        # 后端（内含 RAG 模块）
├── xwEmbedding/                      # 新增：BGE Embedding 服务
│   ├── app.py                        # Flask 主程序
│   ├── requirements.txt              # Python 依赖
│   ├── models/                       # 模型文件目录（离线部署时放入）
│   │   └── bge-large-zh-v1.5/        # HuggingFace 模型文件
│   │       ├── config.json
│   │       ├── pytorch_model.bin
│   │       ├── tokenizer.json
│   │       ├── tokenizer_config.json
│   │       └── special_tokens_map.json
│   └── start.sh / start.bat          # 启动脚本
```

### 10.4 API 接口

#### POST /embed

请求体：
```json
{
  "texts": ["文本1", "文本2", "..."],
  "normalize": true
}
```

响应：
```json
{
  "code": 1,
  "data": {
    "embeddings": [
      [0.123, -0.456, ...],
      [0.789, 0.012, ...]
    ],
    "dimension": 1024,
    "count": 2,
    "took_ms": 145
  }
}
```

- `texts` 最大 64 条/次
- 超过 512 tokens 的文本自动截断
- 返回向量默认 L2 归一化（适配 ES cosine 相似度）

#### GET /health

```json
{
  "status": "ok",
  "model": "bge-large-zh-v1.5",
  "dimension": 1024,
  "uptime_seconds": 3600
}
```

### 10.5 模型选型

| 模型 | 维度 | 中文效果 | 内存 | 选择 |
|------|------|----------|------|------|
| bge-large-zh-v1.5 | 1024 | 最佳 | ~1.3GB | ✅ 推荐 |
| bge-base-zh-v1.5 | 768 | 良好 | ~0.4GB | 备选（内存受限） |
| text2vec-large-chinese | 1024 | 良好 | ~1.2GB | 备选 |

推荐 **bge-large-zh-v1.5**，理由：
- C-MTEB 中文评测基准排名第一
- 军事/政治文本语义理解准确
- 与 xwRAG 已有测试一致，迁移风险最低

若服务器内存受限（< 8GB），降级为 bge-base-zh-v1.5（768 维）。

### 10.6 启动方式

```bash
# 开发环境
cd xwEmbedding
pip install -r requirements.txt
python app.py  # 端口 5002

# 生产环境（Docker / 离线）
docker run -d \
  --name xw-embedding \
  -p 5002:5002 \
  -v ./models:/app/models \
  xw-embedding:latest
```

---

## 11. 离线部署方案

### 11.1 离线部署的挑战

| 组件 | 在线环境 | 离线环境 |
|------|----------|----------|
| ES Java Client | Maven 中央仓库下载 | 需提前下载放入私有仓库或本地（ES 7.17 兼容 JDK 8） |
| BGE 模型 | 首次运行从 HuggingFace 下载 | 需预下载模型文件随项目打包 |
| Python 依赖 | pip install 在线安装 | 需预下载 .whl 文件 |
| ES 安装包 | 在线下载 | 需提前下载 .tar.gz |
| IK 分词器 | 在线安装 | 需提前下载 .zip 放入 plugins |

### 11.2 离线部署包结构

```
xwSystem部署包_v1.5/
├── xwBackend/
│   ├── target/xw-backend.jar              # Spring Boot 可执行 JAR
│   └── lib/                               # 离线 Maven 依赖（如有需要）
├── xwAlgorithm/                           # 算法服务（Flask :5001）
├── xwFrontend/                            # 前端静态文件
├── xwEmbedding/                           # BGE Embedding 服务（新增）
│   ├── app.py
│   ├── requirements.txt
│   ├── models/
│   │   └── bge-large-zh-v1.5/             # 预下载的模型文件
│   └── wheels/                            # 离线 Python 包
│       ├── sentence_transformers-2.2.2-py3-none-any.whl
│       ├── torch-2.0.1-cp310-cp310-linux_x86_64.whl
│       └── ...
├── elasticsearch/                         # ES 离线安装包
│   ├── elasticsearch-7.17.15-linux-x86_64.tar.gz
│   └── elasticsearch-analysis-ik-7.17.15.zip
├── deploy/
│   └── setup.sh                           # 一键部署脚本
└── README.md                              # 部署说明
```

### 11.3 离线安装步骤

```bash
# 1. 安装 Elasticsearch
tar -xzf elasticsearch-7.17.15-linux-x86_64.tar.gz
cd elasticsearch-7.17.15
# 安装 IK 分词器
mkdir -p plugins/ik
unzip elasticsearch-analysis-ik-7.17.15.zip -d plugins/ik
./bin/elasticsearch -d

# 2. 安装 BGE Embedding 服务
cd xwEmbedding
pip install --no-index --find-links=./wheels -r requirements.txt
python app.py &

# 3. 启动后端（含 RAG 模块）
java -jar xw-backend.jar --spring.profiles.active=prod

# 4. 启动算法服务
cd xwAlgorithm
python app.py &
```

### 11.4 服务启动顺序（离线环境）

```
  ES → BGE Embedding → 算法服务 → 后端（含RAG）→ 前端
  (1)      (2)          (3)         (4)         (5)
```

RAG 模块启动时会检查 ES 和 BGE 服务是否可达，若不可达会记录 WARN 日志但不阻止后端启动（检索功能降级不可用）。

### 11.5 离线环境注意事项

1. **模型文件大小**：bge-large-zh-v1.5 约 1.3GB，确保部署包有足够空间
2. **Python 版本**：生产环境需提前确认 Python 3.10+ 已安装
3. **PyTorch 版本**：需根据目标 CPU 架构选择正确的 .whl（x86_64 / ARM64）
4. **ES 内存**：建议分配至少 2GB 堆内存给 ES（`-Xms2g -Xmx2g`）
5. **BGE 内存**：模型加载占用 ~1.3GB，服务总内存需求 ~2GB

---

## 12. 外部依赖

### 12.1 pom.xml 新增

```xml
<!-- Elasticsearch 7.17 RestHighLevelClient (兼容 JDK 8) -->
<dependency>
    <groupId>org.elasticsearch.client</groupId>
    <artifactId>elasticsearch-rest-high-level-client</artifactId>
    <version>7.17.15</version>
</dependency>

<dependency>
    <groupId>org.elasticsearch</groupId>
    <artifactId>elasticsearch</artifactId>
    <version>7.17.15</version>
</dependency>

<!-- Jackson 沿用 SB 2.7.18 自带版本 -->
```

**JDK 8 兼容性说明**：
- ES 7.17.x 是最后一个支持 JDK 8 的 ES 版本
- RestHighLevelClient 在 ES 7.x 中稳定可用（ES 8.x 中已移除）
- 向量检索使用 `script_score` + `cosineSimilarity` 函数替代 ES 8.x 原生 kNN API

### 12.2 外部服务

| 服务 | 端口 | 用途 |
|------|------|------|
| Elasticsearch | :9200 | 向量存储 + BM25 全文检索 + kNN 向量检索 |
| BGE Embedding | :5002 | 独立部署，文本向量化，1024 维 |

---

## 13. 配置项

```yaml
# application.yml 新增
rag:
  elasticsearch:
    host: ${ES_HOST:localhost}
    port: ${ES_PORT:9200}
    username: ${ES_USERNAME:}
    password: ${ES_PASSWORD:}
    index-name: xianwei_docs
  embedding:
    base-url: ${EMBEDDING_BASE_URL:http://localhost:8082}
    model: bge-large-zh-v1.5
    dimension: 1024
    batch-size: 32
    retry-count: 3
    retry-delay-ms: 5000
    timeout-seconds: 60
  chunk:
    short-threshold: 128
    medium-threshold: 512
    medium-size: 256
    long-size: 512
    overlap: 64
  indexing:
    max-duration-minutes: 30
    es-batch-size: 100
  search:
    bm25-weight: 0.3
    vector-weight: 0.7
    rrf-k: 60
    default-top-k: 10
```

---

## 14. 开发顺序

| 步骤 | 内容 | 依赖 |
|------|------|------|
| 0 | **xwEmbedding 服务**：Flask + BGE 模型加载 + `/embed` `/health` 端点 | 无 |
| 1 | pom.xml 加 ES Client，新增 `RagProperties` | 无 |
| 2 | `ElasticsearchConfig` + `EsVectorStore`（索引创建 + 批量写入） | 1 |
| 3 | `ChunkService` + `EmbeddingService`（从 xwRAG 迁移） | 1, 0 |
| 4 | `HybridSearchService`（从 xwRAG 迁移） | 2, 3 |
| 5 | `RagMapper` + DDL（数据库变更） | 1 |
| 6 | `RagServiceImpl`（业务编排） | 2-5 |
| 7 | `RagController`（4 个 REST 接口） | 6 |
| 8 | `RagIndexingTask`（定时任务） | 6 |
| 9 | 单元测试 + 集成测试 | 7, 8 |
| 10 | 离线部署包制作 + 部署脚本 | 0-9 |

---

## 15. 决策汇总

| 决策点 | 结论 |
|--------|------|
| 合并方式 | 代码合并到 xwBackend（方案 A） |
| 向量引擎 | Elasticsearch 7.17（RestHighLevelClient，兼容 JDK 8） |
| JDK | JDK 8（全局统一） |
| 数据范围 | 仅原始报文 `origin_text.content` |
| Embedding 位置 | Java 后端直连 BGE（高内聚低耦合） |
| 向量化触发 | 定时任务（每日 3:00）+ 手动触发 |
| 标记字段 | `origin_text.is_indexed` |
| Embedding 服务 | 独立 Flask 服务（:5002），BGE-large-zh-v1.5，离线打包模型 |
| 离线部署 | 模型预下载 + wheels 打包 + ES 离线安装包 |

---

**文档版本**: v1.2
**最后更新**: 2026-06-11
