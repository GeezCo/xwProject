package com.qy.dch.rag;

import com.qy.dch.rag.chunk.ChunkService;
import com.qy.dch.rag.config.RagProperties;
import com.qy.dch.rag.model.DocumentChunk;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class RagModuleTest {

    @Autowired
    private ChunkService chunkService;

    @Autowired
    private RagProperties ragProperties;

    @Test
    void testShortTextChunk() {
        String content = "短文本测试内容。";
        List<DocumentChunk> chunks = chunkService.chunkDocument("test1", content, new HashMap<>());
        assertNotNull(chunks);
        assertEquals(1, chunks.size());
        assertEquals("short", chunks.get(0).getChunkType());
    }

    @Test
    void testMediumTextChunk() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            sb.append("测试文本。用于验证中等长度文本的切片逻辑。");
        }
        List<DocumentChunk> chunks = chunkService.chunkDocument("test2", sb.toString(), new HashMap<>());
        assertNotNull(chunks);
        assertTrue(chunks.size() > 1);
        assertEquals("medium", chunks.get(0).getChunkType());
    }

    @Test
    void testLongTextChunk() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 50; i++) {
            sb.append("长文本测试。用于验证滑动窗口切片逻辑。");
        }
        List<DocumentChunk> chunks = chunkService.chunkDocument("test3", sb.toString(), new HashMap<>());
        assertNotNull(chunks);
        assertTrue(chunks.size() > 2);
        assertEquals("long", chunks.get(0).getChunkType());
    }

    @Test
    void testEmptyContentChunk() {
        List<DocumentChunk> chunks = chunkService.chunkDocument("test4", "", new HashMap<>());
        assertNotNull(chunks);
        assertEquals(0, chunks.size());
    }

    @Test
    void testNullContentChunk() {
        List<DocumentChunk> chunks = chunkService.chunkDocument("test5", null, new HashMap<>());
        assertNotNull(chunks);
        assertEquals(0, chunks.size());
    }
}