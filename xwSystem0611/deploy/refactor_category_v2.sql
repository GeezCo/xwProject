-- ============================================
-- 献微系统 - 报文分类表重构脚本
-- 日期: 2026-06-09
-- 说明: text_type 表已丢失，直接创建新表
-- ============================================

USE uygur_project;

SET FOREIGN_KEY_CHECKS = 0;

START TRANSACTION;

-- ============================================
-- 步骤 1: 创建新分类表
-- ============================================
DROP TABLE IF EXISTS text_type;

CREATE TABLE text_type (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '分类ID',
    name VARCHAR(100) NOT NULL UNIQUE COMMENT '分类名称（全局唯一）',
    parent_id BIGINT DEFAULT NULL COMMENT '父节点ID',
    level TINYINT NOT NULL DEFAULT 1 COMMENT '层级（1-5）',
    full_path VARCHAR(500) NOT NULL COMMENT '完整路径',
    sort_order INT DEFAULT 0 COMMENT '同级排序',
    is_leaf TINYINT(1) DEFAULT 0 COMMENT '是否叶子节点',
    description VARCHAR(255) DEFAULT NULL COMMENT '分类描述',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_parent_id (parent_id),
    INDEX idx_level (level),
    INDEX idx_full_path (full_path),
    INDEX idx_is_leaf (is_leaf),
    CONSTRAINT chk_text_type_level CHECK (level BETWEEN 1 AND 5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报文分类表（最多5层）';

-- ============================================
-- 步骤 2: 初始化默认根节点
-- ============================================
INSERT INTO text_type (id, name, parent_id, level, full_path, is_leaf, sort_order, description)
VALUES
    (1, '根分类', NULL, 1, '根分类', 0, 0, '系统根节点'),
    (2, '未分类', 1, 2, '根分类/未分类', 1, 999, '新导入无匹配单位的默认归属');

-- ============================================
-- 步骤 3: 从 origin_text 提取所有 sendUnitName 作为叶子节点
-- ============================================
INSERT INTO text_type (name, parent_id, level, full_path, is_leaf, sort_order, description)
SELECT
    sendUnitName,
    1,
    2,
    CONCAT('根分类/', sendUnitName),
    1,
    1000 + ROW_NUMBER() OVER (ORDER BY sendUnitName),
    CONCAT('来源单位，共', COUNT(*), '篇报文')
FROM origin_text
WHERE sendUnitName IS NOT NULL
  AND sendUnitName != ''
GROUP BY sendUnitName
ORDER BY sendUnitName;

-- ============================================
-- 步骤 4: 调整 origin_text 关联
-- ============================================
ALTER TABLE origin_text MODIFY COLUMN type BIGINT COMMENT '分类节点ID';

-- 添加索引（检查是否存在）
SET @idx_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
                   WHERE TABLE_SCHEMA = 'uygur_project'
                   AND TABLE_NAME = 'origin_text'
                   AND INDEX_NAME = 'idx_type');

SET @sql = IF(@idx_exists = 0,
    'ALTER TABLE origin_text ADD INDEX idx_type (type)',
    'SELECT ''idx_type exists'' AS msg');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================
-- 步骤 5: 关联报文到分类节点
-- ============================================
-- 通过 sendUnitName 关联
UPDATE origin_text o
JOIN text_type t ON o.sendUnitName = t.name AND t.is_leaf = 1
SET o.type = t.id
WHERE o.sendUnitName IS NOT NULL
  AND o.sendUnitName != '';

-- 未匹配的归入"未分类"
UPDATE origin_text
SET type = 2
WHERE type IS NULL
   OR type NOT IN (SELECT id FROM text_type);

-- ============================================
-- Verification
-- ============================================
SELECT 'Category Level Statistics' AS title;

SELECT
    level,
    COUNT(*) AS node_count,
    SUM(is_leaf) AS leaf_count
FROM text_type
GROUP BY level
ORDER BY level;

SELECT 'Category Tree Preview (Top 20)' AS title;

SELECT
    CONCAT(REPEAT('  ', level - 1), name) AS tree,
    id,
    level,
    is_leaf,
    full_path
FROM text_type
ORDER BY full_path
LIMIT 20;

SELECT 'Report Statistics (TOP 10)' AS title;

SELECT
    t.name,
    COUNT(o.sid) AS report_count
FROM text_type t
LEFT JOIN origin_text o ON t.id = o.type
WHERE t.is_leaf = 1
GROUP BY t.id, t.name
HAVING COUNT(o.sid) > 0
ORDER BY COUNT(o.sid) DESC
LIMIT 10;

SELECT 'Overall Statistics' AS title;

SELECT
    (SELECT COUNT(*) FROM text_type) AS total_categories,
    (SELECT COUNT(*) FROM text_type WHERE is_leaf = 1) AS leaf_categories,
    (SELECT COUNT(*) FROM origin_text) AS total_reports,
    (SELECT COUNT(*) FROM origin_text WHERE type = 2) AS uncategorized_reports,
    (SELECT COUNT(DISTINCT type) FROM origin_text) AS used_categories;

COMMIT;

SET FOREIGN_KEY_CHECKS = 1;

SELECT 'Execution Completed' AS status;
