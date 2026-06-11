package com.intel.rag.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 文档切片模型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentChunk {

    /**
     * 切片唯一标识
     */
    private String id;

    /**
     * 父文档ID
     */
    private String documentId;

    /**
     * 切片内容
     */
    private String content;

    /**
     * 切片序号
     */
    private int chunkIndex;

    /**
     * 向量表示（1024维）
     * 使用float[]以与pgvector兼容
     */
    private float[] embedding;

    /**
     * 元数据（继承自父文档）
     */
    private Map<String, Object> metadata;

    /**
     * 切片类型（short, medium, long, table等）
     */
    private String chunkType;

    /**
     * 字符长度
     */
    private int length;

    /**
     * 检索得分
     */
    private Float score;
}
