-- ============================================
-- 献微系统 - origin_text 补充字段脚本
-- 日期: 2026-06-09
-- 说明: times已经是DATETIME，只需添加新字段
-- ============================================

USE uygur_project;

START TRANSACTION;

-- 1. 新增 briefTypeName 字段
ALTER TABLE origin_text
ADD COLUMN briefTypeName VARCHAR(100) DEFAULT NULL COMMENT '简报类型名称'
AFTER modal_type;

-- 2. 新增 sendUnitName 字段
ALTER TABLE origin_text
ADD COLUMN sendUnitName VARCHAR(100) DEFAULT NULL COMMENT '发送单位名称'
AFTER briefTypeName;

-- 3. 添加索引
ALTER TABLE origin_text ADD INDEX idx_times (times);
ALTER TABLE origin_text ADD INDEX idx_briefTypeName (briefTypeName);
ALTER TABLE origin_text ADD INDEX idx_sendUnitName (sendUnitName);

-- 4. 删除备份列
ALTER TABLE origin_text DROP COLUMN times_backup;

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
