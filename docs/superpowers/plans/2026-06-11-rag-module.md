# RAG 知识库模块实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 MySQL 中原始报文通过切片+Embedding 向量化存储到 Elasticsearch，向前端提供语义检索能力。

**Architecture:** RAG 模块代码合并到 xwBackend 作为 `rag` 子包；独立 xwEmbedding Flask 服务（:5002）提供 BGE 向量化；ES 7.17 RestHighLevelClient 兼容 JDK 8。

**Tech Stack:** JDK 8, Spring Boot 2.7.18, MyBatis, FastJSON, Lombok, ES 7.17 RestHighLevelClient, Flask + BGE-large-zh-v1.5

---

## 文件清单

### 新增文件

| 文件 | 职责 |
|------|------|
| `xwEmbedding/app.py` | BGE Embedding Flask 服务 |
| `xwEmbedding/requirements.txt` | Python 依赖 |
| `xwEmbedding/start.sh` | Linux 启动脚本 |
| `xwEmbedding/start.bat` | Windows 启动脚本 |
| `xwBackend/src/main/java/com/qy/dch/rag/config/RagProperties.java` | RAG 配置属性类 |
| `xwBackend/src/main/java/com/qy/dch/rag/config/ElasticsearchConfig.java` | ES RestHighLevelClient 配置 |
| `xwBackend/src/main/java/com/qy/dch/rag/config/EmbeddingConfig.java` | BGE WebClient 配置 |
| `xwBackend/src/main/java/com/qy/dch/rag/model/DocumentChunk.java` | 切片模型 |
| `xwBackend/src/main/java/com/qy/dch/rag/model/SearchResult.java` | 检索结果模型 |
| `xwBackend/src/main/java/com/qy/dch/rag/chunk/ChunkService.java` | 混合切片服务 |
| `xwBackend/src/main/java/com/qy/dch/rag/chunk/ChineseTextAnalyzer.java` | 中文文本分析器 |
| `xwBackend/src/main/java/com/qy/dch/rag/chunk/IdGenerator.java` | ID 生成工具 |
| `xwBackend/src/main/java/com/qy/dch/rag/embed/EmbeddingService.java` | BGE Embedding 客户端 |
| `xwBackend/src/main/java/com/qy/dch/rag/store/EsVectorStore.java` | ES 索引 + 批量写入 |
| `xwBackend/src/main/java/com/qy/dch/rag/search/HybridSearchService.java` | BM25 + kNN + RRF |
| `xwBackend/src/main/java/com/qy/dch/mapper/RagMapper.java` | MyBatis Mapper（查询未索引报文） |
| `xwBackend/src/main/java/com/qy/dch/request/RagSearchRequest.java` | 检索请求 DTO |
| `xwBackend/src/main/java/com/qy/dch/request/RagIndexTriggerRequest.java` | 触发索引请求 DTO |
| `xwBackend/src/main/java/com/qy/dch/dto/RagIndexStatusDTO.java` | 索引状态 DTO |
| `xwBackend/src/main/java/com/qy/dch/dto/RagIndexLogDTO.java` | 索引日志 DTO |
| `xwBackend/src/main/java/com/qy/dch/service/RagService.java` | RAG 服务接口 |
| `xwBackend/src/main/java/com/qy/dch/service/impl/RagServiceImpl.java` | RAG 服务实现 |
| `xwBackend/src/main/java/com/qy/dch/controller/RagController.java` | RAG REST 控制器 |
| `xwBackend/src/main/java/com/qy/dch/task/RagIndexingTask.java` | 定时索引任务 |

### 修改文件

| 文件 | 变更 |
|------|------|
| `xwBackend/pom.xml` | 新增 ES 7.17 依赖 |
| `xwBackend/src/main/resources/application.yml` | 新增 RAG 配置项 |

---

### Task 0: xwEmbedding BGE 服务

**Files:**
- Create: `xwSystem0611/xwEmbedding/app.py`
- Create: `xwSystem0611/xwEmbedding/requirements.txt`
- Create: `xwSystem0611/xwEmbedding/start.sh`
- Create: `xwSystem0611/xwEmbedding/start.bat`

- [ ] **Step 1: 创建 xwEmbedding/app.py**

```python
"""
BGE Embedding 服务
独立 Flask 应用，端口 5002
加载 BAAI/bge-large-zh-v1.5 模型，提供文本向量化 API
"""
import os
import time
import logging
from flask import Flask, request, jsonify

logging.basicConfig(level=logging.INFO, format='%(asctime)s [%(levelname)s] %(message)s')
logger = logging.getLogger(__name__)

app = Flask(__name__)

# ---------- 模型加载 ----------
MODEL_PATH = os.environ.get("BGE_MODEL_PATH", os.path.join(os.path.dirname(__file__), "models/bge-large-zh-v1.5"))
MODEL_NAME = "bge-large-zh-v1.5"
DIMENSION = 1024
MAX_BATCH_SIZE = 64
MAX_SEQ_LENGTH = 512
START_TIME = time.time()

model = None

def load_model():
    global model
    try:
        from sentence_transformers import SentenceTransformer
        logger.info(f"正在加载模型: {MODEL_PATH}")
        if os.path.exists(MODEL_PATH):
            model = SentenceTransformer(MODEL_PATH)
        else:
            model = SentenceTransformer(MODEL_NAME)
        logger.info(f"模型加载完成，维度: {model.get_sentence_embedding_dimension()}")
    except Exception as e:
        logger.error(f"模型加载失败: {e}")
        raise

load_model()


# ---------- API ----------
@app.route("/embed", methods=["POST"])
def embed():
    """文本向量化"""
    data = request.get_json(force=True)
    texts = data.get("texts", [])
    normalize = data.get("normalize", True)

    if not texts:
        return jsonify({"code": 0, "msg": "texts 不能为空", "data": None}), 400

    if isinstance(texts, str):
        texts = [texts]

    if len(texts) > MAX_BATCH_SIZE:
        texts = texts[:MAX_BATCH_SIZE]

    texts = [t[:MAX_SEQ_LENGTH * 4] if len(t) > MAX_SEQ_LENGTH * 4 else t for t in texts]

    t0 = time.time()
    try:
        embeddings = model.encode(texts, normalize_embeddings=normalize, show_progress_bar=False)
        embeddings_list = [emb.tolist() for emb in embeddings]
        took_ms = int((time.time() - t0) * 1000)

        return jsonify({
            "code": 1,
            "data": {
                "embeddings": embeddings_list,
                "dimension": DIMENSION,
                "count": len(embeddings_list),
                "took_ms": took_ms
            }
        })
    except Exception as e:
        logger.error(f"向量化失败: {e}")
        return jsonify({"code": 0, "msg": str(e), "data": None}), 500


@app.route("/health", methods=["GET"])
def health():
    """健康检查"""
    return jsonify({
        "status": "ok",
        "model": MODEL_NAME,
        "dimension": DIMENSION,
        "uptime_seconds": int(time.time() - START_TIME)
    })


@app.route("/model/info", methods=["GET"])
def model_info():
    """模型信息"""
    return jsonify({
        "model": MODEL_NAME,
        "dimension": DIMENSION,
        "max_batch_size": MAX_BATCH_SIZE,
        "max_seq_length": MAX_SEQ_LENGTH
    })


if __name__ == "__main__":
    port = int(os.environ.get("PORT", 5002))
    app.run(host="0.0.0.0", port=port, threaded=True)
```

- [ ] **Step 2: 创建 xwEmbedding/requirements.txt**

```
flask>=2.0
sentence-transformers>=2.2.0
torch>=1.13.0
```

- [ ] **Step 3: 创建 xwEmbedding/start.sh**

```bash
#!/bin/bash
cd "$(dirname "$0")"
pip install -r requirements.txt
python app.py
```

- [ ] **Step 4: 创建 xwEmbedding/start.bat**

```bat
@echo off
cd /d %~dp0
pip install -r requirements.txt
python app.py
```

- [ ] **Step 5: 验证启动**

```bash
cd xwEmbedding
pip install -r requirements.txt
python app.py
# 另开终端: curl http://localhost:5002/health
# 预期: {"status":"ok","model":"bge-large-zh-v1.5","dimension":1024}
```

- [ ] **Step 6: Commit**

```bash
git add xwEmbedding/
git commit -m "feat: add xwEmbedding BGE service"
```

---

### Task 1: pom.xml 新增 ES 7.17 依赖

**Files:**
- Modify: `xwSystem0611/xwBackend/pom.xml`

- [ ] **Step 1: 添加 ES 7.17 依赖**

在 `pom.xml` 的 `<dependencies>` 块末尾（`</dependencies>` 之前）添加：

```xml
<!-- Elasticsearch 7.17 RestHighLevelClient (兼容 JDK 8) -->
<dependency>
    <groupId>org.elasticsearch.client</groupId>
    <artifactId>elasticsearch-rest-high-level-client</artifactId>
    <version>7.17.15</version>
</dependency>
<dependency>
    <groupId>org.elasticsearch</groupId>
    <artifactId>elasticsearch</artifactId>
    <version>7.17.15</version>
</dependency>
```

- [ ] **Step 2: 验证依赖下载**

```bash
cd xwBackend
mvn dependency:resolve -DincludeArtifactIds=elasticsearch-rest-high-level-client,elasticsearch 2>&1 | tail -5
# 预期: BUILD SUCCESS
```

- [ ] **Step 3: Commit**

```bash
git add xwBackend/pom.xml
git commit -m "feat: add ES 7.17 RestHighLevelClient dependency"
```

---

### Task 2: RAG 配置属性类

**Files:**
- Create: `xwSystem0611/xwBackend/src/main/java/com/qy/dch/rag/config/RagProperties.java`

- [ ] **Step 1: 创建 RagProperties.java**

```java
package com.qy.dch.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * RAG 模块统一配置属性
 * 对应 application.yml 中 rag.* 配置项
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "rag")
public class RagProperties {

    private Elasticsearch es = new Elasticsearch();
    private Embedding embedding = new Embedding();
    private Chunk chunk = new Chunk();
    private Indexing indexing = new Indexing();
    private Search search = new Search();

    @Data
    public static class Elasticsearch {
        private String host = "localhost";
        private int port = 9200;
        private String username = "";
        private String password = "";
        private String indexName = "xianwei_docs";
    }

    @Data
    public static class Embedding {
        private String baseUrl = "http://localhost:5002";
        private String model = "bge-large-zh-v1.5";
        private int dimension = 1024;
        private int batchSize = 32;
        private int retryCount = 3;
        private long retryDelayMs = 5000;
        private int timeoutSeconds = 60;
    }

    @Data
    public static class Chunk {
        private int shortThreshold = 128;
        private int mediumThreshold = 512;
        private int mediumSize = 256;
        private int longSize = 512;
        private int overlap = 64;
    }

    @Data
    public static class Indexing {
        private int maxDurationMinutes = 30;
        private int esBatchSize = 100;
    }

    @Data
    public static class Search {
        private float bm25Weight = 0.3f;
        private float vectorWeight = 0.7f;
        private int rrfK = 60;
        private int defaultTopK = 10;
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add xwBackend/src/main/java/com/qy/dch/rag/config/RagProperties.java
git commit -m "feat: add RagProperties configuration class"
```

---

### Task 3: ES 和 Embedding 配置类

**Files:**
- Create: `xwSystem0611/xwBackend/src/main/java/com/qy/dch/rag/config/ElasticsearchConfig.java`
- Create: `xwSystem0611/xwBackend/src/main/java/com/qy/dch/rag/config/EmbeddingConfig.java`

- [ ] **Step 1: 创建 ElasticsearchConfig.java**

```java
package com.qy.dch.rag.config;

import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Elasticsearch 7.17 RestHighLevelClient 配置
 */
@Configuration
public class ElasticsearchConfig {

    @Autowired
    private RagProperties ragProperties;

    @Bean
    public RestHighLevelClient restHighLevelClient() {
        RagProperties.Elasticsearch es = ragProperties.getEs();
        HttpHost host = new HttpHost(es.getHost(), es.getPort(), "http");

        RestClient restClient;
        if (es.getUsername() != null && !es.getUsername().isEmpty()) {
            CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            credentialsProvider.setCredentials(
                    AuthScope.ANY,
                    new UsernamePasswordCredentials(es.getUsername(), es.getPassword())
            );
            restClient = RestClient.builder(host)
                    .setHttpClientConfigCallback(httpClientBuilder ->
                            httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider))
                    .build();
        } else {
            restClient = RestClient.builder(host).build();
        }

        return new RestHighLevelClient(restClient);
    }
}
```

- [ ] **Step 2: 创建 EmbeddingConfig.java**

```java
package com.qy.dch.rag.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * Embedding 服务 HTTP 客户端配置
 * 复用 RestTemplate 调用 BGE 服务
 */
@Configuration
public class EmbeddingConfig {

    @Autowired
    private RagProperties ragProperties;

    @Bean
    public RestTemplate embeddingRestTemplate() {
        return new RestTemplate();
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add xwBackend/src/main/java/com/qy/dch/rag/config/ElasticsearchConfig.java xwBackend/src/main/java/com/qy/dch/rag/config/EmbeddingConfig.java
git commit -m "feat: add ES and Embedding config classes"
```

---

### Task 4: 数据模型

**Files:**
- Create: `xwSystem0611/xwBackend/src/main/java/com/qy/dch/rag/model/DocumentChunk.java`
- Create: `xwSystem0611/xwBackend/src/main/java/com/qy/dch/rag/model/SearchResult.java`

- [ ] **Step 1: 创建 DocumentChunk.java**

```java
package com.qy.dch.rag.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Map;

/**
 * 文档切片模型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentChunk {

    /** 切片唯一标识 (doc_id_chunk_N) */
    private String id;

    /** 父文档ID (origin_text.sid) */
    private String documentId;

    /** 切片内容 */
    private String content;

    /** 切片序号 */
    private int chunkIndex;

    /** 向量表示 (1024维) */
    private float[] embedding;

    /** 元数据 */
    private Map<String, Object> metadata;

    /** 切片类型 (short / medium / long) */
    private String chunkType;

    /** 字符长度 */
    private int length;

    /** 检索得分 */
    private Float score;
}
```

- [ ] **Step 2: 创建 SearchResult.java**

```java
package com.qy.dch.rag.model;

import lombok.Builder;
import lombok.Data;
import java.util.Map;

/**
 * 检索结果模型
 */
@Data
@Builder
public class SearchResult {

    /** 切片ID */
    private String chunkId;

    /** 文档ID (origin_text.sid) */
    private String docId;

    /** 切片内容 */
    private String content;

    /** 融合得分 */
    private Float finalScore;

    /** BM25得分 */
    private Float bm25Score;

    /** 向量相似度得分 */
    private Float vectorScore;

    /** 文档标题 */
    private String title;

    /** 发布时间 */
    private String publishTime;

    /** 分类 */
    private String category;

    /** 切片索引 */
    private Integer chunkIndex;

    /** 切片类型 */
    private String chunkType;

    /** 排名 */
    private Integer rank;
}
```

- [ ] **Step 3: Commit**

```bash
git add xwBackend/src/main/java/com/qy/dch/rag/model/
git commit -m "feat: add DocumentChunk and SearchResult models"
```

---

### Task 5: 切片服务迁移

**Files:**
- Create: `xwSystem0611/xwBackend/src/main/java/com/qy/dch/rag/chunk/ChineseTextAnalyzer.java`
- Create: `xwSystem0611/xwBackend/src/main/java/com/qy/dch/rag/chunk/IdGenerator.java`
- Create: `xwSystem0611/xwBackend/src/main/java/com/qy/dch/rag/chunk/ChunkService.java`

- [ ] **Step 1: 创建 ChineseTextAnalyzer.java**

从 xwRAG 迁移，包名改为 `com.qy.dch.rag.chunk`，去掉 `@Slf4j` 和 `@Component`，改用 `java.util.logging` 或直接去掉日志（轻量工具类）。

```java
package com.qy.dch.rag.chunk;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 中文文本分析器 - 句子切分
 */
public class ChineseTextAnalyzer {

    private static final Pattern SENTENCE_PATTERN = Pattern.compile(
        "[。！？；\\n]+|(?<=[。！？；])(?=[^\\s])"
    );

    /**
     * 按句子切分文本
     */
    public List<String> splitBySentence(String text) {
        if (text == null || text.trim().isEmpty()) {
            return new ArrayList<>();
        }

        List<String> sentences = new ArrayList<>();
        String[] parts = SENTENCE_PATTERN.split(text);

        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                sentences.add(trimmed);
            }
        }

        return sentences;
    }
}
```

- [ ] **Step 2: 创建 IdGenerator.java**

```java
package com.qy.dch.rag.chunk;

/**
 * ID 生成工具
 */
public class IdGenerator {

    /**
     * 生成切片ID: {docId}_chunk_{chunkIndex}
     */
    public static String generateChunkId(String documentId, int chunkIndex) {
        return documentId + "_chunk_" + chunkIndex;
    }
}
```

- [ ] **Step 3: 创建 ChunkService.java**

从 xwRAG 的 `DocumentChunkService` 迁移，简化并适配本项目参数。

```java
package com.qy.dch.rag.chunk;

import com.qy.dch.rag.config.RagProperties;
import com.qy.dch.rag.model.DocumentChunk;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 混合切片服务
 */
@Slf4j
@Service
public class ChunkService {

    @Autowired
    private RagProperties ragProperties;

    private final ChineseTextAnalyzer textAnalyzer = new ChineseTextAnalyzer();

    /**
     * 对文档内容进行切片
     */
    public List<DocumentChunk> chunkDocument(String documentId, String content, Map<String, Object> metadata) {
        if (content == null || content.trim().isEmpty()) {
            return new ArrayList<>();
        }

        List<DocumentChunk> chunks = new ArrayList<>();
        int contentLength = content.length();
        int shortThreshold = ragProperties.getChunk().getShortThreshold();
        int mediumThreshold = ragProperties.getChunk().getMediumThreshold();

        if (contentLength <= shortThreshold) {
            chunks.add(createChunk(documentId, content, 0, "short", metadata));
        } else if (contentLength <= mediumThreshold) {
            chunks.addAll(chunkBySentence(documentId, content, "medium", metadata));
        } else {
            chunks.addAll(chunkBySentenceWithOverlap(documentId, content, "long", metadata));
        }

        log.debug("文档 {} 切片完成: {} 个切片", documentId, chunks.size());
        return chunks;
    }

    private List<DocumentChunk> chunkBySentence(String documentId, String content,
                                                 String chunkType, Map<String, Object> metadata) {
        List<DocumentChunk> chunks = new ArrayList<>();
        List<String> sentences = textAnalyzer.splitBySentence(content);
        int targetLength = ragProperties.getChunk().getMediumSize();
        int currentIndex = 0;
        StringBuilder chunkBuilder = new StringBuilder();

        for (String sentence : sentences) {
            if (chunkBuilder.length() + sentence.length() <= targetLength) {
                chunkBuilder.append(sentence).append("\n");
            } else {
                if (chunkBuilder.length() > 0) {
                    chunks.add(createChunk(documentId, chunkBuilder.toString().trim(),
                            currentIndex++, chunkType, metadata));
                    chunkBuilder = new StringBuilder();
                }
                if (sentence.length() > targetLength) {
                    chunks.addAll(chunkByFixedSize(documentId, sentence, targetLength,
                            currentIndex, chunkType, metadata));
                    currentIndex = chunks.size();
                } else {
                    chunkBuilder.append(sentence).append("\n");
                }
            }
        }

        if (chunkBuilder.length() > 0) {
            chunks.add(createChunk(documentId, chunkBuilder.toString().trim(),
                    currentIndex, chunkType, metadata));
        }

        return chunks;
    }

    private List<DocumentChunk> chunkBySentenceWithOverlap(String documentId, String content,
                                                            String chunkType, Map<String, Object> metadata) {
        List<DocumentChunk> chunks = new ArrayList<>();
        List<String> sentences = textAnalyzer.splitBySentence(content);
        int targetLength = ragProperties.getChunk().getLongSize();
        int overlapLength = ragProperties.getChunk().getOverlap();
        int currentIndex = 0;

        List<String> currentChunkSentences = new ArrayList<>();
        int currentChunkLength = 0;

        for (int i = 0; i < sentences.size(); i++) {
            String sentence = sentences.get(i);
            int sentenceLength = sentence.length();

            if (currentChunkLength + sentenceLength <= targetLength) {
                currentChunkSentences.add(sentence);
                currentChunkLength += sentenceLength;
            } else {
                if (!currentChunkSentences.isEmpty()) {
                    String chunkContent = String.join("\n", currentChunkSentences);
                    chunks.add(createChunk(documentId, chunkContent, currentIndex++, chunkType, metadata));
                }

                currentChunkSentences = new ArrayList<>();
                currentChunkLength = 0;

                if (overlapLength > 0 && i > 0) {
                    int overlapStart = findOverlapStart(sentences, i, overlapLength);
                    for (int j = overlapStart; j < i; j++) {
                        currentChunkSentences.add(sentences.get(j));
                        currentChunkLength += sentences.get(j).length();
                    }
                }

                currentChunkSentences.add(sentence);
                currentChunkLength += sentenceLength;
            }
        }

        if (!currentChunkSentences.isEmpty()) {
            String chunkContent = String.join("\n", currentChunkSentences);
            chunks.add(createChunk(documentId, chunkContent, currentIndex, chunkType, metadata));
        }

        return chunks;
    }

    private int findOverlapStart(List<String> sentences, int currentIndex, int overlapLength) {
        int accumulatedLength = 0;
        int startIndex = currentIndex;
        while (startIndex > 0 && accumulatedLength < overlapLength) {
            startIndex--;
            accumulatedLength += sentences.get(startIndex).length();
        }
        return startIndex;
    }

    private List<DocumentChunk> chunkByFixedSize(String documentId, String content, int chunkSize,
                                                  int startIndex, String chunkType, Map<String, Object> metadata) {
        List<DocumentChunk> chunks = new ArrayList<>();
        int index = startIndex;
        int position = 0;

        while (position < content.length()) {
            int end = Math.min(position + chunkSize, content.length());
            String chunkContent = content.substring(position, end);
            chunks.add(createChunk(documentId, chunkContent, index++, chunkType, metadata));
            position += chunkSize;
        }

        return chunks;
    }

    private DocumentChunk createChunk(String documentId, String content, int index,
                                       String chunkType, Map<String, Object> metadata) {
        String cleanContent = content.replaceAll("\\s+", " ").trim();
        return DocumentChunk.builder()
                .id(IdGenerator.generateChunkId(documentId, index))
                .documentId(documentId)
                .content(cleanContent)
                .chunkIndex(index)
                .chunkType(chunkType)
                .length(cleanContent.length())
                .metadata(metadata != null ? new HashMap<>(metadata) : new HashMap<>())
                .build();
    }
}
```

- [ ] **Step 4: Commit**

```bash
git add xwBackend/src/main/java/com/qy/dch/rag/chunk/
git commit -m "feat: add ChunkService with hybrid chunking strategy"
```

---

### Task 6: EmbeddingService 迁移

**Files:**
- Create: `xwSystem0611/xwBackend/src/main/java/com/qy/dch/rag/embed/EmbeddingService.java`

- [ ] **Step 1: 创建 EmbeddingService.java**

从 xwRAG 迁移，去掉 WebClient 改为 RestTemplate（项目已有，无需 WebFlux），`float[]` 改为 `List<Float>` 适配 FastJSON。

```java
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

/**
 * BGE Embedding 客户端
 * 通过 HTTP 调用 xwEmbedding 服务 (:5002)
 */
@Slf4j
@Service
public class EmbeddingService {

    @Autowired
    private RagProperties ragProperties;

    @Autowired
    private RestTemplate embeddingRestTemplate;

    /**
     * 批量文本向量化
     * @param texts 文本列表
     * @return 向量列表，每个向量为 1024 维 float 数组
     */
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

    /**
     * 单条文本向量化
     */
    public float[] embed(String text) {
        List<float[]> results = embedBatch(Collections.singletonList(text));
        if (results != null && !results.isEmpty()) {
            return results.get(0);
        }
        return null;
    }

    /**
     * 调用 Embedding API，带重试
     */
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

        // 重试
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

    /**
     * 检查 Embedding 服务是否可用
     */
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
```

- [ ] **Step 2: Commit**

```bash
git add xwBackend/src/main/java/com/qy/dch/rag/embed/EmbeddingService.java
git commit -m "feat: add EmbeddingService with RestTemplate retry"
```

---

### Task 7: EsVectorStore ES 存储

**Files:**
- Create: `xwSystem0611/xwBackend/src/main/java/com/qy/dch/rag/store/EsVectorStore.java`

- [ ] **Step 1: 创建 EsVectorStore.java**

```java
package com.qy.dch.rag.store;

import com.qy.dch.rag.config.RagProperties;
import com.qy.dch.rag.model.DocumentChunk;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequest;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * ES 向量存储服务
 * 负责索引创建、批量写入
 */
@Slf4j
@Service
public class EsVectorStore {

    @Autowired
    private RestHighLevelClient restHighLevelClient;

    @Autowired
    private RagProperties ragProperties;

    private static final String INDEX_MAPPING = "{\n" +
            "  \"properties\": {\n" +
            "    \"chunk_id\":     { \"type\": \"keyword\" },\n" +
            "    \"doc_id\":       { \"type\": \"keyword\" },\n" +
            "    \"chunk_index\":  { \"type\": \"integer\" },\n" +
            "    \"content\":      { \"type\": \"text\", \"analyzer\": \"ik_max_word\" },\n" +
            "    \"embedding\":    { \"type\": \"dense_vector\", \"dims\": 1024 },\n" +
            "    \"title\":        { \"type\": \"text\", \"analyzer\": \"ik_smart\" },\n" +
            "    \"publish_time\": { \"type\": \"date\", \"format\": \"yyyy-MM-dd HH:mm:ss||yyyy-MM-dd||epoch_millis\" },\n" +
            "    \"category\":     { \"type\": \"keyword\" },\n" +
            "    \"indexed_at\":   { \"type\": \"date\" }\n" +
            "  }\n" +
            "}";

    /**
     * 确保索引存在，不存在则创建
     */
    public void ensureIndex() throws Exception {
        String indexName = ragProperties.getEs().getIndexName();
        GetIndexRequest getRequest = new GetIndexRequest(indexName);
        boolean exists = restHighLevelClient.indices().exists(getRequest, RequestOptions.DEFAULT);

        if (!exists) {
            CreateIndexRequest createRequest = new CreateIndexRequest(indexName);
            createRequest.settings(Settings.builder()
                    .put("index.number_of_shards", 3)
                    .put("index.number_of_replicas", 1)
                    .build());
            createRequest.mapping(INDEX_MAPPING, XContentType.JSON);
            restHighLevelClient.indices().create(createRequest, RequestOptions.DEFAULT);
            log.info("ES 索引 {} 创建成功", indexName);
        } else {
            log.info("ES 索引 {} 已存在", indexName);
        }
    }

    /**
     * 批量写入切片到 ES
     * @param chunks 切片列表
     * @return 成功写入的 doc_id 集合
     */
    public Set<String> bulkIndex(List<DocumentChunk> chunks) throws Exception {
        String indexName = ragProperties.getEs().getIndexName();
        BulkRequest bulkRequest = new BulkRequest();
        Set<String> successDocIds = new LinkedHashSet<>();

        for (DocumentChunk chunk : chunks) {
            Map<String, Object> source = new HashMap<>();
            source.put("chunk_id", chunk.getId());
            source.put("doc_id", chunk.getDocumentId());
            source.put("chunk_index", chunk.getChunkIndex());
            source.put("content", chunk.getContent());
            source.put("embedding", chunk.getEmbedding());
            source.put("title", chunk.getMetadata() != null ? chunk.getMetadata().getOrDefault("title", "") : "");
            source.put("publish_time", chunk.getMetadata() != null ? chunk.getMetadata().getOrDefault("publish_time", "") : "");
            source.put("category", chunk.getMetadata() != null ? chunk.getMetadata().getOrDefault("category", "") : "");
            source.put("indexed_at", new Date());

            IndexRequest indexRequest = new IndexRequest(indexName)
                    .id(chunk.getId())
                    .source(source, XContentType.JSON);
            bulkRequest.add(indexRequest);
        }

        BulkResponse bulkResponse = restHighLevelClient.bulk(bulkRequest, RequestOptions.DEFAULT);

        if (bulkResponse.hasFailures()) {
            log.warn("ES 批量写入部分失败: {}", bulkResponse.buildFailureMessage());
        }

        // 收集成功的 doc_id
        for (int i = 0; i < bulkResponse.getItems().length; i++) {
            if (!bulkResponse.getItems()[i].isFailed()) {
                DocumentChunk chunk = chunks.get(i);
                successDocIds.add(chunk.getDocumentId());
            } else {
                log.warn("ES 写入失败: chunk_id={}, reason={}",
                        chunks.get(i).getId(),
                        bulkResponse.getItems()[i].getFailureMessage());
            }
        }

        log.info("ES 批量写入完成: 总数={}, 成功={}", chunks.size(), successDocIds.size());
        return successDocIds;
    }

    /**
     * 检查 ES 是否可用
     */
    public boolean isAvailable() {
        try {
            return restHighLevelClient.ping(RequestOptions.DEFAULT);
        } catch (Exception e) {
            log.warn("ES 不可用: {}", e.getMessage());
            return false;
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add xwBackend/src/main/java/com/qy/dch/rag/store/EsVectorStore.java
git commit -m "feat: add EsVectorStore with index creation and bulk write"
```

---

### Task 8: HybridSearchService 混合检索

**Files:**
- Create: `xwSystem0611/xwBackend/src/main/java/com/qy/dch/rag/search/HybridSearchService.java`

- [ ] **Step 1: 创建 HybridSearchService.java**

基于 ES 7.17 的 `script_score` + `cosineSimilarity` 实现 kNN，替代 xwRAG 的 pgvector 方案。

```java
package com.qy.dch.rag.search;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.qy.dch.rag.config.RagProperties;
import com.qy.dch.rag.embed.EmbeddingService;
import com.qy.dch.rag.model.SearchResult;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.functionscore.FunctionScoreQueryBuilder;
import org.elasticsearch.index.query.functionscore.ScoreFunctionBuilders;
import org.elasticsearch.script.Script;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 混合检索服务
 * BM25 (match query) + kNN (script_score + cosineSimilarity) + RRF 融合
 */
@Slf4j
@Service
public class HybridSearchService {

    @Autowired
    private RestHighLevelClient restHighLevelClient;

    @Autowired
    private EmbeddingService embeddingService;

    @Autowired
    private RagProperties ragProperties;

    /**
     * BM25 全文检索
     */
    public List<SearchResult> bm25Search(String query, int topK) throws Exception {
        String indexName = ragProperties.getEs().getIndexName();
        SearchRequest searchRequest = new SearchRequest(indexName);
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        sourceBuilder.query(QueryBuilders.matchQuery("content", query));
        sourceBuilder.size(topK);

        searchRequest.source(sourceBuilder);
        SearchResponse response = restHighLevelClient.search(searchRequest, RequestOptions.DEFAULT);

        List<SearchResult> results = new ArrayList<>();
        int rank = 1;
        for (SearchHit hit : response.getHits().getHits()) {
            Map<String, Object> source = hit.getSourceAsMap();
            results.add(SearchResult.builder()
                    .chunkId((String) source.get("chunk_id"))
                    .docId((String) source.get("doc_id"))
                    .content((String) source.get("content"))
                    .bm25Score(hit.getScore())
                    .title((String) source.get("title"))
                    .publishTime((String) source.get("publish_time"))
                    .category((String) source.get("category"))
                    .rank(rank++)
                    .build());
        }

        return results;
    }

    /**
     * kNN 向量检索 (script_score + cosineSimilarity)
     */
    public List<SearchResult> vectorSearch(float[] queryVector, int topK) throws Exception {
        String indexName = ragProperties.getEs().getIndexName();

        // 将 float[] 转为 script 参数格式
        StringBuilder vectorStr = new StringBuilder("[");
        for (int i = 0; i < queryVector.length; i++) {
            if (i > 0) vectorStr.append(",");
            vectorStr.append(queryVector[i]);
        }
        vectorStr.append("]");

        String scriptSource = "cosineSimilarity(params.query_vector, 'embedding') + 1.0";
        Map<String, Object> params = new HashMap<>();
        params.put("query_vector", queryVector);

        Script script = new Script(Script.DEFAULT_SCRIPT_TYPE, "painless", scriptSource, params);

        FunctionScoreQueryBuilder functionScoreQuery = QueryBuilders.functionScoreQuery(
                QueryBuilders.matchAllQuery(),
                ScoreFunctionBuilders.scriptFunction(script)
        );

        SearchRequest searchRequest = new SearchRequest(indexName);
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        sourceBuilder.query(functionScoreQuery);
        sourceBuilder.size(topK);
        searchRequest.source(sourceBuilder);

        SearchResponse response = restHighLevelClient.search(searchRequest, RequestOptions.DEFAULT);

        List<SearchResult> results = new ArrayList<>();
        int rank = 1;
        for (SearchHit hit : response.getHits().getHits()) {
            Map<String, Object> source = hit.getSourceAsMap();
            results.add(SearchResult.builder()
                    .chunkId((String) source.get("chunk_id"))
                    .docId((String) source.get("doc_id"))
                    .content((String) source.get("content"))
                    .vectorScore(hit.getScore())
                    .title((String) source.get("title"))
                    .publishTime((String) source.get("publish_time"))
                    .category((String) source.get("category"))
                    .rank(rank++)
                    .build());
        }

        return results;
    }

    /**
     * 混合检索: BM25 + kNN + RRF 融合
     */
    public Map<String, Object> hybridSearch(String query, int topK) throws Exception {
        float bm25Weight = ragProperties.getSearch().getBm25Weight();
        float vectorWeight = ragProperties.getSearch().getVectorWeight();
        int rrfK = ragProperties.getSearch().getRrfK();

        // 1. 获取 query 向量
        float[] queryVector = embeddingService.embed(query);
        if (queryVector == null) {
            log.warn("Query 向量化失败，降级为纯 BM25 检索");
            List<SearchResult> bm25Results = bm25Search(query, topK);
            Map<String, Object> result = new HashMap<>();
            result.put("results", bm25Results);
            result.put("totalHits", bm25Results.size());
            result.put("searchMode", "bm25");
            result.put("took", 0);
            return result;
        }

        // 2. 并行检索
        List<SearchResult> bm25Results = bm25Search(query, topK * 2);
        List<SearchResult> vectorResults = vectorSearch(queryVector, topK * 2);

        // 3. RRF 融合
        Map<String, SearchResult> resultMap = new LinkedHashMap<>();
        Map<String, Float> scoreMap = new HashMap<>();

        for (int i = 0; i < bm25Results.size(); i++) {
            SearchResult r = bm25Results.get(i);
            String key = r.getChunkId();
            float rrfScore = bm25Weight / (rrfK + i + 1);
            resultMap.put(key, r);
            scoreMap.put(key, scoreMap.getOrDefault(key, 0f) + rrfScore);
        }

        for (int i = 0; i < vectorResults.size(); i++) {
            SearchResult r = vectorResults.get(i);
            String key = r.getChunkId();
            float rrfScore = vectorWeight / (rrfK + i + 1);
            resultMap.putIfAbsent(key, r);
            scoreMap.put(key, scoreMap.getOrDefault(key, 0f) + rrfScore);
        }

        List<SearchResult> fused = resultMap.values().stream()
                .peek(r -> r.setFinalScore(scoreMap.get(r.getChunkId())))
                .sorted((a, b) -> Float.compare(b.getFinalScore() != null ? b.getFinalScore() : 0,
                        a.getFinalScore() != null ? a.getFinalScore() : 0))
                .limit(topK)
                .collect(Collectors.toList());

        for (int i = 0; i < fused.size(); i++) {
            fused.get(i).setRank(i + 1);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("results", fused);
        result.put("totalHits", resultMap.size());
        result.put("searchMode", "hybrid");
        result.put("took", 0);
        return result;
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add xwBackend/src/main/java/com/qy/dch/rag/search/HybridSearchService.java
git commit -m "feat: add HybridSearchService with BM25 + kNN + RRF for ES 7.17"
```

---

### Task 9: RagMapper + DDL

**Files:**
- Create: `xwSystem0611/xwBackend/src/main/java/com/qy/dch/mapper/RagMapper.java`

- [ ] **Step 1: 创建 RagMapper.java**

```java
package com.qy.dch.mapper;

import com.qy.dch.dto.OriginTextDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

/**
 * RAG 模块数据访问层
 * 查询未索引报文、更新索引状态
 */
@Mapper
public interface RagMapper {

    /**
     * 按时间段查询未索引的报文ID列表
     */
    @Select("<script>" +
            "SELECT sid FROM origin_text " +
            "WHERE is_indexed = 0 " +
            "<if test='startDate != null and startDate != \"\"'>" +
            "AND times &gt;= #{startDate} " +
            "</if>" +
            "<if test='endDate != null and endDate != \"\"'>" +
            "AND times &lt;= #{endDate} " +
            "</if>" +
            "ORDER BY sid" +
            "</script>")
    List<Long> selectUnindexedIds(@Param("startDate") String startDate,
                                  @Param("endDate") String endDate);

    /**
     * 查询所有未索引的报文ID
     */
    @Select("SELECT sid FROM origin_text WHERE is_indexed = 0 ORDER BY sid")
    List<Long> selectAllUnindexedIds();

    /**
     * 根据ID列表查询报文完整信息
     */
    @Select("<script>" +
            "SELECT sid, title, content, times, type, modal_type, is_indexed " +
            "FROM origin_text WHERE sid IN " +
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>" +
            "#{id}" +
            "</foreach>" +
            "ORDER BY sid" +
            "</script>")
    List<OriginTextDTO> selectByIds(@Param("ids") List<Long> ids);

    /**
     * 批量更新 is_indexed 状态
     */
    @Update("<script>" +
            "UPDATE origin_text SET is_indexed = 1 WHERE sid IN " +
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>" +
            "#{id}" +
            "</foreach>" +
            "</script>")
    int updateIndexedStatus(@Param("ids") List<Long> ids);

    /**
     * 单条更新 is_indexed 状态
     */
    @Update("UPDATE origin_text SET is_indexed = 1 WHERE sid = #{sid}")
    int updateIndexedStatusById(@Param("sid") Long sid);

    /**
     * 查询索引状态统计
     */
    @Select("SELECT COUNT(*) as total, " +
            "SUM(CASE WHEN is_indexed=1 THEN 1 ELSE 0 END) as indexedCount, " +
            "SUM(CASE WHEN is_indexed=0 THEN 1 ELSE 0 END) as unindexedCount " +
            "FROM origin_text")
    Map<String, Object> getIndexStats();

    /**
     * 按日期分组统计索引状态
     */
    @Select("SELECT DATE(times) as date, COUNT(*) as total, " +
            "SUM(CASE WHEN is_indexed=1 THEN 1 ELSE 0 END) as indexedCount, " +
            "SUM(CASE WHEN is_indexed=0 THEN 1 ELSE 0 END) as unindexedCount " +
            "FROM origin_text " +
            "WHERE times IS NOT NULL " +
            "GROUP BY DATE(times) " +
            "ORDER BY date DESC " +
            "LIMIT 30")
    List<Map<String, Object>> getIndexStatsByDate();

    /**
     * 按条件查询已索引报文
     */
    @Select("<script>" +
            "SELECT sid, title, content, times, type, modal_type, is_indexed " +
            "FROM origin_text WHERE is_indexed = 1 " +
            "<if test='startDate != null and startDate != \"\"'>" +
            "AND times &gt;= #{startDate} " +
            "</if>" +
            "<if test='endDate != null and endDate != \"\"'>" +
            "AND times &lt;= #{endDate} " +
            "</if>" +
            "ORDER BY sid" +
            "</script>")
    List<OriginTextDTO> selectIndexedByDateRange(@Param("startDate") String startDate,
                                                  @Param("endDate") String endDate);
}
```

- [ ] **Step 2: 执行 DDL（手动或由部署脚本执行）**

```sql
ALTER TABLE origin_text ADD COLUMN is_indexed TINYINT(1) DEFAULT 0 COMMENT '是否已向量化入ES 0-未索引 1-已索引';
CREATE INDEX idx_is_indexed ON origin_text(is_indexed);
```

- [ ] **Step 3: Commit**

```bash
git add xwBackend/src/main/java/com/qy/dch/mapper/RagMapper.java
git commit -m "feat: add RagMapper with index status queries"
```

---

### Task 10: 请求 DTO 和响应 DTO

**Files:**
- Create: `xwSystem0611/xwBackend/src/main/java/com/qy/dch/request/RagSearchRequest.java`
- Create: `xwSystem0611/xwBackend/src/main/java/com/qy/dch/request/RagIndexTriggerRequest.java`
- Create: `xwSystem0611/xwBackend/src/main/java/com/qy/dch/dto/RagIndexStatusDTO.java`
- Create: `xwSystem0611/xwBackend/src/main/java/com/qy/dch/dto/RagIndexLogDTO.java`

- [ ] **Step 1: 创建 RagSearchRequest.java**

```java
package com.qy.dch.request;

import lombok.Data;

/**
 * 语义检索请求
 */
@Data
public class RagSearchRequest {
    private String query;
    private Integer topK = 10;
    private Boolean hybrid = true;
}
```

- [ ] **Step 2: 创建 RagIndexTriggerRequest.java**

```java
package com.qy.dch.request;

import lombok.Data;

/**
 * 触发索引请求
 */
@Data
public class RagIndexTriggerRequest {
    private String startDate;
    private String endDate;
}
```

- [ ] **Step 3: 创建 RagIndexStatusDTO.java**

```java
package com.qy.dch.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * 索引状态 DTO
 */
@Data
public class RagIndexStatusDTO {
    private Long totalDocs;
    private Long indexedDocs;
    private Long unindexedDocs;
    private List<Map<String, Object>> byDate;
}
```

- [ ] **Step 4: 创建 RagIndexLogDTO.java**

```java
package com.qy.dch.dto;

import lombok.Data;

/**
 * 索引日志 DTO
 */
@Data
public class RagIndexLogDTO {
    private Long id;
    private String triggerType;
    private String startTime;
    private String endTime;
    private String status;
    private Integer processedCount;
    private Integer successCount;
    private Integer skippedCount;
    private Integer failedCount;
}
```

- [ ] **Step 5: Commit**

```bash
git add xwBackend/src/main/java/com/qy/dch/request/RagSearchRequest.java xwBackend/src/main/java/com/qy/dch/request/RagIndexTriggerRequest.java xwBackend/src/main/java/com/qy/dch/dto/RagIndexStatusDTO.java xwBackend/src/main/java/com/qy/dch/dto/RagIndexLogDTO.java
git commit -m "feat: add RAG DTOs and request objects"
```

---

### Task 11: RagService 接口和实现

**Files:**
- Create: `xwSystem0611/xwBackend/src/main/java/com/qy/dch/service/RagService.java`
- Create: `xwSystem0611/xwBackend/src/main/java/com/qy/dch/service/impl/RagServiceImpl.java`

- [ ] **Step 1: 创建 RagService.java**

```java
package com.qy.dch.service;

import com.qy.dch.common.ResultVO;

/**
 * RAG 知识库服务接口
 */
public interface RagService {

    /**
     * 查询索引状态
     */
    ResultVO getIndexStatus();

    /**
     * 手动触发索引任务
     */
    ResultVO triggerIndexing(String startDate, String endDate);

    /**
     * 语义检索
     */
    ResultVO search(String query, Integer topK, Boolean hybrid);

    /**
     * 查询索引日志
     */
    ResultVO getIndexLog(Integer pageNum, Integer pageSize);

    /**
     * 定时任务：索引前一天新增报文
     */
    void scheduledIndexing();
}
```

- [ ] **Step 2: 创建 RagServiceImpl.java**

```java
package com.qy.dch.service.impl;

import com.alibaba.fastjson.JSON;
import com.qy.dch.common.ResultVO;
import com.qy.dch.dto.OriginTextDTO;
import com.qy.dch.dto.RagIndexStatusDTO;
import com.qy.dch.mapper.RagMapper;
import com.qy.dch.rag.chunk.ChunkService;
import com.qy.dch.rag.config.RagProperties;
import com.qy.dch.rag.embed.EmbeddingService;
import com.qy.dch.rag.model.DocumentChunk;
import com.qy.dch.rag.search.HybridSearchService;
import com.qy.dch.rag.store.EsVectorStore;
import com.qy.dch.service.RagService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * RAG 服务实现
 * 编排切片→向量化→ES 写入→检索全流程
 */
@Slf4j
@Service
public class RagServiceImpl implements RagService {

    @Resource
    private RagMapper ragMapper;

    @Autowired
    private ChunkService chunkService;

    @Autowired
    private EmbeddingService embeddingService;

    @Autowired
    private EsVectorStore esVectorStore;

    @Autowired
    private HybridSearchService hybridSearchService;

    @Autowired
    private RagProperties ragProperties;

    private final AtomicBoolean indexingRunning = new AtomicBoolean(false);

    @Override
    public ResultVO getIndexStatus() {
        try {
            Map<String, Object> stats = ragMapper.getIndexStats();
            List<Map<String, Object>> byDate = ragMapper.getIndexStatsByDate();

            RagIndexStatusDTO dto = new RagIndexStatusDTO();
            dto.setTotalDocs((Long) stats.get("total"));
            dto.setIndexedDocs((Long) stats.get("indexedCount"));
            dto.setUnindexedDocs((Long) stats.get("unindexedCount"));
            dto.setByDate(byDate);

            return ResultVO.success(dto);
        } catch (Exception e) {
            log.error("查询索引状态失败", e);
            return ResultVO.error("查询索引状态失败: " + e.getMessage());
        }
    }

    @Override
    public ResultVO triggerIndexing(String startDate, String endDate) {
        if (indexingRunning.get()) {
            return ResultVO.error("已有索引任务正在执行中，请等待其完成后再试");
        }

        List<Long> ids = ragMapper.selectUnindexedIds(startDate, endDate);
        if (ids.isEmpty()) {
            return ResultVO.success(createTaskResult("rag-0", "COMPLETED", 0, "没有需要索引的报文"));
        }

        String taskId = "rag-" + System.currentTimeMillis();
        indexingRunning.set(true);

        // 异步执行
        new Thread(() -> {
            try {
                doIndexing(ids);
            } finally {
                indexingRunning.set(false);
            }
        }, "rag-indexing-" + taskId).start();

        Map<String, Object> result = createTaskResult(taskId, "RUNNING", ids.size(),
                "任务已在后台执行，预计需要 5-10 分钟");
        return ResultVO.success(result);
    }

    private Map<String, Object> createTaskResult(String taskId, String status, int totalCount, String message) {
        Map<String, Object> result = new HashMap<>();
        result.put("taskId", taskId);
        result.put("status", status);
        result.put("totalCount", totalCount);
        result.put("message", message);
        return result;
    }

    /**
     * 执行索引：查询→切片→向量化→写入
     */
    private void doIndexing(List<Long> ids) {
        log.info("开始索引任务: 共 {} 篇报文", ids.size());
        int successCount = 0;
        int skippedCount = 0;
        int failedCount = 0;

        for (Long sid : ids) {
            try {
                // 1. 获取报文
                List<OriginTextDTO> texts = ragMapper.selectByIds(Collections.singletonList(sid));
                if (texts.isEmpty()) {
                    skippedCount++;
                    continue;
                }
                OriginTextDTO text = texts.get(0);

                if (text.getContent() == null || text.getContent().trim().isEmpty()) {
                    ragMapper.updateIndexedStatusById(sid);
                    skippedCount++;
                    continue;
                }

                // 2. 切片
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("title", text.getTitle() != null ? text.getTitle() : "");
                metadata.put("publish_time", text.getTimes() != null ? text.getTimes() : "");
                metadata.put("category", text.getType() != null ? String.valueOf(text.getType()) : "");

                List<DocumentChunk> chunks = chunkService.chunkDocument(
                        String.valueOf(sid), text.getContent(), metadata);

                if (chunks.isEmpty()) {
                    ragMapper.updateIndexedStatusById(sid);
                    skippedCount++;
                    continue;
                }

                // 3. 向量化
                List<String> chunkTexts = chunks.stream()
                        .map(DocumentChunk::getContent).collect(Collectors.toList());
                List<float[]> embeddings = embeddingService.embedBatch(chunkTexts);

                if (embeddings == null || embeddings.size() != chunks.size()) {
                    log.warn("向量化返回数量不匹配: sid={}, chunks={}, embeddings={}",
                            sid, chunks.size(), embeddings != null ? embeddings.size() : 0);
                    failedCount++;
                    continue;
                }

                for (int i = 0; i < chunks.size(); i++) {
                    chunks.get(i).setEmbedding(embeddings.get(i));
                }

                // 4. 写入 ES
                Set<String> successDocIds = esVectorStore.bulkIndex(chunks);
                if (successDocIds.contains(String.valueOf(sid))) {
                    ragMapper.updateIndexedStatusById(sid);
                    successCount++;
                } else {
                    failedCount++;
                }

            } catch (Exception e) {
                log.error("索引失败: sid={}", sid, e);
                failedCount++;
            }
        }

        log.info("索引任务完成: 成功={}, 跳过={}, 失败={}", successCount, skippedCount, failedCount);
    }

    @Override
    public ResultVO search(String query, Integer topK, Boolean hybrid) {
        if (query == null || query.trim().isEmpty()) {
            return ResultVO.error("搜索关键词不能为空");
        }

        try {
            if (topK == null || topK <= 0) {
                topK = ragProperties.getSearch().getDefaultTopK();
            }
            if (hybrid == null) {
                hybrid = true;
            }

            Map<String, Object> result = hybridSearchService.hybridSearch(query, topK);
            return ResultVO.success(result);
        } catch (Exception e) {
            log.error("语义检索失败", e);
            return ResultVO.error("语义检索失败: " + e.getMessage());
        }
    }

    @Override
    public ResultVO getIndexLog(Integer pageNum, Integer pageSize) {
        // 日志暂时返回空列表，后续可接入 rag_index_log 表
        Map<String, Object> result = new HashMap<>();
        result.put("total", 0);
        result.put("list", Collections.emptyList());
        return ResultVO.success(result);
    }

    @Override
    public void scheduledIndexing() {
        log.info("========== 开始每日 RAG 索引任务 ==========");
        try {
            // 初始化 ES 索引
            esVectorStore.ensureIndex();

            // 查询前一天新增的未索引报文
            LocalDate yesterday = LocalDate.now().minusDays(1);
            String dateStr = yesterday.toString();
            List<Long> ids = ragMapper.selectUnindexedIds(dateStr, dateStr);

            if (ids.isEmpty()) {
                log.info("没有需要索引的报文");
            } else {
                doIndexing(ids);
            }
        } catch (Exception e) {
            log.error("每日 RAG 索引任务失败", e);
        }
        log.info("========== 每日 RAG 索引任务结束 ==========");
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add xwBackend/src/main/java/com/qy/dch/service/RagService.java xwBackend/src/main/java/com/qy/dch/service/impl/RagServiceImpl.java
git commit -m "feat: add RagService with indexing orchestration"
```

---

### Task 12: RagController

**Files:**
- Create: `xwSystem0611/xwBackend/src/main/java/com/qy/dch/controller/RagController.java`

- [ ] **Step 1: 创建 RagController.java**

```java
package com.qy.dch.controller;

import com.qy.dch.common.ResultVO;
import com.qy.dch.request.RagIndexTriggerRequest;
import com.qy.dch.request.RagSearchRequest;
import com.qy.dch.service.RagService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * RAG 知识库控制器
 * 提供语义检索和向量化管理接口
 */
@RestController
@RequestMapping("/api/rag")
@Slf4j
public class RagController {

    @Autowired
    private RagService ragService;

    /**
     * 查询索引状态
     */
    @GetMapping("/index/status")
    public ResultVO getIndexStatus() {
        log.info("查询索引状态");
        return ragService.getIndexStatus();
    }

    /**
     * 手动触发向量化索引
     */
    @PostMapping("/index/trigger")
    public ResultVO triggerIndexing(@RequestBody RagIndexTriggerRequest request) {
        log.info("手动触发索引: startDate={}, endDate={}", request.getStartDate(), request.getEndDate());
        return ragService.triggerIndexing(request.getStartDate(), request.getEndDate());
    }

    /**
     * 语义检索
     */
    @PostMapping("/search")
    public ResultVO search(@RequestBody RagSearchRequest request) {
        log.info("语义检索: query={}, topK={}, hybrid={}", request.getQuery(), request.getTopK(), request.getHybrid());
        return ragService.search(request.getQuery(), request.getTopK(), request.getHybrid());
    }

    /**
     * 索引日志查询
     */
    @GetMapping("/index/log")
    public ResultVO getIndexLog(@RequestParam(defaultValue = "1") Integer pageNum,
                                 @RequestParam(defaultValue = "20") Integer pageSize) {
        log.info("查询索引日志: pageNum={}, pageSize={}", pageNum, pageSize);
        return ragService.getIndexLog(pageNum, pageSize);
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add xwBackend/src/main/java/com/qy/dch/controller/RagController.java
git commit -m "feat: add RagController with 4 REST endpoints"
```

---

### Task 13: RagIndexingTask 定时任务

**Files:**
- Create: `xwSystem0611/xwBackend/src/main/java/com/qy/dch/task/RagIndexingTask.java`

- [ ] **Step 1: 创建 RagIndexingTask.java**

```java
package com.qy.dch.task;

import com.qy.dch.service.RagService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 每日 RAG 索引定时任务
 * 每日凌晨 3:00 自动向量化前一天新增报文
 */
@Component
@Slf4j
public class RagIndexingTask {

    @Autowired
    private RagService ragService;

    /**
     * 每日凌晨 3:00 执行
     * DailyAnalysisTask 凌晨 2:00 执行，错开一小时
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void dailyIndexing() {
        log.info("========== 开始每日 RAG 索引任务 ==========");
        try {
            ragService.scheduledIndexing();
        } catch (Exception e) {
            log.error("每日 RAG 索引任务失败", e);
        }
        log.info("========== 每日 RAG 索引任务结束 ==========");
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add xwBackend/src/main/java/com/qy/dch/task/RagIndexingTask.java
git commit -m "feat: add RagIndexingTask scheduled at 3:00 AM daily"
```

---

### Task 14: application.yml 配置更新

**Files:**
- Modify: `xwSystem0611/xwBackend/src/main/resources/application.yml`

- [ ] **Step 1: 在 application.yml 末尾添加 RAG 配置**

```yaml
# RAG 知识库配置
rag:
  elasticsearch:
    host: ${ES_HOST:localhost}
    port: ${ES_PORT:9200}
    username: ${ES_USERNAME:}
    password: ${ES_PASSWORD:}
    index-name: xianwei_docs
  embedding:
    base-url: ${EMBEDDING_BASE_URL:http://localhost:5002}
    model: bge-large-zh-v1.5
    dimension: 1024
    batch-size: 32
    retry-count: 3
    retry-delay-ms: 5000
    timeout-seconds: 60
  chunk:
    short-threshold: 128
    medium-threshold: 512
    medium-size: 256
    long-size: 512
    overlap: 64
  indexing:
    max-duration-minutes: 30
    es-batch-size: 100
  search:
    bm25-weight: 0.3
    vector-weight: 0.7
    rrf-k: 60
    default-top-k: 10
```

- [ ] **Step 2: 验证配置加载**

```bash
cd xwBackend
mvn spring-boot:run 2>&1 | grep -i "rag"
# 预期: 无报错，配置正常绑定
```

- [ ] **Step 3: Commit**

```bash
git add xwBackend/src/main/resources/application.yml
git commit -m "feat: add RAG configuration to application.yml"
```

---

### Task 15: 集成测试与验证

**Files:**
- Create: `xwSystem0611/xwBackend/src/test/java/com/qy/dch/rag/RagModuleTest.java`

- [ ] **Step 1: 创建集成测试**

```java
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
        String content = "这是一条短文本，不超过128字符。";
        List<DocumentChunk> chunks = chunkService.chunkDocument("test1", content, new HashMap<>());
        assertNotNull(chunks);
        assertEquals(1, chunks.size());
        assertEquals("short", chunks.get(0).getChunkType());
    }

    @Test
    void testMediumTextChunk() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            sb.append("这是第").append(i).append("条测试文本。内容用于验证中等长度文本的切片逻辑。");
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
            sb.append("这是第").append(i).append("条测试文本。内容用于验证长文本滑动窗口切片逻辑。");
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
```

- [ ] **Step 2: 运行测试**

```bash
cd xwBackend
mvn test -Dtest=RagModuleTest
# 预期: 5 tests passed
```

- [ ] **Step 3: Commit**

```bash
git add xwBackend/src/test/java/com/qy/dch/rag/RagModuleTest.java
git commit -m "test: add RagModuleTest for chunk service"
```

---

## Spec 覆盖检查清单

| 设计要求 | 对应任务 |
|----------|----------|
| xwEmbedding 独立服务 | Task 0 |
| ES 7.17 依赖 | Task 1 |
| RagProperties 配置 | Task 2 |
| ES + Embedding 配置类 | Task 3 |
| DocumentChunk / SearchResult 模型 | Task 4 |
| 混合切片服务 | Task 5 |
| EmbeddingService 迁移 | Task 6 |
| ES 索引 + 批量写入 | Task 7 |
| BM25 + kNN 混合检索 | Task 8 |
| RagMapper + DDL | Task 9 |
| 请求/响应 DTO | Task 10 |
| RagService 业务编排 | Task 11 |
| 4 个 REST 端点 | Task 12 |
| 定时任务 3:00 | Task 13 |
| application.yml 配置 | Task 14 |
| 单元测试 | Task 15 |
| is_indexed 标记 | Task 9 (DDL) + Task 11 (回写) |
| 幂等性 (doc_id+chunk 作为 _id) | Task 7 |
| 错误处理 (重试/跳过/回滚) | Task 6, Task 7, Task 11 |
| 并发控制 (AtomicBoolean) | Task 11 |
| 离线部署 | Task 0 (models 目录) |

---

**计划版本**: v1.0
**最后更新**: 2026-06-11