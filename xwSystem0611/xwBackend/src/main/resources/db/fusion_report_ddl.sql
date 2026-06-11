-- 融合报告表DDL
-- 用于存储多篇报文融合生成的综合报告

CREATE TABLE IF NOT EXISTS fusion_report (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '融合报告ID',
    title VARCHAR(200) NOT NULL COMMENT '报告标题',
    summary TEXT COMMENT '报告摘要',
    timeline JSON COMMENT '事件时间线（JSON格式）',
    content TEXT COMMENT '详细内容',
    entities JSON COMMENT '关键实体（人物/组织/地点）',
    labels JSON COMMENT '综合标签',
    source_ids VARCHAR(100) COMMENT '参与融合的报文ID列表（逗号分隔）',
    model_used VARCHAR(50) COMMENT '使用的大模型',
    create_time DATETIME COMMENT '创建时间',
    update_time DATETIME COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报文融合报告表';