-- ============================================
-- 献微系统 - origin_text 表结构调整脚本（完整版）
-- 日期: 2026-06-09
-- 功能:
--   1. times 字段从 VARCHAR 转为 DATETIME
--   2. 新增 briefTypeName 字段（简报类型名称）
--   3. 新增 sendUnitName 字段（发送单位名称）
--   4. 添加索引优化查询性能
-- ============================================

USE uygur_project;

-- 禁用外键检查（提高执行速度）
SET FOREIGN_KEY_CHECKS = 0;

-- 开始事务
START TRANSACTION;

-- ============================================
-- 步骤 1: 处理 times 字段
-- ============================================

-- 检查 times 当前类型
SET @times_type = (
    SELECT DATA_TYPE
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = 'uygur_project'
      AND TABLE_NAME = 'origin_text'
      AND COLUMN_NAME = 'times'
);

-- 如果 times 还是 VARCHAR，则进行转换
SET @need_convert = (@times_type = 'varchar');

-- 清理无效的日期值（空字符串、异常格式）
UPDATE origin_text
SET times = NULL
WHERE @need_convert = 1
  AND (
    times = ''
    OR times IS NULL
    OR times NOT REGEXP '^[0-9]{4}-[0-9]{2}-[0-9]{2}$'
    OR times LIKE 'X-%'
    OR STR_TO_DATE(times, '%Y-%m-%d') IS NULL
  );

-- 转换 times 字段类型为 DATETIME
SET @sql_convert_times = IF(
    @need_convert = 1,
    'ALTER TABLE origin_text MODIFY COLUMN times DATETIME DEFAULT NULL COMMENT ''报文时间''',
    'SELECT ''times already DATETIME'' AS message'
);
PREPARE stmt FROM @sql_convert_times;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================
-- 步骤 2: 添加新字段
-- ============================================

-- 检查 briefTypeName 是否存在
SET @col_exists = (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = 'uygur_project'
      AND TABLE_NAME = 'origin_text'
      AND COLUMN_NAME = 'briefTypeName'
);

-- 添加 briefTypeName 字段
SET @sql_add_briefType = IF(
    @col_exists = 0,
    'ALTER TABLE origin_text ADD COLUMN briefTypeName VARCHAR(100) DEFAULT NULL COMMENT ''简报类型名称'' AFTER modal_type',
    'SELECT ''briefTypeName exists'' AS message'
);
PREPARE stmt FROM @sql_add_briefType;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 检查 sendUnitName 是否存在
SET @col_exists = (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = 'uygur_project'
      AND TABLE_NAME = 'origin_text'
      AND COLUMN_NAME = 'sendUnitName'
);

-- 添加 sendUnitName 字段
SET @sql_add_sendUnit = IF(
    @col_exists = 0,
    'ALTER TABLE origin_text ADD COLUMN sendUnitName VARCHAR(100) DEFAULT NULL COMMENT ''发送单位名称'' AFTER briefTypeName',
    'SELECT ''sendUnitName exists'' AS message'
);
PREPARE stmt FROM @sql_add_sendUnit;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================
-- 步骤 3: 添加索引
-- ============================================

-- 添加 times 索引
SET @idx_exists = (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = 'uygur_project'
      AND TABLE_NAME = 'origin_text'
      AND INDEX_NAME = 'idx_times'
);

SET @sql_add_idx = IF(
    @idx_exists = 0,
    'ALTER TABLE origin_text ADD INDEX idx_times (times)',
    'SELECT ''idx_times exists'' AS message'
);
PREPARE stmt FROM @sql_add_idx;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 添加 briefTypeName 索引
SET @idx_exists = (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = 'uygur_project'
      AND TABLE_NAME = 'origin_text'
      AND INDEX_NAME = 'idx_briefTypeName'
);

SET @sql_add_idx = IF(
    @idx_exists = 0,
    'ALTER TABLE origin_text ADD INDEX idx_briefTypeName (briefTypeName)',
    'SELECT ''idx_briefTypeName exists'' AS message'
);
PREPARE stmt FROM @sql_add_idx;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 添加 sendUnitName 索引
SET @idx_exists = (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = 'uygur_project'
      AND TABLE_NAME = 'origin_text'
      AND INDEX_NAME = 'idx_sendUnitName'
);

SET @sql_add_idx = IF(
    @idx_exists = 0,
    'ALTER TABLE origin_text ADD INDEX idx_sendUnitName (sendUnitName)',
    'SELECT ''idx_sendUnitName exists'' AS message'
);
PREPARE stmt FROM @sql_add_idx;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================
-- 步骤 4: 清理备份列（如果存在）
-- ============================================

SET @backup_exists = (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = 'uygur_project'
      AND TABLE_NAME = 'origin_text'
      AND COLUMN_NAME = 'times_backup'
);

SET @sql_drop_backup = IF(
    @backup_exists > 0,
    'ALTER TABLE origin_text DROP COLUMN times_backup',
    'SELECT ''times_backup not exists'' AS message'
);
PREPARE stmt FROM @sql_drop_backup;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================
-- 验证结果
-- ============================================

SELECT '========== 字段结构验证 ==========' AS title;

SELECT
    COLUMN_NAME AS 字段名,
    DATA_TYPE AS 数据类型,
    COLUMN_TYPE AS 完整类型,
    IS_NULLABLE AS 可空,
    COLUMN_DEFAULT AS 默认值,
    COLUMN_COMMENT AS 注释
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = 'uygur_project'
  AND TABLE_NAME = 'origin_text'
  AND COLUMN_NAME IN ('times', 'briefTypeName', 'sendUnitName')
ORDER BY ORDINAL_POSITION;

SELECT '========== 索引验证 ==========' AS title;

SELECT
    INDEX_NAME AS 索引名,
    COLUMN_NAME AS 字段名,
    INDEX_TYPE AS 索引类型
FROM INFORMATION_SCHEMA.STATISTICS
WHERE TABLE_SCHEMA = 'uygur_project'
  AND TABLE_NAME = 'origin_text'
  AND INDEX_NAME IN ('idx_times', 'idx_briefTypeName', 'idx_sendUnitName')
ORDER BY INDEX_NAME, SEQ_IN_INDEX;

SELECT '========== 数据统计 ==========' AS title;

SELECT
    COUNT(*) AS 总记录数,
    COUNT(times) AS 有效时间,
    COUNT(briefTypeName) AS briefTypeName非空,
    COUNT(sendUnitName) AS sendUnitName非空,
    ROUND(COUNT(times) * 100.0 / COUNT(*), 2) AS 时间填充率
FROM origin_text;

-- 提交事务
COMMIT;

-- 恢复外键检查
SET FOREIGN_KEY_CHECKS = 1;

SELECT '========== 执行完成 ==========' AS title;
