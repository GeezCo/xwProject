package com.intel.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Embedding服务配置属性
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "embedding")
public class EmbeddingProperties {

    private ServiceProperties service = new ServiceProperties();

    private ModelProperties model = new ModelProperties();

    @Data
    public static class ServiceProperties {
        private String url = "http://localhost:8000";
        private int timeoutSeconds = 30;
        private int batchSize = 100;
        private int retryAttempts = 3;
        private long retryDelayMs = 1000;
    }

    @Data
    public static class ModelProperties {
        private String name = "bge-large-zh-v1.5";
        private int dimension = 1024;
    }
}

