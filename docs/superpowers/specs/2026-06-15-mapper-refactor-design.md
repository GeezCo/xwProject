# Mapper 全量重构 & Entity/DTO 规范化 设计

> 日期：2026-06-15
> 作者：后端工程师
> 状态：草案

## 1. Context（为什么做）

`xwBackend` 当前 9 个 Mapper 三种风格并存（注解 SQL / XML / MyBatis-Plus），其中 `UygurMapper` 单文件 750+ 行混合 `origin_text` 和 `text_type` 两表 40+ 个方法，存在大量重复的 `@Select <script>` 硬编码 SQL；`Category` Entity 名字与 `text_type` 表名脱节，多个 DTO 同时充当 Entity 使用，导致代码可维护性差、风格不统一、易出错（如本周修过 `o.sid → o.id`、`type_name → name` 的 schema 漂移 bug）。

目标：把所有 Mapper 统一到 **MyBatis-Plus + mybatis-plus-join** 风格，所有表都有独立 Entity 和 Mapper，DTO 与 Entity 分离，消除三套风格并存的维护噩梦。

## 2. 当前问题清单（依据真实库 schema 勘查）

### 2.1 库 schema 现状（14 表）

| 表 | 主键类型 | 行数 | 备注 |
|---|---|---|---|
| origin_text | varchar(32) PRI | 47856 | 报文主表 |
| text_type | varchar(36) PRI | 17 | 分类树（最多 5 层）|
| extraction_result | varchar(32) PRI | 114 | 抽取结果 |
| event_analysis | varchar(32) PRI | 0 | 事件分析 |
| target_analysis | varchar(32) PRI | 0 | 目标分析 |
| target_alias | varchar(32) PRI | 0 | 别名映射 |
| target_fusion | varchar(32) PRI | 0 | 目标融合 |
| fusion_report | varchar(32) PRI | 22 | 报文融合 |
| origin_file | file_id varchar(36) PRI | 7 | 附件（主键非 id）|
| origin_keyword | varchar(36) PRI | 0 | 关键词 |
| rag_document | varchar(32) PRI | 1 | RAG 文档 |
| sync_brief | varchar(32) PRI | 2 | 报文同步 |
| sys_user | user_id varchar(32) PRI | 6 | 用户（主键非 id）|
| sys_config | int unsigned PRI | 0 | 配置 |

**已知 schema 不规范点**（本 spec 不解决，留给 Sprint 2 Schema 治理）：
- `origin_file.file_id` / `sys_user.user_id` 主键名非 `id`
- ID 类型混用：varchar(32) / varchar(36) / int unsigned
- `origin_keyword.text_id varchar(64)` 与 `origin_text.id varchar(32)` JOIN 类型不匹配
- 0 个外键约束

### 2.2 Mapper 代码现状

| Mapper | 行数 | 风格 | 问题 |
|---|---|---|---|
| UygurMapper | 750+ | @Select 注解 | 单文件混 2 表 40+ 方法 |
| FusionMapper | 60 + XML | XML | 列 id ↔ 字段 fusionId 映射绕 |
| EventAnalysisMapper | 60 | @Select 注解 | 关键词 LIKE 全表扫 |
| ExtractionMapper | 80 | @Select 注解 | OK |
| TargetAnalysisMapper | 130 | @Select 注解 | 多个 JOIN 重复写 |
| TargetAliasMapper | 70 | @Select 注解 | OK |
| TargetFusionMapper | 65 | @Select 注解 | OK |
| RagMapper | 130 | **MyBatis-Plus** | 已是新风格，作为模板 |
| RagDocumentMapper | 16 + XML | XML | OK |

### 2.3 影响范围

依赖 UygurMapper 的 Service：UygurServiceImpl、CategoryServiceImpl、DashboardServiceImpl、ExtractionServiceImpl、RagServiceImpl、EventAnalysisServiceImpl、TargetAnalysisServiceImpl

## 3. 方案

### 3.1 一表一 Mapper（14 个）

| 表 | Mapper | Entity（新建/已有）|
|---|---|---|
| origin_text | `OriginTextMapper extends MPJBaseMapper<OriginText>` | OriginText（已有，需补全字段）|
| text_type | `TextTypeMapper extends MPJBaseMapper<TextType>` | **TextType**（Category 改名）|
| extraction_result | `ExtractionResultMapper extends MPJBaseMapper<ExtractionResult>` | ExtractionResult（新建）|
| event_analysis | `EventAnalysisMapper extends MPJBaseMapper<EventAnalysis>` | EventAnalysis（新建）|
| fusion_report | `FusionReportMapper extends MPJBaseMapper<FusionReport>` | FusionReport（新建）|
| origin_file | `OriginFileMapper extends MPJBaseMapper<OriginFile>` | OriginFile（新建）|
| origin_keyword | `OriginKeywordMapper extends MPJBaseMapper<OriginKeyword>` | OriginKeyword（新建）|
| target_analysis | `TargetAnalysisMapper extends MPJBaseMapper<TargetAnalysis>` | TargetAnalysis（已有）|
| target_alias | `TargetAliasMapper extends MPJBaseMapper<TargetAlias>` | TargetAlias（已有）|
| target_fusion | `TargetFusionMapper extends MPJBaseMapper<TargetFusion>` | TargetFusion（已有）|
| rag_document | `RagDocumentMapper extends MPJBaseMapper<RagDocument>` | RagDocument（已有）|
| sync_brief | `SyncBriefMapper extends MPJBaseMapper<SyncBrief>` | SyncBrief（新建）|
| sys_user | `SysUserMapper extends MPJBaseMapper<SysUser>` | SysUser（新建）|
| sys_config | `SysConfigMapper extends MPJBaseMapper<SysConfig>` | SysConfig（新建）|

> sys_user.id 字段在 Entity 中用 `@TableId("user_id")` 映射，对外仍以 `id` 暴露；origin_file.file_id 同样处理。这是过渡方案，库改名留给 Sprint 2。

### 3.2 风格统一规则

| 查询类型 | 实现方式 |
|---|---|
| 单表 CRUD | `BaseMapper` 自带方法（selectById/insert/updateById/deleteById）|
| 单表条件查询 | `LambdaQueryWrapper`（在 Service 写或 Mapper default 方法）|
| 单表条件更新 | `LambdaUpdateWrapper` |
| **多表 JOIN / 聚合** | **`MPJLambdaWrapper`**（mybatis-plus-join 提供）|
| 动态拼接 SQL | LambdaWrapper 链式 `.eq(condition, ...)` `.in(...)` |
| 极端复杂 SQL | 保留 `@Select` 注解 SQL，但必须有注释说明为什么 |

**禁止**：
- 写新的 `@Select <script>` 字符串拼接 SQL
- 在 Java 代码里手动拼 SQL 字符串
- 新建 XML Mapper（现有 FusionMapper.xml / RagDocumentMapper.xml 也要迁移到 Wrapper）

### 3.3 依赖变更

```xml
<!-- pom.xml 新增 -->
<dependency>
    <groupId>com.github.yulichang</groupId>
    <artifactId>mybatis-plus-join-boot-starter</artifactId>
    <version>1.5.0</version>
</dependency>
```

需把 `BaseMapper` 改成 `MPJBaseMapper`（mybatis-plus-join 提供，向下兼容 BaseMapper）。

### 3.4 Entity 命名约定

- **Entity 名 = 表名驼峰**：`text_type` → `TextType`、`origin_file` → `OriginFile`
- **字段名 = 列名驼峰**：`is_extracted` → `isExtracted`、`brief_type_name` → `briefTypeName`
- 不再用 Lombok `@Accessors(chain=true)` 等非标准注解，统一 `@Data`

### 3.5 DTO/Entity 分离原则

| 类 | 用途 |
|---|---|
| **Entity**（com.qy.dch.entity）| 直接映射数据库表，给 Mapper 用 |
| **DTO**（com.qy.dch.dto） | Controller 入参/返回值，Service 内部传递 |
| **VO**（com.qy.dch.vo，可选）| 前端展示专用，含派生字段 |

Entity 不能出现在 Controller 签名里；DTO 不能出现在 Mapper 签名里。Service 负责转换。

### 3.6 重构步骤（一次性，单 PR）

1. 加 `mybatis-plus-join` 依赖
2. 新建 13 个 Entity（已有的复用并补字段）
3. 新建 13 个 Mapper，全部继承 `MPJBaseMapper<Entity>`
4. 把 UygurMapper / FusionMapper / RagMapper 的所有方法**重新实现**在对应新 Mapper 里（用 Wrapper 或 default 方法）
5. **改 Service**：所有 `uygurMapper.xxx()` → `originTextMapper.xxx()` / `textTypeMapper.xxx()`
6. **删除** UygurMapper.java、FusionMapper.java、FusionMapper.xml、RagMapper.java（如原文件）、RagDocumentMapper.xml
7. **改名** Category → TextType（全项目）
8. 编译通过 → 跑测试

### 3.7 Category → TextType 改名清单

- `entity/Category.java` → `entity/TextType.java`
- `service/CategoryService.java` → `service/TextTypeService.java`
- `service/impl/CategoryServiceImpl.java` → `service/impl/TextTypeServiceImpl.java`
- `controller/CategoryController.java` 保留，**路径 `/api/category/*` 不变**（前端兼容）
- 字段：所有 `Category category` → `TextType textType`、`categoryService` → `textTypeService`、`categoryMapper`（如有）→ `textTypeMapper`
- DTO `TextTypeDTO` 保留（DTO 名跟表名一致，无需改）
- 数据库表名 `text_type` **不改**（Sprint 2 也不改，这就是它本来的名字）

## 4. 关键文件/类清单（执行时参考）

新建：
- `entity/`：`TextType.java`、`ExtractionResult.java`、`EventAnalysis.java`、`FusionReport.java`、`OriginFile.java`、`OriginKeyword.java`、`SyncBrief.java`、`SysUser.java`、`SysConfig.java`
- `mapper/`：`OriginTextMapper.java`、`TextTypeMapper.java`、`ExtractionResultMapper.java`、`EventAnalysisMapper.java`（重写）、`FusionReportMapper.java`、`OriginFileMapper.java`、`OriginKeywordMapper.java`、`SyncBriefMapper.java`、`SysUserMapper.java`、`SysConfigMapper.java`

修改：
- `pom.xml`：加 mybatis-plus-join 依赖
- 所有 7 个 ServiceImpl：换 Mapper 引用
- 现有 `OriginText.java` `TargetAnalysis.java` 等已有 Entity：补字段对齐真实 schema

删除：
- `UygurMapper.java`、`FusionMapper.java`、`mapper/FusionMapper.xml`、`mapper/RagDocumentMapper.xml`
- `entity/Category.java`（被 TextType 替代）

## 5. 验证

### 5.1 必须通过的检查

```bash
# 1. 编译通过
mvn clean compile -B

# 2. 全部接口测试（只读模式）75/75 通过
mvn test -Dtest="com.qy.dch.api.*ApiTest"

# 3. 全部接口测试（写操作模式）75/75 通过
mvn test -Dtest="com.qy.dch.api.*ApiTest" -Dtest.write=true
```

### 5.2 补充的真业务流程验证

在 `BaseApiTest` 基础上新增 1 个测试类 `RefactorSmokeTest`，**不 mock，真打远程库**：

| 测试 | 流程 |
|---|---|
| createTextThenDelete | POST 创建分类 → 读 ID → DELETE 删除 → 确认查不到 |
| getTextDetailHappyPath | GET /uygur/detail/{真实ID} → 验证 sid/title/labelsJson 字段都有 |
| categoryTreeIntegrity | GET /api/category/tree → 验证含 reportCount 字段 |

测试**带 `@AfterEach` 清理**，防止污染数据。

## 6. 不做的事（明确 out-of-scope）

- ❌ 改库 schema（留给 Sprint 2 Schema 治理 spec）
- ❌ 改 Controller 路径（前端兼容）
- ❌ 改算法服务接口
- ❌ 性能优化（LIKE 全表扫等问题留给 Sprint 2）
- ❌ 引入分页插件（PageHelper 等）

## 7. 风险与回避

| 风险 | 回避 |
|---|---|
| 改动 ~25 文件，单 PR 容易遗漏 | 严格按"先编译过，再跑全量测试"分步推进 |
| mybatis-plus-join 是第三方库，可能有兼容性问题 | 先验证版本兼容 JDK 8 + Spring Boot 2.7 + MyBatis-Plus 现有版本 |
| Category → TextType 改名影响面大 | 用 IDE 批量重构（Refactor → Rename），不手改 |
| `sys_user.user_id` 等非标主键 Entity 映射需特殊处理 | 用 `@TableId("user_id")` 注解显式指定 |
| 远程库 `extraction_result` 是否有 UNIQUE on origin_text_id 决定 ON DUPLICATE 是否真生效 | 重构前先查清楚（Sprint 0 验证）|

## 8. Sprint 切分

| Sprint | 内容 | 本 spec |
|---|---|---|
| **0** | 验证：mybatis-plus-join 版本兼容、扫一遍 `extraction_result` 索引情况 | ⚠️ 前置 |
| **1** | 本 spec：Mapper 重构 + Entity/DTO 规范化（不改库）| ✅ |
| **2** | 下一个 spec：Schema 治理（统一 ID 类型、加外键、改非标主键名）| ⏭️ |
