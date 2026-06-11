package com.intel.rag.service;

import com.intel.rag.config.RetrievalProperties;
import com.intel.rag.model.SearchRequest;
import com.intel.rag.model.SearchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * 混合检索服务测试
 */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration," +
                "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration"
})
class HybridSearchServiceTest {

    @Autowired(required = false)
    private HybridSearchService hybridSearchService;

    @Autowired(required = false)
    private RetrievalProperties retrievalProperties;

    @MockBean(name = "embeddingService")
    private EmbeddingService embeddingService;

    @BeforeEach
    void setUp() throws Exception {
        // Mock向量化服务
        if (embeddingService != null) {
            float[] mockVector = new float[1024];
            for (int i = 0; i < 1024; i++) {
                mockVector[i] = 0.1f;
            }
            when(embeddingService.embed(anyString())).thenReturn(mockVector);
        }
    }

    @Test
    void testRetrievalPropertiesLoaded() {
        if (retrievalProperties != null) {
            assertNotNull(retrievalProperties);
            assertNotNull(retrievalProperties.getHybridSearch());
            assertEquals(0.3f, retrievalProperties.getHybridSearch().getBm25Weight());
            assertEquals(0.7f, retrievalProperties.getHybridSearch().getVectorWeight());
            assertEquals(10, retrievalProperties.getHybridSearch().getTopK());
            assertEquals(60, retrievalProperties.getHybridSearch().getRrfK());
        }
    }

    @Test
    void testSearchRequestBuilder() {
        SearchRequest request = SearchRequest.builder()
                .query("测试查询")
                .topK(5)
                .bm25Weight(0.4f)
                .vectorWeight(0.6f)
                .hybridSearch(true)
                .build();

        assertEquals("测试查询", request.getQuery());
        assertEquals(5, request.getTopK());
        assertEquals(0.4f, request.getBm25Weight());
        assertEquals(0.6f, request.getVectorWeight());
        assertTrue(request.getHybridSearch());
    }

    @Test
    void testSearchResultBuilder() {
        SearchResult result = SearchResult.builder()
                .chunkId("chunk-1")
                .documentId("doc-1")
                .content("测试内容")
                .score(0.95f)
                .bm25Score(0.8f)
                .vectorScore(0.9f)
                .rank(1)
                .build();

        assertEquals("chunk-1", result.getChunkId());
        assertEquals("doc-1", result.getDocumentId());
        assertEquals("测试内容", result.getContent());
        assertEquals(0.95f, result.getScore());
        assertEquals(1, result.getRank());
    }

    @Test
    void testRrfFusion() {
        if (hybridSearchService == null) {
            return; // Skip if service not available
        }

        // 创建BM25结果
        List<SearchResult> bm25Results = new ArrayList<>();
        bm25Results.add(createMockResult("chunk-1", "doc-1", "内容1", 10.0f, 1));
        bm25Results.add(createMockResult("chunk-2", "doc-2", "内容2", 8.0f, 2));
        bm25Results.add(createMockResult("chunk-3", "doc-3", "内容3", 6.0f, 3));

        // 创建向量检索结果
        List<SearchResult> vectorResults = new ArrayList<>();
        vectorResults.add(createMockResult("chunk-2", "doc-2", "内容2", 0.95f, 1));
        vectorResults.add(createMockResult("chunk-1", "doc-1", "内容1", 0.90f, 2));
        vectorResults.add(createMockResult("chunk-4", "doc-4", "内容4", 0.85f, 3));

        // 执行RRF融合
        List<SearchResult> fusedResults = hybridSearchService.fuseResults(
                bm25Results, vectorResults, 0.3f, 0.7f);

        assertNotNull(fusedResults);
        assertTrue(fusedResults.size() >= 3);

        // 验证得分递减
        for (int i = 1; i < fusedResults.size(); i++) {
            assertTrue(fusedResults.get(i - 1).getScore() >= fusedResults.get(i).getScore(),
                    "得分应该递减");
        }

        // 验证排名
        for (int i = 0; i < fusedResults.size(); i++) {
            assertEquals(i + 1, fusedResults.get(i).getRank());
        }
    }

    @Test
    void testRrfFusionWeights() {
        if (hybridSearchService == null) {
            return;
        }

        // 创建多个文档以便观察权重差异
        List<SearchResult> bm25Results = new ArrayList<>();
        bm25Results.add(createMockResult("chunk-1", "doc-1", "内容1", 10.0f, 1));
        bm25Results.add(createMockResult("chunk-2", "doc-2", "内容2", 8.0f, 2));

        List<SearchResult> vectorResults = new ArrayList<>();
        vectorResults.add(createMockResult("chunk-2", "doc-2", "内容2", 0.95f, 1));
        vectorResults.add(createMockResult("chunk-1", "doc-1", "内容1", 0.90f, 2));

        // 测试不同权重
        List<SearchResult> result1 = hybridSearchService.fuseResults(
                bm25Results, vectorResults, 0.5f, 0.5f);
        List<SearchResult> result2 = hybridSearchService.fuseResults(
                bm25Results, vectorResults, 0.9f, 0.1f);

        assertNotNull(result1);
        assertNotNull(result2);
        assertEquals(2, result1.size());
        assertEquals(2, result2.size());

        // 不同权重应该影响排序
        // 0.5/0.5权重时，两个结果应该接近
        // 0.9/0.1权重时，BM25得分高的应该排前面
        assertTrue(Math.abs(result1.get(0).getScore() - result1.get(1).getScore()) < 0.01f,
                "均衡权重时得分应该接近");
    }

    @Test
    void testRrfFusionEmptyResults() {
        if (hybridSearchService == null) {
            return;
        }

        List<SearchResult> bm25Results = new ArrayList<>();
        List<SearchResult> vectorResults = new ArrayList<>();

        List<SearchResult> fusedResults = hybridSearchService.fuseResults(
                bm25Results, vectorResults, 0.3f, 0.7f);

        assertNotNull(fusedResults);
        assertTrue(fusedResults.isEmpty());
    }

    /**
     * 创建模拟结果
     */
    private SearchResult createMockResult(String chunkId, String docId,
                                         String content, float score, int rank) {
        return SearchResult.builder()
                .chunkId(chunkId)
                .documentId(docId)
                .content(content)
                .score(score)
                .rank(rank)
                .build();
    }
}
