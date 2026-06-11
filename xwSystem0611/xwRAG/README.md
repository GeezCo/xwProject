# RAG知识库系统

基于Java的RAG（Retrieval-Augmented Generation）知识库系统，支持从MySQL数据库和DOCX文档中提取数据，通过混合切片策略处理后存入Elasticsearch，并提供智能检索能力。

## 技术栈

- **开发语言**: Java 17
- **框架**: Spring Boot 3.2.1
- **向量数据库**: **Qdrant v1.7.0** (替代ES，解决ARM64兼容性问题)
- **关系数据库**: PostgreSQL + pgvector
- **RAG框架**: LangChain4j
- **文档解析**: Apache POI
- **OCR引擎**: Tess4j
- **Embedding模型**: BGE-large-zh-v1.5

### 为什么选择Qdrant？

Elasticsearch在Apple Silicon虚拟化环境下存在JVM兼容性问题（初始化阶段崩溃），禁用硬件加速参数无效。Qdrant使用Rust编写，无JVM依赖，完美兼容ARM64架构。

**注意**：Qdrant不支持IK中文分词，当前使用子字符串匹配进行文本检索。

## 项目结构

```
xwRAG/
├── src/
│   ├── main/
│   │   ├── java/com/intel/rag/
│   │   │   ├── config/          # 配置类
│   │   │   ├── service/         # 业务逻辑
│   │   │   ├── model/           # 数据模型
│   │   │   ├── repository/      # 数据访问
│   │   │   ├── util/            # 工具类
│   │   │   ├── controller/      # REST接口
│   │   │   └── RagApplication.java
│   │   └── resources/
│   │       ├── application.yml
│   │       └── testdata/        # 测试数据
│   │           ├── docx/
│   │           │   ├── simple/  # 纯文本文档
│   │           │   ├── table/   # 包含表格的文档
│   │           │   └── mixed/   # 图文混排文档
│   │           └── sql/         # MySQL测试数据
│   └── test/
│       └── java/com/intel/rag/
├── pom.xml
└── README.md
```

## 环境要求

- JDK 17+
- Maven 3.8+
- **Qdrant v1.7.0** (部署在 192.168.57.10:6333)
- PostgreSQL 14+ (with pgvector extension)
- Python 3.10+ (用于BGE Embedding服务)
- Tesseract OCR (用于图片文字识别)

## 快速开始

### 1. 配置数据库

编辑 `src/main/resources/application.yml`，配置PostgreSQL和Qdrant连接信息：

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/rag_db
    username: your_username
    password: your_password

qdrant:
  host: 192.168.57.10
  port: 6333
  grpc-port: 6334
```

### 2. 部署BGE Embedding服务

参考 `.trellis/tasks/06-04-xinjiang-rag/research/bge-embedding.md` 部署Python Embedding服务。

### 3. 安装Tesseract OCR

参考 `.trellis/tasks/06-04-xinjiang-rag/research/ocr-tesseract.md` 安装Tesseract和中文语言包。

### 4. 构建运行

```bash
# 编译项目
mvn clean package

# 运行应用
java -jar target/rag-knowledge-base-1.0.0-SNAPSHOT.jar
```

## 核心功能

1. **数据导入**: 从MySQL和DOCX文档中提取数据
2. **智能切片**: 混合切片策略（短文本/中等/长文本）
3. **向量化**: BGE模型生成1024维向量
4. **混合检索**: BM25关键词 + kNN向量检索
5. **OCR识别**: 支持图片文字识别

## 开发文档

详细的技术文档和研究报告位于 `.trellis/tasks/06-04-xinjiang-rag/`：

- `prd.md`: 产品需求文档
- `research/docx-parsing.md`: DOCX解析技术选型
- `research/ocr-tesseract.md`: OCR集成指南
- `research/bge-embedding.md`: Embedding服务部署方案

## License

Proprietary
