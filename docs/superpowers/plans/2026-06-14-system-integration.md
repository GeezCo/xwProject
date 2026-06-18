# 20260613 系统对接实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 按 `docs/20260613系统对接.md` 落实 6 大需求模块（多级目录、自动分类、关键词筛选、目标分析/融合/别名、isFused、融合详情），同时完成 ID 全局 String 化改造。

**Architecture:**
- **DB 层**：新增 `target_analysis` / `target_alias` / `target_fusion` 三张表 + `origin_text.category` 列；全部沿用迁移期 ID 规范（VARCHAR(32) ascii ascii_bin，utf8mb4_0900_ai_ci）。
- **服务端**：新增 `/api/directory/tree` + `/api/uygur/filter/advanced` + `/api/target/analyze/task/{taskId}` + `/api/target/fusion/detail`；批量分析改为线程池 + 内存任务表的异步模式。
- **代码层**：全局 yml 配 `id-type: ASSIGN_ID`，所有实体/DTO/Controller/Service/Mapper/XML 的 Long/Integer 主键字段统一为 `String`，孤儿类同步改造为可用类。

**Tech Stack:** Spring Boot 2.7.18 / JDK 8 / MyBatis-Plus 3.5.5 / MySQL 8 (utf8mb4_0900_ai_ci) / 服务器 36.141.21.176:9204/uygur_project。

---

## 文件结构总览

**DDL 文件（新建 1 个，写入服务器）：**
- `docs/sql/2026-06-14-system-integration.sql` — 三张目标表 + origin_text.category 列 + 初始化 SQL

**Java 后端改动：**
- `xwSystem/xwBackend/src/main/resources/application.yml` — 加 `mybatis-plus.global-config.db-config.id-type: ASSIGN_ID`
- `xwSystem/xwBackend/src/main/java/com/qy/dch/entity/BaseEntity.java` — `Long id` → `String id`
- `xwSystem/xwBackend/src/main/java/com/qy/dch/entity/OriginText.java` — 增加 `category` 字段；`type` 已为 String
- `xwSystem/xwBackend/src/main/java/com/qy/dch/entity/{TargetAnalysis,TargetFusion,TargetAlias,Category,TextType}.java` — 主键 Long → String，TargetFusion 补 source_count/is_fused，Category 弃用或改为内存常量
- `xwSystem/xwBackend/src/main/java/com/qy/dch/dto/*.java` — 凡是 Long/Integer 的 id 字段全部 String
- `xwSystem/xwBackend/src/main/java/com/qy/dch/request/GetListRequest.java` — `Integer typeId` → `String typeId`
- `xwSystem/xwBackend/src/main/java/com/qy/dch/controller/UygurController.java` — `@RequestMapping("/uygur")` → `/api/uygur`，新增 `/filter` 和 `/filter/advanced`
- `xwSystem/xwBackend/src/main/java/com/qy/dch/controller/TargetController.java` — 批量分析改异步，加 task 进度查询，加 fusion/detail
- `xwSystem/xwBackend/src/main/java/com/qy/dch/controller/CategoryController.java` — id 类型同步改 String
- `xwSystem/xwBackend/src/main/java/com/qy/dch/service/UygurService.java` + impl — 新增 filter、filterAdvanced 方法，savetext 内调 CategoryClassifier
- `xwSystem/xwBackend/src/main/java/com/qy/dch/service/TargetAnalysisService.java` + impl — 新增 analyzeBatchAsync / getTaskProgress 方法
- `xwSystem/xwBackend/src/main/java/com/qy/dch/service/TargetFusionService.java` + impl — 新增 getFusionDetail 方法
- `xwSystem/xwBackend/src/main/java/com/qy/dch/service/impl/DashboardServiceImpl.java` — id 类型同步
- `xwSystem/xwBackend/src/main/java/com/qy/dch/mapper/UygurMapper.java` — 加 filter / filterAdvanced 方法
- `xwSystem/xwBackend/src/main/resources/mapper/{UygurMapper,FusionMapper,RagDocumentMapper}.xml` — id 类型 jdbcType=BIGINT/INTEGER → VARCHAR；新增 SQL

**测试文件：**
- `xwSystem/xwBackend/src/test/java/com/qy/dch/util/CategoryClassifierTest.java`
- `xwSystem/xwBackend/src/test/java/com/qy/dch/service/impl/UygurServiceImplFilterTest.java`
- `xwSystem/xwBackend/src/test/java/com/qy/dch/service/impl/TargetAnalysisAsyncTest.java`
- `xwSystem/xwBackend/src/test/java/com/qy/dch/service/impl/TargetFusionDetailTest.java`

---

## 阶段 0 — DDL 建表与列扩展

### Task 1: 写 SQL 迁移脚本（含详细注释）

**Files:**
- Create: `docs/sql/2026-06-14-system-integration.sql`

- [ ] **Step 1: 写 DDL（直接展示完整可执行版本，注释给算法/前端同事看）**

```sql
-- ================================================================
-- 20260613 系统对接 - 业务表新增 + 字段扩展
-- 目标库：36.141.21.176:9204 / uygur_project
-- 约定（与 2026-06-13 ID 全局化迁移保持一致）：
--   * 所有业务主键 id 统一 VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin
--     - 取消 AUTO_INCREMENT；改由 Java 侧 MyBatis-Plus 雪花算法生成 19 位字符串
--     - ascii_bin 让索引体积仅为 utf8mb4 的 1/4，比较走二进制速度最快
--   * 业务文本字段统一 utf8mb4 / utf8mb4_0900_ai_ci（MySQL 8 默认排序规则）
--   * 创建/更新时间统一 DATETIME DEFAULT CURRENT_TIMESTAMP
-- ================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------
-- 1. origin_text 增加 category 列（一级分类）
--    用于「03-search.html」左侧目录树第一层：开源信息 / HZ报 / JZ报 / 未分类
--    入库时由 CategoryClassifier 计算：
--      send_unit_name 匹配 /HZ\d+报/  → 'HZ报'
--      content 包含 'JZX'            → 'JZ报'
--      content 包含 '入网QB'          → '开源信息'
--      其余                          → '未分类'
-- ---------------------------------------------------------------
ALTER TABLE `origin_text`
  ADD COLUMN `category` VARCHAR(20)
    CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci
    NOT NULL DEFAULT '未分类'
    COMMENT '一级分类：开源信息/HZ报/JZ报/未分类（入库时自动计算）'
    AFTER `send_unit_name`,
  ADD INDEX `idx_category` (`category`) COMMENT '目录树一级聚合用',
  ADD INDEX `idx_category_send_unit` (`category`, `send_unit_name`) COMMENT '二级目录聚合用';

-- 回填存量数据（一次性脚本，迁移后执行）
UPDATE `origin_text` SET `category` =
  CASE
    WHEN `send_unit_name` REGEXP '^HZ[0-9]+报' THEN 'HZ报'
    WHEN `content` LIKE '%JZX%'              THEN 'JZ报'
    WHEN `content` LIKE '%入网QB%'            THEN '开源信息'
    ELSE '未分类'
  END
WHERE `category` = '未分类';

-- ---------------------------------------------------------------
-- 2. target_analysis - 目标分析结果表
--    存储算法对单条报文做「目标分析」后抽取出的多个目标条目。
--    每行 = 一个报文中的一个目标。
--    数据来源：POST http://algorithm-service/api/target/analyze 的返回。
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `target_analysis` (
  `id`               VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin
                     NOT NULL COMMENT '主键ID（雪花算法生成的19位字符串）',
  `origin_text_id`   VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin
                     NOT NULL COMMENT '关联报文ID → origin_text.id',
  `region_name`      VARCHAR(255) DEFAULT NULL COMMENT '区域名称，例如「哈尔科夫」',
  `target_name`      VARCHAR(255) NOT NULL COMMENT '目标名称（经别名映射后的标准名）',
  `raw_target_name`  VARCHAR(255) DEFAULT NULL COMMENT '算法返回的原始目标名（未别名映射）',
  `target_type`      VARCHAR(100) DEFAULT NULL COMMENT '目标类型，例如「军事设施」',
  `found_time`       VARCHAR(50)  DEFAULT NULL COMMENT '算法识别的发现时间（字符串原样保留）',
  `description`      TEXT         COMMENT '情况描述（针对该目标的那段文字）',
  `attachment_url`   TEXT         COMMENT '对应附件URL（图片/视频，可能为 null）',
  `is_fused`         TINYINT(1)   NOT NULL DEFAULT 0
                     COMMENT '是否已参与融合：0-否 1-是（融合接口完成后置1）',
  `create_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
                     ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_origin_text_id` (`origin_text_id`),
  KEY `idx_target_name`    (`target_name`),
  KEY `idx_region_name`    (`region_name`),
  KEY `idx_is_fused`       (`is_fused`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='目标分析结果（每行=一个报文中的一个目标）';

-- ---------------------------------------------------------------
-- 3. target_alias - 目标别名映射表
--    某些目标有多个名称（如「xxx弹药库」→「哈尔科夫弹药库」），
--    算法返回的 target_name 经 resolveAlias 映射后再入库。
--    UNIQUE (alias) 保证一个别名只映射一个标准名。
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `target_alias` (
  `id`              VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin
                    NOT NULL COMMENT '主键ID',
  `alias`           VARCHAR(255) NOT NULL COMMENT '别名（输入侧），如「xxx弹药库」',
  `canonical_name`  VARCHAR(255) NOT NULL COMMENT '标准名（统一名），如「哈尔科夫弹药库」',
  `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_alias`         (`alias`),
  KEY        `idx_canonical_name`(`canonical_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='目标别名映射（别名 → 标准名）';

-- ---------------------------------------------------------------
-- 4. target_fusion - 目标融合结果表
--    用户在前端勾选多个目标 → 后端聚合该目标全部 target_analysis →
--    调算法 /api/target/fusion → 返回融合结果（综合描述+变化分析+区域融合）→ 入此表。
--    一次融合 = 一行（多个目标合并到一行的 target_names）。
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `target_fusion` (
  `id`                    VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin
                          NOT NULL COMMENT '融合记录ID',
  `target_names`          VARCHAR(1000) NOT NULL
                          COMMENT '参与融合的目标名 JSON 数组字符串，如 ["哈尔科夫弹药库","某机场"]',
  `analysis`              TEXT          COMMENT '算法返回-综合描述（analysis）',
  `difference`            TEXT          COMMENT '算法返回-变化分析（difference）',
  `region_fusion_result`  TEXT          COMMENT '算法返回-区域融合结果文本（region 维度的整体态势）',
  `source_count`          INT NOT NULL DEFAULT 0 COMMENT '参与融合的 target_analysis 记录条数',
  `is_fused`              TINYINT(1) NOT NULL DEFAULT 1
                          COMMENT '该记录是否有效（保留位，恒为1，删除走逻辑删除）',
  `fusion_time`           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '融合执行时间',
  `create_time`           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
                          ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_target_names_prefix` (`target_names`(64)) COMMENT '按目标名前缀检索',
  KEY `idx_fusion_time` (`fusion_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='目标融合结果';

-- ---------------------------------------------------------------
-- 5. 别名表初始化数据（示例 — 真实数据由业务方维护）
-- ---------------------------------------------------------------
-- INSERT INTO `target_alias` (`id`, `alias`, `canonical_name`) VALUES
-- ('SEED0000000000000000000000000001', '哈市弹药库',    '哈尔科夫弹药库'),
-- ('SEED0000000000000000000000000002', '哈尔科夫弹药点', '哈尔科夫弹药库');
```

- [ ] **Step 2: 把上述 SQL 在服务器执行**

```bash
mysql -h 36.141.21.176 -P 9204 -uroot -pjixianyuan1314 uygur_project < docs/sql/2026-06-14-system-integration.sql
```

Expected: 0 errors. 验证：
```bash
mysql -h 36.141.21.176 -P 9204 -uroot -pjixianyuan1314 uygur_project -e \
  "SHOW TABLES LIKE 'target_%'; SHOW COLUMNS FROM origin_text LIKE 'category';"
```
Expected 输出：`target_alias / target_analysis / target_fusion` 三表 + `category` 列存在。

- [ ] **Step 3: Commit**

```bash
git add docs/sql/2026-06-14-system-integration.sql
git commit -m "feat(db): 新增 target_analysis/target_alias/target_fusion 表 + origin_text.category 列"
```

---

## 阶段 1 — Java ID String 化全局收口

### Task 2: MyBatis-Plus 全局 ASSIGN_ID

**Files:**
- Modify: `xwSystem/xwBackend/src/main/resources/application.yml:82-86`

- [ ] **Step 1: 把雪花算法默认主键策略写到 yml 全局**

在 `mybatis-plus.global-config.db-config` 下加 `id-type: ASSIGN_ID`：

```yaml
mybatis-plus:
  type-aliases-package: com.qy.dch.dto
  mapper-locations: classpath:mapper/*.xml
  configuration:
    map-underscore-to-camel-case: true
  global-config:
    db-config:
      id-type: ASSIGN_ID           # 全局雪花算法主键（19位字符串，String 类型）
      logic-delete-field: deleted
      logic-delete-value: 1
      logic-not-delete-value: 0
```

- [ ] **Step 2: Commit**

```bash
git add xwSystem/xwBackend/src/main/resources/application.yml
git commit -m "feat(mp): 全局启用 ASSIGN_ID 雪花主键策略"
```

---

### Task 3: BaseEntity / 实体主键 Long → String

**Files:**
- Modify: `xwSystem/xwBackend/src/main/java/com/qy/dch/entity/BaseEntity.java:13`
- Modify: `xwSystem/xwBackend/src/main/java/com/qy/dch/entity/OriginText.java`（补 `category`）
- Modify: `xwSystem/xwBackend/src/main/java/com/qy/dch/entity/TargetAnalysis.java:20`
- Modify: `xwSystem/xwBackend/src/main/java/com/qy/dch/entity/TargetAlias.java:20`
- Modify: `xwSystem/xwBackend/src/main/java/com/qy/dch/entity/TargetFusion.java:20`（同时补 `sourceCount` / `isFused`）
- Modify: `xwSystem/xwBackend/src/main/java/com/qy/dch/entity/Category.java:23,33`
- Modify: `xwSystem/xwBackend/src/main/java/com/qy/dch/entity/TextType.java`

- [ ] **Step 1: 写 BaseEntity 测试（验证主键是 String）**

`xwSystem/xwBackend/src/test/java/com/qy/dch/entity/BaseEntityTest.java`：
```java
package com.qy.dch.entity;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BaseEntityTest {
    static class SubEntity extends BaseEntity {}
    @Test void idIsString() throws Exception {
        SubEntity e = new SubEntity();
        java.lang.reflect.Field f = BaseEntity.class.getDeclaredField("id");
        assertEquals(String.class, f.getType(), "BaseEntity.id 必须是 String 类型");
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

`mvn -pl xwSystem/xwBackend test -Dtest=BaseEntityTest` → FAIL（id 当前是 Long）

- [ ] **Step 3: 改 BaseEntity.java**

```java
package com.qy.dch.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

@Data
public class BaseEntity {
    @TableId  // 不写 type，走 application.yml 全局 ASSIGN_ID
    private String id;
}
```

- [ ] **Step 4: 改 OriginText.java（补 category 字段）**

定位现有 `private String type;` 之后插入：
```java
/** 一级分类：开源信息/HZ报/JZ报/未分类 */
private String category;
```

- [ ] **Step 5: 改 TargetAnalysis.java**

`private Long id;` → `private String id;`
确认还有：`private String originTextId;`（原本可能是 Long）

- [ ] **Step 6: 改 TargetAlias.java**

`private Long id;` → `private String id;`

- [ ] **Step 7: 改 TargetFusion.java（id+source_count+is_fused）**

```java
private String id;
private String targetNames;
private String analysis;
private String difference;
private String regionFusionResult;
private Integer sourceCount;
private Integer isFused;
private LocalDateTime fusionTime;
private LocalDateTime createTime;
private LocalDateTime updateTime;
```

- [ ] **Step 8: 改 Category.java**（孤儿类，保留并修正类型）

`private Long id;` → `private String id;`，`private Long parentId;` → `private String parentId;`

- [ ] **Step 9: 改 TextType.java**

`private Long id;` → `private String id;`，`private Long parentId;` → `private String parentId;`

- [ ] **Step 10: 跑测试**

`mvn -pl xwSystem/xwBackend test -Dtest=BaseEntityTest` → PASS

- [ ] **Step 11: Commit**

```bash
git add xwSystem/xwBackend/src/main/java/com/qy/dch/entity/ xwSystem/xwBackend/src/test/java/com/qy/dch/entity/BaseEntityTest.java
git commit -m "refactor(entity): 所有实体主键 Long → String（全局雪花）"
```

---

### Task 4: DTO / Request 主键字段 String 化

**Files:**
- Modify: `xwSystem/xwBackend/src/main/java/com/qy/dch/dto/AddCategoryResultDTO.java:7,9`
- Modify: `xwSystem/xwBackend/src/main/java/com/qy/dch/dto/EventAnalysisDTO.java:12`
- Modify: `xwSystem/xwBackend/src/main/java/com/qy/dch/dto/ExtractionResultDTO.java:23`
- Modify: `xwSystem/xwBackend/src/main/java/com/qy/dch/dto/FusionDTO.java:21,24`
- Modify: `xwSystem/xwBackend/src/main/java/com/qy/dch/dto/ImportResultDTO.java:12`
- Modify: `xwSystem/xwBackend/src/main/java/com/qy/dch/dto/RagIndexLogDTO.java:7`
- Modify: `xwSystem/xwBackend/src/main/java/com/qy/dch/dto/TargetAnalysisDTO.java:18`
- Modify: `xwSystem/xwBackend/src/main/java/com/qy/dch/dto/TargetFusionResultDTO.java:17`
- Modify: `xwSystem/xwBackend/src/main/java/com/qy/dch/dto/TextTypeDTO.java:13,15`
- Modify: `xwSystem/xwBackend/src/main/java/com/qy/dch/request/GetListRequest.java:22`

- [ ] **Step 1: 全部把 `Long id`、`Long parentId`、`Long originTextId`、`Integer id`、`Integer typeId`、`Integer parentId` 改为 `String`**

例（AddCategoryResultDTO.java）：
```java
private String id;          // 原 Integer
private String parentId;    // 原 Integer
```

例（FusionDTO.java）：
```java
private String id;          // 原 Long
private String originTextId;// 原 Long
```

例（GetListRequest.java）：
```java
private String typeId;      // 原 Integer
```

- [ ] **Step 2: Commit**

```bash
git add xwSystem/xwBackend/src/main/java/com/qy/dch/dto/ xwSystem/xwBackend/src/main/java/com/qy/dch/request/
git commit -m "refactor(dto): 所有 DTO/Request 的 id 字段 Long/Integer → String"
```

---

### Task 5: Controller / Service / Mapper 签名同步 String 化

**Files:**
- Modify: `xwSystem/xwBackend/src/main/java/com/qy/dch/controller/UygurController.java:213,246,285,395,475,494,495`
- Modify: `xwSystem/xwBackend/src/main/java/com/qy/dch/controller/CategoryController.java:83,117,146,174,198`
- Modify: `xwSystem/xwBackend/src/main/java/com/qy/dch/controller/TargetController.java:212,239`（updateAlias / deleteAlias 的 @PathVariable Long id → String id）
- Modify: `xwSystem/xwBackend/src/main/java/com/qy/dch/service/UygurService.java:112,131,163,172,181,190,199`
- Modify: `xwSystem/xwBackend/src/main/java/com/qy/dch/service/CategoryService.java:34,44,53,60,76`
- Modify: `xwSystem/xwBackend/src/main/java/com/qy/dch/service/TargetAliasService.java`（updateAlias/deleteAlias 参数）
- Modify: `xwSystem/xwBackend/src/main/java/com/qy/dch/service/impl/UygurServiceImpl.java:340,385,395,540,600,619,636,650,676,709`
- Modify: `xwSystem/xwBackend/src/main/java/com/qy/dch/service/impl/CategoryServiceImpl.java`
- Modify: `xwSystem/xwBackend/src/main/java/com/qy/dch/service/impl/DashboardServiceImpl.java:79`
- Modify: `xwSystem/xwBackend/src/main/java/com/qy/dch/service/impl/TargetAliasServiceImpl.java`（id 参数 Long → String）
- Modify: `xwSystem/xwBackend/src/main/java/com/qy/dch/service/impl/TargetAnalysisServiceImpl.java:116`（`List<Integer>` → `List<String>`）
- Modify: `xwSystem/xwBackend/src/main/java/com/qy/dch/mapper/UygurMapper.java:335,379,425,440,450,573,736`

- [ ] **Step 1: 全部签名 Long/Integer → String，编译驱动改完**

逐文件把 `@PathVariable Long id` → `@PathVariable String id`，`@Param("originTextId") Long` → `@Param("originTextId") String`，等等。

- [ ] **Step 2: 跑编译**

`mvn -pl xwSystem/xwBackend compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add xwSystem/xwBackend/src/main/java/com/qy/dch/{controller,service,mapper}
git commit -m "refactor(api): Controller/Service/Mapper 签名 Long/Integer → String"
```

---

### Task 6: Mapper XML jdbcType 修正

**Files:**
- Modify: `xwSystem/xwBackend/src/main/resources/mapper/UygurMapper.xml` 所有 `jdbcType="BIGINT"` / `jdbcType="INTEGER"` 在 id / origin_text_id / text_id / parent_id 列上 → `jdbcType="VARCHAR"`
- Modify: `xwSystem/xwBackend/src/main/resources/mapper/FusionMapper.xml` 同上
- Modify: `xwSystem/xwBackend/src/main/resources/mapper/RagDocumentMapper.xml` 同上

- [ ] **Step 1: grep 出所有需要改的位置**

```bash
grep -nE 'jdbcType="(BIGINT|INTEGER)"' xwSystem/xwBackend/src/main/resources/mapper/*.xml
```

- [ ] **Step 2: 全部改为 VARCHAR（只改 id 类列，业务整数字段如 count/size 保留）**

- [ ] **Step 3: 跑全量编译 + 单元测试（如有）**

```bash
mvn -pl xwSystem/xwBackend test-compile
```
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add xwSystem/xwBackend/src/main/resources/mapper/
git commit -m "refactor(mapper-xml): 主键/外键 jdbcType BIGINT/INTEGER → VARCHAR"
```

---

## 阶段 2 — 多级目录 + 自动分类（1.1 / 1.2 / 1.4）

### Task 7: CategoryClassifier 接入 savetext

**Files:**
- Modify: `xwSystem/xwBackend/src/main/java/com/qy/dch/service/impl/UygurServiceImpl.java`
- Create: `xwSystem/xwBackend/src/test/java/com/qy/dch/util/CategoryClassifierTest.java`

- [ ] **Step 1: 写 CategoryClassifier 测试（已有工具类，仅验证）**

```java
package com.qy.dch.util;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CategoryClassifierTest {
    @Test void hzReportTakesPriority() {
        assertEquals("HZ报", CategoryClassifier.classify("HZ123报", "包含 JZX 和 入网QB"));
    }
    @Test void jzReportSecond() {
        assertEquals("JZ报", CategoryClassifier.classify("某单位", "正文 JZX 内容"));
    }
    @Test void openSourceThird() {
        assertEquals("开源信息", CategoryClassifier.classify("某单位", "正文 入网QB 内容"));
    }
    @Test void defaultUncategorized() {
        assertEquals("未分类", CategoryClassifier.classify("某单位", "纯文本"));
    }
    @Test void nullSafe() {
        assertEquals("未分类", CategoryClassifier.classify(null, null));
    }
}
```

- [ ] **Step 2: 跑测试**

`mvn -pl xwSystem/xwBackend test -Dtest=CategoryClassifierTest` → PASS

- [ ] **Step 3: 在 UygurServiceImpl.savetext / importFromJsonl 插入前注入 category**

定位 `uygurMapper.insertOriginText(originTextDTO)` 调用，前面加：
```java
originTextDTO.setCategory(
    com.qy.dch.util.CategoryClassifier.classify(
        originTextDTO.getSendUnitName(),
        originTextDTO.getContent()
    )
);
```

- [ ] **Step 4: 同步 OriginTextDTO 加 `private String category;`**

- [ ] **Step 5: UygurMapper.xml 的 `insertOriginText` SQL 加 category 列**

- [ ] **Step 6: Commit**

```bash
git add xwSystem/xwBackend
git commit -m "feat(category): 报文入库自动分类（HZ报/JZ报/开源信息/未分类）"
```

---

### Task 8: GET /api/directory/tree（已有 controller，仅验证 + 修正）

**Files:**
- Modify: `xwSystem/xwBackend/src/main/java/com/qy/dch/service/impl/DirectoryServiceImpl.java`
- Modify: `xwSystem/xwBackend/src/main/java/com/qy/dch/mapper/UygurMapper.java`（countByCategory / countBySendUnit 走 category 列）
- Modify: `xwSystem/xwBackend/src/main/resources/mapper/UygurMapper.xml`

- [ ] **Step 1: 在 UygurMapper.xml 写两段聚合 SQL**

```xml
<!-- 按一级分类统计报文数 -->
<select id="countByCategory" resultType="map">
  SELECT category AS name, COUNT(*) AS count
  FROM origin_text
  GROUP BY category
</select>

<!-- 按一级+二级（SendUnit）统计 -->
<select id="countBySendUnit" resultType="map" parameterType="string">
  SELECT send_unit_name AS name, COUNT(*) AS count
  FROM origin_text
  WHERE category = #{category}
  GROUP BY send_unit_name
  ORDER BY count DESC
</select>
```

- [ ] **Step 2: DirectoryServiceImpl 已实现，确认 category 字段已扩展、走通**

- [ ] **Step 3: 集成验证（curl）**

```bash
curl http://localhost:8081/api/directory/tree | python3 -m json.tool
```
Expected: 4 个一级节点（开源信息/HZ报/JZ报/未分类），每个 children 含 SendUnit + count。

- [ ] **Step 4: Commit**

```bash
git add xwSystem/xwBackend
git commit -m "feat(directory): 目录树 SQL 接入 category 列"
```

---

## 阶段 3 — 报文筛选接口（1.3 / 2.1）

### Task 9: GET /api/uygur/filter

**Files:**
- Modify: `xwSystem/xwBackend/src/main/java/com/qy/dch/controller/UygurController.java`（`/uygur` → `/api/uygur`，新增 `/filter`）
- Modify: `xwSystem/xwBackend/src/main/java/com/qy/dch/service/UygurService.java` + impl
- Modify: `xwSystem/xwBackend/src/main/java/com/qy/dch/mapper/UygurMapper.java`
- Modify: `xwSystem/xwBackend/src/main/resources/mapper/UygurMapper.xml`

- [ ] **Step 1: Controller 改前缀 + 新增 endpoint**

```java
@RestController
@RequestMapping("/api/uygur")  // 原 "/uygur"
@Slf4j
public class UygurController {
    // ...
    @GetMapping("/filter")
    public ResultVO filter(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String sendUnit,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        return ResultVO.success(uygurService.filter(category, sendUnit, keyword, startDate, endDate));
    }
}
```

- [ ] **Step 2: UygurService 接口加 filter 方法 + impl**

```java
List<OriginTextDTO> filter(String category, String sendUnit, String keyword, String startDate, String endDate);
```
impl 调 `uygurMapper.filter(...)`。

- [ ] **Step 3: UygurMapper.xml 加动态 SQL**

```xml
<select id="filter" resultType="com.qy.dch.dto.OriginTextDTO">
  SELECT * FROM origin_text
  <where>
    <if test="category  != null and category  != ''">AND category = #{category}</if>
    <if test="sendUnit  != null and sendUnit  != ''">AND send_unit_name = #{sendUnit}</if>
    <if test="keyword   != null and keyword   != ''">
      AND (content LIKE CONCAT('%', #{keyword}, '%') OR title LIKE CONCAT('%', #{keyword}, '%'))
    </if>
    <if test="startDate != null and startDate != ''">AND times &gt;= #{startDate}</if>
    <if test="endDate   != null and endDate   != ''">AND times &lt;= #{endDate}</if>
  </where>
  ORDER BY times DESC
  LIMIT 1000
</select>
```

- [ ] **Step 4: 验证**

```bash
curl "http://localhost:8081/api/uygur/filter?category=HZ报&startDate=2024-01-01&endDate=2024-12-31"
```

- [ ] **Step 5: Commit**

```bash
git add xwSystem/xwBackend
git commit -m "feat(filter): GET /api/uygur/filter 基础筛选接口"
```

---

### Task 10: POST /api/uygur/filter/advanced

**Files:**
- Modify: `xwSystem/xwBackend/src/main/java/com/qy/dch/controller/UygurController.java`
- Modify: `xwSystem/xwBackend/src/main/java/com/qy/dch/service/UygurService.java` + impl
- Create: `xwSystem/xwBackend/src/main/java/com/qy/dch/request/AdvancedFilterRequest.java`
- Modify: `xwSystem/xwBackend/src/main/resources/mapper/UygurMapper.xml`

- [ ] **Step 1: 建 Request DTO**

```java
package com.qy.dch.request;
import lombok.Data;
import java.util.List;

@Data
public class AdvancedFilterRequest {
    private List<String> targetKeywords;  // 关重目标关键词
    private List<String> regionKeywords;  // 关重区域关键词
    private String startDate;
    private String endDate;
}
```

- [ ] **Step 2: Controller**

```java
@PostMapping("/filter/advanced")
public ResultVO filterAdvanced(@RequestBody AdvancedFilterRequest req) {
    return ResultVO.success(uygurService.filterAdvanced(req));
}
```

- [ ] **Step 3: Mapper XML 动态拼 OR LIKE**

```xml
<select id="filterAdvanced" resultType="com.qy.dch.dto.OriginTextDTO">
  SELECT * FROM origin_text
  <where>
    <if test="startDate != null and startDate != ''">AND times &gt;= #{startDate}</if>
    <if test="endDate   != null and endDate   != ''">AND times &lt;= #{endDate}</if>
    <if test="(targetKeywords != null and targetKeywords.size() > 0) or (regionKeywords != null and regionKeywords.size() > 0)">
      AND (
        <trim prefixOverrides="OR ">
          <if test="targetKeywords != null">
            <foreach collection="targetKeywords" item="kw" separator=" OR ">
              content LIKE CONCAT('%', #{kw}, '%')
            </foreach>
          </if>
          <if test="regionKeywords != null">
            OR
            <foreach collection="regionKeywords" item="kw" separator=" OR ">
              content LIKE CONCAT('%', #{kw}, '%')
            </foreach>
          </if>
        </trim>
      )
    </if>
  </where>
  ORDER BY times DESC
  LIMIT 1000
</select>
```

- [ ] **Step 4: 测试**

```bash
curl -X POST http://localhost:8081/api/uygur/filter/advanced \
  -H "Content-Type: application/json" \
  -d '{"targetKeywords":["弹药库"],"regionKeywords":["哈尔科夫"],"startDate":"2024-01-01","endDate":"2024-12-31"}'
```

- [ ] **Step 5: Commit**

```bash
git add xwSystem/xwBackend
git commit -m "feat(filter): POST /api/uygur/filter/advanced 关键词OR+时间AND"
```

---

## 阶段 4 — 目标分析异步 + 进度（2.2）

### Task 11: 批量分析改异步 + taskId 进度查询

**Files:**
- Create: `xwSystem/xwBackend/src/main/java/com/qy/dch/service/TaskProgressService.java`
- Create: `xwSystem/xwBackend/src/main/java/com/qy/dch/service/impl/TaskProgressServiceImpl.java`
- Modify: `xwSystem/xwBackend/src/main/java/com/qy/dch/service/TargetAnalysisService.java`
- Modify: `xwSystem/xwBackend/src/main/java/com/qy/dch/service/impl/TargetAnalysisServiceImpl.java`
- Modify: `xwSystem/xwBackend/src/main/java/com/qy/dch/controller/TargetController.java`

- [ ] **Step 1: 建 TaskProgressService（ConcurrentHashMap 存任务状态）**

```java
package com.qy.dch.service;
public interface TaskProgressService {
    String submit(int total);
    void increment(String taskId, boolean success);
    java.util.Map<String, Object> get(String taskId);
}
```

impl：用 `ConcurrentHashMap<String, AtomicInteger[]>` 记录 total/completed/failed/status。

- [ ] **Step 2: TargetAnalysisService 加 analyzeBatchAsync**

```java
String analyzeBatchAsync(String startTime, String endTime);  // 返回 taskId
```

impl：用 `@Async` 或 `Executors.newFixedThreadPool(4)` 后台跑 analyzeReport 循环，每条 `taskProgressService.increment`。

- [ ] **Step 3: Controller 改 batch + 加 task 查询**

```java
@PostMapping("/analyze/batch")
public ResultVO analyzeBatch(@RequestParam String startTime, @RequestParam String endTime) {
    String taskId = targetAnalysisService.analyzeBatchAsync(startTime, endTime);
    return ResultVO.success(java.util.Collections.singletonMap("taskId", taskId));
}

@GetMapping("/analyze/task/{taskId}")
public ResultVO taskProgress(@PathVariable String taskId) {
    return ResultVO.success(taskProgressService.get(taskId));
}
```

- [ ] **Step 4: 线程池配置加 @EnableAsync 到 Application**

- [ ] **Step 5: 集成测试**

提交一个小区间，立刻拿 taskId，轮询 `/analyze/task/{taskId}` 看 completed 增长。

- [ ] **Step 6: Commit**

```bash
git add xwSystem/xwBackend
git commit -m "feat(target): 批量目标分析改异步 + /analyze/task/{taskId} 进度查询"
```

---

## 阶段 5 — 目标融合详情（2.6）

### Task 12: GET /api/target/fusion/detail

**Files:**
- Modify: `xwSystem/xwBackend/src/main/java/com/qy/dch/controller/TargetController.java`
- Modify: `xwSystem/xwBackend/src/main/java/com/qy/dch/service/TargetFusionService.java`
- Modify: `xwSystem/xwBackend/src/main/java/com/qy/dch/service/impl/TargetFusionServiceImpl.java`
- Create: `xwSystem/xwBackend/src/main/java/com/qy/dch/dto/TargetFusionDetailDTO.java`

- [ ] **Step 1: 建 DTO**

```java
package com.qy.dch.dto;
import lombok.Data;
import java.util.List;

@Data
public class TargetFusionDetailDTO {
    private BasicInfo basicInfo;
    private FusionResult fusionResult;

    @Data public static class BasicInfo {
        private String targetName;
        private Integer sourceCount;
        private List<Source> sources;
    }
    @Data public static class Source {
        private String sendUnit;
        private String foundTime;
        private String description;
        private String url;
    }
    @Data public static class FusionResult {
        private String analysis;
        private String difference;
        private String regionFusionResult;
    }
}
```

- [ ] **Step 2: Service 方法**

```java
TargetFusionDetailDTO getFusionDetail(String targetName);
```

impl：
1. 查 target_analysis 表，按 target_name 聚合，组装 BasicInfo + sources。
2. 查 target_fusion 表（最新一条参与该 target_name 的融合记录），填 FusionResult。

- [ ] **Step 3: Controller**

```java
@GetMapping("/fusion/detail")
public ResultVO fusionDetail(@RequestParam String targetName) {
    return ResultVO.success(targetFusionService.getFusionDetail(targetName));
}
```

- [ ] **Step 4: 验证**

```bash
curl "http://localhost:8081/api/target/fusion/detail?targetName=哈尔科夫弹药库"
```

- [ ] **Step 5: Commit**

```bash
git add xwSystem/xwBackend
git commit -m "feat(target): GET /api/target/fusion/detail 融合详情接口"
```

---

## 阶段 6 — 收尾验证

### Task 13: 全量编译 + 接口冒烟

- [ ] **Step 1: 全量编译**

```bash
cd xwSystem/xwBackend && mvn clean compile
```
Expected: BUILD SUCCESS

- [ ] **Step 2: 启动应用**

```bash
mvn spring-boot:run
```
Expected: 启动无 SQL 异常（Mapper 加载、表存在）。

- [ ] **Step 3: 冒烟测试核心接口**

```bash
curl http://localhost:8081/api/directory/tree
curl "http://localhost:8081/api/uygur/filter?category=HZ报"
curl -X POST http://localhost:8081/api/uygur/filter/advanced -H "Content-Type: application/json" -d '{"targetKeywords":["弹药库"]}'
curl http://localhost:8081/api/target/alias/list
```

- [ ] **Step 4: Commit + 收尾**

```bash
git commit --allow-empty -m "chore: 20260613 系统对接全部任务完成"
```

---

## 自检（writing-plans self-review）

- 需求覆盖：1.1✅(Task 8) 1.2✅(Task 7) 1.3✅(Task 9) 1.4✅(Task 8) 1.5✅(无需改动) 2.1✅(Task 10) 2.2✅(Task 11) 2.3✅(Task 1 建表+已有逻辑) 2.4✅(Task 1 建表+已有逻辑) 2.5✅(Task 3 entity+Task 1 建表) 2.6✅(Task 12) — 全部覆盖。
- 类型一致：所有 id 全局 String、所有 jdbcType=VARCHAR；TargetFusion 已补 sourceCount/isFused 字段，Task 12 dto 字段命名一致。
- 占位符：无 TBD / 无 "类似 Task N"，所有代码块完整。
