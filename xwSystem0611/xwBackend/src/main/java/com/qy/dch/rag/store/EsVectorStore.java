package com.qy.dch.rag.store;

import com.qy.dch.rag.config.RagProperties;
import com.qy.dch.rag.model.DocumentChunk;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequest;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
public class EsVectorStore {

    @Autowired
    private RestHighLevelClient restHighLevelClient;

    @Autowired
    private RagProperties ragProperties;

    private static final String INDEX_MAPPING = "{\n" +
            "  \"properties\": {\n" +
            "    \"chunk_id\":     { \"type\": \"keyword\" },\n" +
            "    \"doc_id\":       { \"type\": \"keyword\" },\n" +
            "    \"chunk_index\":  { \"type\": \"integer\" },\n" +
            "    \"content\":      { \"type\": \"text\", \"analyzer\": \"ik_max_word\" },\n" +
            "    \"embedding\":    { \"type\": \"dense_vector\", \"dims\": 1024 },\n" +
            "    \"title\":        { \"type\": \"text\", \"analyzer\": \"ik_smart\" },\n" +
            "    \"publish_time\": { \"type\": \"date\", \"format\": \"yyyy-MM-dd HH:mm:ss||yyyy-MM-dd||epoch_millis\" },\n" +
            "    \"category\":     { \"type\": \"keyword\" },\n" +
            "    \"indexed_at\":   { \"type\": \"date\" }\n" +
            "  }\n" +
            "}";

    public void ensureIndex() throws Exception {
        String indexName = ragProperties.getEs().getIndexName();
        GetIndexRequest getRequest = new GetIndexRequest(indexName);
        boolean exists = restHighLevelClient.indices().exists(getRequest, RequestOptions.DEFAULT);

        if (!exists) {
            CreateIndexRequest createRequest = new CreateIndexRequest(indexName);
            createRequest.settings(Settings.builder()
                    .put("index.number_of_shards", 3)
                    .put("index.number_of_replicas", 1)
                    .build());
            createRequest.mapping(INDEX_MAPPING, XContentType.JSON);
            restHighLevelClient.indices().create(createRequest, RequestOptions.DEFAULT);
            log.info("ES 索引 {} 创建成功", indexName);
        } else {
            log.info("ES 索引 {} 已存在", indexName);
        }
    }

    public Set<String> bulkIndex(List<DocumentChunk> chunks) throws Exception {
        String indexName = ragProperties.getEs().getIndexName();
        BulkRequest bulkRequest = new BulkRequest();
        Set<String> successDocIds = new LinkedHashSet<>();

        for (DocumentChunk chunk : chunks) {
            Map<String, Object> source = new HashMap<>();
            source.put("chunk_id", chunk.getId());
            source.put("doc_id", chunk.getDocumentId());
            source.put("chunk_index", chunk.getChunkIndex());
            source.put("content", chunk.getContent());
            source.put("embedding", chunk.getEmbedding());
            source.put("title", chunk.getMetadata() != null ? chunk.getMetadata().getOrDefault("title", "") : "");
            source.put("publish_time", chunk.getMetadata() != null ? chunk.getMetadata().getOrDefault("publish_time", "") : "");
            source.put("category", chunk.getMetadata() != null ? chunk.getMetadata().getOrDefault("category", "") : "");
            source.put("indexed_at", new Date());

            IndexRequest indexRequest = new IndexRequest(indexName)
                    .id(chunk.getId())
                    .source(source, XContentType.JSON);
            bulkRequest.add(indexRequest);
        }

        BulkResponse bulkResponse = restHighLevelClient.bulk(bulkRequest, RequestOptions.DEFAULT);

        if (bulkResponse.hasFailures()) {
            log.warn("ES 批量写入部分失败: {}", bulkResponse.buildFailureMessage());
        }

        for (int i = 0; i < bulkResponse.getItems().length; i++) {
            if (!bulkResponse.getItems()[i].isFailed()) {
                DocumentChunk chunk = chunks.get(i);
                successDocIds.add(chunk.getDocumentId());
            } else {
                log.warn("ES 写入失败: chunk_id={}, reason={}",
                        chunks.get(i).getId(),
                        bulkResponse.getItems()[i].getFailureMessage());
            }
        }

        log.info("ES 批量写入完成: 总数={}, 成功={}", chunks.size(), successDocIds.size());
        return successDocIds;
    }

    public boolean isAvailable() {
        try {
            return restHighLevelClient.ping(RequestOptions.DEFAULT);
        } catch (Exception e) {
            log.warn("ES 不可用: {}", e.getMessage());
            return false;
        }
    }
}
