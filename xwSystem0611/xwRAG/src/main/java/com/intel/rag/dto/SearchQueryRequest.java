package com.intel.rag.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import java.util.Map;

/**
 * 检索查询请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchQueryRequest {

    /**
     * 查询文本
     */
    @NotBlank(message = "查询文本不能为空")
    private String query;

    /**
     * 返回结果数量
     */
    @Min(value = 1, message = "topK必须大于0")
    @Builder.Default
    private Integer topK = 10;

    /**
     * 检索类型：hybrid（混合）、bm25（关键词）、vector（向量）
     */
    @Builder.Default
    private String searchType = "hybrid";

    /**
     * BM25权重
     */
    @Builder.Default
    private Float bm25Weight = 0.3f;

    /**
     * 向量权重
     */
    @Builder.Default
    private Float vectorWeight = 0.7f;

    /**
     * kNN候选数量
     */
    @Builder.Default
    private Integer numCandidates = 100;

    /**
     * 过滤条件
     */
    private Map<String, Object> filters;
}
