package com.intel.rag.controller;

import com.intel.rag.dto.ApiResponse;
import com.intel.rag.model.SearchRequest;
import com.intel.rag.dto.SearchQueryRequest;
import com.intel.rag.model.SearchResult;
import com.intel.rag.service.HybridSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 检索查询接口
 */
@Slf4j
@RestController
@RequestMapping("/api/v1")
@Tag(name = "检索接口", description = "文档检索相关接口")
@Validated
public class SearchController {

    private final HybridSearchService hybridSearchService;

    public SearchController(HybridSearchService hybridSearchService) {
        this.hybridSearchService = hybridSearchService;
    }

    /**
     * 检索查询
     */
    @PostMapping("/search")
    @Operation(summary = "检索查询", description = "支持混合检索、BM25检索、向量检索")
    public ApiResponse<Map<String, Object>> search(@Valid @RequestBody SearchQueryRequest request) {
        log.info("收到检索请求: query={}, topK={}, searchType={}",
                request.getQuery(), request.getTopK(), request.getSearchType());

        long startTime = System.currentTimeMillis();

        List<SearchResult> results;

        // 根据检索类型执行不同的检索
        switch (request.getSearchType().toLowerCase()) {
            case "bm25":
                results = hybridSearchService.bm25Search(request.getQuery(), request.getTopK());
                break;
            case "vector":
                results = hybridSearchService.vectorSearch(
                        request.getQuery(), request.getTopK(), request.getNumCandidates());
                break;
            case "hybrid":
            default:
                SearchRequest searchRequest = SearchRequest.builder()
                        .query(request.getQuery())
                        .topK(request.getTopK())
                        .bm25Weight(request.getBm25Weight())
                        .vectorWeight(request.getVectorWeight())
                        .numCandidates(request.getNumCandidates())
                        .hybridSearch(true)
                        .filters(request.getFilters())
                        .build();
                results = hybridSearchService.hybridSearch(searchRequest);
                break;
        }

        long elapsedTime = System.currentTimeMillis() - startTime;

        // 构建响应
        Map<String, Object> responseData = new HashMap<>();
        responseData.put("query", request.getQuery());
        responseData.put("searchType", request.getSearchType());
        responseData.put("total", results.size());
        responseData.put("results", results);
        responseData.put("elapsedTime", elapsedTime);

        log.info("检索完成: 返回{}条结果, 耗时{}ms", results.size(), elapsedTime);

        return ApiResponse.success(responseData);
    }

    /**
     * BM25检索（快捷接口）
     */
    @GetMapping("/search/bm25")
    @Operation(summary = "BM25关键词检索", description = "基于关键词的全文检索")
    public ApiResponse<Map<String, Object>> bm25Search(
            @RequestParam String query,
            @RequestParam(defaultValue = "10") Integer topK) {

        log.info("BM25检索: query={}, topK={}", query, topK);

        List<SearchResult> results = hybridSearchService.bm25Search(query, topK);

        Map<String, Object> responseData = new HashMap<>();
        responseData.put("query", query);
        responseData.put("searchType", "bm25");
        responseData.put("total", results.size());
        responseData.put("results", results);

        return ApiResponse.success(responseData);
    }

    /**
     * 向量检索（快捷接口）
     */
    @GetMapping("/search/vector")
    @Operation(summary = "向量语义检索", description = "基于语义相似度的向量检索")
    public ApiResponse<Map<String, Object>> vectorSearch(
            @RequestParam String query,
            @RequestParam(defaultValue = "10") Integer topK) {

        log.info("向量检索: query={}, topK={}", query, topK);

        List<SearchResult> results = hybridSearchService.vectorSearch(query, topK);

        Map<String, Object> responseData = new HashMap<>();
        responseData.put("query", query);
        responseData.put("searchType", "vector");
        responseData.put("total", results.size());
        responseData.put("results", results);

        return ApiResponse.success(responseData);
    }
}
