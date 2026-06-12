# 设计文档：xwRAG 模块并入 xwBackend

**日期**：2026-06-12
**作者**：架构组
**状态**：设计已批准
**范围**：仅 xwBackend 后端代码，前端与算法服务不动

---

## 1. 背景与动机

献微系统当前存在双数据库（MySQL + PostgreSQL）、双 JDK（8 + 17）、双 Spring Boot（2.7 + 3.2）的异构架构，xwBackend 与 xwRAG 在 RAG 能力上功能重叠：

- xwBackend 内 `com.qy.dch.rag` 包已实现完整的 ES dense_vector 方案（1024 维向量、ik 中文分词、BM25 + 向量 RRF 融合检索）
- xwRAG 模块依赖 PostgreSQL/pgvector + LangChain4j 0.26 + JDK 17

本次重构目标：

1. **部署简化**：减少容器与外部依赖数量
2. **环境约束**：生产环境硬性要求 JDK 8 + MySQL + ES
3. **可维护性**：消除重复实现，保留 xwRAG 中独有的 OCR 与 DOCX 解析能力

---

## 2. 架构变化

### 2.1 改造前

```
┌──────────────┐  ┌─────────────┐  ┌─────────────┐  ┌──────────────┐
│  xwBackend   │  │   xwRAG     │  │ xwEmbedding │  │   algorithm  │
│ Spring 2.7   │  │ Spring 3.2  │  │   Flask     │  │    Flask     │
│  JDK 8       │  │  JDK 17     │  │             │  │              │
└──────┬───────┘  └──────┬──────┘  └─────────────┘  └──────────────┘
       │                 │
   ┌───▼───┐      ┌──────▼──────┐
   │ MySQL │      │ PostgreSQL  │
   │  +ES  │      │ + pgvector  │
   └───────┘      └─────────────┘
```

### 2.2 改造后

```
┌──────────────┐  ┌─────────────┐  ┌──────────────┐
│  xwBackend   │  │ xwEmbedding │  │   algorithm  │
│ Spring 2.7   │  │   Flask     │  │    Flask     │
│  JDK 8       │  │             │  │              │
│  + Tess4j    │  │             │  │              │
└──────┬───────┘  └─────────────┘  └──────────────┘
       │
   ┌───▼───────────────┐
   │ MySQL + ES        │
   │ (业务+向量+全文)  │
   └───────────────────┘
```

### 2.3 改造范围

| 项目 | 操作 | 原因 |
|------|------|------|
| `xwRAG/` 整个模块 | 删除 | JDK 17 + PostgreSQL + LangChain4j 与约束冲突 |
| `xwBackend/com.qy.dch.rag` 包 | 保留并扩展 | 已是完整的 ES dense_vector 实现 |
| xwRAG 的 OCR / DOCX 解析 | 移植到 xwBackend | 唯一不重复的能力 |
| docker-compose 中 PostgreSQL | 移除 | RAG 改用 ES |
| docker-compose 中 xwRAG service | 移除 | 模块已合并 |
| 前端 / 算法服务 | 不变 | 范围外 |

---

## 3. 文件级移植清单

### 3.1 要移植的 Java 文件（7 个，~1,465 行）

| xwRAG 源文件 | 行数 | 移植到 xwBackend 路径 | 改造点 |
|---|---|---|---|
| `service/OcrService.java` | 229 | `com.qy.dch.rag.parser.OcrService` | 无技术改造（Tess4j 5.9 兼容 JDK 8）；`@Async` 配合独立线程池 |
| `service/DocxParserService.java` | 93 | `com.qy.dch.rag.parser.DocxParserService` | POI 5.2.5 已有依赖，无改造 |
| `service/DocxMixedParserService.java` | 181 | `com.qy.dch.rag.parser.DocxMixedParserService` | 同上 |
| `service/DocxTableParserService.java` | 213 | `com.qy.dch.rag.parser.DocxTableParserService` | 同上 |
| `service/DataImportService.java` | 292 | `com.qy.dch.rag.importer.DataImportService` | 把 PgVectorService 调用替换为 `EsVectorStore.bulkIndex()` |
| `controller/DocumentController.java` | 173 | `com.qy.dch.controller.DocumentController` | `jakarta.*` → `javax.*`；去 Swagger 注解；用 `ResultVO` 替代 `ApiResponse` |

### 3.2 跳过移植（功能等价已存在）

| xwRAG 源文件 | 原因 |
|---|---|
| `service/DocumentChunkService.java` | 复用 xwBackend 已有 `chunk/ChunkService` |
| `service/PgVectorService.java` | 由 `EsVectorStore` 替代 |
| `service/EmbeddingService.java` | 由 xwBackend 同名服务替代 |
| `service/HybridSearchService.java` | 由 xwBackend 同名服务替代 |
| `repository/DataRecordRepository.java` + JPA Entity | 改用 MyBatis Mapper |

### 3.3 新增依赖（xwBackend pom.xml）

```xml
<!-- Tess4j OCR (JDK 8 兼容) -->
<dependency>
    <groupId>net.sourceforge.tess4j</groupId>
    <artifactId>tess4j</artifactId>
    <version>5.9.0</version>
</dependency>

<!-- 图片处理 -->
<dependency>
    <groupId>org.imgscalr</groupId>
    <artifactId>imgscalr-lib</artifactId>
    <version>4.2</version>
</dependency>
```

### 3.4 要移植的配置类

| xwRAG 配置 | xwBackend 路径 | 改造 |
|---|---|---|
| `OcrProperties` | `com.qy.dch.rag.config.OcrProperties` | 前缀改 `rag.ocr` |
| `DocumentParserProperties` | `com.qy.dch.rag.config.DocumentParserProperties` | 前缀改 `rag.parser` |
| `AsyncConfiguration` | 新建 `com.qy.dch.rag.config.AsyncConfiguration` | xwBackend 当前无此类；新建并提供 `ocrExecutor` 线程池 bean |

### 3.5 改造点的兼容性

| 风险 | 应对 |
|---|---|
| `jakarta.validation` → `javax.validation` | 全局替换 |
| `springdoc-openapi` 注解 | 直接删除（xwBackend 未用） |
| `org.springframework.lang.Nullable` | Spring 5.x 已含，保留 |
| Tess4j native 库（`libtesseract`） | Docker 镜像中 `apt-get install tesseract-ocr tesseract-ocr-chi-sim` |

---

## 4. 接口设计

### 4.1 合并后 RAG 接口全集

统一前缀 `/api/rag`：

| 路径 | 方法 | 来源 | 功能 |
|---|---|---|---|
| `/api/rag/index/status` | GET | 已有 | 查询索引状态 |
| `/api/rag/index/trigger` | POST | 已有 | 触发从 `origin_text` 表批量索引 |
| `/api/rag/index/log` | GET | 已有 | 查询索引日志（分页） |
| `/api/rag/search` | POST | 已有 | 混合检索（BM25 + 向量 RRF） |
| `/api/rag/document/upload` | POST | 新增（移植） | 上传 DOCX，解析→分块→向量化→索引 |
| `/api/rag/document/upload/mixed` | POST | 新增（移植） | 上传含图片 DOCX，调 OCR 提取图片文字 |
| `/api/rag/document/parse` | POST | 新增（移植） | 仅解析返回文本，不入库（调试用） |
| `/api/rag/document/status/{docId}` | GET | 新增 | 查询单次上传任务的索引状态（pending/indexed/failed） |

### 4.2 统一返回结构

全部使用 `com.qy.dch.common.ResultVO`：

```java
public class ResultVO {
    private Integer code;   // 1 成功，0 失败
    private String msg;
    private Object data;
}
```

---

## 5. 数据流

### 5.1 DOCX 上传索引（新增能力）

```
前端 multipart 上传 .docx
        ↓
DocumentController.uploadDocument()
        ↓
保存为临时文件 (File.createTempFile)
        ↓
DocxMixedParserService.parseMixedDocx()
  ├─ POI 解析段落、表格 → 文本
  └─ POI 抽取嵌入图片 → byte[] 列表
        ↓
若有图片 → OcrService.recognizeAsync()（独立线程池并发）
  └─ Tesseract → 图片文字
        ↓
ChunkService.chunk(全文 + OCR 文本)
  └─ 按 ChineseTextAnalyzer 切片，含重叠
        ↓
EmbeddingService.embedBatch(chunks)
  └─ HTTP → xwEmbedding 服务 → 1024 维向量
        ↓
EsVectorStore.bulkIndex(chunks)
  └─ ES BulkRequest，doc_id = 上传ID
        ↓
RagDocumentMapper.insert(...)
  └─ MySQL 记录文档元数据
        ↓
返回 ResultVO { docId, chunks: N, elapsedMs }
```

### 5.2 原有 origin_text 批量索引（不变）

```
RagIndexingTask（定时） 或 /api/rag/index/trigger
        ↓
UygurMapper.selectByDateRange(start, end)
        ↓
对每篇报文：ChunkService.chunk(content)
        ↓
EmbeddingService.embedBatch → EsVectorStore.bulkIndex
        ↓
RagMapper 记录状态
```

### 5.3 混合检索（不变）

```
POST /api/rag/search { query, topK, hybrid: true }
        ↓
HybridSearchService.hybridSearch()
  ├─ BM25 检索 (matchQuery on content)
  ├─ 向量化 query → vectorSearch (cosineSimilarity)
  └─ RRF 融合：score = bm25Weight/(k+rank) + vectorWeight/(k+rank)
        ↓
返回 topK 个 SearchResult
```

---

## 6. 数据模型

### 6.1 MySQL 表的去留

| 表 | 操作 |
|---|---|
| `origin_text` / `text_type` / `extraction_result` / `fusion_report` / `event_analysis` | 保持不变 |
| `rag_index_log` / `rag_index_status`（xwBackend 已有） | 保持不变 |
| `rag_document` | 新增 |
| `data_record`（xwRAG JPA 表） | 不迁移，由 `rag_document` 替代 |

### 6.2 新增表 DDL

```sql
CREATE TABLE rag_document (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    doc_id VARCHAR(64) NOT NULL UNIQUE,
    filename VARCHAR(255),
    file_size BIGINT,
    chunk_count INT,
    status VARCHAR(20),       -- pending/indexed/failed
    error_msg TEXT,
    upload_time DATETIME,
    indexed_time DATETIME,
    INDEX idx_status (status),
    INDEX idx_upload_time (upload_time)
);
```

文件路径：`xwBackend/src/main/resources/db/rag_document_ddl.sql`

---

## 7. 部署架构变化

### 7.1 docker-compose 变更

**删除**：
- `postgres`（含 pgvector 扩展容器）
- `xianwei-rag`（独立 RAG 进程）

**xwBackend 容器变更**：
- 镜像加装 tesseract：
  ```dockerfile
  RUN apt-get update && apt-get install -y \
      tesseract-ocr tesseract-ocr-chi-sim tesseract-ocr-eng \
      && rm -rf /var/lib/apt/lists/*
  ```
- 新增上传临时目录卷：`./uploads:/app/uploads`

### 7.2 最终容器列表

| 容器 | 端口 | 备注 |
|---|---|---|
| xianwei-frontend (Nginx) | 9201 | 不变 |
| xianwei-backend | 9202 | 新增 OCR + DOCX 解析 |
| xianwei-algorithm | 9203 | 不变 |
| xianwei-mysql | 9204 | 不变 |
| xianwei-minio | 9205 / 9206 | 不变 |
| xianwei-elasticsearch | 9207 | 已有 |
| xianwei-embedding | 9208 | 已有 |
| ~~xianwei-rag~~ | ~~9209~~ | 删除 |
| ~~xianwei-postgres~~ | ~~9210~~ | 删除 |

容器数 8 → 6，减少 25%。

### 7.3 application.yml 配置

```yaml
rag:
  ocr:
    enabled: true
    tesseract:
      datapath: /usr/share/tesseract-ocr/4.00/tessdata
      language: chi_sim+eng
      page-seg-mode: 1
      ocr-engine-mode: 1
  parser:
    max-file-size-mb: 50
```

---

## 8. 实施步骤

### Stage 1：准备工作（无破坏性）
1. 在 xwBackend 创建 git 分支 `feature/merge-rag-into-backend`
2. xwBackend `pom.xml` 添加 Tess4j 5.9.0、imgscalr-lib 4.2 依赖
3. 验证 `mvn compile` 通过，确认 Tess4j 在 JDK 8 下能加载

### Stage 2：移植解析层（不接业务）
4. 新建包 `com.qy.dch.rag.parser`，移植 4 个 service
5. 新建包 `com.qy.dch.rag.config`，移植 2 个配置类
6. 新建 `com.qy.dch.rag.config.AsyncConfiguration`，提供名为 `ocrExecutor` 的线程池 bean（xwBackend 当前无 Async 配置）
7. `application.yml` 增加 `rag.ocr` 与 `rag.parser` 配置块
8. 编译通过，启动 xwBackend，确认无 bean 冲突

### Stage 3：接入 RAG 主管道
9. 新建 `com.qy.dch.controller.DocumentController`，实现 4 个接口（upload / upload/mixed / parse / status）
10. 新建 `RagDocumentMapper` + 创建 `rag_document` 表
11. 在 `RagServiceImpl` 增加 `uploadAndIndex(MultipartFile)` 方法
12. 错误处理：文件 > 50MB 拒绝；OCR 失败降级为只索引文本；ES 写失败回写 `status=failed`

### Stage 4：清理 xwRAG
13. 删除整个 `xwSystem0611/xwRAG/` 目录
14. 修改 `xwSystem0611/deploy/docker-compose.yml`，删除 postgres 与 xianwei-rag service
15. 修改 xwBackend Dockerfile，增加 tesseract 安装
16. 修改 `xwSystem0611/CLAUDE.md`，更新模块说明与容器列表

### Stage 5：验证
17. 运行测试套件
18. Docker 镜像重新打包，本地起容器验证全链路
19. 合并分支前 code review

---

## 9. 风险评估

| 风险 | 严重度 | 缓解措施 |
|---|---|---|
| Tess4j native 库加载失败 | 高 | 在目标镜像（debian/ubuntu base）预先验证；准备 `tesseract.useSystemPath=true` 兜底 |
| OCR 单页耗时 1-3 秒，阻塞请求线程 | 中 | `@Async` + 独立 `ocrExecutor` 线程池；上传接口立即返回 docId，状态轮询查询 |
| Tess4j 中文识别准确率不够 | 中 | PRD 无精度硬性要求；后续可换 PaddleOCR-VL |
| xwRAG `data_record` 表里有生产数据 | 高 | 删 xwRAG 前必须确认是否有线上数据；如有需先导出 |
| `org.springframework.lang.Nullable` 不可用 | 低 | Spring 5.x 已包含，验证一次即可 |
| 删除 PostgreSQL 后回滚困难 | 中 | docker-compose 完整备份 + git tag `v1-before-merge-rag` |
| xwBackend 镜像膨胀（~150MB） | 低 | 可接受，仍小于现两套合计 |
| 上传管道与 `RagIndexingTask` 并发写 ES | 低 | ES bulk 自带幂等（chunk_id 主键） |

---

## 10. 测试策略

### 10.1 单元测试（直接移植 xwRAG 中已有的）

| 测试类 | 范围 |
|---|---|
| `OcrServiceTest` | Tess4j 加载、识别一张已知图片 |
| `DocxParserServiceTest` | 解析纯文本 DOCX |
| `DocxMixedParserServiceTest` | 解析含图片 DOCX，验证图片提取 |
| `DocxTableParserServiceTest` | 解析含表格 DOCX |

### 10.2 集成测试（新增）

| 测试类 | 场景 |
|---|---|
| `DocumentUploadIT` | 上传 .docx → 验证 `rag_document` 表写入 + ES 索引存在 |
| `HybridSearchIT` | 上传后立即检索能命中 |
| `OcrFallbackIT` | OCR 故意失败 → 文档仍能索引（只是少了图片文本） |

### 10.3 端到端验证

- docker-compose 起整套，Postman 走完整链路
- 旧的 `/api/rag/index/trigger` 与 `RagIndexingTask` 回归
- 全部业务接口（UygurController / ExtractionController / FusionController）无影响

### 10.4 性能基准

- 100 篇 origin_text 批量索引耗时与改造前一致
- 单文档 5MB DOCX 上传 + 索引耗时 < 30s（含 OCR）

### 10.5 必须通过的回归

- 主业务接口 100% 不受影响
- ES 索引结构（dense_vector 1024 维）保持兼容
- 现有索引数据无需重建

---

## 11. 总览

| 维度 | 数值 |
|---|---|
| 移植代码量 | ~1,465 行（7 个 Java 文件） |
| 删除模块 | xwRAG 整个（46 个 Java 文件） |
| 容器减少 | 2 个（postgres + xianwei-rag） |
| 新增依赖 | 2 个（Tess4j + imgscalr） |
| 实施阶段 | 5 个 |
| 主要风险 | Tess4j native 兼容性 + xwRAG 历史数据迁移 |
