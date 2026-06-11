-- ============================================
-- 献微系统 - origin_text 表结构调整脚本
-- 日期: 2026-06-09
-- 说明:
--   1. times 字段从 VARCHAR(255) 改为 DATETIME
--   2. 新增 briefTypeName 字段 (简报类型名称)
--   3. 新增 sendUnitName 字段 (发送单位名称)
-- ============================================

USE uygur_project;

-- 开始事务
START TRANSACTION;

-- 备份当前 times 字段数据到临时列
ALTER TABLE origin_text ADD COLUMN times_backup VARCHAR(255);
UPDATE origin_text SET times_backup = times;

-- 1. 修改 times 字段类型为 DATETIME
-- 先清空所有空字符串和无效日期格式的数据，再转换类型
UPDATE origin_text
SET times = NULL
WHERE times = ''  -- 清空空字符串
   OR times IS NULL
   OR times NOT REGEXP '^[0-9]{4}-[0-9]{2}-[0-9]{2}$'  -- 不符合 YYYY-MM-DD 格式
   OR times LIKE 'X-%'  -- 年份为 X 的异常数据
   OR STR_TO_DATE(times, '%Y-%m-%d') IS NULL;  -- 无法转换为日期

-- 修改字段类型
ALTER TABLE origin_text
MODIFY COLUMN times DATETIME DEFAULT NULL COMMENT '报文时间';

-- 2. 新增 briefTypeName 字段
ALTER TABLE origin_text
ADD COLUMN briefTypeName VARCHAR(100) DEFAULT NULL COMMENT '简报类型名称'
AFTER modal_type;

-- 3. 新增 sendUnitName 字段
ALTER TABLE origin_text
ADD COLUMN sendUnitName VARCHAR(100) DEFAULT NULL COMMENT '发送单位名称'
AFTER briefTypeName;

-- 添加索引以提升查询性能
ALTER TABLE origin_text
ADD INDEX idx_times (times),
ADD INDEX idx_briefTypeName (briefTypeName),
ADD INDEX idx_sendUnitName (sendUnitName);

-- 验证修改结果
SELECT
    COLUMN_NAME,
    DATA_TYPE,
    COLUMN_TYPE,
    IS_NULLABLE,
    COLUMN_DEFAULT,
    COLUMN_COMMENT
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = 'uygur_project'
  AND TABLE_NAME = 'origin_text'
  AND COLUMN_NAME IN ('times', 'briefTypeName', 'sendUnitName')
ORDER BY ORDINAL_POSITION;

-- 检查数据转换情况
SELECT
    COUNT(*) as total_records,
    COUNT(times) as valid_times,
    COUNT(times_backup) as original_times,
    COUNT(briefTypeName) as has_briefType,
    COUNT(sendUnitName) as has_sendUnit
FROM origin_text;

-- 提交事务（执行前请先检查验证结果）
-- COMMIT;

-- 如果需要回滚，执行以下命令：
-- ROLLBACK;

-- ============================================
-- 执行后的清理操作（确认无误后再执行）
-- ============================================
-- ALTER TABLE origin_text DROP COLUMN times_backup;
