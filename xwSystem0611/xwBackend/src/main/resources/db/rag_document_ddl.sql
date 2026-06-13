CREATE TABLE IF NOT EXISTS rag_document (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    doc_id VARCHAR(64) NOT NULL UNIQUE COMMENT '文档唯一ID',
    filename VARCHAR(255) COMMENT '原始文件名',
    file_size BIGINT COMMENT '文件字节数',
    chunk_count INT DEFAULT 0 COMMENT '切片数量',
    status VARCHAR(20) NOT NULL DEFAULT 'pending' COMMENT 'pending/indexed/failed',
    error_msg TEXT COMMENT '失败原因',
    upload_time DATETIME NOT NULL,
    indexed_time DATETIME,
    INDEX idx_status (status),
    INDEX idx_upload_time (upload_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RAG 上传文档元数据';
