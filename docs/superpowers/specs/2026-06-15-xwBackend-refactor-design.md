# xwBackend 全面重构设计

**主题**：献微系统后端的全面代码质量重构，覆盖分层、模块化、HTTP 客户端、异常体系、SQL 规范五个核心维度。

| 模块 | 范围 | 主要收益 |
|------|------|----------|
| M1 | UygurMapper 拆分（846 行 → 3 个 Mapper） | 降低耦合，单一职责 |
| M2 | AlgorithmClient 统一 HTTP 客户端 | 消除 5 处分散 HTTP 调用，统一配置 |
| M3 | 路径与命名规范化 | RESTful、kebab-case、删除调试 Controller |
| M4 | 异常处理体系改造 | 删除 ~85 处 try-catch 模板 |
| M5 | SQL 迁移与 MyBatis 规范化 | 复杂 SQL 迁 XML，启用动态 SQL |

**统一原因**：5 个维度相互关联——拆 Mapper（M1）就要重写 Service 注入；统一 HTTP 客户端（M2）后才能把兜底逻辑移除（M4）；路径修改（M3）需要同步 OpenAPI；SQL 迁移（M5）跟 Mapper 拆分（M1）必须同步。整组改动一次性落地能最大化减少回归测试成本。

**前置约定**：
- 项目尚未正式上线，**不做对外 API 向后兼容**；前端同步修改
- 重构期间**接口语义与字段不变**，只改路径前缀与命名风格
- 全程**保持 Lombok 风格**（项目已统一）
- 使用 `@RequiredArgsConstructor` 构造器注入作为唯一 DI 方式

---

## 1. 背景与现状

### 1.1 代码规模

- Java 文件 117 个，总行数 11,081 行
- 单文件最大：`UygurServiceImpl.java`（917 行）、`UygurMapper.java`（846 行）、`UygurController.java`（557 行）
- 已统一 Lombok 风格（昨日完成）

### 1.2 已识别的核心问题

| 问题 | 严重度 | 位置 | 现象 |
|------|--------|------|------|
| 上帝类 UygurMapper | 🔴 | `mapper/UygurMapper.java` | 58 个方法，89 条 SQL，被 8 个 Service 共享 |
| 分层违规 | 🔴 | `service/RagService`、`ExtractionService` 等 | Service 接口返回 ResultVO；TestController 直接注入 Mapper |
| HTTP 客户端分裂 | 🔴 | 5 个 Service | HttpURLConnection vs RestTemplate 混用；URL 默认值不一致（5001 vs 9203） |
| DI 方式混乱 | 🔴 | 全项目 | `@Autowired` 22 处、`@RequiredArgsConstructor` 18 处、`@Resource` 19 处并存 |
| 路径前缀不一致 | 🔴 | `ExtractionController`、`TestController` | 缺 `/api` 前缀 |
| 异常处理粗暴 | 🟡 | 全项目 | 21 处裸 `RuntimeException`；85 处 `catch (Exception e)`；Controller 重复 try-catch 模板 |
| SQL 全部用注解 | 🟡 | 9 个 Mapper | 89 条 SQL 用 `@Select`，复杂 SQL 用 `<script>` 字符串拼接 |
| RAG 模块边界模糊 | 🟢 | `com/qy/dch/rag/` | 20 个文件自成子系统但混在主包 |

### 1.3 不在本次范围内

- `text_type` 与 `origin_text.category` 的冗余清理（独立议题，需要数据迁移）
- 性能优化（索引、缓存、连接池）
- 安全加固（鉴权、CORS、敏感信息加密）
- 前端代码

---

## 2. 总体架构

### 2.1 包结构（重构后）

```
com/qy/dch/
├── common/                      # 通用基础设施
│   ├── ErrorCode.java               # 扩充错误码枚举
│   ├── BusinessException.java
│   ├── AlgorithmServiceException.java   # 新增
│   └── ResultVO.java
├── config/                      # 配置（不变）
├── controller/                  # 12 个 Controller，统一规范
│   └── ...
├── domain/                      # 删除（合并到 dto）
├── dto/                         # DTO（不变）
├── entity/                      # 数据库实体（不变）
├── mapper/                      # 9 个 Mapper（UygurMapper 拆分为 3 个）
│   ├── OriginTextMapper.java        # 拆出
│   ├── TextTypeMapper.java          # 拆出
│   ├── ImportMapper.java            # 拆出
│   └── ...
├── request/                     # 请求对象（不变）
├── service/
│   ├── client/                  # 新增：外部服务客户端
│   │   ├── AlgorithmClient.java
│   │   ├── AlgorithmClientImpl.java
│   │   └── dto/
│   ├── impl/                    # Service 实现，不返回 ResultVO
│   └── *.java                   # Service 接口
├── rag/                         # RAG 子系统（不动内部）
├── task/                        # 定时任务（不变）
└── util/
    └── CategoryClassifier.java
```

### 2.2 分层规则（红线）

```
   ┌──────────────────────────────────────────────────────────┐
   │                       Controller                          │
   │   - 参数校验（@Valid + Bean Validation）                  │
   │   - 调用 Service                                          │
   │   - 包装 ResultVO                                         │
   │   - 不写 try-catch（异常由 GlobalExceptionHandler 处理）  │
   └──────────────────────────────────────────────────────────┘
                                ↓
   ┌──────────────────────────────────────────────────────────┐
   │                        Service                            │
   │   - 业务逻辑                                              │
   │   - 调用 Mapper / AlgorithmClient / RagService            │
   │   - 抛 BusinessException（带 ErrorCode）                 │
   │   - 接口签名不含 ResultVO                                 │
   └──────────────────────────────────────────────────────────┘
                                ↓
   ┌─────────────────┐     ┌──────────────────────────────────┐
   │     Mapper      │     │       AlgorithmClient              │
   │  - 数据访问      │     │  - 外部 HTTP 调用                  │
   │  - 单一 SQL 表   │     │  - 统一配置、超时、错误处理        │
   └─────────────────┘     └──────────────────────────────────┘
```

**红线检查表**：
- ❌ Controller 不直接注入 Mapper
- ❌ Service 接口不返回 ResultVO
- ❌ Service 不直接使用 RestTemplate/HttpURLConnection 调外部服务
- ❌ Controller 不写 try-catch 包装 ResultVO.error
- ❌ 不裸抛 RuntimeException

---

## 3. M1：UygurMapper 拆分

### 3.1 拆分清单

| 新 Mapper | 主要操作的表 | 行数预估 | 方法数 | 使用方 |
|-----------|------------|---------|--------|--------|
| `OriginTextMapper` | `origin_text` | ~400 | ~30 | 8 个 Service |
| `TextTypeMapper` | `text_type` | ~250 | ~15 | CategoryService、DashboardService、OriginTextService |
| `ImportMapper` | `origin_text` + `text_type`（写入） | ~150 | ~10 | OriginTextService（仅导入路径） |

### 3.2 方法归属规则

- `OriginTextMapper`：所有 `SELECT/UPDATE/DELETE FROM origin_text` 的查询和单条/批量修改
- `TextTypeMapper`：所有针对 `text_type` 的查询和分类树操作（`selectAllCategories`、`countReportsByCategory`、`getCategoryByNameAndParent`）
- `ImportMapper`：仅用于 JSONL 批量导入路径的批量 INSERT（`batchInsertOriginText`、首次创建分类时的 INSERT）

### 3.3 影响范围

需要修改注入引用的 Service：
- `UygurServiceImpl` → `OriginTextServiceImpl`（含改名，详见 M3）
- `DirectoryServiceImpl`、`DashboardServiceImpl`、`CategoryServiceImpl`、`FusionServiceImpl`、`ExtractionServiceImpl`、`EventAnalysisServiceImpl`、`TargetAnalysisServiceImpl`
- `RagServiceImpl`

### 3.4 SQL 内容不变

拆分仅迁移方法位置，**SQL 字符串内容不变**（这一步先不动 SQL，等 M5 再统一规范化）。

---

## 4. M2：AlgorithmClient 统一 HTTP 客户端

### 4.1 接口设计

```java
public interface AlgorithmClient {
    /** 事件抽取 - POST /extract */
    ExtractionResult extract(String content, String originTextId);

    /** 报文融合 - POST /fusion/create */
    FusionResult fuse(FusionCreateRequest request);

    /** 目标分析 - POST /api/target/analyze */
    AlgorithmAnalyzeResponse analyzeTarget(AlgorithmAnalyzeRequest request);

    /** 目标融合 - POST /api/target/fusion */
    AlgorithmFusionResponse fuseTarget(AlgorithmFusionRequest request);

    /** 事件分析 - POST /eventSplit */
    EventSplitResponse splitEvents(EventSplitRequest request);
}
```

### 4.2 实现要求

- **统一用 RestTemplate**（项目已有 `RestTemplateConfig`），废弃 `HttpURLConnection`
- **URL 配置只在一处注入**：`@Value("${algorithm.service.url}")`，删除其他 4 处分散注入
- **超时设定**：
  - 抽取 / 融合：连接 10s、读取 5min（与原有 `HttpURLConnection` 配置对齐）
  - 目标分析 / 事件分析：连接 10s、读取 60s
- **失败语义**：
  - HTTP 非 2xx → 抛 `AlgorithmServiceException(ALGORITHM_SERVICE_UNAVAILABLE)`
  - 业务 code ≠ 1 → 抛 `AlgorithmServiceException(ALGORITHM_SERVICE_INVALID_RESPONSE)`
  - 超时 → 抛 `AlgorithmServiceException(ALGORITHM_SERVICE_TIMEOUT)`

### 4.3 兜底逻辑迁移

`FusionServiceImpl.createDefaultFusion`（算法服务失败时返回默认融合内容）**从 Service 移除**。如业务确需保留 fallback，由 Controller 层用 `try { } catch (AlgorithmServiceException e) { ... }` 显式处理。

### 4.4 影响范围

5 个 Service 删除 HTTP 模板代码：
- `ExtractionServiceImpl.callAlgorithmService`（98 行）→ 1 行 `client.extract(...)`
- `FusionServiceImpl.callAlgorithmService`（84 行）→ 1 行 `client.fuse(...)`
- `EventAnalysisServiceImpl`（~30 行 HTTP）→ 1 行 `client.splitEvents(...)`
- `TargetAnalysisServiceImpl`（~25 行 HTTP）→ 1 行 `client.analyzeTarget(...)`
- `TargetFusionServiceImpl`（~25 行 HTTP）→ 1 行 `client.fuseTarget(...)`

预计删除 ~250 行重复代码。

---

## 5. M3：路径与命名规范化

### 5.1 路径前缀修正

| 旧路径 | 新路径 |
|--------|--------|
| `/extraction/*` | `/api/extraction/*` |
| `/test/*` | （整体删除，见 5.4） |

### 5.2 RESTful 化与 kebab-case

| 当前 | 改为 |
|------|------|
| `POST /api/category/create` | `POST /api/category` |
| `PUT /api/category/update` | `PUT /api/category/{id}` |
| `DELETE /api/category/delete/{id}` | `DELETE /api/category/{id}` |
| `GET /api/category/detail/{id}` | `GET /api/category/{id}` |
| `POST /api/category/move` | `PATCH /api/category/{id}/parent` |
| `/api/eventAnalysis/*` | `/api/event-analysis/*` |
| `/api/uygur/getTextList` | `/api/origin-text/list` |
| `/api/uygur/addCategory` | `/api/category`（合并到 CategoryController） |
| `/api/uygur/importFromJsonl` | `/api/origin-text/import/jsonl` |
| `/api/uygur/importFromJsonlWithImages` | `/api/origin-text/import/jsonl-with-images` |
| `/api/uygur/text/{sid}` | `/api/origin-text/{sid}` (DELETE) |
| `/api/uygur/text/batchDelete` | `/api/origin-text/batch-delete` |
| `/api/uygur/filter` | `/api/origin-text` (GET，查询参数) |
| `/api/uygur/resetExtracted` | `/api/origin-text/reset-extracted` |
| `/api/uygur/savetext` | `/api/origin-text/import-default` |
| `/api/uygur/config` | `/api/system/config` |
| `/api/uygur/detail/{sid}` | `/api/origin-text/{sid}` (GET) |
| `/api/uygur/category` (GET) | **删除**（与 `GET /api/category/leafs` 重复，前端切换） |
| `/api/uygur/category` (POST) | **删除**（合并到 `POST /api/category`） |
| `/api/uygur/category/{id}` (PUT/DELETE) | **删除**（合并到 `PUT/DELETE /api/category/{id}`） |
| `/api/uygur/category/batchDelete` | **删除**（合并到 `POST /api/category/batch-delete`） |
| `/api/uygur/category/{id}/withTexts` | `DELETE /api/category/{id}?with-texts=true` |
| `/api/uygur/addCategory` | **删除**（合并到 `POST /api/category`） |
| `/api/uygur/text/updateByOldType` | `PATCH /api/origin-text/batch-update-type` |
| `/api/fusion/searchByTarget` | `/api/fusion/search-by-target` |

### 5.3 Controller 与 Service 改名

| 旧名 | 新名 | 理由 |
|------|------|------|
| `UygurController` | `OriginTextController` | "Uygur" 是项目名不是业务域 |
| `UygurService` | `OriginTextService` | 同上 |
| `UygurServiceImpl` | `OriginTextServiceImpl` | 同上 |

### 5.4 删除 TestController

`TestController` 仅用于调试编码问题且违反"Controller 不直接注入 Mapper"原则。**整体删除**，调试需求改用单元测试或 curl。

### 5.5 OpenAPI 同步

`xwSystem/xwBackend/docs/献微系统 - 后端 API (xwBackend).openapi.json` 同步更新：
- 全部新路径
- kebab-case 规范化
- HTTP 方法语义对齐
- 删除 `/test/*` 相关条目

---

## 6. M4：异常处理体系改造

### 6.1 ErrorCode 扩充

新增枚举值：
```java
// 报文相关
ORIGIN_TEXT_NOT_FOUND(2101, "报文不存在"),
ORIGIN_TEXT_ALREADY_EXTRACTED(2102, "报文已抽取"),

// 分类相关
CATEGORY_NOT_FOUND(2201, "分类不存在"),
CATEGORY_HAS_CHILDREN(2202, "分类下存在子节点，无法删除"),
CATEGORY_NAME_DUPLICATE(2203, "分类名称已存在"),

// 导入相关
IMPORT_FILE_EMPTY(2301, "导入文件不能为空"),
IMPORT_FILE_FORMAT_ERROR(2302, "文件格式错误"),

// 算法服务
ALGORITHM_SERVICE_UNAVAILABLE(5001, "算法服务不可用"),
ALGORITHM_SERVICE_TIMEOUT(5002, "算法服务超时"),
ALGORITHM_SERVICE_INVALID_RESPONSE(5003, "算法服务返回数据异常"),
```

### 6.2 异常分类约定

| 异常 | 何时抛 | 处理方 |
|------|--------|--------|
| `BusinessException` | 业务规则违反 | `GlobalExceptionHandler` → `ResultVO.bizFail()` |
| `AlgorithmServiceException`（继承 `BusinessException`） | 算法服务调用失败 | `GlobalExceptionHandler` → `ResultVO.error()` |
| 受检异常（IOException 等） | Service 内捕获后转 `BusinessException` | - |
| 裸 `RuntimeException` | **禁止** | - |

### 6.3 Service 层改造

**"找不到"语义统一为抛异常，而非返回 null**：

```java
// 改造前
public OriginTextDTO getTextById(String sid) {
    return originTextMapper.selectById(sid);  // 可能返回 null
}

// 改造后
public OriginTextDTO getTextById(String sid) {
    OriginTextDTO dto = originTextMapper.selectById(sid);
    if (dto == null) {
        throw new BusinessException(ErrorCode.ORIGIN_TEXT_NOT_FOUND);
    }
    return dto;
}
```

例外：**列表查询返回空集合**（不抛异常）。

### 6.4 Controller 层改造

**删除所有 try-catch 模板**：

```java
// 改造前（典型）
@DeleteMapping("/text/{sid}")
public ResultVO deleteText(@PathVariable String sid) {
    log.info("deleteText: sid={}", sid);
    try {
        boolean success = uygurService.deleteText(sid);
        if (success) {
            return ResultVO.success("删除成功");
        } else {
            return ResultVO.error("删除失败，报文不存在");
        }
    } catch (Exception e) {
        log.error("删除报文失败", e);
        return ResultVO.error("删除失败: " + e.getMessage());
    }
}

// 改造后
@DeleteMapping("/{sid}")
public ResultVO deleteText(@PathVariable @NotBlank String sid) {
    originTextService.deleteText(sid);  // 失败时抛 BusinessException
    return ResultVO.success();
}
```

**参数校验改用 Bean Validation**：
- `@NotBlank` / `@NotNull` / `@Size` 等注解
- 在 Controller 方法上加 `@Validated`
- 校验失败由 `GlobalExceptionHandler` 统一返回 `PARAM_INVALID`

**保留 try-catch 的场景**：
- 异步任务（`CompletableFuture.runAsync` 内）
- 资源关闭（`try-with-resources` 不算 catch）

### 6.5 GlobalExceptionHandler 扩展

新增处理器：
```java
@ExceptionHandler(MethodArgumentNotValidException.class)   // @Valid 校验失败
@ExceptionHandler(ConstraintViolationException.class)      // @PathVariable/@RequestParam 校验失败
@ExceptionHandler(MaxUploadSizeExceededException.class)    // 文件超限
@ExceptionHandler(DataIntegrityViolationException.class)   // 数据库唯一约束等
@ExceptionHandler(AlgorithmServiceException.class)         // 算法服务异常
@ExceptionHandler(MissingServletRequestParameterException.class) // 缺必填参数
```

### 6.6 日志规范

- **业务异常** 仅记录 message（避免噪音）
- **系统异常** 完整 stack trace
- 全程使用 SLF4J 占位符 `{}`，禁用字符串拼接

---

## 7. M5：SQL 迁移与 MyBatis 规范化

### 7.1 迁移规则

| 类型 | 判断标准 | 处理 |
|------|---------|------|
| 简单 CRUD | 单表、≤5 行、无动态 SQL | 保留 `@Select`/`@Insert` 注解 |
| 复杂查询 | 含 JOIN、`<script>`、`<foreach>`、>5 行 | 迁移到 XML |

### 7.2 XML 文件清单（重构后）

```
src/main/resources/mapper/
├── OriginTextMapper.xml        # 新增（含 filterReports、复杂筛选查询等）
├── TextTypeMapper.xml          # 新增（含树形查询、统计）
├── ImportMapper.xml            # 新增（批量插入）
├── ExtractionMapper.xml        # 新增（如有复杂 SQL）
├── EventAnalysisMapper.xml     # 新增
├── TargetAnalysisMapper.xml    # 新增
├── FusionMapper.xml            # 保留
└── RagDocumentMapper.xml       # 保留
```

### 7.3 XML 编写规范

- **统一 `resultMap`** 替代散落各处的 `id AS sid, send_unit_name AS sendUnitName` 别名
- **公共列用 `<sql>` 片段** 抽取
- **驼峰自动映射** 已在 `application.yml` 开启，多数 `AS xxxName` 可删除
- **动态 SQL 用 `<where>` `<if>` `<foreach>`**，禁用 Java 字符串拼接
- **`IN (...)`** 用 `<foreach>` 替代字符串拼接（防注入）

### 7.4 调试支持

`application.yml` 启用 MyBatis SQL 日志：
```yaml
mybatis-plus:
  configuration:
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl  # 开发期临时启用
```

非侵入式，仅一行配置，方便排查动态 SQL。后期日志监控系统上线后会统一对接，本次不引入额外依赖。

### 7.5 迁移量估算

| Mapper | 当前注解 SQL | 迁 XML | 保留注解 |
|--------|-----------|--------|---------|
| OriginTextMapper（拆出） | ~30 | ~18 | ~12 |
| TextTypeMapper（拆出） | ~15 | ~10 | ~5 |
| ImportMapper（拆出） | ~10 | ~6 | ~4 |
| ExtractionMapper | ~10 | ~5 | ~5 |
| EventAnalysisMapper | ~8 | ~4 | ~4 |
| TargetAnalysisMapper | ~7 | ~3 | ~4 |
| 其他（保留） | ~9 | 0 | ~9 |

合计迁移到 XML：~46 条 SQL。

---

## 8. 测试策略

### 8.1 单元测试

为以下核心组件补充 JUnit5 + Mockito 测试：
- `CategoryClassifier`（纯函数，4 条规则）
- 拆分后的 3 个 Service（OriginTextService、CategoryService、ExtractionService）的关键方法
- `AlgorithmClient` 的所有方法（mock RestTemplate）
- `BusinessException` 在不同 ErrorCode 下的语义

目标覆盖率：核心 Service 方法 70%+。

### 8.2 集成测试

- `SpringBootTest` + `MockMvc` 扫描每个 Controller，验证：
  - 接口能正常启动
  - 路径前缀正确（所有接口可 ping）
  - 异常路径返回正确的 ErrorCode 与 HTTP 200（项目用 ResultVO，不返回 HTTP 错误码）
  - 参数校验生效

### 8.3 烟雾测试

重构后执行：
1. `./mvnw clean package` 编译通过
2. 启动 Spring Boot，端口 8081 监听成功
3. 关键接口 curl 验证：dashboard/overview、origin-text 列表、category/tree、target/alias/list、rag/index/status

---

## 9. 落地顺序

一次性重构，但建议在本地按以下顺序提交（便于自我 review）：

1. **第一波（基础设施）**：扩充 ErrorCode、新增 AlgorithmServiceException、GlobalExceptionHandler 增强
2. **第二波（拆 Mapper）**：UygurMapper → OriginTextMapper + TextTypeMapper + ImportMapper，所有 Service 注入更新
3. **第三波（HTTP 客户端）**：新增 AlgorithmClient + 实现，5 个 Service 删除原有 HTTP 代码
4. **第四波（命名 + 路径）**：UygurController → OriginTextController，路径全面 kebab-case 化，删除 TestController，同步 OpenAPI
5. **第五波（异常 + SQL）**：删除 Controller try-catch 模板、加 Bean Validation、复杂 SQL 迁 XML
6. **第六波（测试）**：补充单元测试 + 集成测试
7. **第七波（文档）**：更新 `docs/xwBackend-complete-reference.md`，文风保持一致

每波编译 + 启动 + 烟雾测试通过后再进下一波。

---

## 10. 风险与回滚

| 风险 | 缓解措施 |
|------|---------|
| 前端联调时遗漏路径修改 | OpenAPI 同步更新，前端按 OpenAPI diff 修改 |
| Mapper 拆分时遗漏 SQL | 用 Git diff 全量对照 SQL 内容 |
| 删除兜底逻辑导致体验回退 | 验收时确认算法服务失败时的错误提示符合预期 |
| 大范围改名导致编译错误 | 用 IDE 重构功能（不用文本替换） |
| 删除 TestController 影响调试 | 替换为单元测试或 curl 文档 |

**回滚策略**：本次重构不删除任何数据库字段、不修改任何 SQL 语义，因此可以通过 git revert 任意一波单独回滚。

---

## 11. 后续工作（不在本次范围）

- `text_type` 与 `origin_text.category` 冗余清理（需数据迁移）
- RAG 模块独立为 Maven 子模块
- 性能优化：连接池调优、缓存
- 安全加固：鉴权、CORS、密码加密
- 日志监控系统对接

---

## 12. 验收标准

- ✅ `./mvnw clean package` BUILD SUCCESS（0 错误、0 新增警告）
- ✅ Spring Boot 启动成功，端口 8081
- ✅ 所有重构后路径返回 HTTP 200（curl 烟雾测试）
- ✅ 单元测试通过率 100%，核心 Service 覆盖率 ≥70%
- ✅ `grep -rn "throw new RuntimeException" src/main` 结果为 0
- ✅ `grep -rn "@Autowired\|@Resource" src/main` 结果为 0（统一为 `@RequiredArgsConstructor`）
- ✅ `grep -rn "HttpURLConnection" src/main` 结果为 0
- ✅ Controller 中不再出现 `try { ... } catch (Exception e) { return ResultVO.error... }` 模板
- ✅ `docs/xwBackend-complete-reference.md` 更新对应章节
- ✅ OpenAPI JSON 与代码完全对齐

---

**设计文档结束。**
