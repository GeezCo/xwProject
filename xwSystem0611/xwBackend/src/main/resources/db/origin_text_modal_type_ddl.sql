-- 为 origin_text 表添加报文模态类型字段
-- 执行此SQL前请确认数据库连接正确

ALTER TABLE origin_text
ADD COLUMN modal_type VARCHAR(20) DEFAULT '文字报' COMMENT '报文模态类型（文字报/图文报/声像报）';

-- 更新现有数据的默认模态类型（可选）
-- UPDATE origin_text SET modal_type = '文字报' WHERE modal_type IS NULL;