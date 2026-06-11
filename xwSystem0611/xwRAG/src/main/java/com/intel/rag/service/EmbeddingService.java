package com.intel.rag.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intel.rag.config.EmbeddingProperties;
import com.intel.rag.exception.EmbeddingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.*;

/**
 * Embedding向量化服务
 * 调用远程Python BGE服务进行文本向量化
 */
@Slf4j
@Service
public class EmbeddingService {

    private final EmbeddingProperties embeddingProperties;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public EmbeddingService(EmbeddingProperties embeddingProperties) {
        this.embeddingProperties = embeddingProperties;
        this.objectMapper = new ObjectMapper();
        this.webClient = WebClient.builder()
                .baseUrl(embeddingProperties.getService().getUrl())
                .build();

        log.info("Embedding服务初始化完成: url={}, model={}, dimension={}",
                embeddingProperties.getService().getUrl(),
                embeddingProperties.getModel().getName(),
                embeddingProperties.getModel().getDimension());
    }

    /**
     * 对单个文本进行向量化
     *
     * @param text 文本内容
     * @return 向量数组
     * @throws EmbeddingException 如果文本为空或向量化失败
     */
    public float[] embed(String text) {
        if (text == null || text.trim().isEmpty()) {
            throw new EmbeddingException("文本不能为空", text);
        }

        long startTime = System.currentTimeMillis();

        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("text", text);
            requestBody.put("model", embeddingProperties.getModel().getName());

            String response = webClient.post()
                    .uri("/embed")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .retryWhen(Retry.backoff(
                            embeddingProperties.getService().getRetryAttempts(),
                            Duration.ofMillis(embeddingProperties.getService().getRetryDelayMs()))
                            .maxBackoff(Duration.ofSeconds(5)))
                    .timeout(Duration.ofSeconds(embeddingProperties.getService().getTimeoutSeconds()))
                    .block();

            if (response == null) {
                throw new EmbeddingException("Embedding服务返回空响应", text);
            }

            float[] embedding = parseEmbeddingResponse(response);

            if (embedding == null || embedding.length != embeddingProperties.getModel().getDimension()) {
                throw new EmbeddingException("返回的向量维度不正确", text);
            }

            long elapsedTime = System.currentTimeMillis() - startTime;
            log.debug("文本向量化完成: 耗时={}ms, 维度={}", elapsedTime, embedding.length);

            return embedding;

        } catch (EmbeddingException e) {
            throw e;
        } catch (Exception e) {
            log.error("文本向量化失败: {}", text.substring(0, Math.min(50, text.length())), e);
            throw new EmbeddingException("文本向量化失败", text, e);
        }
    }

    /**
     * 批量文本向量化
     *
     * @param texts 文本列表
     * @return 向量列表
     * @throws EmbeddingException 如果文本列表为空或向量化失败
     */
    public List<float[]> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            throw new EmbeddingException("文本列表不能为空");
        }

        log.info("开始批量文本向量化: 文本数量={}", texts.size());
        long startTime = System.currentTimeMillis();

        // 分批处理，每批最多处理batchSize个文本
        int batchSize = embeddingProperties.getService().getBatchSize();
        List<float[]> allEmbeddings = new ArrayList<>();

        for (int i = 0; i < texts.size(); i += batchSize) {
            int end = Math.min(i + batchSize, texts.size());
            List<String> batch = texts.subList(i, end);

            try {
                Map<String, Object> requestBody = new HashMap<>();
                requestBody.put("texts", batch);
                requestBody.put("model", embeddingProperties.getModel().getName());

                String response = webClient.post()
                        .uri("/embed_batch")
                        .bodyValue(requestBody)
                        .retrieve()
                        .bodyToMono(String.class)
                        .retryWhen(Retry.backoff(
                                embeddingProperties.getService().getRetryAttempts(),
                                Duration.ofMillis(embeddingProperties.getService().getRetryDelayMs()))
                                .maxBackoff(Duration.ofSeconds(5)))
                        .timeout(Duration.ofSeconds(embeddingProperties.getService().getTimeoutSeconds()))
                        .block();

                if (response == null) {
                    throw new EmbeddingException("批量Embedding服务返回空响应", batch.size(), null);
                }

                List<float[]> batchEmbeddings = parseBatchEmbeddingResponse(response);

                if (batchEmbeddings == null || batchEmbeddings.size() != batch.size()) {
                    throw new EmbeddingException(
                        String.format("批量向量化返回数量不匹配: 期望%d, 实际%d",
                            batch.size(), batchEmbeddings != null ? batchEmbeddings.size() : 0),
                        batch.size(), null);
                }

                allEmbeddings.addAll(batchEmbeddings);

                log.debug("批次向量化完成: 批次={}/{}, 文本数={}",
                        (i / batchSize) + 1,
                        (texts.size() + batchSize - 1) / batchSize,
                        batch.size());

            } catch (EmbeddingException e) {
                throw e;
            } catch (Exception e) {
                log.error("批次向量化失败: 批次={}", (i / batchSize) + 1, e);
                throw new EmbeddingException("批次向量化失败", batch.size(), e);
            }
        }

        long totalTime = System.currentTimeMillis() - startTime;
        log.info("批量文本向量化完成: 文本数量={}, 总耗时={}ms, 平均耗时={}ms",
                texts.size(), totalTime, totalTime / texts.size());

        return allEmbeddings;
    }

    /**
     * 解析单个向量响应
     */
    private float[] parseEmbeddingResponse(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode embeddingNode = root.get("embedding");

            if (embeddingNode == null || !embeddingNode.isArray()) {
                throw new EmbeddingException("响应格式错误: 缺少embedding字段");
            }

            float[] embedding = new float[embeddingNode.size()];
            for (int i = 0; i < embeddingNode.size(); i++) {
                embedding[i] = (float) embeddingNode.get(i).asDouble();
            }

            return embedding;

        } catch (EmbeddingException e) {
            throw e;
        } catch (Exception e) {
            log.error("解析向量响应失败", e);
            throw new EmbeddingException("解析向量响应失败", e);
        }
    }

    /**
     * 解析批量向量响应
     */
    private List<float[]> parseBatchEmbeddingResponse(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode embeddingsNode = root.get("embeddings");

            if (embeddingsNode == null || !embeddingsNode.isArray()) {
                throw new EmbeddingException("响应格式错误: 缺少embeddings字段");
            }

            List<float[]> embeddings = new ArrayList<>();
            for (JsonNode embeddingNode : embeddingsNode) {
                float[] embedding = new float[embeddingNode.size()];
                for (int i = 0; i < embeddingNode.size(); i++) {
                    embedding[i] = (float) embeddingNode.get(i).asDouble();
                }
                embeddings.add(embedding);
            }

            return embeddings;

        } catch (EmbeddingException e) {
            throw e;
        } catch (Exception e) {
            log.error("解析批量向量响应失败", e);
            throw new EmbeddingException("解析批量向量响应失败", e);
        }
    }

    /**
     * 检查Embedding服务是否可用
     */
    public boolean isAvailable() {
        try {
            String response = webClient.get()
                    .uri("/health")
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(5))
                    .block();

            return response != null && response.contains("ok");

        } catch (Exception e) {
            log.warn("Embedding服务不可用: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 获取向量维度
     */
    public int getDimension() {
        return embeddingProperties.getModel().getDimension();
    }

    /**
     * 获取模型名称
     */
    public String getModelName() {
        return embeddingProperties.getModel().getName();
    }
}
