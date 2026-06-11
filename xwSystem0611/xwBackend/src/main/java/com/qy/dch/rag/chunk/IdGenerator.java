package com.qy.dch.rag.chunk;

public class IdGenerator {

    public static String generateChunkId(String documentId, int chunkIndex) {
        return documentId + "_chunk_" + chunkIndex;
    }
}
