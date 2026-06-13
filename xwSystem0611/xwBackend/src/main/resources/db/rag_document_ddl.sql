-- RAG文档表DDL
-- 用于存储RAG模块上传、解析后的文档元信息

CREATE TABLE IF NOT EXISTS rag_document (
    doc_id VARCHAR(50) PRIMARY KEY COMMENT '文档唯一标识',
    original_filename VARCHAR(255) NOT NULL COMMENT '原始文件名',
    file_path VARCHAR(500) NOT NULL COMMENT '文件存储路径（MinIO路径）',
    content_type VARCHAR(50) COMMENT '文档类型（docx/pdf/txt）',
    file_size BIGINT COMMENT '文件大小（字节）',
    upload_time DATETIME NOT NULL COMMENT '上传时间',
    parse_status VARCHAR(20) NOT NULL DEFAULT 'pending' COMMENT '解析状态：pending/parsing/completed/failed',
    parse_time DATETIME COMMENT '解析完成时间',
    chunk_count INT DEFAULT 0 COMMENT '切片数量',
    metadata JSON COMMENT '文档元数据（解析器配置、OCR参数等）',
    error_message TEXT COMMENT '错误信息（解析失败时记录）',

    INDEX idx_upload_time (upload_time),
    INDEX idx_parse_status (parse_status),
    UNIQUE KEY uk_file_path (file_path)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RAG文档元信息表';
