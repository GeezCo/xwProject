package com.qy.dch.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "rag")
public class RagProperties {

    private Elasticsearch es = new Elasticsearch();
    private Embedding embedding = new Embedding();
    private Chunk chunk = new Chunk();
    private Indexing indexing = new Indexing();
    private Search search = new Search();

    @Data
    public static class Elasticsearch {
        private String host = "localhost";
        private int port = 9200;
        private String username = "";
        private String password = "";
        private String indexName = "xianwei_docs";
    }

    @Data
    public static class Embedding {
        private String baseUrl = "http://localhost:5002";
        private String model = "bge-large-zh-v1.5";
        private int dimension = 1024;
        private int batchSize = 32;
        private int retryCount = 3;
        private long retryDelayMs = 5000;
        private int timeoutSeconds = 60;
    }

    @Data
    public static class Chunk {
        private int shortThreshold = 128;
        private int mediumThreshold = 512;
        private int mediumSize = 256;
        private int longSize = 512;
        private int overlap = 64;
    }

    @Data
    public static class Indexing {
        private int maxDurationMinutes = 30;
        private int esBatchSize = 100;
    }

    @Data
    public static class Search {
        private float bm25Weight = 0.3f;
        private float vectorWeight = 0.7f;
        private int rrfK = 60;
        private int defaultTopK = 10;
    }
}