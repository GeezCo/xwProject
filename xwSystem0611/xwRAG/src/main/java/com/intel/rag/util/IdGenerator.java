package com.intel.rag.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * ID生成工具类
 */
public class IdGenerator {

    private static final Logger logger = LoggerFactory.getLogger(IdGenerator.class);

    /**
     * 生成文档ID
     */
    public static String generateDocumentId(String source) {
        return source + "_" + UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 生成切片ID
     */
    public static String generateChunkId(String documentId, int chunkIndex) {
        return documentId + "_chunk_" + chunkIndex;
    }

    /**
     * 生成UUID
     */
    public static String generateUuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
