package com.intel.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 检索配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "retrieval")
public class RetrievalProperties {

    private HybridSearch hybridSearch = new HybridSearch();
    private VectorSearch vectorSearch = new VectorSearch();
    private Filters filters = new Filters();

    @Data
    public static class HybridSearch {
        /**
         * BM25权重
         */
        private Float bm25Weight = 0.3f;

        /**
         * 向量权重
         */
        private Float vectorWeight = 0.7f;

        /**
         * 默认返回数量
         */
        private Integer topK = 10;

        /**
         * RRF算法的k常数
         */
        private Integer rrfK = 60;
    }

    @Data
    public static class VectorSearch {
        /**
         * kNN候选数量
         */
        private Integer numCandidates = 100;
    }

    @Data
    public static class Filters {
        /**
         * 是否启用过滤
         */
        private Boolean enabled = true;
    }
}
