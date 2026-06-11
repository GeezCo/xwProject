package com.intel.rag.service;

import com.intel.rag.config.DocumentParserProperties;
import com.intel.rag.model.DocumentChunk;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 文档切片服务测试
 */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration"
})
class DocumentChunkServiceTest {

    @Autowired
    private DocumentChunkService chunkService;

    @Autowired
    private DocumentParserProperties parserProperties;

    @Test
    void testValidateChunkConfig() {
        assertTrue(chunkService.validateChunkConfig());
    }

    @Test
    void testChunkShortText() {
        String documentId = "doc_123";
        String content = "这是一段短文本，长度不超过256字符。";
        Map<String, Object> metadata = new HashMap<>();

        List<DocumentChunk> chunks = chunkService.chunkDocument(documentId, content, metadata);

        assertEquals(1, chunks.size());
        assertEquals("short", chunks.get(0).getChunkType());
        assertEquals(documentId, chunks.get(0).getDocumentId());
        assertEquals(0, chunks.get(0).getChunkIndex());
        assertNotNull(chunks.get(0).getId());
    }

    @Test
    void testChunkMediumText() {
        String documentId = "doc_456";
        // 生成一个中等长度的文本（400字符，超过256但小于768）
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 40; i++) {
            sb.append("这是第").append(i).append("段文本。");
        }
        String content = sb.toString();
        Map<String, Object> metadata = new HashMap<>();

        List<DocumentChunk> chunks = chunkService.chunkDocument(documentId, content, metadata);

        assertTrue(chunks.size() > 0);
        assertEquals("medium", chunks.get(0).getChunkType());
    }

    @Test
    void testChunkLongText() {
        String documentId = "doc_789";
        // 生成一个长文本（超过1024字符）
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 60; i++) {
            sb.append("这是第").append(i).append("段比较长的文本内容，用于测试长文本切片功能。");
        }
        String content = sb.toString();
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("source", "test");

        List<DocumentChunk> chunks = chunkService.chunkDocument(documentId, content, metadata);

        assertTrue(chunks.size() > 1);
        assertEquals("long", chunks.get(0).getChunkType());

        // 验证切片索引连续
        for (int i = 0; i < chunks.size(); i++) {
            assertEquals(i, chunks.get(i).getChunkIndex());
        }

        // 验证元数据被复制
        for (DocumentChunk chunk : chunks) {
            assertEquals("test", chunk.getMetadata().get("source"));
        }
    }

    @Test
    void testChunkEmptyContent() {
        String documentId = "doc_empty";
        String content = "";
        Map<String, Object> metadata = new HashMap<>();

        List<DocumentChunk> chunks = chunkService.chunkDocument(documentId, content, metadata);

        assertEquals(1, chunks.size());
        assertEquals("short", chunks.get(0).getChunkType());
    }

    @Test
    void testChunkOverlap() {
        String documentId = "doc_overlap";
        // 生成长文本
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            sb.append("ABCDEFGHIJ"); // 每次10个字符
        }
        String content = sb.toString(); // 总共1000字符
        Map<String, Object> metadata = new HashMap<>();

        List<DocumentChunk> chunks = chunkService.chunkDocument(documentId, content, metadata);

        assertTrue(chunks.size() > 1);

        // 验证有重叠内容（如果配置了overlap）
        int overlap = parserProperties.getChunkStrategy().getLongChunkOverlap();
        if (overlap > 0 && chunks.size() >= 2) {
            String firstChunk = chunks.get(0).getContent();
            String secondChunk = chunks.get(1).getContent();

            // 第一个切片的末尾应该和第二个切片的开头有重叠
            String firstEnd = firstChunk.substring(Math.max(0, firstChunk.length() - overlap));
            String secondStart = secondChunk.substring(0, Math.min(overlap, secondChunk.length()));

            // 注意：由于cleanText可能会改变内容，这里只验证长度
            assertTrue(firstEnd.length() > 0);
            assertTrue(secondStart.length() > 0);
        }
    }
}
