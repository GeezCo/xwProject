-- 事件分析结果表
CREATE TABLE event_analysis (
    id INT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    origin_text_id INT NOT NULL COMMENT '原始报文ID（关联 origin_text.sid）',
    event_time VARCHAR(100) COMMENT '事件时间',
    event_location VARCHAR(200) COMMENT '事件地点',
    event_content TEXT COMMENT '事件内容',
    event_analysis TEXT COMMENT '事件分析（6维分析）',
    analysis_date DATE NOT NULL COMMENT '分析日期（用于日期范围查询）',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_origin_text_id (origin_text_id),
    INDEX idx_analysis_date (analysis_date),
    UNIQUE KEY uk_origin_event (origin_text_id, event_content(100))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='事件分析结果表';
