package com.intel.rag.service;

import com.intel.rag.config.PostgresProperties;
import com.intel.rag.model.DocumentChunk;
import com.intel.rag.util.ChineseTextAnalyzer;
import com.pgvector.PGvector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * PostgreSQL + pgvector 向量数据库服务
 */
@Slf4j
@Service
public class PgVectorService {

    private final PostgresProperties properties;
    private final EmbeddingService embeddingService;
    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;
    private final TransactionTemplate transactionTemplate;
    private final ChineseTextAnalyzer chineseTextAnalyzer;

    public PgVectorService(PostgresProperties properties,
                          EmbeddingService embeddingService,
                          DataSource dataSource,
                          TransactionTemplate transactionTemplate,
                          ChineseTextAnalyzer chineseTextAnalyzer) {
        this.properties = properties;
        this.embeddingService = embeddingService;
        this.dataSource = dataSource;
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.transactionTemplate = transactionTemplate;
        this.chineseTextAnalyzer = chineseTextAnalyzer;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        try {
            log.info("正在初始化PostgreSQL + pgvector: {}:{}", properties.getHost(), properties.getPort());

            // 检查并启用 pgvector 扩展
            enablePgVector();

            // 启用 pg_trgm 扩展（用于中文全文检索）
            enablePgTrgm();

            // 创建向量表
            createVectorTable();

            // 创建向量索引
            createVectorIndex();

            // 创建全文检索索引
            createFullTextIndex();

            log.info("PostgreSQL + pgvector 初始化完成");
        } catch (Exception e) {
            log.error("PostgreSQL + pgvector 初始化失败", e);
        }
    }

    private void enablePgVector() {
        try {
            jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS vector");
            log.info("pgvector 扩展已启用");
        } catch (Exception e) {
            log.warn("无法启用 pgvector 扩展，可能在开发环境（H2）: {}", e.getMessage());
        }
    }

    private void enablePgTrgm() {
        try {
            jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS pg_trgm");
            log.info("pg_trgm 扩展已启用");
        } catch (Exception e) {
            log.warn("无法启用 pg_trgm 扩展: {}", e.getMessage());
        }
    }

    private void createVectorTable() {
        String sql = """
            CREATE TABLE IF NOT EXISTS document_chunks (
                id VARCHAR(36) PRIMARY KEY,
                document_id VARCHAR(36) NOT NULL,
                content TEXT NOT NULL,
                chunk_index INTEGER NOT NULL,
                chunk_type VARCHAR(20),
                embedding vector(%d),
                metadata JSONB,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """.formatted(properties.getVector().getDimension());

        jdbcTemplate.execute(sql);
        log.info("向量表 document_chunks 创建成功");
    }

    private void createVectorIndex() {
        String indexType = properties.getVector().getIndexType();
        int lists = properties.getVector().getLists();

        try {
            String sql;
            if ("ivfflat".equalsIgnoreCase(indexType)) {
                sql = String.format(
                    "CREATE INDEX IF NOT EXISTS idx_embedding ON document_chunks USING ivfflat (embedding vector_cosine_ops) WITH (lists = %d)",
                    lists
                );
            } else {
                // 默认使用 HNSW
                sql = "CREATE INDEX IF NOT EXISTS idx_embedding ON document_chunks USING hnsw (embedding vector_cosine_ops)";
            }
            jdbcTemplate.execute(sql);
            log.info("向量索引创建成功: {}", indexType);
        } catch (Exception e) {
            log.warn("创建向量索引失败（可能在开发环境）: {}", e.getMessage());
        }
    }

    private void createFullTextIndex() {
        try {
            // 创建 trigram 索引用于全文检索
            jdbcTemplate.execute(
                "CREATE INDEX IF NOT EXISTS idx_content_trgm ON document_chunks USING gin (content gin_trgm_ops)"
            );
            log.info("全文检索索引创建成功");
        } catch (Exception e) {
            log.warn("创建全文检索索引失败（可能在开发环境）: {}", e.getMessage());
        }
    }

    /**
     * 存储文档块
     */
    public void storeChunks(List<DocumentChunk> chunks) {
        transactionTemplate.executeWithoutResult(status -> {
            try (Connection conn = dataSource.getConnection()) {
                // 生成向量
                List<float[]> embeddings = embeddingService.embedBatch(
                    chunks.stream().map(DocumentChunk::getContent).toList()
                );

                // 使用参数化查询批量插入
                String sql = """
                    INSERT INTO document_chunks (id, document_id, content, chunk_index, chunk_type, embedding, metadata)
                    VALUES (?, ?, ?, ?, ?, ?, ?::jsonb)
                    ON CONFLICT (id) DO UPDATE SET
                        content = EXCLUDED.content,
                        embedding = EXCLUDED.embedding,
                        metadata = EXCLUDED.metadata
                    """;

                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    for (int i = 0; i < chunks.size(); i++) {
                        DocumentChunk chunk = chunks.get(i);
                        float[] embedding = embeddings.get(i);

                        ps.setString(1, chunk.getId() != null ? chunk.getId() : UUID.randomUUID().toString());
                        ps.setString(2, chunk.getDocumentId());
                        ps.setString(3, chunk.getContent());
                        ps.setInt(4, chunk.getChunkIndex());
                        ps.setString(5, chunk.getChunkType());
                        ps.setObject(6, toPGvector(embedding));
                        ps.setString(7, toJsonString(chunk.getMetadata()));
                        ps.addBatch();
                    }

                    int[] results = ps.executeBatch();
                    log.info("成功存储 {} 个文档块", results.length);
                }
            } catch (Exception e) {
                status.setRollbackOnly();
                log.error("存储文档块失败，事务已回滚", e);
                throw new RuntimeException("存储文档块失败", e);
            }
        });
    }

    /**
     * 向量相似度检索
     */
    public List<DocumentChunk> search(float[] queryVector, int topK) {
        try (Connection conn = dataSource.getConnection()) {
            String sql = """
                SELECT id, document_id, content, chunk_index, chunk_type, metadata,
                       1 - (embedding <=> ?) as score
                FROM document_chunks
                ORDER BY embedding <=> ?
                LIMIT ?
                """;

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                PGvector pgVector = toPGvector(queryVector);
                ps.setObject(1, pgVector);
                ps.setObject(2, pgVector);
                ps.setInt(3, topK);

                List<DocumentChunk> results = new ArrayList<>();
                try (var rs = ps.executeQuery()) {
                    while (rs.next()) {
                        DocumentChunk chunk = new DocumentChunk();
                        chunk.setId(rs.getString("id"));
                        chunk.setDocumentId(rs.getString("document_id"));
                        chunk.setContent(rs.getString("content"));
                        chunk.setChunkIndex(rs.getInt("chunk_index"));
                        chunk.setChunkType(rs.getString("chunk_type"));
                        chunk.setScore(rs.getFloat("score"));
                        results.add(chunk);
                    }
                }
                return results;
            }
        } catch (Exception e) {
            log.error("向量检索失败", e);
            return Collections.emptyList();
        }
    }

    /**
     * 全文检索（使用中文分词 + trigram）
     */
    public List<DocumentChunk> searchByText(String query, int topK) {
        try (Connection conn = dataSource.getConnection()) {
            // 使用中文分词生成查询词汇
            List<String> tokens = chineseTextAnalyzer.tokenize(query);
            String tokenizedQuery = tokens.stream()
                .filter(t -> t.length() >= 2)
                .collect(Collectors.joining(" | "));

            if (tokenizedQuery.isEmpty()) {
                log.warn("查询文本无法提取有效词汇: {}", query);
                return searchByLike(query, topK);
            }

            // 使用pg_trgm扩展进行相似度匹配
            String sql = """
                SELECT id, document_id, content, chunk_index, chunk_type, metadata,
                       similarity(content, ?) as score
                FROM document_chunks
                WHERE content % ?
                ORDER BY score DESC
                LIMIT ?
                """;

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, query);
                ps.setString(2, query);
                ps.setInt(3, topK);

                List<DocumentChunk> results = new ArrayList<>();
                try (var rs = ps.executeQuery()) {
                    while (rs.next()) {
                        DocumentChunk chunk = new DocumentChunk();
                        chunk.setId(rs.getString("id"));
                        chunk.setDocumentId(rs.getString("document_id"));
                        chunk.setContent(rs.getString("content"));
                        chunk.setChunkIndex(rs.getInt("chunk_index"));
                        chunk.setChunkType(rs.getString("chunk_type"));
                        chunk.setScore(rs.getFloat("score"));
                        results.add(chunk);
                    }
                }
                return results;
            }
        } catch (Exception e) {
            log.warn("全文检索失败，使用简单 LIKE 搜索: {}", e.getMessage());
            return searchByLike(query, topK);
        }
    }

    private List<DocumentChunk> searchByLike(String query, int topK) {
        String sql = """
            SELECT id, document_id, content, chunk_index, chunk_type, metadata, 0.5 as score
            FROM document_chunks
            WHERE content ILIKE ?
            LIMIT ?
            """;

        return jdbcTemplate.query(sql, new DocumentChunkRowMapper(), "%" + query + "%", topK);
    }

    /**
     * 删除文档的所有块
     */
    public void deleteByDocumentId(String documentId) {
        jdbcTemplate.update("DELETE FROM document_chunks WHERE document_id = ?", documentId);
        log.info("已删除文档 {} 的所有块", documentId);
    }

    /**
     * 清空所有数据
     */
    public void deleteAll() {
        jdbcTemplate.update("DELETE FROM document_chunks");
        log.info("已清空所有文档块");
    }

    /**
     * 统计总数
     */
    public long count() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM document_chunks", Long.class);
    }

    // 辅助方法 - 使用pgvector-jdbc扩展
    private PGvector toPGvector(float[] vector) {
        return new PGvector(vector);
    }

    private String toJsonString(Map<String, Object> map) {
        if (map == null || map.isEmpty()) return "{}";
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(entry.getKey()).append("\":");
            if (entry.getValue() instanceof String) {
                sb.append("\"").append(entry.getValue()).append("\"");
            } else {
                sb.append(entry.getValue());
            }
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }

    private static class DocumentChunkRowMapper implements RowMapper<DocumentChunk> {
        @Override
        public DocumentChunk mapRow(java.sql.ResultSet rs, int rowNum) throws SQLException {
            DocumentChunk chunk = new DocumentChunk();
            chunk.setId(rs.getString("id"));
            chunk.setDocumentId(rs.getString("document_id"));
            chunk.setContent(rs.getString("content"));
            chunk.setChunkIndex(rs.getInt("chunk_index"));
            chunk.setChunkType(rs.getString("chunk_type"));
            chunk.setScore(rs.getFloat("score"));
            return chunk;
        }
    }
}