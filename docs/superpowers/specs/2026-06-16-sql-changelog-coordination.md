# SQL 集中协调方案（多窗口/多人并发）

> 日期：2026-06-16
> 作者：后端工程师
> 状态：草案

## 1. Context

当前 4 个窗口/多人同时改 schema，18 个 SQL 文件散落在 4 个目录（`docs/sql/`、`xwSystem/deploy/`、`xwSystem/xwBackend/src/main/resources/db/`、还有 `2026-06-14-system-integration.sql`）。没有执行顺序、没有幂等保证、没有版本追踪、没有作者署名。在 47000 行生产数据上每一次 `ALTER` 都是高风险操作。

目标：**所有 schema 变更集中到一个单调递增的变更日志文件**，任何窗口/任何人改 schema 只能在这个文件末尾追加。其他 `.sql` 文件全部废弃删除。

## 2. 唯一真相源

```
xwSystem/xwBackend/src/main/resources/db/changelog.sql
```

**所有 schema 改动**（DDL / ALTER / DML 修复）写到这个文件的**末尾**。一律追加，禁止修改历史。

## 3. 文件结构

```sql
-- ============================================================
-- xwBackend SCHEMA CHANGELOG
-- 唯一真相源，禁止删除/修改历史 changeset
-- 新增改动：在末尾追加，按以下模板
-- ============================================================

-- ============================================================
-- changeset id=0001 author=zhang date=2026-06-14
-- desc: 加 origin_text.category 字段（分类粗粒度）
-- depends: 无
-- ============================================================
ALTER TABLE origin_text
  ADD COLUMN IF NOT EXISTS category VARCHAR(20) NULL COMMENT '分类:开源/HZ/JZ/未分类'
  AFTER send_unit_name;
-- end changeset 0001

-- ============================================================
-- changeset id=0002 author=wq date=2026-06-15
-- desc: text_type 加 parent_id 支持树形
-- depends: 0001
-- ============================================================
ALTER TABLE text_type
  ADD COLUMN IF NOT EXISTS parent_id VARCHAR(36) NULL AFTER name,
  ADD COLUMN IF NOT EXISTS level TINYINT NOT NULL DEFAULT 1 AFTER parent_id,
  ADD COLUMN IF NOT EXISTS full_path VARCHAR(500) NOT NULL DEFAULT '' AFTER level,
  ADD INDEX IF NOT EXISTS idx_parent_id (parent_id),
  ADD INDEX IF NOT EXISTS idx_full_path (full_path);
-- end changeset 0002
```

### Changeset 模板（每段必须遵守）

```sql
-- ============================================================
-- changeset id=NNNN author=<花名/git用户> date=YYYY-MM-DD
-- desc: 一句话说明做了什么 / 为什么
-- depends: <依赖的 changeset id，没有写"无">
-- ============================================================
<可被重复执行的 SQL>
-- end changeset NNNN
```

**id**：4 位数字单调递增，向下文件追加时取上一个 +1。
**author**：你的花名或 git username。
**desc**：业务原因 + 做了什么。
**depends**：前置依赖 changeset，便于 review 时检查顺序。

## 4. 幂等约束（强制）

每段 SQL 必须可重复执行而不报错。规则：

| 操作 | 模板 |
|---|---|
| 加列 | `ALTER TABLE x ADD COLUMN IF NOT EXISTS ...` |
| 删列 | `ALTER TABLE x DROP COLUMN IF EXISTS ...` |
| 加索引 | `ALTER TABLE x ADD INDEX IF NOT EXISTS idx_xxx (col)` |
| 建表 | `CREATE TABLE IF NOT EXISTS ...` |
| 数据修复 | `UPDATE ... WHERE col IS NULL` 等带条件的 SQL |
| 改列类型 | 用 `INFORMATION_SCHEMA` 先判断当前类型再决定改不改（见示例 5.2）|

**禁止**：
- ❌ `DROP TABLE` / `TRUNCATE TABLE`（如必须，必须在 changeset 头部红字警告）
- ❌ 不带 WHERE 的 UPDATE / DELETE
- ❌ `RENAME TABLE`（涉及生产数据迁移走特殊流程）
- ❌ 修改历史 changeset（如果发现 0003 的 SQL 错了，新增 0017 来纠正它，**不要回去改 0003**）

## 5. 复杂场景示例

### 5.1 加列 + 回填默认值

```sql
-- changeset id=0010 author=zhang date=2026-06-16
-- desc: origin_text 新加 keyword 字段，旧数据回填空字符串
ALTER TABLE origin_text ADD COLUMN IF NOT EXISTS keyword VARCHAR(255) NULL AFTER content;
UPDATE origin_text SET keyword = '' WHERE keyword IS NULL;
-- end changeset 0010
```

### 5.2 改列类型（条件判断 + 改）

```sql
-- changeset id=0011 author=zhang date=2026-06-16
-- desc: extraction_result.id 从 INT 改成 VARCHAR(32) 对齐其他表
SET @col_type := (SELECT COLUMN_TYPE FROM information_schema.COLUMNS
                  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'extraction_result' AND COLUMN_NAME = 'id');
SET @stmt := IF(@col_type = 'varchar(32)',
                'SELECT "skip: already varchar"',
                'ALTER TABLE extraction_result MODIFY COLUMN id VARCHAR(32) NOT NULL');
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;
-- end changeset 0011
```

### 5.3 建新表

```sql
-- changeset id=0012 author=wq date=2026-06-16
-- desc: 新建 sync_brief 报文同步表
CREATE TABLE IF NOT EXISTS sync_brief (
  id          VARCHAR(32) NOT NULL PRIMARY KEY,
  title       VARCHAR(255) NOT NULL,
  content     TEXT NOT NULL,
  status      SMALLINT DEFAULT 1,
  sync_time   DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报文同步表';
-- end changeset 0012
```

## 6. 工作流（每次改 schema 的人必读）

1. **拉最新 changelog.sql**：`git pull` 确保看到所有同事的 changeset
2. **看末尾最新 id**：比如 0023
3. **新增 changeset id=0024**：在末尾追加，按模板写
4. **本地试跑**：连测试库执行新增的 SQL，确认幂等（连跑两次都不报错）
5. **commit**：commit message 用 `db(schema): <desc> [#0024]`
6. **生产执行**：把 0024 这段 SQL 单独贴到生产 MySQL 客户端跑（不跑整个 changelog）

### Schema 版本表（可选 P1）

如果你想要严格的"已执行追踪"，建一张元数据表：

```sql
-- changeset id=0001 author=system date=2026-06-16
-- desc: 元数据表，追踪已执行的 changeset
CREATE TABLE IF NOT EXISTS schema_changelog (
  id           VARCHAR(8) NOT NULL PRIMARY KEY,
  author       VARCHAR(50) NOT NULL,
  applied_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  description  VARCHAR(500) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='schema 变更追踪';
-- end changeset 0001
```

每次执行 changeset 后手动 INSERT 一行：
```sql
INSERT IGNORE INTO schema_changelog (id, author, description)
VALUES ('0024', 'zhang', '加 origin_text.keyword 字段');
```

## 7. 迁移现有 18 个 SQL 文件到 changelog

**重组步骤**：

| 步骤 | 操作 |
|---|---|
| 1 | 创建 `src/main/resources/db/changelog.sql`，写头部说明 |
| 2 | 把现有 18 个文件中的有效 SQL 按时间顺序提取，封装成 changeset 0001-00NN |
| 3 | 每个 changeset 加幂等保护（IF NOT EXISTS 等）|
| 4 | **删除** 17 个散落 SQL 文件（保留 `deploy/init.sql` 作为生产数据备份）|
| 5 | 在 `docs/` 加一个`SQL变更协议.md` 指向 changelog.sql 和本规范 |

**保留的文件**：
- `xwSystem/xwBackend/src/main/resources/db/changelog.sql` ⭐ 唯一真相源
- `xwSystem/deploy/init.sql` （41MB 生产数据 dump，不动）

**删除的文件**（17 个）：
- `docs/sql/2026-06-14-system-integration.sql`
- `xwSystem/deploy/alter_origin_text.sql`
- `xwSystem/deploy/refactor_category.sql`
- `xwSystem/deploy/refactor_category_v2.sql`
- `xwSystem/xwBackend/src/main/resources/db/{14 个 .sql}`

## 8. 重组后目录结构

```
xwSystem/xwBackend/src/main/resources/db/
├── changelog.sql              ⭐ 唯一 SQL 入口
└── README.md                  规范说明（指向本文档）
```

## 9. 为什么不引入 Flyway/Liquibase

| 维度 | 我们的方案 | Flyway/Liquibase |
|---|---|---|
| 学习成本 | 低（纯 SQL）| 中（要学 yaml/xml DSL）|
| 离线部署兼容 | ✅ 任何 MySQL client 能跑 | 需带 jar/插件 |
| 自动化执行 | ❌ 需手动跑 | ✅ Spring Boot 启动自动跑 |
| 强校验 | ❌ 靠 PR review | ✅ 强校验 |

**结论**：当前阶段离线部署、人少、改动频繁，**纯文件协议 + PR review** 足够。后续团队规模上来再引入 Flyway。

## 10. 文件清单

| 操作 | 路径 |
|---|---|
| ➕ 新建 | `xwSystem/xwBackend/src/main/resources/db/changelog.sql` |
| ➕ 新建 | `xwSystem/xwBackend/src/main/resources/db/README.md` |
| ✏️ 改 | `xwSystem/xwBackend/CLAUDE.md`（增加 schema 变更协议章节）|
| ❌ 删 | 17 个散落 SQL 文件 |
| 🚫 不动 | `xwSystem/deploy/init.sql`（生产数据备份）|

## 11. 验证

```bash
# 1. 在测试库连续执行 2 次 changelog.sql，第二次应该无任何 ERROR
mysql -h 36.141.21.176 -P 9204 -u root -p < changelog.sql
mysql -h 36.141.21.176 -P 9204 -u root -p < changelog.sql   # 幂等性测试

# 2. 全量回归测试（确认 schema 重组没破坏现有功能）
cd xwSystem/xwBackend && mvn test -Dtest='com.qy.dch.api.*ApiTest'
# Expected: 75/75 BUILD SUCCESS

# 3. 验证 db/ 目录只剩 2 个文件
ls xwSystem/xwBackend/src/main/resources/db/
# Expected: changelog.sql  README.md
```

## 12. 不做的事

- ❌ 不引入 Flyway / Liquibase（保持纯 SQL）
- ❌ 不动 `init.sql`（41MB 生产数据备份，独立用途）
- ❌ 不立即执行任何破坏性操作（DROP/TRUNCATE）
- ❌ 不修改算法仓库 / 前端仓库的任何 SQL
