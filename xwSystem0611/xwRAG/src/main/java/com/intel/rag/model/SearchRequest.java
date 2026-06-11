package com.intel.rag.model;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * 检索请求
 */
@Data
@Builder
public class SearchRequest {

    /**
     * 查询文本
     */
    private String query;

    /**
     * 返回结果数量（默认10）
     */
    @Builder.Default
    private Integer topK = 10;

    /**
     * BM25权重（默认0.3）
     */
    @Builder.Default
    private Float bm25Weight = 0.3f;

    /**
     * 向量权重（默认0.7）
     */
    @Builder.Default
    private Float vectorWeight = 0.7f;

    /**
     * 是否混合检索（默认true）
     */
    @Builder.Default
    private Boolean hybridSearch = true;

    /**
     * 过滤条件
     */
    private Map<String, Object> filters;

    /**
     * kNN候选数量（默认100）
     */
    @Builder.Default
    private Integer numCandidates = 100;
}
