package com.intel.rag.model;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * 检索结果
 */
@Data
@Builder
public class SearchResult {

    /**
     * 切片ID
     */
    private String chunkId;

    /**
     * 文档ID
     */
    private String documentId;

    /**
     * 内容
     */
    private String content;

    /**
     * 融合得分
     */
    private Float score;

    /**
     * BM25得分
     */
    private Float bm25Score;

    /**
     * 向量相似度得分
     */
    private Float vectorScore;

    /**
     * 切片索引
     */
    private Integer chunkIndex;

    /**
     * 切片类型
     */
    private String chunkType;

    /**
     * 元数据
     */
    private Map<String, Object> metadata;

    /**
     * 排名
     */
    private Integer rank;
}
