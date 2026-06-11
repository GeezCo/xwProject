package com.qy.dch.rag.embed;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.qy.dch.rag.config.RagProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Slf4j
@Service
public class EmbeddingService {

    @Autowired
    private RagProperties ragProperties;

    @Autowired
    private RestTemplate embeddingRestTemplate;

    public List<float[]> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return Collections.emptyList();
        }

        int batchSize = ragProperties.getEmbedding().getBatchSize();
        String url = ragProperties.getEmbedding().getBaseUrl() + "/embed";
        List<float[]> allEmbeddings = new ArrayList<>();

        for (int i = 0; i < texts.size(); i += batchSize) {
            int end = Math.min(i + batchSize, texts.size());
            List<String> batch = texts.subList(i, end);
            List<float[]> batchResult = callEmbedApi(url, batch, 0);
            if (batchResult != null) {
                allEmbeddings.addAll(batchResult);
            }
        }
        return allEmbeddings;
    }

    public float[] embed(String text) {
        List<float[]> results = embedBatch(Collections.singletonList(text));
        if (results != null && !results.isEmpty()) {
            return results.get(0);
        }
        return null;
    }

    private List<float[]> callEmbedApi(String url, List<String> texts, int attempt) {
        try {
            JSONObject requestBody = new JSONObject();
            requestBody.put("texts", texts);
            requestBody.put("normalize", true);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(requestBody.toJSONString(), headers);

            ResponseEntity<String> response = embeddingRestTemplate.postForEntity(url, entity, String.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                JSONObject result = JSON.parseObject(response.getBody());
                if (result.getInteger("code") == 1) {
                    JSONObject data = result.getJSONObject("data");
                    JSONArray embeddings = data.getJSONArray("embeddings");
                    List<float[]> vectors = new ArrayList<>();
                    for (int i = 0; i < embeddings.size(); i++) {
                        JSONArray arr = embeddings.getJSONArray(i);
                        float[] vec = new float[arr.size()];
                        for (int j = 0; j < arr.size(); j++) {
                            vec[j] = arr.getFloatValue(j);
                        }
                        vectors.add(vec);
                    }
                    return vectors;
                } else {
                    log.warn("Embedding API 返回错误: {}", result.getString("msg"));
                }
            }
        } catch (Exception e) {
            log.warn("调用 Embedding API 失败 (尝试 {}/{}): {}", attempt + 1,
                    ragProperties.getEmbedding().getRetryCount(), e.getMessage());
        }

        int maxRetry = ragProperties.getEmbedding().getRetryCount();
        if (attempt + 1 < maxRetry) {
            try {
                Thread.sleep(ragProperties.getEmbedding().getRetryDelayMs());
            } catch (InterruptedException ignored) {}
            return callEmbedApi(url, texts, attempt + 1);
        }

        log.error("Embedding API 调用失败，已重试 {} 次", maxRetry);
        return null;
    }

    public boolean isAvailable() {
        try {
            String url = ragProperties.getEmbedding().getBaseUrl() + "/health";
            ResponseEntity<String> response = embeddingRestTemplate.getForEntity(url, String.class);
            return response.getStatusCode() == HttpStatus.OK && response.getBody() != null
                    && response.getBody().contains("ok");
        } catch (Exception e) {
            log.warn("Embedding 服务不可用: {}", e.getMessage());
            return false;
        }
    }
}
