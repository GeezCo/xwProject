package com.qy.dch.rag.search;

import com.qy.dch.rag.config.RagProperties;
import com.qy.dch.rag.embed.EmbeddingService;
import com.qy.dch.rag.model.SearchResult;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.functionscore.FunctionScoreQueryBuilder;
import org.elasticsearch.index.query.functionscore.ScoreFunctionBuilders;
import org.elasticsearch.script.Script;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class HybridSearchService {

    @Autowired
    private RestHighLevelClient restHighLevelClient;

    @Autowired
    private EmbeddingService embeddingService;

    @Autowired
    private RagProperties ragProperties;

    public List<SearchResult> bm25Search(String query, int topK) throws Exception {
        String indexName = ragProperties.getEs().getIndexName();
        SearchRequest searchRequest = new SearchRequest(indexName);
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        sourceBuilder.query(QueryBuilders.matchQuery("content", query));
        sourceBuilder.size(topK);
        searchRequest.source(sourceBuilder);

        SearchResponse response = restHighLevelClient.search(searchRequest, RequestOptions.DEFAULT);
        List<SearchResult> results = new ArrayList<>();
        int rank = 1;
        for (SearchHit hit : response.getHits().getHits()) {
            Map<String, Object> source = hit.getSourceAsMap();
            results.add(SearchResult.builder()
                    .chunkId((String) source.get("chunk_id"))
                    .docId((String) source.get("doc_id"))
                    .content((String) source.get("content"))
                    .bm25Score(hit.getScore())
                    .title((String) source.get("title"))
                    .publishTime((String) source.get("publish_time"))
                    .category((String) source.get("category"))
                    .rank(rank++)
                    .build());
        }
        return results;
    }

    public List<SearchResult> vectorSearch(float[] queryVector, int topK) throws Exception {
        String indexName = ragProperties.getEs().getIndexName();

        String scriptSource = "cosineSimilarity(params.query_vector, 'embedding') + 1.0";
        Map<String, Object> params = new HashMap<>();
        params.put("query_vector", queryVector);

        Script script = new Script(Script.DEFAULT_SCRIPT_TYPE, "painless", scriptSource, params);
        FunctionScoreQueryBuilder functionScoreQuery = QueryBuilders.functionScoreQuery(
                QueryBuilders.matchAllQuery(),
                ScoreFunctionBuilders.scriptFunction(script)
        );

        SearchRequest searchRequest = new SearchRequest(indexName);
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        sourceBuilder.query(functionScoreQuery);
        sourceBuilder.size(topK);
        searchRequest.source(sourceBuilder);

        SearchResponse response = restHighLevelClient.search(searchRequest, RequestOptions.DEFAULT);
        List<SearchResult> results = new ArrayList<>();
        int rank = 1;
        for (SearchHit hit : response.getHits().getHits()) {
            Map<String, Object> source = hit.getSourceAsMap();
            results.add(SearchResult.builder()
                    .chunkId((String) source.get("chunk_id"))
                    .docId((String) source.get("doc_id"))
                    .content((String) source.get("content"))
                    .vectorScore(hit.getScore())
                    .title((String) source.get("title"))
                    .publishTime((String) source.get("publish_time"))
                    .category((String) source.get("category"))
                    .rank(rank++)
                    .build());
        }
        return results;
    }

    public Map<String, Object> hybridSearch(String query, int topK) throws Exception {
        float bm25Weight = ragProperties.getSearch().getBm25Weight();
        float vectorWeight = ragProperties.getSearch().getVectorWeight();
        int rrfK = ragProperties.getSearch().getRrfK();

        float[] queryVector = embeddingService.embed(query);
        if (queryVector == null) {
            log.warn("Query 向量化失败，降级为纯 BM25 检索");
            List<SearchResult> bm25Results = bm25Search(query, topK);
            Map<String, Object> result = new HashMap<>();
            result.put("results", bm25Results);
            result.put("totalHits", bm25Results.size());
            result.put("searchMode", "bm25");
            return result;
        }

        List<SearchResult> bm25Results = bm25Search(query, topK * 2);
        List<SearchResult> vectorResults = vectorSearch(queryVector, topK * 2);

        Map<String, SearchResult> resultMap = new LinkedHashMap<>();
        Map<String, Float> scoreMap = new HashMap<>();

        for (int i = 0; i < bm25Results.size(); i++) {
            SearchResult r = bm25Results.get(i);
            String key = r.getChunkId();
            float rrfScore = bm25Weight / (rrfK + i + 1);
            resultMap.put(key, r);
            scoreMap.put(key, scoreMap.getOrDefault(key, 0f) + rrfScore);
        }

        for (int i = 0; i < vectorResults.size(); i++) {
            SearchResult r = vectorResults.get(i);
            String key = r.getChunkId();
            float rrfScore = vectorWeight / (rrfK + i + 1);
            resultMap.putIfAbsent(key, r);
            scoreMap.put(key, scoreMap.getOrDefault(key, 0f) + rrfScore);
        }

        List<SearchResult> fused = resultMap.values().stream()
                .peek(r -> r.setFinalScore(scoreMap.get(r.getChunkId())))
                .sorted((a, b) -> Float.compare(
                        b.getFinalScore() != null ? b.getFinalScore() : 0,
                        a.getFinalScore() != null ? a.getFinalScore() : 0))
                .limit(topK)
                .collect(Collectors.toList());

        for (int i = 0; i < fused.size(); i++) {
            fused.get(i).setRank(i + 1);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("results", fused);
        result.put("totalHits", resultMap.size());
        result.put("searchMode", "hybrid");
        return result;
    }
}
