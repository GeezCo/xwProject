# 献微系统 - 后端 xwBackend 完整技术参考文档

> 单一权威文档。仅凭本文档应足以从零复刻整套后端（Schema、配置、API、模型、服务、RAG 子系统、定时任务、运维）。
> 适用版本：`uygur-project 0.0.1-SNAPSHOT`（截至 2026-06-15）。

---

## 目录

1. [系统概览](#1-系统概览)
2. [技术栈与依赖](#2-技术栈与依赖)
3. [运行环境与配置](#3-运行环境与配置)
4. [代码骨架与目录结构](#4-代码骨架与目录结构)
5. [数据库设计（最终态 schema）](#5-数据库设计最终态-schema)
6. [全局约定](#6-全局约定)
7. [模型层](#7-模型层实体--dto--request--公共)
8. [Mapper（持久层）](#8-mapper-持久层)
9. [Service（业务层）](#9-service-业务层)
10. [Controller（REST API）](#10-controller-rest-api)
11. [配置类与异常处理](#11-配置类与异常处理)
12. [RAG 知识库子系统](#12-rag-知识库子系统)
13. [定时任务](#13-定时任务)
14. [工具类](#14-工具类)
15. [外部依赖：算法服务 / Embedding / OCR / MinIO / ES](#15-外部依赖)
16. [构建、部署与运维](#16-构建部署与运维)
17. [复刻 Checklist](#17-复刻-checklist)

---

## 1. 系统概览

献微系统是一个**情报报文（origin_text）全生命周期管理与分析后端**。围绕一张「报文表」展开下列能力：

- **采集 / 导入**：JSON、JSONL（含 JSONL + 图片）批量入库，按发报单位自动分类。
- **目录与分类**：5 层 `text_type` 树 + 一级 `category`（开源信息 / HZ报 / JZ报 / 未分类）+ 二级 `send_unit_name`。
- **属性抽取**：调外部算法服务（LLM）做事件 / 实体 / 标签三段式抽取（`extraction_result`）。
- **事件分析**：日级别批量事件六维分析（`event_analysis`）。
- **目标分析与融合**：算法侧抽出每条报文中的目标（`target_analysis`），可勾选目标后做融合（`target_fusion`），别名表（`target_alias`）做名称归一。
- **多报融合**：勾选多份报文做综合融合报告（`fusion_report`），支持导出 PDF/Word（预留）。
- **RAG 检索**：报文 + 上传 docx 文档 → 中文分块 → bge-large-zh-v1.5 向量化 → ES 7.17 dense_vector，BM25 + 向量 RRF 混合检索。
- **看板 / 库管**：Dashboard 指标、白名单表的字段级 DDL 操作。

### 1.1 服务端口

| 服务 | 端口 | 备注 |
|---|---|---|
| xwBackend | 8081 | Spring Boot |
| MySQL 8 | 9204 | `36.141.21.176`，库 `uygur_project` |
| MinIO | 8522 | 桶 `xianwei-images` |
| Elasticsearch 7.17 | 9200 | 索引 `xianwei_docs` |
| Embedding 服务 | 5002 | 自部署 bge-large-zh-v1.5 |
| 算法服务 | 5001/9203 | 抽取/事件/融合/目标分析 |

### 1.2 启动类

```java
@SpringBootApplication
@EnableScheduling
public class DchApplication {
    public static void main(String[] args) { SpringApplication.run(DchApplication.class, args); }
}
```

---

## 2. 技术栈与依赖

| 类型 | 名称 | 版本 |
|---|---|---|
| 父 POM | spring-boot-starter-parent | 2.7.18 |
| JDK | OpenJDK | 1.8 |
| Web | spring-boot-starter-web / actuator / validation | (随父 BOM) |
| ORM | mybatis-plus-boot-starter | 3.5.5 |
| DB | mysql-connector-java | 8.0.33 |
| 连接池 | druid-spring-boot-starter | 1.2.20 |
| JSON | fastjson | 1.2.83 |
| 工具 | hutool-all | 5.7.21 |
| 工具 | commons-lang3 | 3.12.0 |
| 报表 | poi / poi-ooxml | 5.2.5 |
| SSH | ganymed-ssh2 | 262 |
| SSH | mwiede/jsch | 0.2.24 |
| JSON | org.json | 20180813 |
| 对象存储 | minio | 8.5.7 |
| 搜索 | elasticsearch-rest-high-level-client / elasticsearch | 7.17.15 |
| OCR | tess4j | 5.9.0 |
| 图像 | imgscalr-lib | 4.2 |
| 日志 | log4j-slf4j-impl | (BOM) |
| Lombok（编译期） | lombok | 1.18.34 |

**Build plugins**：`maven-compiler-plugin`（source/target=1.8，Lombok annotationProcessor），`spring-boot-maven-plugin`（构建可执行 jar，排除 lombok）。

---

## 3. 运行环境与配置

### 3.1 `application.yml`（关键节选）

```yaml
spring:
  application: { name: uygur-project }
  datasource:
    url: jdbc:mysql://${DB_HOST:36.141.21.176}:${DB_PORT:9204}/${DB_NAME:uygur_project}
         ?useUnicode=true&characterEncoding=utf8&zeroDateTimeBehavior=convertToNull
         &useSSL=true&serverTimezone=GMT%2B8
    username: ${DB_USERNAME:root}
    password: ${DB_PASSWORD:jixianyuan1314}
    driver-class-name: com.mysql.cj.jdbc.Driver
    type: com.alibaba.druid.pool.DruidDataSource
    druid:
      initial-size: 5
      min-idle: 5
      max-active: 50
      max-wait: 60000
      time-between-eviction-runs-millis: 60000
      min-evictable-idle-time-millis: 300000
      validation-query: SELECT 1 FROM DUAL
      test-while-idle: true
      pool-prepared-statements: true
      max-pool-prepared-statement-per-connection-size: 20
      filters: stat,wall,log4j2
      stat-view-servlet: { enabled: true, url-pattern: /druid/*, login-username: admin, login-password: admin }
      web-stat-filter: { enabled: true, url-pattern: /*, exclusions: "*.js,*.gif,*.jpg,*.png,*.css,*.ico,/druid/*" }
      filter: { stat: { slow-sql-millis: 2000, log-slow-sql: true } }
  servlet:
    multipart: { max-file-size: 100MB, max-request-size: 100MB }
    encoding:  { charset: UTF-8, enabled: true, force: true }

server: { port: 8081 }

management:
  endpoints: { web: { exposure: { include: health,info } } }
  endpoint:  { health: { show-details: never } }

logging:
  level: { root: info, com.qy.dch: debug }
  file:  { path: logs, name: logs/application.log }
  pattern: { file: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n" }

mybatis-plus:
  type-aliases-package: com.qy.dch.dto
  mapper-locations: classpath:mapper/*.xml
  configuration: { map-underscore-to-camel-case: true }
  global-config:
    db-config:
      id-type: ASSIGN_ID            # 雪花算法 19 位字符串
      logic-delete-field: deleted
      logic-delete-value: 1
      logic-not-delete-value: 0

filePath: ${IMPORT_FILE_PATH:/opt/uygur-project/data/all_text_time.json}

algorithm: { service: { url: ${ALGORITHM_SERVICE_URL:http://localhost:5001} } }
# 注意：TargetAnalysis/TargetFusion 注入时使用默认 9203（详见 §9）

minio:
  endpoint:   ${MINIO_ENDPOINT:http://36.141.21.176:8522}
  access-key: ${MINIO_ACCESS_KEY:xianwei-admin}
  secret-key: ${MINIO_SECRET_KEY:xianwei2024}
  bucket:     ${MINIO_BUCKET:xianwei-images}

rag:
  elasticsearch: { host: ${ES_HOST:localhost}, port: ${ES_PORT:9200}, username: "", password: "", index-name: xianwei_docs }
  embedding:
    base-url:   ${EMBEDDING_BASE_URL:http://localhost:5002}
    model:      bge-large-zh-v1.5
    dimension:  1024
    batch-size: 32
    retry-count: 3
    retry-delay-ms: 5000
    timeout-seconds: 60
  chunk:    { short-threshold: 128, medium-threshold: 512, medium-size: 256, long-size: 512, overlap: 64 }
  indexing: { max-duration-minutes: 30, es-batch-size: 100 }
  search:   { bm25-weight: 0.3, vector-weight: 0.7, rrf-k: 60, default-top-k: 10 }
  ocr:
    enabled: ${OCR_ENABLED:true}
    thread-pool-size: 4
    tesseract: { datapath: ${TESSERACT_DATAPATH:/usr/share/tesseract-ocr/4.00/tessdata}, language: chi_sim, page-seg-mode: 1, ocr-engine-mode: 1 }
    image:     { enable-ocr: true, max-size-mb: 10 }
  parser:    { max-file-size-mb: 50, temp-dir: /tmp/xianwei-uploads, strict-docx-only: true }
```

### 3.2 环境变量速查

`DB_HOST/PORT/NAME/USERNAME/PASSWORD`、`IMPORT_FILE_PATH`、`ALGORITHM_SERVICE_URL`、`MINIO_ENDPOINT/ACCESS_KEY/SECRET_KEY/BUCKET`、`ES_HOST/PORT/USERNAME/PASSWORD`、`EMBEDDING_BASE_URL`、`OCR_ENABLED`、`TESSERACT_DATAPATH`。

### 3.3 离线环境提示

- 服务器**可能为纯离线**——所有外部依赖（MinIO/ES/Embedding/算法服务/Tesseract 语言包）必须就地部署。
- Maven 构建走本地仓库镜像 `./mvnw -o`。
- 字体、tessdata（`chi_sim.traineddata`）需随机器交付。

---

## 4. 代码骨架与目录结构

```
xwBackend/
├── pom.xml
├── mvnw / mvnw.cmd / .mvn/
├── src/main/java/com/qy/dch/
│   ├── DchApplication.java               # 启动类（@SpringBootApplication + @EnableScheduling）
│   ├── common/                           # ResultVO / ErrorCode / BusinessException
│   ├── config/                           # CORS / Druid / Mybatis-Plus / Minio / RestTemplate / GlobalExceptionHandler / MetaObjectHandler
│   ├── controller/                       # 12 个 Controller（详见 §10）
│   ├── domain/                           # PageDomain（旧版分页 VO）
│   ├── dto/                              # 19 个 DTO（出参/算法侧契约）
│   ├── entity/                           # BaseEntity / Category / OriginText / TargetAlias / TargetAnalysis / TargetFusion
│   ├── mapper/                           # 9 个 Mapper（注解 + XML 混合）
│   ├── rag/                              # 知识库子系统 (chunk/config/embed/model/parser/search/store)
│   ├── request/                          # 12 个 RequestVO（入参）
│   ├── service/                          # interface + impl
│   ├── task/                             # 定时任务（RagIndexingTask、DailyAnalysisTask）
│   └── util/                             # CategoryClassifier
├── src/main/resources/
│   ├── application.yml
│   ├── db/                               # DDL 历史（已被 docs/sql/2026-06-14-system-integration.sql 收敛为权威）
│   └── mapper/                           # FusionMapper.xml / RagDocumentMapper.xml
└── src/test/java/com/qy/dch/             # 接口集成测试 + RAG 单测
```

---

## 5. 数据库设计（最终态 schema）

> 服务器 MySQL 8。库 `uygur_project`，DEFAULT CHARSET=utf8mb4，COLLATE=utf8mb4_0900_ai_ci。
> **所有业务主键 = `VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin`**，取消 `AUTO_INCREMENT`，由 Java 雪花算法生成 19 位字符串。
> 创建/更新时间统一 `DATETIME DEFAULT CURRENT_TIMESTAMP [ON UPDATE CURRENT_TIMESTAMP]`。

### 5.1 表清单

| 表 | 用途 |
|---|---|
| `origin_text` | 原始报文 |
| `text_type` | 5 层分类树（数据落于此，最终通过 Category 实体管理） |
| `extraction_result` | LLM 属性抽取（events/labels/entities）|
| `event_analysis` | 事件六维分析 |
| `fusion_report` | 多报文综合融合报告（含 LLM 输出） |
| `target_analysis` | 目标抽取（每行 = 一篇报文中的一个目标） |
| `target_alias` | 目标别名 → 标准名 |
| `target_fusion` | 目标融合结果 |
| `rag_document` | RAG 上传文档元数据 |

### 5.2 `origin_text`

```sql
-- 原表已存在；以下为权威列（已含历次 ALTER 后的合集）
CREATE TABLE `origin_text` (
  `id`              VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '主键',
  `title`           TEXT,
  `content`         LONGTEXT,
  `times`           VARCHAR(50)  DEFAULT NULL COMMENT '报文时间（字符串原样）',
  `type`            VARCHAR(64)  DEFAULT NULL COMMENT 'text_type.id（叶子节点）',
  `modal_type`      VARCHAR(20)  DEFAULT '文字报' COMMENT '文字报/图文报/声像报',
  `category`        VARCHAR(20)  NOT NULL DEFAULT '未分类'
                    COMMENT '一级分类：开源信息/HZ报/JZ报/未分类',
  `send_unit_name`  VARCHAR(255) DEFAULT NULL COMMENT '发报单位',
  `brief_type_name` VARCHAR(100) DEFAULT NULL,
  `is_extracted`    TINYINT(1)   NOT NULL DEFAULT 0,
  `is_indexed`      TINYINT(1)   NOT NULL DEFAULT 0,
  `images`          TEXT         DEFAULT NULL COMMENT 'JSON 数组：["188_1.jpg",...]',
  PRIMARY KEY (`id`),
  KEY `idx_type` (`type`),
  KEY `idx_category` (`category`),
  KEY `idx_category_send_unit` (`category`,`send_unit_name`),
  KEY `idx_send_unit` (`send_unit_name`),
  KEY `idx_is_extracted` (`is_extracted`),
  KEY `idx_is_indexed`   (`is_indexed`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
```

`category` 回填规则（CategoryClassifier 一致）：
```sql
UPDATE origin_text SET category =
  CASE
    WHEN send_unit_name REGEXP '^HZ[0-9]+报' THEN 'HZ报'
    WHEN content LIKE '%JZX%'               THEN 'JZ报'
    WHEN content LIKE '%入网QB%'             THEN '开源信息'
    ELSE '未分类'
  END
WHERE category='未分类';
```

### 5.3 `text_type`（5 层分类）

```sql
CREATE TABLE `text_type` (
  `id`           VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `name`         VARCHAR(200) NOT NULL,
  `parent_id`    VARCHAR(32)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `level`        INT          DEFAULT NULL COMMENT '1..5',
  `full_path`    VARCHAR(1000) DEFAULT NULL COMMENT '父名/子名/…',
  `sort_order`   INT          DEFAULT 1000,
  `is_leaf`      TINYINT(1)   DEFAULT 1,
  `description`  TEXT,
  `create_time`  DATETIME     DEFAULT CURRENT_TIMESTAMP,
  `update_time`  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_parent_id` (`parent_id`),
  KEY `idx_full_path` (`full_path`(255)),
  KEY `idx_is_leaf`   (`is_leaf`)
);

-- 固定占位
INSERT INTO `text_type`(`id`,`name`,`level`,`full_path`,`is_leaf`) VALUES ('2','未分类',1,'未分类',1);
```

### 5.4 `extraction_result`

```sql
CREATE TABLE `extraction_result` (
  `id`               VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `origin_text_id`   VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `extraction_time`  DATETIME    DEFAULT CURRENT_TIMESTAMP,
  `model`            VARCHAR(50) DEFAULT 'GLM-5',
  `total_events`     INT         DEFAULT 0,
  `events_json`      LONGTEXT,
  `labels_json`      LONGTEXT,
  `entities_json`    LONGTEXT,
  `status`           VARCHAR(20) DEFAULT 'completed',  -- processing/completed/failed
  `error_message`    TEXT,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_origin_text_id` (`origin_text_id`)
);
```

### 5.5 `event_analysis`

```sql
CREATE TABLE `event_analysis` (
  `id`              VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `origin_text_id`  VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `event_time`      VARCHAR(100),
  `event_location`  VARCHAR(200),
  `event_content`   TEXT,
  `event_analysis`  TEXT,
  `analysis_date`   DATE NOT NULL,
  `create_time`     DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_origin_text_id` (`origin_text_id`),
  KEY `idx_analysis_date`  (`analysis_date`),
  UNIQUE KEY `uk_origin_event` (`origin_text_id`, `event_content`(100))
);
```

### 5.6 `fusion_report`

```sql
CREATE TABLE `fusion_report` (
  `id`           VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `title`        VARCHAR(200) NOT NULL,
  `summary`      TEXT,
  `timeline`     JSON,
  `content`      TEXT,
  `entities`     JSON,
  `labels`       JSON,
  `source_ids`   VARCHAR(1000),   -- 报文 ID JSON 字符串
  `model_used`   VARCHAR(50),
  `create_time`  DATETIME,
  `update_time`  DATETIME,
  PRIMARY KEY (`id`)
);
```

### 5.7 `target_analysis`

```sql
CREATE TABLE `target_analysis` (
  `id`              VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `origin_text_id`  VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `region_name`     VARCHAR(255),
  `target_name`     VARCHAR(255) NOT NULL COMMENT '别名映射后的标准名',
  `raw_target_name` VARCHAR(255),
  `target_type`     VARCHAR(100),
  `found_time`      VARCHAR(50),
  `description`     TEXT,
  `attachment_url`  TEXT,
  `is_fused`        TINYINT(1) NOT NULL DEFAULT 0,
  `create_time`     DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`     DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_origin_text_id` (`origin_text_id`),
  KEY `idx_target_name`    (`target_name`),
  KEY `idx_region_name`    (`region_name`),
  KEY `idx_is_fused`       (`is_fused`)
);
```

### 5.8 `target_alias`

```sql
CREATE TABLE `target_alias` (
  `id`              VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `alias`           VARCHAR(255) NOT NULL,
  `canonical_name`  VARCHAR(255) NOT NULL,
  `create_time`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_alias`           (`alias`),
  KEY        `idx_canonical_name` (`canonical_name`)
);
```

### 5.9 `target_fusion`

```sql
CREATE TABLE `target_fusion` (
  `id`                    VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `target_names`          VARCHAR(1000) NOT NULL COMMENT 'JSON 数组：["哈尔科夫弹药库","某机场"]',
  `analysis`              TEXT,
  `difference`            TEXT,
  `region_fusion_result`  TEXT,
  `source_count`          INT NOT NULL DEFAULT 0,
  `is_fused`              TINYINT(1) NOT NULL DEFAULT 1,
  `fusion_time`           DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `create_time`           DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`           DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_target_names_prefix` (`target_names`(64)),
  KEY `idx_fusion_time`         (`fusion_time`)
);
```

`target_names` 包含某 `targetName` 的精确匹配（避免子串误中）：`LIKE CONCAT('%','"<targetName>"','%')`。

### 5.10 `rag_document`

```sql
CREATE TABLE `rag_document` (
  `id`           BIGINT       AUTO_INCREMENT PRIMARY KEY,
  `doc_id`       VARCHAR(64)  NOT NULL UNIQUE COMMENT 'upload_<uuid16>',
  `filename`     VARCHAR(255),
  `file_size`    BIGINT,
  `chunk_count`  INT          DEFAULT 0,
  `status`       VARCHAR(20)  NOT NULL DEFAULT 'pending', -- pending/indexed/failed
  `error_msg`    TEXT,
  `upload_time`  DATETIME     NOT NULL,
  `indexed_time` DATETIME,
  KEY `idx_status` (`status`),
  KEY `idx_upload_time` (`upload_time`)
);
```

> `rag_document` 保留 BIGINT AUTO_INCREMENT 主键（与业务表不同），DTO 中 `id:Long`。

---

## 6. 全局约定

- **命名**：
  - 数据库表名/列名/索引名一律 snake_case；
  - Java 字段一律 camelCase，由 `map-underscore-to-camel-case=true` 自动映射；
  - 写 SQL（DDL/DML/XML/注解）严格下划线；写 Java 严格驼峰。
- **ID**：
  - 业务表全部 VARCHAR(32) 字符串，雪花算法（MyBatis-Plus `IdType.ASSIGN_ID`）；
  - `OriginText.id` 标 `IdType.INPUT`（由导入路径自带 sid）；
  - 单独显式创建 ID 时用 `com.baomidou.mybatisplus.core.toolkit.IdWorker.getIdStr()`。
- **时间**：业务时间字段统一 `LocalDateTime`，序列化 `"yyyy-MM-dd HH:mm:ss"`/`GMT+8`；报文时间 `times` 为字符串原样保留。
- **统一返回**：`ResultVO`（详见 §7.7）；异常由 `GlobalExceptionHandler` 全局接管。
- **逻辑删除**：MyBatis-Plus 全局字段 `deleted`，0 未删 / 1 已删。
- **commit**：项目约定 git commit **不写** `Co-Authored-By`。

---

## 7. 模型层（实体 / DTO / Request / 公共）

### 7.1 公共：`com.qy.dch.common`

#### 7.1.1 `ResultVO`（`@Data @NoArgsConstructor @AllArgsConstructor`）

| 字段 | 类型 | 含义 |
|---|---|---|
| code | Integer | 1=成功, 400=业务错, 500=系统错 |
| data | Object | 业务数据 |
| msg  | String  | 文案 |
| flag | Object  | 旧版兼容："true"/"false" |

常量：`CODE_SUCCESS=1` / `CODE_BIZ_ERROR=400` / `CODE_SYS_ERROR=500`。
工厂方法：`success()` / `success(data)` / `bizFail(msg)` / `systemError(msg)` / `error(msg)`【@Deprecated】 / `error(code,msg[,data])` / `error(ErrorCode)`.

#### 7.1.2 `ErrorCode`

| 名 | code | message |
|---|---|---|
| SUCCESS | 0 | success |
| SYSTEM_ERROR | 1001 | 系统内部错误 |
| PARAM_INVALID | 1002 | 参数校验失败 |
| FORBIDDEN | 1003 | 禁止访问 |
| RESOURCE_NOT_FOUND | 2001 | 资源不存在 |
| DUPLICATE_KEY | 2002 | 数据重复 |
| RAG_INDEXING_RUNNING | 3001 | 索引任务正在执行中 |
| RAG_SEARCH_FAILED | 3002 | 语义检索失败 |
| DB_TABLE_NOT_ALLOWED | 4001 | 不允许操作的表 |
| DB_FIELD_PROTECTED | 4002 | 核心字段禁止修改 |
| DB_DDL_FAILED | 4003 | DDL 执行失败 |

#### 7.1.3 `BusinessException extends RuntimeException`

`@Getter`，`final ErrorCode errorCode; final Object data;` 三构造：`(ErrorCode)`、`(ErrorCode, String customMessage)`、`(ErrorCode, String customMessage, Object data)`。

### 7.2 `entity`

#### 7.2.1 `BaseEntity`（abstract，`@Data implements Serializable`）

| 字段 | 类型 | 注解 |
|---|---|---|
| id | String | `@TableId` |
| createTime | LocalDateTime | `@TableField(fill=INSERT)` |
| updateTime | LocalDateTime | `@TableField(fill=INSERT_UPDATE)` |
| deleted | Integer | `@TableLogic`, `@TableField(fill=INSERT)` |

#### 7.2.2 `Category`

不继承 BaseEntity。字段：`id, name, parentId, level, fullPath, sortOrder, isLeaf, description, createTime(@JsonFormat), updateTime(@JsonFormat), children(瞬态), reportCount(瞬态)`。辅助：`getChildren()` 懒初始化、`isLeaf()`、`isRoot()`、`canAddChild()` 检 level<5。

#### 7.2.3 `OriginText`（`@TableName("origin_text")`，`IdType.INPUT`）

字段（均 `@TableField`）：`id, title, content, times, type, category, modalType, isIndexed, isExtracted, sendUnitName, briefTypeName, images`。

#### 7.2.4 `TargetAlias`（`@Data @NoArgs @AllArgs`）

`id, alias, canonicalName, createTime, updateTime`.

#### 7.2.5 `TargetAnalysis`（`@Data @NoArgs @AllArgs`）

`id, originTextId, regionName, targetName, rawTargetName, targetType, foundTime(String), description, attachmentUrl, isFused:Integer, createTime, updateTime`.

#### 7.2.6 `TargetFusion`（`@Data @NoArgs @AllArgs`）

`id, targetNames(String JSON), analysis, difference, regionFusionResult, sourceCount, isFused, fusionTime, createTime, updateTime`.

### 7.3 `dto`

| DTO | 关键字段 |
|---|---|
| `AddCategoryResultDTO` | categoryId/Name, parentId/Name, isNewParent:Boolean |
| `AlgorithmAnalyzeRequest` | messageTime, content, url |
| `AlgorithmAnalyzeResponse` | code, msg, data:List\<AlgorithmTargetItem\> |
| `AlgorithmFusionRequest` | targets:List\<FusionTargetItem(regionName,targetName,foundTime,description)\> |
| `AlgorithmFusionResponse` | code, msg, data:FusionData{ fusionResults:List\<FusionResult{analysis,difference}\>, regionFusionResult } |
| `AlgorithmTargetItem` | regionName, targetName, targetType, foundTime, description, attachmentUrl |
| `DirectoryTreeNodeDTO` | name, type, reportId, title, count, children |
| `EventAnalysisDTO` | id, originTextId, eventTime, eventLocation, eventContent, eventAnalysis, analysisDate, sourceTitle, createTime:Instant |
| `ExtractionResultDTO` | id, originTextId, model, labelsJson, entitiesJson, eventsJson, status, errorMessage, extractionTime, totalEvents；`getEvents/getLabels/getEntities` 走 fastjson |
| `FieldInfoDTO` | fieldName, dataType, defaultValue, isNullable, comment, columnKey, maxLength |
| `FusionDTO` | id, fusionId, title, summary, timeline, content, entities, labels, sourceIds, modelUsed, createTime, updateTime（全 String） |
| `ImportResultDTO` | totalLines, successCount, failCount, uploadedImages=0, categoryId, categoryName, errors:List\<String\> |
| `OriginTextDTO` | sid(主键), title, content, times, type, modalType, labelsJson, images, sendUnitName, briefTypeName, category, isExtracted |
| `PageResultDTO<T>` | list, total, pageNum, pageSize, totalPages（构造自动算 ceil(total/size)） |
| `RagIndexLogDTO` | id, triggerType, startTime, endTime, status, processedCount, successCount, skippedCount, failedCount |
| `RagIndexStatusDTO` | totalDocs, indexedDocs, unindexedDocs, byDate:List\<Map\> |
| `TargetAnalysisDTO` | id, originTextId, regionName, targetName, targetType, foundTime, description, attachmentUrl, reportTitle, sendUnitName, isFused |
| `TargetFusionResultDTO` | fusionId, targetNames, analysis, difference, regionFusionResult, targetAnalyses:List\<TargetAnalysisDTO\> |
| `TextTypeDTO` | id, typeName, parentId, children |

### 7.4 `request`

- `AddCategoryRequest`：`categoryName, parentCategoryName`
- `AddFieldRequest`：`tableName, fieldName, dataType, defaultValue, comment, length, nullable:Boolean`
- `DeleteFieldRequest`：`tableName, fieldName`
- `EventAnalysisQueryRequest`：`startDate, endDate, keywords:List<String>`
- `FusionCreateRequest`：`reports:List<ReportData>, fusionType, customTitle`
- `GetListRequest`：`pageNum, pageSize, typeId, typeIds, modalType, modalTypes, keywords, startTime, endTime`
- `ModifyFieldRequest`：`tableName, fieldName, dataType, comment, length`
- `RagIndexTriggerRequest`：`startDate @NotBlank @Pattern(yyyy-MM-dd), endDate 同`
- `RagSearchRequest`：`query @NotBlank @Size(1..500), topK:Integer=10 @Min1 @Max100, hybrid:Boolean=true`
- `ReportData`：手写 getter/setter。`id, title, content, times, type, extractionResult:ExtractionResult{events:List<Map>, entities:Map, labels:List<String>}`
- `TargetSearchRequest`：`targetName, maxReports=10`
- `TriggerAnalysisRequest`：`startDate, endDate`

### 7.5 `domain.PageDomain`

`total:Integer, list:List, pageNum, pageSize, pages`（旧式分页 VO，UygurController 用）。

### 7.6 rag/model

| 类 | 字段 |
|---|---|
| `DocumentChunk` | id(`docId_chunk_N`), documentId, content, chunkIndex, embedding(float[1024]), metadata, chunkType(short/medium/long), length, score |
| `ParsedDocument` | id, content, source("docx"), type(text/mixed/table), metadata(默认 HashMap) |
| `RagDocument` | id:Long, docId, filename, fileSize, chunkCount, status, errorMsg, uploadTime/indexedTime:Instant |
| `SearchResult` | chunkId, docId, content, finalScore, bm25Score, vectorScore, title, publishTime, category, chunkIndex, chunkType, rank |

---

## 8. Mapper（持久层）

### 8.1 `EventAnalysisMapper`（注解）

- `insertOrUpdate(EventAnalysisDTO)` — `INSERT ... ON DUPLICATE KEY UPDATE event_time, event_location, event_analysis, analysis_date`
- `queryByDateAndKeywords(startDate, endDate, List<String> keywords)` — `<script>` `event_analysis ea LEFT JOIN origin_text ot ON ea.origin_text_id=ot.id`，`ea.analysis_date BETWEEN`，keywords AND-组合 OR 三字段 LIKE
- `countByDate(LocalDate)`
- `countByOriginTextId(String)`

### 8.2 `ExtractionMapper`（注解）

- `selectByOriginTextId(String)` / `selectById(Integer)`
- `insertOrUpdate(ExtractionResultDTO)` — events/labels/entities/total/model/status/error_message，`extraction_time=NOW()`
- `deleteByOriginTextId(String)`
- `searchByEntityKeyword(keyword, limit)` — `SELECT DISTINCT origin_text_id FROM extraction_result WHERE entities_json LIKE '%key%' LIMIT N`

### 8.3 `FusionMapper`（XML：`mapper/FusionMapper.xml`）

ResultMap `FusionResultMap → FusionDTO`：`id→fusionId, title, summary, timeline, content, entities, labels, source_ids→sourceIds, model_used→modelUsed, create_time→createTime, update_time→updateTime`。

- `insertFusion` — useGeneratedKeys=true keyProperty=fusionId
- `selectFusionById(String id)`
- `selectFusionList(offset, limit)` — `ORDER BY create_time DESC`
- `selectFusionCount`
- `updateFusion` — 不更新 source_ids/model_used/create_time

### 8.4 `RagDocumentMapper`（XML）

- `insert(RagDocument)` — useGeneratedKeys=true
- `selectByDocId(@Param docId)`
- `updateStatus(docId, status, chunkCount, errorMsg)` — `status='indexed'` 时把 `indexed_time` 置 NOW()

### 8.5 `RagMapper extends BaseMapper<OriginText>`

- `selectUnindexedIds(startDate, endDate)`：默认方法 + LambdaQueryWrapper
- `selectAllUnindexedIds()`
- `selectByIds(List<String>)` → `OriginTextDTO`
- `updateIndexedStatus(List<String>)` / `updateIndexedStatusById(String)`
- `static toDTO(OriginText) → OriginTextDTO`
- `@Select` `getIndexStats()` 总数 / 已索 / 未索
- `@Select` `getIndexStatsByDate()` 按 DATE(times) 聚合 LIMIT 30

### 8.6 `TargetAliasMapper`

`insert / selectCanonicalNameByAlias / selectAll(ORDER BY id) / update(canonical_name) / deleteById / selectAliasesByCanonicalName`

### 8.7 `TargetAnalysisMapper`

- `insert(TargetAnalysis)` — 字段含 `raw_target_name`，is_fused=0
- `batchInsert(List)` — `<foreach>`
- `selectByOriginTextId / selectAll(ta LEFT JOIN ot, 出 reportTitle、sendUnitName) / selectByFilter(regionName?, targetName?)`
- `selectByTargetNames(List<String>)` — `WHERE target_name IN (<foreach>)`
- `updateFusedStatus(List<String> ids, Integer isFused)`
- `countByTargetName()` — `GROUP BY target_name`

### 8.8 `TargetFusionMapper`

- `insert(TargetFusion)` — 写 id/target_names/analysis/difference/region_fusion_result/source_count/is_fused
- `selectById / selectAll(ORDER BY fusion_time DESC)`
- `selectLatestByTargetName(@Param("targetNameWithQuotes") String)` — `WHERE target_names LIKE CONCAT('%', #{tn}, '%') ORDER BY fusion_time DESC LIMIT 1`（needle 形如 `"哈尔科夫弹药库"`）

### 8.9 `UygurMapper`（~50 方法，**只列方法分组**，SQL 形态见上）

> 所有列表查询均 `LEFT JOIN extraction_result e ON o.id=e.origin_text_id` 并投影 `o.*, o.id AS sid, e.labels_json AS labelsJson`。

- 插入：`insertOriginText, batchInsertTexts, batchInsertTextsWithImages`
- 列表/分页：
  - 单条件：`getTextListByType/ModalTypePaged + count + 非分页`、`getTextListAllPaged + count + 非分页`
  - 多条件：`getTextListByTypeIds/ModalTypesPaged + count`、`getTextListByCombinedFilterPaged + count`、`getTextListByAdvancedFilterPaged(typeIds, modalTypes, keywords, startTime, endTime, offset, pageSize) + count`
  - 报文筛选：`getReportsByFilter(category, sendUnit, keyword, startDate, endDate, offset, pageSize) + count`
  - 日期：`getReportsByDateRange(start, end, isExtracted)`、`selectIdsByTimeRange(start, end, isExtracted)`
- 单行/状态：`selectById, selectIsExtracted, getTextById, updateExtractedStatus, resetAllExtractedStatus, getExtractionStats`
- 删除/迁移：`deleteText, deleteTextsBatch, deleteTextsByType, updateTextsType, updateTextsByOldType`
- 统计：`countByType, countByModalType, countByCategory, countBySendUnitInCategory, getReportsByCategoryAndSendUnit`
- 旧分类（`text_type` name 字段）：`getCategories, getCategoryByNameAndParent, getCategoryIdByName, deleteCategory, deleteCategoriesBatch, updateCategoryName`
- 新分类（`Category` 实体）：`selectAllCategories / selectByParentId / selectCategoryById / selectCategoryByName / insertCategory / updateCategory / deleteCategoryById / deleteCategoryByPathPrefix / updateChildrenPath(old, new) / selectLeafCategories / countReportsByCategory / selectCategoryBySendUnitName`

---

## 9. Service（业务层）

> 均 `@Service` + `@Resource` 注入 mapper / 第三方 bean。复杂逻辑要点描述如下；实现完整代码见仓库。

### 9.1 `CategoryService`

- `getCategoryTree()`：全表 + 报文计数 → 按 parentId 装树。
- `getLeafCategories()`：`selectLeafCategories`。
- `createCategory(name, parentId, desc)`：唯一名校验 → 父 `level<5` → id=`IdWorker.getIdStr()` → 顶级 `level=1, fullPath=name`，子级 `level=parent.level+1, fullPath=parent.fullPath+"/"+name`，`isLeaf=1, sortOrder=1000`。
- `updateCategory(id, newName, newDesc)`：唯一性校验 → 改本节点 `fullPath`（拼前缀 + newName）→ `updateChildrenPath(oldPath, newPath)`。
- `moveCategory(id, newParentId)`：禁止移到自身子孙下；层级+子树深度 ≤5；递归更新子孙 `fullPath/level`（带 levelDiff）。
- `deleteCategory(id)`：`deleteCategoryByPathPrefix(fullPath)`。
- `findOrCreateLeafBySendUnitName(name)`：空 → 固定 `UNCATEGORIZED_ID="2"`；同名叶子 → 返回 id；不存在 → 挂在「未分类」下创建，并把「未分类」`isLeaf` 置 0。

### 9.2 `DashboardService`

- `getOverview()` → `{totalReports, extracted, unextracted, fusionReports, totalLabels=0}`
- `getCategoryDistribution()`：`countByType` + `getCategories` 映射 typeName，null→「未分类」
- `getModalDistribution()`：`countByModalType`
- `getRecentFusions(limit)`：`selectFusionList(0, limit)` → `{id, fusionId, title, summary, createTime}`

### 9.3 `DatabaseService`

白名单：操作表只允许 `origin_text`；受保护核心字段（id/sid/title/content/times/type）禁修改/删除；允许 dataType：`VARCHAR/INT/BIGINT/TEXT/DATETIME/TIMESTAMP/DECIMAL`；标识符正则 `^[a-zA-Z_][a-zA-Z0-9_]{0,63}$`。

- `getTableStructure(tableName)`：查 `INFORMATION_SCHEMA.COLUMNS`，schema 从 `spring.datasource.url` 解析。
- `addField/modifyField/deleteField`：组装 `ALTER TABLE ... ADD/MODIFY/DROP COLUMN`，VARCHAR 长度 1..65535，COMMENT 转义 `'`、`\`、CR/LF。

### 9.4 `DirectoryService`

- `getDirectoryTree()`：三层 — `countByCategory` 一级 → `countBySendUnitInCategory` 二级 → `getReportsByCategoryAndSendUnit` 三级。节点 `type` 取值 `category|sendUnit|report`。

### 9.5 `EventAnalysisService`

- 算法服务：`POST {algorithm.service.url}/eventSplit` body `{text}`，期待 `{code, data.events:[{eventTime, eventLocation, eventContent, eventAnalysis}, ...]}`。
- `analyzeReportsByDate(start, end)`：`getReportsByDateRange` → 跳过 `countByOriginTextId>0` → 逐条算法 → `insertOrUpdate`。返回 total/success/failed/skipped。
- `queryEvents(start, end, keywords)`：`queryByDateAndKeywords`。
- `getAnalysisStatus(date)` → `{date, eventCount, analyzed:eventCount>0}`。

### 9.6 `ExtractionService`

- HTTP：`HttpURLConnection POST {algorithm.service.url}/extract` body `{text, origin_text_id}`，10s/1200s。
- `extract(id, force)`：`is_extracted=1 && !force` 直接返回；否则取文本 → 算法服务 → `parseAndSave` → `updateExtractedStatus(id,1)`。
- `parseAndSave`：`model="GLM-5-hierarchical"`, status=`completed`，events/labels/entities 空缺自动补 `{"events":[]}/[]/{}`，`insertOrUpdate`。
- `getResult(id)`：events_json 兼容包对象/裸数组；labels/entities 透传。
- 批量异步（委托 `TaskProgressService`）：
  - `startBatchExtraction(start, end, scope)` — scope `unextracted`(`isExtracted=0`) / `all`(null)
  - `getBatchProgress(taskId)` / `stopBatchTask(taskId)`

### 9.7 `FusionService`（多报文 LLM 融合）

- `createFusion(req)`：2 ≤ reports ≤ 20；缺 ExtractionResult 时调用 `ExtractionService.extract` 补；POST `{algorithm.service.url}/fusion/create`（10s/600s）。
- `parseAlgorithmResult`：取 `fusionId/title/summary/content/modelUsed/createTime/updateTime`；timeline/entities/labels 统一序列化 JSON；sourceIds 用 reports.id 列表 JSON 化。
- 兜底 `createDefaultFusion`：算法挂时返回默认 markdown content、默认实体/标签、`modelUsed="default"`。
- `saveFusion`：补 create/update，`fusionMapper.insertFusion`。
- `getFusionList(p, size) / getFusionDetail(id) / searchByTarget(targetName, maxReports)`：`searchByTarget` 通过 `ExtractionMapper.searchByEntityKeyword` 找 ID 再补报文 + 抽取结果。

### 9.8 `RagService`

详见 §12。

### 9.9 `TargetAliasService`

- `resolveAlias(alias)`：`selectCanonicalNameByAlias`，无则返回原 alias。
- `addAlias / getAllAliases / updateAlias / deleteAlias`：CRUD，ID = `IdWorker.getIdStr()`。

### 9.10 `TargetAnalysisService`

- 算法 URL：`@Value("${algorithm.service.url:http://localhost:9203}")`（默认与全局不同）。
- `analyzeReport(originTextId)`：`getTextById` → `AlgorithmAnalyzeRequest{messageTime, content, url:""}` → POST `/api/target/analyze` → `targetAliasService.resolveAlias` 规范化 target_name → `TargetAnalysis(originTextId, regionName, targetName, targetType, foundTime, description, attachmentUrl, isFused=0)` → `batchInsert` → 返回 `selectByOriginTextId`。
- `analyzeBatch(start, end)`：循环 `analyzeReport`，返回 success 数。
- `getTargetAnalysisList(region, target) / getByOriginTextId(id)`：直查。
- 异步批量（委托 `TaskProgressService`）：`startBatchAnalysisAsync(start, end)`（taskId 前缀 `analysis_batch`）/`getBatchAnalysisProgress / stopBatchAnalysis`。

### 9.11 `TargetFusionService`

- 算法 URL：同上（默认 9203）。
- `fuseTargets(targetNames)`：`selectByTargetNames` → 组 `AlgorithmFusionRequest{targets:[{regionName, targetName, foundTime, description}]}` → POST `/api/target/fusion` → 取 `FusionData.fusionResults[0].{analysis, difference}` + `regionFusionResult` → 落 `target_fusion` → `updateFusedStatus(ids, 1)` → 返回完整 DTO（含 targetAnalyses）。
- `getAllFusions()`：列出 target_fusion，反序列化 targetNames JSON。
- `getFusionDetail(targetName)`：
  1. `targetAnalysisMapper.selectByFilter(null, targetName)` 拿 analyses（join 出 sendUnitName）；空 → null。
  2. `basicInfo = {targetName, sourceCount, sources:[{sendUnit, foundTime, description, url=attachmentUrl}]}`
  3. `needle = "\"" + targetName + "\""` → `targetFusionMapper.selectLatestByTargetName(needle)` 取最新一条 → `fusionResult = {analysis, difference, regionFusionResult}`，无则三字段空字符串。
  4. 返回 `{basicInfo, fusionResult}`。

### 9.12 `TaskProgressService` + `Impl`

抽取后的共享批量任务进度组件。`@Service` 单例。

- 内部：`ConcurrentHashMap<String, BatchTaskState> tasks`、`Executors.newFixedThreadPool(2)`、`@PreDestroy` shutdown + 5s awaitTermination。
- `BatchTaskState`：
  - `taskId / total / AtomicInteger done, failed / AtomicBoolean running(true) / volatile currentSid / synchronizedList logs(滚动上限 100) / startTime`
- API：
  - `createTask(prefix, total)` → `prefix_<ts>`
  - `log/setCurrent/incrementDone/incrementFailed/isRunning/finish/requestStop`
  - `getProgress(taskId)` → `{taskId, total, done, failed, running, currentSid, logs(拷贝), eta=avg*remaining/1000秒}`
  - `executor()` 暴露线程池

### 9.13 `UygurService`

- 依赖：UygurMapper、CategoryService、MinioClient、`@Value("${minio.bucket}")`、`@Value("${filePath}")`。
- `savetext()`：读 `filePath` 整 JSON 数组 → 每条插 origin_text，times 数组逗号拼接；`CategoryClassifier.classify(sendUnitName, content)` 取分类名 → `CategoryService.findOrCreateLeafBySendUnitName` 取叶子 id 当 type。
- `getCategory()`：拼一二级 children。
- `getTextList(req)`：三分支（modalType > typeId > all）。
- `getTextListPaged(req)`：合并 typeIds/modalTypes/keywords/startTime/endTime；有 keywords/时间 → `advancedFilter`；只有组合 → `combinedFilter`；都没 → all；返回 `PageResultDTO`。
- `getTextById / resetAllExtractedStatus / getExtractionStats`：直查。
- `addCategory` / `addCategoryByParentId`：**已停用**，抛 `RuntimeException`，提示走 `CategoryService.createCategory()`。
- `importFromJsonl(file, defaultCategoryId)`：兼容中英文键（标题/title、内容/content、时间/createDate）、sendUnitName、briefTypeName；时间格式三态（`yyyy-MM-dd HH:mm:ss` 截前 10、直返 yyyy-MM-dd、中文「年月日」）；按 sendUnitName 选叶子 id（失败回退 default）；modalType=「文字报」；`CategoryClassifier.classify` 写 category；批 100 调 `batchInsertTexts`。
- `importFromJsonlWithImages`：校验一二级分类存在 → MinIO 上传图片 `images/yyyyMMdd/<filename>` → 解析 JSONL 按「图片」数组匹配 minioPath，写 `images` 字段、modalType=「图文报」；批 100 `batchInsertTextsWithImages`。
- `getCategoryTree()`：两层 — `countByCategory` 一级 → `countBySendUnitInCategory` 二级。
- `getReportsByFilter / getReportsByAdvancedFilter`：分页直查。
- 删除/迁移分类与报文一组 CRUD。

---

## 10. Controller（REST API）

> 全部返回 `ResultVO`（DirectoryController 用 Map），异常被全局拦截。下文按 Controller 列举（路径 = class-level + method-level）。

### 10.1 `/api/category` (`CategoryController`)

| Verb | Path | Params | 说明 |
|---|---|---|---|
| GET | `/tree` | — | 完整分类树 + 报文计数 |
| GET | `/leafs` | — | 所有叶子节点（下拉框） |
| POST | `/create` | body `{name, parentId?, description?}` | 新增节点 |
| PUT | `/update` | body `{categoryId, newName, newDescription}` | 重命名 / 改描述 |
| POST | `/move`   | body `{categoryId, newParentId?}` | 移动 |
| DELETE | `/delete/{categoryId}` | path String | 级联删 |
| GET | `/detail/{categoryId}` | path String | 节点详情 |

### 10.2 `/api/dashboard`

`GET /overview`、`GET /categoryDistribution`、`GET /modalDistribution`、`GET /recentFusions?limit=10`。

### 10.3 `/api/database`

`GET /table/structure?tableName=`、`POST /field/add`、`POST /field/modify`、`POST /field/delete`。

### 10.4 `/api/directory`

`GET /tree` → `{code, msg, data:List<DirectoryTreeNodeDTO>}`。

### 10.5 `/api/rag/document` (`DocumentController`)

| Verb | Path | Params |
|---|---|---|
| POST | `/upload` | multipart `file` |
| POST | `/upload/mixed` | multipart `file`（含 OCR） |
| POST | `/parse` | multipart `file`, `withOcr=false` |
| GET | `/status/{docId}` | path String |

### 10.6 `/api/eventAnalysis`

| Verb | Path | Body / Params |
|---|---|---|
| POST | `/trigger` | `TriggerAnalysisRequest{startDate, endDate}`，CompletableFuture 异步 |
| POST | `/query` | `EventAnalysisQueryRequest{startDate, endDate, keywords}` |
| GET | `/status` | `?date=yyyy-MM-dd` |

### 10.7 `/extraction`

| Verb | Path | Params |
|---|---|---|
| POST | `/extract` | `?originTextId=&force=false` |
| GET | `/result/{originTextId}` | path String |
| POST | `/batch/start` | `?startDate=&endDate=&scope=unextracted|all` |
| GET | `/batch/progress/{taskId}` | path |
| POST | `/batch/stop/{taskId}` | path |

### 10.8 `/api/fusion`

| Verb | Path | Body / Params |
|---|---|---|
| POST | `/create` | `FusionCreateRequest` |
| POST | `/save` | `FusionDTO` |
| GET | `/list` | `?pageNum=1&pageSize=10` |
| GET | `/detail/{id}` | path String |
| GET | `/export/pdf/{id}` | path String（占位） |
| GET | `/export/word/{id}` | path String（占位） |
| POST | `/searchByTarget` | `?targetName=&maxReports=10` |

### 10.9 `/api/rag` (`@Validated`)

| Verb | Path | Body |
|---|---|---|
| GET | `/index/status` | — |
| POST | `/index/trigger` | `RagIndexTriggerRequest` |
| POST | `/search` | `RagSearchRequest` |
| GET | `/index/log` | `?pageNum=1&pageSize=20` |

### 10.10 `/api/target` (`TargetController`)

**目标分析**
| Verb | Path | Params |
|---|---|---|
| POST | `/analyze/{originTextId}` | path |
| POST | `/analyze/batch` | `?startTime=&endTime=`（同步） |
| POST | `/analyze/batch/async` | body `{startTime, endTime}` |
| GET | `/analyze/task/{taskId}` | 进度 |
| POST | `/analyze/task/{taskId}/stop` | 停止 |
| GET | `/analysis/list` | `?regionName=&targetName=` |
| GET | `/analysis/byReport/{originTextId}` | path |

**目标融合**
| Verb | Path | Params |
|---|---|---|
| POST | `/fusion` | body `List<String> targetNames` |
| GET | `/fusion/list` | — |
| GET | `/fusion/detail` | `?targetName=` |
| GET | `/fusion/export` | `?targetName=&format=docx|pdf` → 文件流（Content-Disposition: attachment，RFC 5987 UTF-8 中文文件名） |

**别名管理**
| Verb | Path | Params |
|---|---|---|
| POST | `/alias` | body `{alias, canonicalName}` |
| GET | `/alias/list` | — |
| PUT | `/alias/{id}` | body `{canonicalName}` |
| DELETE | `/alias/{id}` | path |

### 10.11 `/test`

`GET /queryCategory?name=`（调试编码）、`POST /testMultipart`（multipart 编码测试）。

### 10.12 `/api/uygur` (`UygurController`)

| Verb | Path | Body / Params |
|---|---|---|
| GET | `/config` | `{minioPrefix}` |
| GET | `/savetext` | 从 filePath 批量导入 JSON |
| GET | `/category` | 文本分类列表 |
| GET | `/detail/{sid}` | 报文详情 |
| POST | `/getTextList` | `GetListRequest` |
| POST | `/resetExtracted` | 全表 is_extracted=0 + 统计 |
| POST | `/addCategory` | `AddCategoryRequest`（按父名） |
| POST | `/category` | `{typeName, parentId?}` 通用 |
| PUT | `/category/{categoryId}` | `{newTypeName}` |
| POST | `/importFromJsonl` | multipart + `defaultCategoryId=2` |
| GET | `/filter` | `?category=&sendUnit=&keyword=&startDate=&endDate=&pageNum=&pageSize=` |
| POST | `/importFromJsonlWithImages` | multipart + parent/categoryName + images[] |
| DELETE | `/category/{categoryId}` | path |
| POST | `/category/batchDelete` | body `List<String>` |
| DELETE | `/text/{sid}` | path |
| POST | `/text/batchDelete` | body `List<String>` |
| DELETE | `/category/{categoryId}/withTexts` | path |
| POST | `/text/updateByOldType` | `{oldTypeId, newTypeId}` |
| GET | `/categoryTree` | — |
| POST | `/filter/advanced` | `{typeIds:[], modalTypes:[], keywords:[], startTime, endTime, pageNum, pageSize}` |

**接口数合计**：Category 7 + Dashboard 4 + Database 4 + Directory 1 + Document 4 + EventAnalysis 3 + Extraction 5 + Fusion 7 + Rag 4 + Target 14 + Test 2 + Uygur 20 = **75**。

---

## 11. 配置类与异常处理

### 11.1 `CorsConfig`

注册全局 `CorsFilter`：`allowedOrigins=*`、所有方法/头、`maxAge=3600`、`/**` 生效。

### 11.2 `DruidConfig`

暴露 `StatViewServlet`，路径 `/druid/*`，硬编码 `admin/admin`、允许任意 IP（`allow=""`）。

### 11.3 `MybatisPlusConfig`

`MybatisPlusInterceptor`：`PaginationInnerInterceptor(MYSQL, maxLimit=500)` + `OptimisticLockerInnerInterceptor`。

### 11.4 `MetaObjectHandlerConfig`

- `insertFill`：createTime/updateTime = now，deleted = 0。
- `updateFill`：updateTime = now。

### 11.5 `MinioConfig`

`MinioClient` Bean，读取 `minio.endpoint / access-key / secret-key`。

### 11.6 `RestTemplateConfig`

单 Bean `RestTemplate`（无自定义超时；按需在调用方设置）。

### 11.7 `GlobalExceptionHandler`

`@RestControllerAdvice`，按优先级：
1. `BusinessException` → warn + `ResultVO.error(code, msg, data?)`
2. `MethodArgumentNotValidException` → 字段:错误 拼接 → `PARAM_INVALID`
3. `DataAccessException` → cause 是 `SQLIntegrityConstraintViolationException` 返 `DUPLICATE_KEY`，否则 `SYSTEM_ERROR`
4. `RuntimeException` → `ResultVO.bizFail(...)`
5. `Exception` → `ResultVO.systemError(...)`

---

## 12. RAG 知识库子系统

### 12.1 包结构

```
com.qy.dch.rag/
├── chunk/    ChunkService / ChineseTextAnalyzer / IdGenerator
├── config/   RagProperties / DocumentParserProperties / OcrProperties
│             AsyncConfiguration / ElasticsearchConfig / EmbeddingConfig
├── embed/    EmbeddingService
├── model/    DocumentChunk / ParsedDocument / RagDocument / SearchResult
├── parser/   DocxParserService / DocxMixedParserService / DocxTableParserService / OcrService
├── search/   HybridSearchService
└── store/    EsVectorStore
```

### 12.2 分块（ChunkService）

按 `content.length()` 三档：
- `len ≤ shortThreshold(128)` → 单 `short` chunk
- `≤ mediumThreshold(512)` → `chunkBySentence`：按句拼接，累计 ≤ `mediumSize(256)` 一片；超长单句调 `chunkByFixedSize`
- `> 512` → `chunkBySentenceWithOverlap`：累计 ≤ `longSize(512)` 一片，新片开头补 `overlap(64)` 字符的句子（`findOverlapStart` 反向累加句长）

`createChunk`：清洗 `\s+ → " "`，id=`docId_chunk_N`，chunkType ∈ short/medium/long。

`ChineseTextAnalyzer.splitBySentence` 正则：`[。！？；\n]+|(?<=[。！？；])(?=[^\s])`。

### 12.3 向量化（EmbeddingService）

- 依赖：`embeddingRestTemplate` + `RagProperties`
- `embedBatch(texts)`：切批（batchSize=32），POST `{baseUrl}/embed` body `{texts, normalize:true}`，期待 `{code:1, data.embeddings:float[][]}`；失败按 `retryCount=3` 间隔 `retryDelayMs=5000` 递归重试
- `embed(text)`：单条便捷
- `isAvailable()`：GET `/health`，body 含 "ok" 即可用

### 12.4 ES 存储（EsVectorStore）

固定 mapping：

```jsonc
{
  "settings": { "shards": 3, "replicas": 1 },
  "mappings": {
    "properties": {
      "chunk_id":     { "type": "keyword" },
      "doc_id":       { "type": "keyword" },
      "chunk_index":  { "type": "integer" },
      "content":      { "type": "text", "analyzer": "ik_max_word" },
      "embedding":    { "type": "dense_vector", "dims": 1024 },
      "title":        { "type": "text", "analyzer": "ik_smart" },
      "publish_time": { "type": "date" },
      "category":     { "type": "keyword" },
      "indexed_at":   { "type": "date" }
    }
  }
}
```

- `ensureIndex()`：不存在则创建
- `bulkIndex(chunks)`：BulkRequest 装入 `chunk_id`(IndexRequest.id)，写入上述字段 + `indexed_at=Instant.now()`；`hasFailures` 警告；返回成功的 docId 去重 `LinkedHashSet`

### 12.5 混合检索（HybridSearchService）

- `bm25Search(q, topK)`：`match(content,q)`，size=topK，提取上面 keyword 字段，bm25Score=hit.score，rank 0..n-1
- `vectorSearch(qVec, topK)`：painless `cosineSimilarity(params.query_vector, 'embedding') + 1.0`，包在 `functionScoreQuery(matchAll, scriptFunction)`，vectorScore=hit.score
- `hybridSearch(query, topK)`：
  1. embed(query) → 失败降级为 `bm25Search`，返回 `{results, totalHits, searchMode:"bm25"}`
  2. 各取 `topK*2`，RRF 融合：`rrfScore = weight / (rrfK + rank_index + 1)`，BM25 权 0.3、向量 0.7、rrfK=60；`LinkedHashMap` 保插入序，BM25 先入；vector 用 `putIfAbsent` 不覆盖元数据
  3. 按 `finalScore` 降序截 topK，重排 rank
  4. 返回 `{results, totalHits=合并 key 数, searchMode:"hybrid"}`

### 12.6 文档解析

| 类 | 功能 |
|---|---|
| `DocxParserService.parseSimpleDocx(path)` | POI `XWPFDocument.getParagraphs` → `\n` 连接；type=`text`，id=`docx_<uuid16>` |
| `DocxMixedParserService.parseMixedDocx(path)` | 段落 + 嵌入图片（条件 OCR），用 `[图片内容]...[/图片内容]` 包裹；type=`mixed` |
| `DocxMixedParserService.extractImages(path)` | 拿所有 `XWPFPicture` 字节 |
| `DocxTableParserService.parseTableDocx` | 表格用 `[表格N]...[/表格N]`，行内 `|` 分隔，行间 `\n` |
| `OcrService` | Tesseract4J + ThreadLocal；datapath/language/pageSegMode/ocrEngineMode 来自 OcrProperties；`recognizeText / recognizeTextAsync(@Async("ocrExecutor")) / recognizeTextBatch / isAvailable` |

`AsyncConfiguration.ocrExecutor`：ThreadPoolTaskExecutor，core=`rag.ocr.threadPoolSize=4`，max=8，queue=100，keepAlive=60，prefix `ocr-`。

### 12.7 `RagService` 行为

- 内部 `AtomicBoolean indexingRunning` 保护并发，已运行则抛 `BusinessException(RAG_INDEXING_RUNNING)`
- `triggerIndexing(start, end)` 启 `new Thread` 跑 `doIndexing(ids)`
- `doIndexing(ids)`：每个 sid 取文本 → metadata{title, publish_time, category} → chunk → embed → `esVectorStore.bulkIndex` → `updateIndexedStatusById`；空内容/空切片直接置 indexed 跳过
- `search(query, topK, hybrid)`：默认 hybrid=true，topK=`rag.search.defaultTopK=10`
- `getIndexLog(...)`：占位 0/[]
- `scheduledIndexing()`：`ensureIndex` 然后挑 yesterday 未索引 ID
- `uploadAndIndex(file, withOcr)`：校验非空 + `.docx` + 大小 ≤ 50MB → 临时文件 → docId=`upload_<uuid16>` → 写 rag_document(pending) → 解析(`DocxMixedParserService` 或 `DocxParserService`) → chunk → embed → ensureIndex → bulkIndex → 更新 `indexed/chunks` 或异常 `failed`
- `parseOnly(file, withOcr)`：解析后返回 content+metadata
- `getDocumentStatus(docId)`：查 rag_document

---

## 13. 定时任务

| Bean | Cron | 行为 |
|---|---|---|
| `RagIndexingTask` | `0 0 3 * * ?`（每日 03:00） | `ragService.scheduledIndexing()` |
| `DailyAnalysisTask` | `0 0 2 * * ?`（每日 02:00） | `eventAnalysisService.analyzeReportsByDate(yesterday, yesterday)` |

DchApplication 启用 `@EnableScheduling`。

---

## 14. 工具类

### 14.1 `CategoryClassifier`

静态 `classify(sendUnitName, content)`，优先级：

1. `sendUnitName` 匹配 `.*HZ\d+报.*` → `HZ报`
2. `content` 含 `JZX` → `JZ报`
3. `content` 含 `入网QB` → `开源信息`
4. 否则 → `未分类`

与 §5.2 的 `category` 回填 SQL 完全等价。

---

## 15. 外部依赖

### 15.1 算法服务

| 路径 | 方法 | body | 返回核心字段 | 调用方 |
|---|---|---|---|---|
| `/extract` | POST | `{text, origin_text_id}` | `{code, data:{events, labels, entities}}` | ExtractionService |
| `/eventSplit` | POST | `{text}` | `{code, data:{events:[{eventTime, eventLocation, eventContent, eventAnalysis}]}}` | EventAnalysisService |
| `/fusion/create` | POST | `{reports:[{id, title, content, times, type, extractionResult}], fusionType, customTitle}` | `{code, data:{fusionId, title, summary, content, modelUsed, createTime, updateTime, timeline, entities, labels}}` | FusionService |
| `/api/target/analyze` | POST | `AlgorithmAnalyzeRequest` | `AlgorithmAnalyzeResponse` | TargetAnalysisService |
| `/api/target/fusion` | POST | `AlgorithmFusionRequest` | `AlgorithmFusionResponse` | TargetFusionService |

默认 baseURL：全局 `algorithm.service.url=http://localhost:5001`；TargetAnalysis/Fusion 子模块为 `http://localhost:9203`。**部署时需统一映射到真实服务。**

### 15.2 Embedding 服务

| 路径 | 方法 | body | 返回 |
|---|---|---|---|
| `/embed` | POST | `{texts:[...], normalize:true}` | `{code:1, data:{embeddings:float[][]}}` |
| `/health` | GET | — | body 含 "ok" |

### 15.3 Elasticsearch 7.17

- 索引：`xianwei_docs`，shards=3 / replicas=1
- 需要中文分词插件：`ik_max_word / ik_smart`
- dense_vector 维度 1024（与 bge-large-zh-v1.5 对齐）

### 15.4 MinIO

- 桶 `xianwei-images`
- 路径前缀：`images/yyyyMMdd/<filename>`
- 前端读图：`{minio.endpoint}/{bucket}/...`（`/api/uygur/config` 返回 `minioPrefix`）

### 15.5 Tesseract OCR

- 语言包：`chi_sim.traineddata`（也支持 `chi_sim+eng`）
- datapath 默认 `/usr/share/tesseract-ocr/4.00/tessdata`
- pageSegMode=1（自动 OSD），ocrEngineMode=1（LSTM）

---

## 16. 构建、部署与运维

### 16.1 本地构建

```bash
# 用项目自带 wrapper（JDK 8）
./mvnw -DskipTests clean package

# 离线
./mvnw -DskipTests -o clean package
```

输出：`target/uygur-project-0.0.1-SNAPSHOT.jar`。

### 16.2 启动

```bash
JAVA_OPTS="-Xms1g -Xmx2g -Dfile.encoding=UTF-8"
java $JAVA_OPTS -jar uygur-project-0.0.1-SNAPSHOT.jar \
  --spring.profiles.active=prod \
  --DB_HOST=... --DB_PORT=... --DB_NAME=... --DB_USERNAME=... --DB_PASSWORD=... \
  --ALGORITHM_SERVICE_URL=http://algo:5001 \
  --MINIO_ENDPOINT=http://minio:8522 \
  --ES_HOST=es --ES_PORT=9200 \
  --EMBEDDING_BASE_URL=http://embed:5002 \
  --TESSERACT_DATAPATH=/opt/tess/tessdata
```

### 16.3 健康检查与监控

- `GET /actuator/health` → status/show-details=never
- Druid 监控：`http://host:8081/druid/`，账号 `admin/admin`（生产应改）
- 日志：`logs/application.log`（`com.qy.dch=debug`）

### 16.4 数据库初始化顺序

1. 建库 `uygur_project`（utf8mb4 / utf8mb4_0900_ai_ci）
2. 执行 §5 所有 CREATE TABLE（顺序无强依赖；推荐：`text_type → origin_text → extraction_result → event_analysis → fusion_report → target_analysis → target_alias → target_fusion → rag_document`）
3. 插入 `text_type` 占位行（id=2 / name="未分类"）
4. 视情况 INSERT 别名种子数据
5. 启动应用前确保 ES 索引允许自动创建（或预创建 `xianwei_docs`，应用启动后 `ensureIndex` 也会建）

### 16.5 离线包交付物清单

- 后端 jar（含全部 lib，spring-boot fat jar）
- application.yml 模板（环境变量化）
- DDL（单 SQL：`docs/sql/2026-06-14-system-integration.sql` + §5 历史合集）
- tessdata 语言包
- ES 中文分词器（ik-7.17.x）
- Embedding 服务镜像 / 模型权重（bge-large-zh-v1.5）
- 算法服务镜像
- MinIO 二进制 + 初始桶脚本

### 16.6 内场离线生产环境部署（2026-06-15 验证）

**部署包**：`production-deploy.tar.gz`（3.1GB）

**SHA-256**：`513ec957afb240869eba5c95ca8a60dc6e978bc82f03dcbce099615c16e78944`

#### 16.6.1 包内容结构

```
176-deploy/
├── manage.sh                        # 一键部署脚本（交互式菜单）
├── docker-compose.yml               # 编排：ES + embedding + backend
├── README.md                        # 快速开始
├── backend/
│   ├── backend-image.tar           # xianwei-backend:latest（209.5MB）
│   ├── uygur-project-0.0.1-SNAPSHOT.jar
│   └── tessdata/                   # OCR 语言包
├── containers/
│   ├── es-7.17.15.tar             # Elasticsearch 镜像（386MB）
│   ├── ik-7.17.15.zip             # 中文分词插件
│   ├── embedding-image.tar        # xianwei-embedding:latest（1.2GB）
│   ├── bge-large-zh-v1.5/         # 向量模型（打入镜像，此为构建源）
│   └── wheels-rebuild/            # Python 依赖（离线 wheels，arm64）
└── docs/
    └── api-examples.md
```

#### 16.6.2 环境要求

| 项目 | 要求 |
|------|------|
| OS | Linux（测试环境：CentOS/Debian，aarch64） |
| Docker | ≥ 20.10（支持 `docker compose` v2 或 `docker-compose` v1） |
| 内存 | ≥ 8GB（ES 4GB + embedding 2GB + backend 1GB） |
| 磁盘 | ≥ 20GB（镜像 + 数据卷） |
| 网络 | 离线（所有依赖已打包） |

#### 16.6.3 部署步骤（傻瓜流程）

##### 1. 上传到目标机器

```bash
# 校验完整性
sha256sum -c production-deploy.tar.gz.sha256
# 应输出：production-deploy.tar.gz: OK

# 解压
tar xzf production-deploy.tar.gz
cd 176-deploy
```

##### 2. 执行部署

```bash
./manage.sh             # 交互菜单（推荐）
# 或
./manage.sh install     # 直接安装
```

**智能检测**：脚本会自动检测已有组件（ES/embedding/backend），复用而非重建：
- 端口 9200 已占用 → 跳过 ES，复用现有实例
- 镜像已存在 → 直接启动容器，不重复加载
- 容器已停止 → 直接 start，不重新创建

##### 3. 验证服务

```bash
./manage.sh status

# 期望输出：
# 服务                 容器状态 端口   健康检查
# xianwei-es           运行中   9200   ✓ 健康
# xianwei-embedding    运行中   5002   ✓ 健康
# xianwei-backend      运行中   8081   ✓ 健康
```

##### 4. 测试接口

```bash
# Backend 健康检查
curl http://localhost:8081/actuator/health

# RAG 索引状态
curl http://localhost:8081/api/rag/index/status

# Embedding 向量化
curl -X POST http://localhost:5002/embed \
  -H 'Content-Type: application/json' \
  -d '{"text":"测试维吾尔语报文"}'

# 触发 RAG 索引（替换实际日期范围）
curl -X POST http://localhost:8081/api/rag/index/trigger \
  -H 'Content-Type: application/json' \
  -d '{"startDate":"2024-01-01","endDate":"2024-12-31"}'

# 语义检索
curl -X POST http://localhost:8081/api/rag/search \
  -H 'Content-Type: application/json' \
  -d '{"query":"测试关键词","topK":10}'
```

#### 16.6.4 运维命令

```bash
cd ~/176-deploy

./manage.sh status      # 查看服务状态
./manage.sh logs        # 查看所有日志（或指定服务名）
./manage.sh restart     # 重启全部服务
./manage.sh stop        # 停止
./manage.sh start       # 启动
./manage.sh uninstall   # 卸载（清理容器 + 镜像，保留数据）
./manage.sh reinstall   # 重装（清理全部数据）
```

**高级**：
- `./manage.sh logs xianwei-backend` — 只看 backend 日志
- `./manage.sh update` — 热更新镜像（不清数据）

#### 16.6.5 关键配置说明

**Embedding 服务**：
- 模型：BAAI/bge-large-zh-v1.5（1.2GB，中文向量化）
- 依赖版本锁定：`transformers==4.40.0` + `torch==2.5.1`（避免 CVE-2025-32434）
- 端口：5002
- 健康检查：`GET /health`

**Backend**：
- JVM 参数：`-Xms1g -Xmx2g`（docker-compose 已配置）
- 数据库：需外部 MySQL 8（环境变量 `DB_HOST` / `DB_PORT` / `DB_NAME` / `DB_USERNAME` / `DB_PASSWORD`）
- Tesseract：内置 `chi_sim` + `eng` 语言包，路径 `/opt/tess/tessdata`

**Elasticsearch**：
- 版本：7.17.15
- 插件：ik 中文分词（自动安装）
- 索引：`xianwei_docs`（应用启动时自动创建）
- 数据持久化：`/data/es`（docker volume）

#### 16.6.6 常见问题

**Q1：Embedding 健康检查超时**  
A：bge 模型加载需 1-2 分钟，等待后再 `curl http://localhost:5002/health`。查看日志：`docker logs xianwei-embedding`。

**Q2：端口冲突（9200/5002/8081 已占用）**  
A：脚本自动检测并跳过（复用现有服务）。若需替换，先停止冲突进程/容器。

**Q3：内存不足（ES OOM）**  
A：修改 `docker-compose.yml` 降低 ES heap：`ES_JAVA_OPTS="-Xms2g -Xmx2g"`。

**Q4：Backend 启动失败（连不上 MySQL）**  
A：检查 `docker-compose.yml` 中 `DB_HOST`/`DB_PORT` 等环境变量是否正确。

**Q5：如何更新 Backend 代码？**  
A：
1. 本地重新编译：`mvn clean package -DskipTests`
2. 替换 `backend/uygur-project-0.0.1-SNAPSHOT.jar`
3. 重建镜像：`cd backend && docker build -t xianwei-backend:latest .`
4. 导出：`docker save -o backend-image.tar xianwei-backend:latest`
5. 拷贝到生产环境替换旧 tar，执行 `./manage.sh update`

#### 16.6.7 已知限制

- **架构绑定**：当前 embedding 镜像为 `arm64`（aarch64）。x86_64 环境需重新构建，更换 wheels 平台参数为 `manylinux2014_x86_64`。
- **MySQL 外部依赖**：未打包 MySQL，需提前准备（建议 8.0.33+，utf8mb4）。
- **算法服务未含**：属性抽取 / 事件分析 / 目标分析 / 融合算法服务（5001/9203）需单独部署。

---

---

## 17. 前端业务对接需求（03-search.html / 05-3-target-fusion.html）

> 本节折叠自 `docs/20260613系统对接.md`（已可归档）。需求源自 `xwSystem/xwFrontend/05-3-target-fusion.html`、`xwSystem/xwFrontend/03-search.html`，与 §10 的接口实现一一对应；如有差异以 §10 / 源码为准。

### 17.1 03-search.html（动态信息页面）

#### 17.1.1 多级目录结构

- **一级目录**：`category` 字段（开源信息、HZ报、JZ报、未分类，4 个固定分类）。
- **二级目录**：按 `origin_text.send_unit_name` 字段分组（如 `HZ123报`）。
- **三级目录**：具体报文列表。
- **数量统计**：每个节点上挂 `count`，前端直接渲染。

接口：`GET /api/directory/tree` → 树形结构，每节点 `{name, type, count, children}`（实现见 §10.4 `DirectoryController`）。

#### 17.1.2 报文分类规则（插入时自动分类）

优先级 **HZ报 > JZ报 > 开源信息 > 未分类**：

| 分类 | 判断条件 |
|------|---------|
| HZ报 | `send_unit_name` 匹配正则 `HZ\d+报` |
| JZ报 | `content` 包含 `JZX` |
| 开源信息 | `content` 包含 `入网QB` |
| 未分类 | 以上都不满足 |

实现位置：`UygurServiceImpl.insert(...)` 中调用 `CategoryClassifier.classify(...)` 后写入 `origin_text.category`（详见 §14 工具类）。

#### 17.1.3 报文筛选查询

- 一级目录（category）+ 二级目录（sendUnit）+ 关键词（keyword，纯 SQL `LIKE %keyword%` 匹配 content/title）+ 时间范围（startDate / endDate）。
- 一级目录点击 → 仅按 category 查询；二级目录点击 → 按 category + sendUnit；filter-bar 是叠加过滤。

接口：`GET /api/uygur/filter?category=...&sendUnit=...&keyword=...&startDate=...&endDate=...` → 报文列表（见 §10.12 `UygurController`）。

#### 17.1.4 前端"属性抽取"面板已移除

前端 03-search.html 已删除"属性抽取"按钮；后端 `ExtractionController`（§10.7）保留，供其他页面/调试使用。

### 17.2 05-3-target-fusion.html（目标融合页面）

#### 17.2.1 高级筛选（多关键词 OR + 时间 AND）

- 关重目标关键词、关重区域关键词之间是 **OR** 关系；时间范围是 **AND** 关系。
- 整体逻辑：`时间范围内 AND (含目标关键词 OR 含区域关键词)`。

示例 SQL：

```sql
SELECT * FROM origin_text
WHERE create_time BETWEEN '2024-01-01' AND '2024-12-31'
  AND ( content LIKE '%目标A%' OR content LIKE '%目标B%'
     OR content LIKE '%区域A%' OR content LIKE '%区域B%' );
```

接口：

```
POST /api/uygur/filter/advanced
Body: {
  "targetKeywords": ["目标A", "目标B"],
  "regionKeywords": ["区域A", "区域B"],
  "startDate":      "2024-01-01",
  "endDate":        "2024-12-31"
}
```

#### 17.2.2 目标分析（调用算法服务）

**算法接口（外部依赖）**：

```
POST http://algorithm-service:9203/api/target/analyze
Request : { messageTime, content, url }
Response: {
  code: 1, msg: "ok",
  data: [
    { regionName, targetName, targetType, foundTime, description, attachmentUrl }, ...
  ]
}
```

一份报文可抽取出多个目标（每个目标对应一段描述 + 一张附件图 URL）。

**后端处理**（`TargetAnalysisServiceImpl`）：

1. 同步：`POST /api/target/analyze/{originTextId}` → 单条调用算法 → 入 `target_analysis`。
2. 异步：`POST /api/target/analyze/batch/async` body `{startTime, endTime}` → 返回 `{taskId, totalCount}`；前端轮询 `GET /api/target/analyze/task/{taskId}` 查进度，必要时 `POST /api/target/analyze/task/{taskId}/stop` 中断。
3. 同步批量（少量调试用）：`POST /api/target/analyze/batch?startTime=...&endTime=...`。

入库前需走 §17.2.3 别名映射。

#### 17.2.3 别名映射

- 部分目标存在多名（如 `xxx弹药库` → `哈尔科夫弹药库`）。
- 表 `target_alias`：`alias`（UNIQUE）→ `canonical_name`。
- `TargetAliasService.resolveAlias(name)` 在算法返回后落库前调用，统一映射到标准名。
- 维护接口：`POST/GET/PUT/DELETE /api/target/alias[...]`（详见 §10.10）。

#### 17.2.4 目标融合（调用算法服务）

**算法接口**：

```
POST http://algorithm-service:9203/api/target/fusion
Request : {
  targets: [
    { regionName, targetName, foundTime, description }, ...
  ]
}
Response: {
  code: 1, msg: "ok",
  data: {
    fusionResults: [{ analysis, difference }],
    regionFusionResult: "..."
  }
}
```

**后端流程**（`TargetFusionServiceImpl.fuseTargets`）：

1. 由前端传入勾选的多个 `targetName` 列表。
2. `target_analysis` 中查这些名字下的全部分析记录，构造 `AlgorithmFusionRequest.targets`。
3. POST 算法 → 落 `target_fusion`（`target_names` 写 JSON 数组，`source_count` = 输入分析记录条数，`is_fused = 1`）。
4. `target_analysis.is_fused` 同步置 1。
5. 返回组装好的 `TargetFusionResultDTO`。

接口：`POST /api/target/fusion`，Body `["哈尔科夫弹药库", "某某机场"]`。

#### 17.2.5 融合状态标记

- `target_fusion.is_fused`、`target_analysis.is_fused` 两个字段；融合完成后双向更新。
- 列表查询 `GET /api/target/analysis/list?regionName=...&targetName=...` 返回的目标会带 `isFused` 标记，前端据此渲染"已融合"角标。

#### 17.2.6 融合结果展示

前端"融合生成目标报"弹窗分两部分：

- 上半（来自 `target_analysis`）：targetName / sendUnit / foundTime / description / sourceCount / sources URL。
- 下半（来自 `target_fusion`）：analysis（综合描述）/ difference（变化分析）/ regionFusionResult（区域融合）。

后端一次性返回完整数据包：

```
GET /api/target/fusion/detail?targetName=哈尔科夫弹药库
→ {
  basicInfo: {
    targetName, sourceCount,
    sources: [{ sendUnit, foundTime, description, url }]
  },
  fusionResult: {
    analysis, difference, regionFusionResult
  }
}
```

`TargetFusionServiceImpl.getFusionDetail(...)` 关键点：

- `basicInfo.sources` 由 `TargetAnalysisMapper.selectByFilter(null, targetName)` 已 JOIN `origin_text` 直接拿 `sendUnitName`。
- `fusionResult` 取 `target_fusion` 中**最新一条**包含该名字的记录，命中条件用 `LIKE CONCAT('%', "<targetName>", '%')`，前后带引号防止子串误中（needle = `"\"" + targetName + "\""`）。
- 该名字无任何分析记录时返回 `null`；有分析无融合时三个字段返回空字符串。

#### 17.2.7 导出功能（A1 PDF + A2 Word）

融合弹窗中提供 Word 与 PDF 两种导出格式：

- 端点：`GET /api/target/fusion/export?targetName=<目标>&format=docx|pdf`
- Word：Apache POI 5.2.5 生成真实 `.docx`（OOXML，可用 Office/WPS 打开）
- PDF：iText 5.5.13.3 + Noto Sans SC 字体（`src/main/resources/fonts/NotoSansSC-Regular.ttf`），支持中文 + 图片嵌入
- 图片来自 MinIO，下载失败时降级为 `[图片：<URL>]` 文本占位（RestTemplate 2s 连接 / 5s 读超时）
- 文件名：`<目标名>_融合报告.docx` / `.pdf`，HTTP 头使用 RFC 5987 `filename*=UTF-8''` 编码确保中文不乱码
- 服务实现：`DocumentExportServiceImpl`，字体通过 `PdfFontProvider` Bean 在启动期 `@PostConstruct` 一次性加载
- 前端入口：`05-3-target-fusion.html` "融合生成目标报"弹窗底部 "导出 Word" / "导出 PDF" 按钮，多目标场景通过隐藏 iframe 串行触发下载

### 17.3 数据量与异步策略

| 分类 | 季度量 | 日均 | 极限 |
|------|--------|-----|------|
| 开源信息 / HZ报 / JZ报 | 8K-10K 各 | ~110 | 2W-5W |
| 未分类 | 少量 | - | - |

- `origin_text` 上 `category` + `send_unit_name` 复合索引（已建）。
- 批量目标分析必走异步任务（见 §17.2.2）；单报文同步秒级返回。

### 17.4 待确认问题（已落实）

| 原问题 | 当前实现/结论 |
|--------|---------------|
| 融合详情前端复用 vs 后端整包 | 选 B，`/api/target/fusion/detail` 返完整 `{basicInfo, fusionResult}` |
| 批量分析是否异步 | 是，`/analyze/batch/async` + taskId 轮询 |
| 别名表初始数据 | 通过 `POST /api/target/alias` 在线维护，不预置 |
| 算法服务地址 | 全局 `http://localhost:5001`；目标分析/融合走 `http://localhost:9203`（`algorithm.service.url` 可独立覆写） |
| 关键词配置存储 | 前端 `localStorage`，后端不持久化 |

---

## 18. 复刻 Checklist

按下列顺序操作即可独立复现一套可用后端：

1. **基础设施**：MySQL 8、Elasticsearch 7.17（含 ik 插件）、MinIO、Embedding 服务（5002）、算法服务（5001 / 9203）、Tesseract + chi_sim。
2. **建库**：按 §5 全部 DDL 执行；插 `text_type` 「未分类」占位行（id=2）。
3. **构建后端**：`./mvnw -DskipTests -o clean package`（JDK 8）。
4. **配置**：覆盖环境变量（§3.2），生产换 Druid 监控账号。
5. **启动**：`java -jar ...`。
6. **冒烟**：
   - `GET /actuator/health` → UP
   - `GET /api/uygur/config` → 返回 minioPrefix
   - `GET /api/category/tree` → 至少有「未分类」
   - `POST /api/target/alias` → 插入测试别名
   - `POST /extraction/extract?originTextId=...` → 算法链路联调
   - `POST /api/rag/index/trigger` → RAG 索引联调
7. **接前端**：CORS 已全开（`/**` allowedOrigins=*）。
8. **定时任务**：02:00 事件分析、03:00 RAG 索引会自动跑。

---

> 本文件即为 xwBackend 的**单一权威说明**，结合 `docs/sql/2026-06-14-system-integration.sql` 与仓库源码即可整体复刻。
>
> 以下文件的内容已并入本文档，原文件已删除，仅在 git 历史中可查：
> - `docs/20260613系统对接.md`（前端业务对接需求 → §17）
> - `docs/20260613系统对接_原始版.md`（原始草稿）
>
> 后续如新增能力，请同步本文件，避免再次出现多份并行说明。
