package com.intel.rag.service;

import com.intel.rag.config.RetrievalProperties;
import com.intel.rag.model.DocumentChunk;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 混合检索服务
 * 支持全文检索、向量检索和RRF融合
 * 使用 PostgreSQL + pgvector
 */
@Slf4j
@Service
public class HybridSearchService {

    private final PgVectorService pgVectorService;
    private final EmbeddingService embeddingService;
    private final RetrievalProperties retrievalProperties;

    public HybridSearchService(PgVectorService pgVectorService,
                               EmbeddingService embeddingService,
                               RetrievalProperties retrievalProperties) {
        this.pgVectorService = pgVectorService;
        this.embeddingService = embeddingService;
        this.retrievalProperties = retrievalProperties;
    }

    /**
     * 向量检索
     */
    public List<com.intel.rag.model.SearchResult> vectorSearch(String query, int topK) {
        log.info("执行向量检索: query={}, topK={}", query, topK);
        return vectorSearch(query, topK, topK * 10);
    }

    /**
     * 向量检索（指定候选数量）
     */
    public List<com.intel.rag.model.SearchResult> vectorSearch(String query, int topK, int numCandidates) {
        log.info("执行向量检索: query={}, topK={}, numCandidates={}", query, topK, numCandidates);

        try {
            float[] queryVector = embeddingService.embed(query);
            List<DocumentChunk> chunks = pgVectorService.search(queryVector, topK);

            List<com.intel.rag.model.SearchResult> results = new ArrayList<>();
            int rank = 1;
            for (DocumentChunk chunk : chunks) {
                results.add(com.intel.rag.model.SearchResult.builder()
                        .chunkId(chunk.getId())
                        .documentId(chunk.getDocumentId())
                        .content(chunk.getContent())
                        .score(chunk.getScore() != null ? chunk.getScore() : 0f)
                        .vectorScore(chunk.getScore() != null ? chunk.getScore() : 0f)
                        .chunkIndex(chunk.getChunkIndex())
                        .chunkType(chunk.getChunkType())
                        .metadata(chunk.getMetadata())
                        .rank(rank++)
                        .build());
            }

            log.info("向量检索完成: 返回{}条结果", results.size());
            return results;

        } catch (Exception e) {
            log.error("向量检索失败", e);
            return Collections.emptyList();
        }
    }

    /**
     * 全文检索（BM25风格）
     */
    public List<com.intel.rag.model.SearchResult> bm25Search(String query, int topK) {
        log.info("执行全文检索: query={}, topK={}", query, topK);

        try {
            List<DocumentChunk> chunks = pgVectorService.searchByText(query, topK);

            List<com.intel.rag.model.SearchResult> results = new ArrayList<>();
            int rank = 1;
            for (DocumentChunk chunk : chunks) {
                results.add(com.intel.rag.model.SearchResult.builder()
                        .chunkId(chunk.getId())
                        .documentId(chunk.getDocumentId())
                        .content(chunk.getContent())
                        .score(chunk.getScore() != null ? chunk.getScore() : 0f)
                        .bm25Score(chunk.getScore() != null ? chunk.getScore() : 0f)
                        .chunkIndex(chunk.getChunkIndex())
                        .chunkType(chunk.getChunkType())
                        .metadata(chunk.getMetadata())
                        .rank(rank++)
                        .build());
            }

            log.info("全文检索完成: 返回{}条结果", results.size());
            return results;

        } catch (Exception e) {
            log.error("全文检索失败", e);
            return Collections.emptyList();
        }
    }

    /**
     * 混合检索（向量 + 全文 + RRF融合）
     */
    public List<com.intel.rag.model.SearchResult> hybridSearch(com.intel.rag.model.SearchRequest request) {
        log.info("执行混合检索: query={}, topK={}, bm25Weight={}, vectorWeight={}",
                request.getQuery(), request.getTopK(), request.getBm25Weight(), request.getVectorWeight());

        float bm25Weight = request.getBm25Weight() != null ? request.getBm25Weight() : 0.3f;
        float vectorWeight = request.getVectorWeight() != null ? request.getVectorWeight() : 0.7f;

        // 并行执行向量检索和全文检索
        List<com.intel.rag.model.SearchResult> vectorResults = vectorSearch(
            request.getQuery(), request.getTopK() * 2);
        List<com.intel.rag.model.SearchResult> bm25Results = bm25Search(
            request.getQuery(), request.getTopK() * 2);

        // RRF融合
        List<com.intel.rag.model.SearchResult> fusedResults = fuseResults(
            bm25Results, vectorResults, bm25Weight, vectorWeight);

        // 返回Top-K结果
        List<com.intel.rag.model.SearchResult> topResults = fusedResults.stream()
                .limit(request.getTopK())
                .collect(Collectors.toList());

        log.info("混合检索完成: 返回{}条结果", topResults.size());
        return topResults;
    }

    /**
     * RRF融合算法
     */
    public List<com.intel.rag.model.SearchResult> fuseResults(
            List<com.intel.rag.model.SearchResult> bm25Results,
            List<com.intel.rag.model.SearchResult> vectorResults,
            float bm25Weight,
            float vectorWeight) {

        log.info("执行RRF融合: bm25Count={}, vectorCount={}, bm25Weight={}, vectorWeight={}",
                bm25Results.size(), vectorResults.size(), bm25Weight, vectorWeight);

        int rrfK = retrievalProperties.getHybridSearch().getRrfK();
        Map<String, com.intel.rag.model.SearchResult> resultMap = new HashMap<>();
        Map<String, Float> scoreMap = new HashMap<>();

        for (int i = 0; i < bm25Results.size(); i++) {
            com.intel.rag.model.SearchResult result = bm25Results.get(i);
            String chunkId = result.getChunkId();
            float rrfScore = bm25Weight / (rrfK + i + 1);
            resultMap.put(chunkId, result);
            scoreMap.put(chunkId, scoreMap.getOrDefault(chunkId, 0f) + rrfScore);
        }

        for (int i = 0; i < vectorResults.size(); i++) {
            com.intel.rag.model.SearchResult result = vectorResults.get(i);
            String chunkId = result.getChunkId();
            float rrfScore = vectorWeight / (rrfK + i + 1);
            if (!resultMap.containsKey(chunkId)) {
                resultMap.put(chunkId, result);
            }
            scoreMap.put(chunkId, scoreMap.getOrDefault(chunkId, 0f) + rrfScore);
        }

        List<com.intel.rag.model.SearchResult> fusedResults = resultMap.values().stream()
                .peek(result -> result.setScore(scoreMap.get(result.getChunkId())))
                .sorted((a, b) -> Float.compare(b.getScore(), a.getScore()))
                .collect(Collectors.toList());

        for (int i = 0; i < fusedResults.size(); i++) {
            fusedResults.get(i).setRank(i + 1);
        }

        log.info("RRF融合完成: 返回{}条结果", fusedResults.size());
        return fusedResults;
    }
}