package com.intel.rag.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 文档数据模型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Document {

    /**
     * 文档唯一标识
     */
    private String id;

    /**
     * 文档内容
     */
    private String content;

    /**
     * 文档来源（mysql, docx等）
     */
    private String source;

    /**
     * 文档类型（text, table, image等）
     */
    private String type;

    /**
     * 元数据
     */
    private Map<String, Object> metadata;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
