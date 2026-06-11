package com.intel.rag.exception;

/**
 * Embedding向量化异常
 */
public class EmbeddingException extends RuntimeException {

    private final String text;
    private final int batchSize;

    public EmbeddingException(String message) {
        super(message);
        this.text = null;
        this.batchSize = 0;
    }

    public EmbeddingException(String message, Throwable cause) {
        super(message, cause);
        this.text = null;
        this.batchSize = 0;
    }

    public EmbeddingException(String message, String text) {
        super(message);
        this.text = text;
        this.batchSize = 0;
    }

    public EmbeddingException(String message, String text, Throwable cause) {
        super(message, cause);
        this.text = text;
        this.batchSize = 0;
    }

    public EmbeddingException(String message, int batchSize, Throwable cause) {
        super(message, cause);
        this.text = null;
        this.batchSize = batchSize;
    }

    public String getText() {
        return text;
    }

    public int getBatchSize() {
        return batchSize;
    }
}