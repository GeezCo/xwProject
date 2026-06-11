package com.intel.rag.service;

import com.intel.rag.config.EmbeddingProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Embedding服务测试
 */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration",
        "embedding.service.url=http://localhost:8000"
})
class EmbeddingServiceTest {

    @Autowired
    private EmbeddingService embeddingService;

    @Autowired
    private EmbeddingProperties embeddingProperties;

    @Test
    void testEmbeddingServiceInitialization() {
        assertNotNull(embeddingService);
        assertNotNull(embeddingProperties);
        assertEquals(1024, embeddingService.getDimension());
        assertEquals("bge-large-zh-v1.5", embeddingService.getModelName());
    }

    @Test
    void testEmbed_EmptyText() {
        float[] embedding = embeddingService.embed("");
        assertNotNull(embedding);
        assertEquals(1024, embedding.length);
    }

    @Test
    void testEmbed_NullText() {
        float[] embedding = embeddingService.embed(null);
        assertNotNull(embedding);
        assertEquals(1024, embedding.length);
    }

    @Test
    void testEmbedBatch_EmptyList() {
        List<float[]> embeddings = embeddingService.embedBatch(null);
        assertTrue(embeddings.isEmpty());

        embeddings = embeddingService.embedBatch(List.of());
        assertTrue(embeddings.isEmpty());
    }

    // 注意：实际的Embedding服务测试需要远程服务运行
    // 以下测试在服务未运行时会返回零向量

    /*
    @Test
    void testEmbed_Success() {
        String text = "这是一段测试文本";

        if (embeddingService.isAvailable()) {
            float[] embedding = embeddingService.embed(text);

            assertNotNull(embedding);
            assertEquals(1024, embedding.length);

            // 验证不是全零向量
            boolean hasNonZero = false;
            for (float v : embedding) {
                if (v != 0.0f) {
                    hasNonZero = true;
                    break;
                }
            }
            assertTrue(hasNonZero);
        }
    }

    @Test
    void testEmbedBatch_Success() {
        List<String> texts = Arrays.asList(
                "第一段文本",
                "第二段文本",
                "第三段文本"
        );

        if (embeddingService.isAvailable()) {
            List<float[]> embeddings = embeddingService.embedBatch(texts);

            assertEquals(3, embeddings.size());

            for (float[] embedding : embeddings) {
                assertEquals(1024, embedding.length);
            }
        }
    }
    */

    @Test
    void testGetDimension() {
        assertEquals(1024, embeddingService.getDimension());
    }

    @Test
    void testGetModelName() {
        assertEquals("bge-large-zh-v1.5", embeddingService.getModelName());
    }
}
