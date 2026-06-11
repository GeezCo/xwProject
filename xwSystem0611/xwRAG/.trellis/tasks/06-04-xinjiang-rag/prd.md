# 新疆专用RAG知识库系统

## Goal

构建一个基于Java的RAG（Retrieval-Augmented Generation）知识库系统，支持从MySQL数据库和DOCX文档（包含长文本、表格、图文混合内容）中提取数据，通过混合切片策略处理后存入Elasticsearch，并提供检索能力，为新疆业务场景提供智能问答支持。

## What I already know

**技术栈选择**：
* 开发语言：Java
* 向量数据库：Elasticsearch（部署在 192.168.57.10）
* RAG框架：LangChain（Java版本需确认）
* 开发环境：当前项目目录 `/Users/adam/Documents/IdeaProjects/xwRAG`

**数据源**：
* MySQL数据库：约100万条以内记录，SQL文件会放在项目目录中
  - 业务场景：军用情报数据报（结构化情报信息）
  - 敏感数据，需考虑访问控制和数据安全
* DOCX文档：包含长文档、表格、图文混合内容（可能包含情报分析报告）

**核心功能模块**：
1. 数据仿真/导入：从MySQL和DOCX文件中提取数据
2. 知识切片：格式+语义+字符长度混合切片策略
3. 向量入库：存入ES并保留元数据
4. 检索逻辑：基于LangChain实现RAG检索

**约束条件**：
* 纯后端开发，首次接触RAG项目
* Elasticsearch尚未配置向量搜索功能
* 需要处理DOCX中的表格和图片内容

## Assumptions (temporary)

* Java生态中存在成熟的LangChain实现（LangChain4j）
* Elasticsearch版本支持向量搜索（dense_vector字段，需≥7.3版本）
* DOCX文档中的图片可能需要OCR识别文字
* 表格内容需要结构化保留（行列关系）
* 用户希望使用开源中文embedding模型

## Open Questions

### 阻塞性问题
* ~~Elasticsearch版本号是多少？~~ → **已确认：部署ES 8.14+，支持原生kNN向量搜索**
* ~~是否需要OCR识别DOCX中的图片文字？~~ → **已确认：需要OCR识别**
* ~~MySQL数据库的业务场景是什么？~~ → **已确认：军用情报数据报（结构化情报信息）**

### 偏好性问题
* ~~文档切片长度偏好~~ → **已确认：混合切片策略（短文本不切/中等512-768/长文本1024+重叠128）**
* ~~Embedding模型选择~~ → **已确认：本地部署 BGE-large-zh-v1.5**
* ~~检索策略偏好~~ → **已确认：混合检索（BM25 + 向量，权重可调）**

## Requirements (evolving)

**阶段0：测试数据准备（优先级最高）**
* 收集公开测试数据（从网络获取）：
  1. 纯文本红头文件DOCX（简单场景）
  2. 包含表格的DOCX文档（中等难度）
  3. 图文混排的DOCX文档（复杂场景）
* MySQL测试数据：用户已准备SQL文件，如数据量不足可基于现有数据生成
* 建立测试数据目录结构

**阶段1：数据导入与预处理**
* 从MySQL导入结构化数据
* 解析DOCX文档（文本、表格、图片）
* 数据清洗与格式标准化

**阶段2：知识切片**
* 实现混合切片策略：
  - 短文本字段（标题、编号、摘要）：不切片，直接入库
  - 中等段落（单条情报、事件描述）：512-768字符
  - 长文档（分析报告、总结）：1024字符，重叠128字符
  - 表格内容：保持完整，转为结构化格式（Markdown或JSON）
  - 格式感知切片（识别段落、章节、表格边界）
  - 语义切片（避免句子/段落中间截断）
* 保留元数据（来源、文档ID、页码/章节、时间戳、数据类型等）

**阶段3：向量化与入库**
* 部署BGE-large-zh-v1.5模型服务（FastAPI或ONNX）
* Java调用Embedding服务生成向量（维度：1024）
* 配置ES索引映射：
  - dense_vector字段（向量存储）
  - text字段（原始文本，支持BM25）
  - keyword字段（元数据过滤）
* 批量入库并建立kNN索引
* 验证检索性能

**阶段4：检索服务**
* 基于LangChain4j实现RAG检索链
* 实现混合检索逻辑：
  - BM25关键词检索（权重可调，默认0.3）
  - kNN向量检索（权重可调，默认0.7）
  - RRF（Reciprocal Rank Fusion）结果融合
* 提供查询接口（RESTful API）
* 支持元数据过滤（时间范围、文档类型、来源等）
* OCR识别结果集成到检索流程

## Acceptance Criteria (evolving)

* [ ] 成功导入MySQL数据和DOCX文档（支持表格和图片OCR）
* [ ] 文档切片保留完整语义，无截断句子
* [ ] BGE模型服务部署成功，Java可正常调用
* [ ] ES中成功存储向量和元数据，索引配置正确
* [ ] 混合检索（BM25+向量）正常工作，权重可调整
* [ ] 检索召回率≥80%，准确率≥70%（基于测试集）
* [ ] 系统可处理100万级数据规模，单次查询响应<2秒
* [ ] OCR识别的图片文字可被正常检索

## Definition of Done

* 单元测试覆盖核心切片和检索逻辑
* 集成测试验证端到端流程
* 代码通过Lint检查
* 提供配置文档（ES连接、模型路径等）
* 性能测试报告（索引速度、检索延迟）

## Out of Scope (explicit)

* 前端界面开发
* 用户权限管理
* 实时数据同步（仅支持批量导入）
* 多语言支持（仅支持中文）

## Technical Notes

**关键技术选型待研究**：
1. ~~Java LangChain实现~~ → **已确认：LangChain4j**
2. DOCX解析库（Apache POI vs docx4j）
3. OCR引擎（Tesseract vs Tess4j vs 云服务API）
4. ~~中文Embedding模型~~ → **已确认：BGE-large-zh-v1.5（1024维）**
5. ~~ES向量搜索配置~~ → **已确认：dense_vector + kNN + BM25混合检索**
6. Embedding服务部署方案（FastAPI + Python vs ONNX Runtime Java）

**文档切片长度的影响**：
* **短切片（256-512字符）**：
  - 优点：检索精准度高，召回的内容更聚焦
  - 缺点：可能丢失上下文，需要更多次检索，索引数量大
  
* **中切片（512-1024字符）**：
  - 优点：平衡精准度和上下文，主流选择
  - 缺点：需要根据实际文档结构调整
  
* **长切片（1024-2048字符）**：
  - 优点：保留完整上下文，适合长文档问答
  - 缺点：检索可能不够精准，向量维度高

**推荐**：从512字符开始，根据实际文档类型调整（政策文档用长切片，FAQ用短切片）

**表格和图片特殊处理说明**：
* **表格**：保留结构化信息（转为Markdown表格或JSON），避免直接flatten成文本
* **图片**：
  - 如果图片中有文字（如截图、扫描件），需要OCR识别后入库
  - 如果是纯图表/照片，可以只保留图片路径引用，或跳过
  - 图文混排时需要保持图片与上下文的关联

## Technical Approach

### 整体架构
```
数据源层              处理层                存储层              服务层
┌─────────┐         ┌──────────┐         ┌─────────┐        ┌──────────┐
│ MySQL   │────────>│  数据提取 │         │         │        │          │
│ 数据库  │         │  清洗转换 │         │         │        │  查询API │
└─────────┘         └──────────┘         │         │        │          │
                          │               │   ES    │<───────│ 混合检索 │
┌─────────┐         ┌──────────┐         │  向量库 │        │  (BM25+  │
│ DOCX    │────────>│DOCX解析  │────────>│         │        │  kNN)    │
│ 文档    │         │表格/图片 │         │         │        │          │
└─────────┘         │OCR识别   │         └─────────┘        └──────────┘
                    └──────────┘               ▲                  │
                          │                     │                  │
                    ┌──────────┐               │                  │
                    │ 混合切片 │               │                  │
                    │ 策略引擎 │               │                  ▼
                    └──────────┘               │            ┌──────────┐
                          │                     │            │ LangChain│
                    ┌──────────┐               │            │ RAG链    │
                    │ BGE向量化│───────────────┘            └──────────┘
                    │ 服务     │
                    └──────────┘
```

### 核心技术栈确认
- **开发语言**: Java 17+（推荐使用Spring Boot 3.x）
- **依赖管理**: Maven
- **RAG框架**: LangChain4j
- **文档解析**: Apache POI（DOCX解析）
- **OCR引擎**: Tess4j（Tesseract的Java封装）
- **向量模型**: BGE-large-zh-v1.5（1024维，Python服务）
- **向量数据库**: Elasticsearch 8.14+
- **ES客户端**: Elasticsearch Java Client 8.x
- **中文分词**: IK Analyzer

### 技术决策（ADR-lite）

**决策1：Embedding服务部署方式**
- **上下文**: Java需要调用Python的BGE模型
- **决策**: 使用FastAPI部署独立的Python Embedding服务
- **理由**:
  - ONNX Runtime方案对BGE模型支持不完善
  - FastAPI方案成熟，易于维护和扩展
  - 可以通过HTTP REST调用，解耦度高
- **后果**: 需要额外部署Python服务，但架构更清晰

**决策2：DOCX解析库选择Apache POI**
- **上下文**: 需要解析DOCX中的文本、表格、图片
- **决策**: 使用Apache POI
- **理由**:
  - 社区活跃，文档丰富
  - 对表格支持更好
  - 与Spring Boot集成方便
- **后果**: 学习曲线相对平缓

**决策3：OCR使用Tess4j（本地）**
- **上下文**: 军用情报敏感性，需要OCR识别图片
- **决策**: 使用Tess4j（Tesseract）本地识别
- **理由**:
  - 数据不出本地，符合安全要求
  - 开源免费
  - 中文识别效果可接受（使用chi_sim语言包）
- **后果**: 识别速度较慢，需要优化批处理流程

## Implementation Plan

### PR1: 项目脚手架 + 测试数据准备
**目标**: 建立基础项目结构，准备测试数据

- [ ] 初始化Spring Boot项目（Java 17 + Maven）
- [ ] 配置基础依赖（LangChain4j、POI、Tess4j、ES Client）
- [ ] 建立目录结构：
  ```
  src/main/java/com/xinjiang/rag/
  ├── config/          # 配置类
  ├── service/         # 业务逻辑
  ├── model/           # 数据模型
  ├── repository/      # 数据访问
  └── util/            # 工具类
  
  src/main/resources/
  ├── application.yml  # 配置文件
  └── testdata/        # 测试数据目录
      ├── docx/
      │   ├── simple/  # 纯文本红头文件
      │   ├── table/   # 包含表格的文档
      │   └── mixed/   # 图文混排文档
      └── sql/         # MySQL测试数据
  ```
- [ ] 从网络下载/创建测试DOCX文档（3类各2-3个）
- [ ] 导入用户提供的MySQL测试数据SQL（如数据量不足可基于现有数据生成更多）
- [ ] 配置application.yml（ES、MySQL连接信息）
- [ ] 编写README.md（环境搭建说明）

### PR2: DOCX解析模块（纯文本）
**目标**: 实现简单DOCX文档解析

- [ ] 实现DocxParser接口和基础实现
- [ ] 解析纯文本红头文件
- [ ] 提取元数据（标题、创建时间等）
- [ ] 单元测试（使用simple测试数据）
- [ ] 集成测试

### PR3: DOCX解析增强（表格支持）
**目标**: 支持表格内容解析

- [ ] 扩展DocxParser支持表格识别
- [ ] 表格转Markdown格式
- [ ] 保留表格结构元数据（行列数、位置）
- [ ] 单元测试（使用table测试数据）

### PR4: DOCX解析完整（图片OCR）
**目标**: 支持图片OCR识别

- [ ] 集成Tess4j
- [ ] 从DOCX提取图片
- [ ] OCR识别图片文字
- [ ] 关联图片位置与上下文
- [ ] 单元测试（使用mixed测试数据）
- [ ] 性能优化（批量处理、线程池）

### PR5: 混合切片引擎
**目标**: 实现智能切片策略

- [ ] 设计ChunkStrategy接口
- [ ] 实现格式感知切片（段落、章节边界）
- [ ] 实现语义切片（句子完整性检测）
- [ ] 实现长度控制（512/768/1024 + 重叠）
- [ ] 元数据提取与保留
- [ ] 单元测试（各类文档切片效果验证）

### PR6: MySQL数据导入
**目标**: 从MySQL导入情报数据

- [ ] 实现DataImporter接口
- [ ] JDBC连接和数据读取
- [ ] 数据清洗和格式标准化
- [ ] 转换为统一Document模型
- [ ] 集成测试

### PR7: BGE Embedding服务
**目标**: 部署向量化服务

- [ ] 编写Python FastAPI服务（embedding_service.py）
- [ ] 加载BGE-large-zh-v1.5模型
- [ ] 提供POST /embed接口
- [ ] 批量向量化支持
- [ ] 健康检查接口
- [ ] Docker部署配置
- [ ] Java EmbeddingClient实现（调用Python服务）

### PR8: ES索引配置与数据入库
**目标**: 配置ES并批量入库

- [ ] 设计ES索引mapping（dense_vector + text + metadata）
- [ ] 实现ESRepository
- [ ] 批量入库逻辑（分批、重试、错误处理）
- [ ] 索引性能优化（bulk API、refresh策略）
- [ ] 验证向量存储正确性
- [ ] 集成测试

### PR9: 混合检索实现
**目标**: 实现BM25+kNN混合检索

- [ ] 实现HybridSearchService
- [ ] BM25查询构建
- [ ] kNN向量查询构建
- [ ] RRF结果融合算法
- [ ] 权重参数可配置（默认BM25:0.3, kNN:0.7）
- [ ] 元数据过滤支持
- [ ] 单元测试和集成测试

### PR10: LangChain4j RAG链集成
**目标**: 基于LangChain4j构建完整RAG流程

- [ ] 集成LangChain4j
- [ ] 实现自定义EmbeddingStore（基于ES）
- [ ] 实现自定义Retriever（使用混合检索）
- [ ] 配置RAG链（Retriever -> Rerank -> Generator）
- [ ] 提供查询API接口
- [ ] 端到端测试

### PR11: 性能优化与测试
**目标**: 性能调优和完整测试

- [ ] 批量处理优化（切片、向量化、入库）
- [ ] 缓存策略（Embedding结果、检索结果）
- [ ] 并发处理（线程池配置）
- [ ] 性能测试（100万数据规模，查询响应时间）
- [ ] 召回率和准确率评估
- [ ] 负载测试

### PR12: 文档与部署
**目标**: 完善文档和部署方案

- [ ] API文档（Swagger）
- [ ] 配置文档（ES、MySQL、BGE服务）
- [ ] 部署文档（Docker Compose一键部署）
- [ ] 使用手册
- [ ] 性能测试报告

## Research References

* [`research/docx-parsing.md`](research/docx-parsing.md) — Apache POI vs docx4j对比，推荐使用Apache POI 5.2.5
* [`research/ocr-tesseract.md`](research/ocr-tesseract.md) — Tesseract OCR集成指南，包含中文支持、性能优化和Docker部署
* [`research/bge-embedding.md`](research/bge-embedding.md) — BGE模型部署方案，推荐FastAPI服务 + Java HTTP客户端

## Technical Decisions (Confirmed)

基于研究结果，确认以下技术选型：

1. **DOCX解析**: Apache POI 5.2.5
   - 理由：API简洁、社区活跃、Spring Boot集成方便、表格和图片支持完善
   
2. **OCR引擎**: Tess4j + Tesseract (tessdata_fast/chi_sim)
   - 理由：本地部署保证数据安全、中文识别率可接受、Docker部署简单
   
3. **Embedding服务**: FastAPI (Python) + BGE-large-zh-v1.5
   - 理由：架构清晰、易于维护、性能满足需求、避免ONNX复杂度
   
4. **Java HTTP客户端**: Spring WebClient (响应式)
   - 理由：异步非阻塞、支持批量并发、性能优于RestTemplate