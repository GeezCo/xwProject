package com.intel.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * PostgreSQL + pgvector 配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "postgres")
public class PostgresProperties {

    private String host = "localhost";
    private int port = 5432;
    private String database = "rag_knowledge";
    private String username = "rag";
    private String password = "";

    private VectorConfig vector = new VectorConfig();

    @Data
    public static class VectorConfig {
        private int dimension = 1024;
        private String indexType = "ivfflat";
        private int lists = 100;
    }

    /**
     * 获取完整 JDBC URL
     */
    public String getJdbcUrl() {
        return String.format("jdbc:postgresql://%s:%d/%s", host, port, database);
    }
}