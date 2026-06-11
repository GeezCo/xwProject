-- 属性抽取功能数据库DDL脚本
-- 执行前请确保已连接到 uygur_project 数据库

-- 1. 新建抽取结果表
CREATE TABLE IF NOT EXISTS `extraction_result` (
  `id` INT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  `origin_text_id` INT NOT NULL COMMENT '原始文本ID（关联origin_text.sid）',
  `extraction_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '抽取时间',
  `model` VARCHAR(50) DEFAULT 'GLM-5' COMMENT '使用的模型',
  `total_events` INT DEFAULT 0 COMMENT '抽取事件总数',
  `events_json` LONGTEXT COMMENT '事件抽取结果JSON',
  `status` VARCHAR(20) DEFAULT 'completed' COMMENT '状态：processing/completed/failed',
  `error_message` TEXT COMMENT '错误信息',
  UNIQUE KEY `uk_origin_text_id` (`origin_text_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='属性抽取结果表';

-- 2. 修改原始文本表，新增抽取状态字段
ALTER TABLE `origin_text` ADD COLUMN IF NOT EXISTS `is_extracted` TINYINT(1) DEFAULT 0 COMMENT '是否已抽取：0-否，1-是';

-- 3. 为origin_text表添加索引（如果不存在）
CREATE INDEX IF NOT EXISTS `idx_is_extracted` ON `origin_text` (`is_extracted`);