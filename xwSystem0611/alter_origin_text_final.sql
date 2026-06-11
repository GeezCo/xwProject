-- ============================================
-- 献微系统 - origin_text 表结构调整脚本（最终版）
-- 日期: 2026-06-09
-- ============================================

USE uygur_project;

START TRANSACTION;

-- 1. 清理并转换 times 字段
UPDATE origin_text
SET times = NULL
WHERE times = ''
   OR times NOT REGEXP '^[0-9]{4}-[0-9]{2}-[0-9]{2}$'
   OR times LIKE 'X-%'
   OR STR_TO_DATE(times, '%Y-%m-%d') IS NULL;

-- 修改 times 字段类型为 DATETIME
ALTER TABLE origin_text
MODIFY COLUMN times DATETIME DEFAULT NULL COMMENT '报文时间';

-- 2. 新增 briefTypeName 字段（手动检查是否存在）
-- 如果报错 Duplicate column，说明字段已存在，可忽略该错误
ALTER TABLE origin_text
ADD COLUMN briefTypeName VARCHAR(100) DEFAULT NULL COMMENT '简报类型名称'
AFTER modal_type;

-- 3. 新增 sendUnitName 字段
ALTER TABLE origin_text
ADD COLUMN sendUnitName VARCHAR(100) DEFAULT NULL COMMENT '发送单位名称'
AFTER briefTypeName;

-- 4. 添加索引
ALTER TABLE origin_text ADD INDEX idx_times (times);
ALTER TABLE origin_text ADD INDEX idx_briefTypeName (briefTypeName);
ALTER TABLE origin_text ADD INDEX idx_sendUnitName (sendUnitName);

-- 验证结果
SELECT
    COLUMN_NAME,
    DATA_TYPE,
    COLUMN_TYPE,
    COLUMN_COMMENT
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = 'uygur_project'
  AND TABLE_NAME = 'origin_text'
  AND COLUMN_NAME IN ('times', 'briefTypeName', 'sendUnitName')
ORDER BY ORDINAL_POSITION;

-- 统计数据
SELECT
    COUNT(*) as total_records,
    COUNT(times) as valid_times,
    COUNT(briefTypeName) as has_briefType,
    COUNT(sendUnitName) as has_sendUnit
FROM origin_text;

COMMIT;

-- 清理备份列（如果存在）
-- ALTER TABLE origin_text DROP COLUMN times_backup;
