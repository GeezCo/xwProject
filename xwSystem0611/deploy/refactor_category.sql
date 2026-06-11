-- ============================================
-- 献微系统 - 报文分类表重构脚本
-- 日期: 2026-06-09
-- 功能:
--   1. 重建 text_type 表为邻接表+物化路径混合结构
--   2. 支持最多5层分类层级
--   3. 迁移旧分类数据到"模拟数据"节点
--   4. 从 sendUnitName 自动创建叶子节点
-- ============================================

USE uygur_project;

SET FOREIGN_KEY_CHECKS = 0;

START TRANSACTION;

-- ============================================
-- 步骤 1: 备份旧表（如果存在）
-- ============================================
-- 先检查 text_type 是否存在
SET @table_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES
                     WHERE TABLE_SCHEMA = 'uygur_project'
                     AND TABLE_NAME = 'text_type');

-- 如果存在，则备份
SET @sql = IF(@table_exists > 0,
    'DROP TABLE IF EXISTS text_type_old; RENAME TABLE text_type TO text_type_old',
    'SELECT ''text_type does not exist, skip backup'' AS message');

-- 注意：RENAME 不能在 PREPARE 中执行，所以分两步
DROP TABLE IF EXISTS text_type_old;

SET @sql = IF(@table_exists > 0,
    'RENAME TABLE text_type TO text_type_old',
    'SELECT ''text_type does not exist'' AS message');

-- 如果 text_type 不存在但 text_type_old 存在，则恢复
SET @old_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES
                   WHERE TABLE_SCHEMA = 'uygur_project'
                   AND TABLE_NAME = 'text_type_old');

SET @need_restore = (@table_exists = 0 AND @old_exists > 0);

-- 执行 RENAME（只有当 text_type 存在时）
RENAME TABLE IF EXISTS text_type TO text_type_old;

-- ============================================
-- 步骤 2: 创建新分类表
-- ============================================
CREATE TABLE text_type (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '分类ID',
    name VARCHAR(100) NOT NULL UNIQUE COMMENT '分类名称（全局唯一）',
    parent_id BIGINT DEFAULT NULL COMMENT '父节点ID',
    level TINYINT NOT NULL DEFAULT 1 COMMENT '层级（1-5）',
    full_path VARCHAR(500) NOT NULL COMMENT '完整路径（如：根/单位A/部门B）',
    sort_order INT DEFAULT 0 COMMENT '同级排序',
    is_leaf TINYINT(1) DEFAULT 0 COMMENT '是否叶子节点（1=是，可挂载报文）',
    description VARCHAR(255) DEFAULT NULL COMMENT '分类描述',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    INDEX idx_parent_id (parent_id),
    INDEX idx_level (level),
    INDEX idx_full_path (full_path),
    INDEX idx_is_leaf (is_leaf),
    CONSTRAINT chk_text_type_level CHECK (level BETWEEN 1 AND 5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报文分类表（最多5层）';

-- ============================================
-- 步骤 3: 初始化默认根节点
-- ============================================
INSERT INTO text_type (id, name, parent_id, level, full_path, is_leaf, sort_order, description)
VALUES
    (1, '根分类', NULL, 1, '根分类', 0, 0, '系统根节点'),
    (2, '模拟数据', 1, 2, '根分类/模拟数据', 0, 1, '旧分类数据归档'),
    (3, '未分类', 1, 2, '根分类/未分类', 1, 999, '新导入无匹配单位的默认归属');

-- ============================================
-- 步骤 4: 迁移旧分类数据到"模拟数据"节点下
-- ============================================
-- 迁移"开源信息"下的二级分类
INSERT INTO text_type (name, parent_id, level, full_path, is_leaf, sort_order, description)
SELECT
    t.type_name,
    2,  -- 挂在"模拟数据"下
    3,
    CONCAT('根分类/模拟数据/', t.type_name),
    1,  -- 标记为叶子节点
    t.sid,
    '旧分类迁移'
FROM text_type_old t
WHERE t.parent_id = 0  -- "开源信息"的子分类
  AND t.type_name IS NOT NULL
ORDER BY t.sid;

-- 设置下一个自增ID（避免冲突）
SET @max_id = (SELECT IFNULL(MAX(id), 3) FROM text_type);
SET @sql = CONCAT('ALTER TABLE text_type AUTO_INCREMENT = ', @max_id + 1);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================
-- 步骤 5: 从 origin_text 提取所有 sendUnitName 作为叶子节点
-- ============================================
-- 插入到根分类下（管理员后续手动调整层级）
INSERT INTO text_type (name, parent_id, level, full_path, is_leaf, sort_order, description)
SELECT DISTINCT
    o.sendUnitName,
    1,  -- 默认挂在根分类下
    2,
    CONCAT('根分类/', o.sendUnitName),
    1,  -- 叶子节点
    1000 + ROW_NUMBER() OVER (ORDER BY o.sendUnitName),
    CONCAT('来源单位，共', COUNT(*), '篇报文')
FROM origin_text o
WHERE o.sendUnitName IS NOT NULL
  AND o.sendUnitName != ''
  AND o.sendUnitName NOT IN (SELECT name FROM text_type)
GROUP BY o.sendUnitName
ORDER BY o.sendUnitName;

-- ============================================
-- 步骤 6: 调整 origin_text 关联
-- ============================================
-- 修改 type 字段类型为 BIGINT
ALTER TABLE origin_text MODIFY COLUMN type BIGINT COMMENT '分类节点ID（关联text_type.id）';

-- 添加索引（先检查是否存在）
SET @idx_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
                   WHERE TABLE_SCHEMA = 'uygur_project'
                   AND TABLE_NAME = 'origin_text'
                   AND INDEX_NAME = 'idx_type');

SET @sql = IF(@idx_exists = 0,
    'ALTER TABLE origin_text ADD INDEX idx_type (type)',
    'SELECT ''idx_type already exists'' AS message');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================
-- 步骤 7: 关联报文到新分类节点
-- ============================================
-- 7.1 通过 sendUnitName 关联
UPDATE origin_text o
JOIN text_type t ON o.sendUnitName = t.name AND t.is_leaf = 1
SET o.type = t.id
WHERE o.sendUnitName IS NOT NULL
  AND o.sendUnitName != '';

-- 7.2 旧分类数据关联（通过旧的 type_name 匹配）
UPDATE origin_text o
JOIN text_type_old told ON o.type = told.id
JOIN text_type tnew ON told.type_name = tnew.name
SET o.type = tnew.id
WHERE o.sendUnitName IS NULL OR o.sendUnitName = ''
  AND told.type_name IS NOT NULL;

-- 7.3 剩余未匹配的归入"未分类"
UPDATE origin_text
SET type = 3
WHERE type IS NULL
   OR type NOT IN (SELECT id FROM text_type);

-- ============================================
-- 验证结果
-- ============================================
SELECT '========== 分类表结构验证 ==========' AS title;

SELECT
    COLUMN_NAME AS 字段名,
    DATA_TYPE AS 数据类型,
    COLUMN_TYPE AS 完整类型,
    COLUMN_COMMENT AS 注释
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = 'uygur_project'
  AND TABLE_NAME = 'text_type'
ORDER BY ORDINAL_POSITION;

SELECT '========== 分类层级统计 ==========' AS title;

SELECT
    level AS 层级,
    COUNT(*) AS 节点数,
    SUM(is_leaf) AS 叶子节点数
FROM text_type
GROUP BY level
ORDER BY level;

SELECT '========== 分类树预览（前30个节点）==========' AS title;

SELECT
    CONCAT(REPEAT('  ', level - 1), name) AS 分类树,
    id,
    level AS 层级,
    is_leaf AS 叶子,
    full_path AS 完整路径
FROM text_type
ORDER BY full_path
LIMIT 30;

SELECT '========== 报文关联统计 ==========' AS title;

SELECT
    t.name AS 分类名称,
    t.level AS 层级,
    COUNT(o.sid) AS 报文数
FROM text_type t
LEFT JOIN origin_text o ON t.id = o.type
WHERE t.is_leaf = 1
GROUP BY t.id, t.name, t.level
HAVING COUNT(o.sid) > 0
ORDER BY COUNT(o.sid) DESC
LIMIT 20;

SELECT '========== 总体数据统计 ==========' AS title;

SELECT
    (SELECT COUNT(*) FROM text_type) AS 总分类节点数,
    (SELECT COUNT(*) FROM text_type WHERE is_leaf = 1) AS 叶子节点数,
    (SELECT COUNT(*) FROM origin_text) AS 总报文数,
    (SELECT COUNT(*) FROM origin_text WHERE type = 3) AS 未分类报文数,
    (SELECT COUNT(DISTINCT type) FROM origin_text) AS 已使用分类数;

-- 提交事务
COMMIT;

SET FOREIGN_KEY_CHECKS = 1;

SELECT '========== 执行完成 ==========' AS title;
SELECT '提示：执行完成后可删除备份表 text_type_old' AS message;
