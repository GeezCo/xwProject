# PR8: 混合检索实现

## 1. 需求概述

实现基于Elasticsearch的混合检索功能，结合BM25关键词检索和kNN向量检索，使用RRF（Reciprocal Rank Fusion）算法融合结果，为RAG系统提供高质量的文档召回能力。

## 2. 功能目标

### 2.1 检索方式
- **BM25关键词检索**：基于传统TF-IDF改进的关键词匹配
- **kNN向量检索**：基于BGE embedding的语义相似度检索
- **混合检索**：同时执行BM25和kNN，使用RRF算法融合

### 2.2 核心功能
- 支持单一查询文本输入
- 返回Top-K相关文档切片
- 包含相关性得分和排序
- 支持可配置的检索参数

## 3. 技术设计

### 3.1 架构设计

```
用户查询文本
    ↓
查询向量化（EmbeddingService）
    ↓
并行检索
├── BM25检索（关键词匹配）
└── kNN检索（向量相似度）
    ↓
RRF融合算法
    ↓
返回Top-K结果
```

### 3.2 类设计

#### SearchRequest（检索请求）
```java
@Data
@Builder
public class SearchRequest {
    private String query;              // 查询文本
    private Integer topK;              // 返回结果数量（默认10）
    private Float bm25Weight;          // BM25权重（默认0.3）
    private Float vectorWeight;        // 向量权重（默认0.7）
    private Boolean hybridSearch;      // 是否混合检索（默认true）
    private Map<String, Object> filters; // 过滤条件
}
```

#### SearchResult（检索结果）
```java
@Data
@Builder
public class SearchResult {
    private String chunkId;            // 切片ID
    private String documentId;         // 文档ID
    private String content;            // 内容
    private Float score;               // 融合得分
    private Float bm25Score;           // BM25得分
    private Float vectorScore;         // 向量得分
    private Map<String, Object> metadata; // 元数据
}
```

#### HybridSearchService（混合检索服务）
```java
@Service
public class HybridSearchService {
    // BM25检索
    List<SearchResult> bm25Search(String query, int topK);
    
    // kNN向量检索
    List<SearchResult> vectorSearch(String query, int topK);
    
    // 混合检索（RRF融合）
    List<SearchResult> hybridSearch(SearchRequest request);
    
    // RRF融合算法
    List<SearchResult> fuseResults(List<SearchResult> bm25Results, 
                                    List<SearchResult> vectorResults,
                                    float bm25Weight, 
                                    float vectorWeight);
}
```

### 3.3 Elasticsearch查询

#### BM25查询（Full-text search）
```json
{
  "query": {
    "match": {
      "content": {
        "query": "用户查询文本",
        "analyzer": "ik_max_word"
      }
    }
  },
  "size": 10
}
```

#### kNN向量查询
```json
{
  "knn": {
    "field": "embedding",
    "query_vector": [0.1, 0.2, ...],
    "k": 10,
    "num_candidates": 100
  }
}
```

#### 混合查询（Elasticsearch 8.x原生支持）
```json
{
  "query": {
    "bool": {
      "should": [
        {
          "match": {
            "content": {
              "query": "用户查询文本",
              "boost": 0.3
            }
          }
        }
      ]
    }
  },
  "knn": {
    "field": "embedding",
    "query_vector": [0.1, 0.2, ...],
    "k": 10,
    "num_candidates": 100,
    "boost": 0.7
  }
}
```

### 3.4 RRF融合算法

**公式**：
```
RRF_score(d) = Σ(1 / (k + rank_i(d)))
```

- `d`: 文档
- `k`: 常量（通常为60）
- `rank_i(d)`: 文档d在第i个检索结果列表中的排名

**实现逻辑**：
1. 分别执行BM25和kNN检索，获得两个排序列表
2. 对每个文档，计算其在两个列表中的RRF得分
3. 按最终RRF得分排序，返回Top-K

### 3.5 配置项

在`application.yml`中添加：

```yaml
retrieval:
  hybrid-search:
    bm25-weight: 0.3       # BM25权重
    vector-weight: 0.7     # 向量权重
    top-k: 10              # 默认返回数量
    rrf-k: 60              # RRF算法的k常数
  vector-search:
    num-candidates: 100    # kNN候选数量
  filters:
    enabled: true          # 是否启用过滤
```

## 4. 实现步骤

### Step 1: 创建模型类
- `SearchRequest.java`
- `SearchResult.java`
- `RetrievalProperties.java`（配置类）

### Step 2: 实现HybridSearchService
- BM25检索方法
- kNN向量检索方法
- RRF融合算法
- 混合检索主方法

### Step 3: 集成EmbeddingService
- 查询文本向量化
- 向量作为kNN查询输入

### Step 4: Elasticsearch查询构建
- 使用Elasticsearch Java API构建查询
- 支持BM25 + kNN混合查询

### Step 5: 测试
- 单元测试（Mock Elasticsearch）
- 集成测试（需要Elasticsearch服务）

## 5. 测试用例

### 5.1 BM25检索测试
```java
@Test
void testBm25Search() {
    SearchRequest request = SearchRequest.builder()
        .query("空军基地")
        .topK(5)
        .hybridSearch(false)
        .build();
    
    List<SearchResult> results = hybridSearchService.bm25Search(
        request.getQuery(), request.getTopK());
    
    assertNotNull(results);
    assertTrue(results.size() <= 5);
}
```

### 5.2 向量检索测试
```java
@Test
void testVectorSearch() {
    SearchRequest request = SearchRequest.builder()
        .query("军事设施概况")
        .topK(5)
        .build();
    
    List<SearchResult> results = hybridSearchService.vectorSearch(
        request.getQuery(), request.getTopK());
    
    assertNotNull(results);
    assertTrue(results.size() <= 5);
}
```

### 5.3 混合检索测试
```java
@Test
void testHybridSearch() {
    SearchRequest request = SearchRequest.builder()
        .query("阿里·萨利姆空军基地")
        .topK(10)
        .bm25Weight(0.3f)
        .vectorWeight(0.7f)
        .hybridSearch(true)
        .build();
    
    List<SearchResult> results = hybridSearchService.hybridSearch(request);
    
    assertNotNull(results);
    assertTrue(results.size() <= 10);
    // 验证得分递减
    for (int i = 1; i < results.size(); i++) {
        assertTrue(results.get(i-1).getScore() >= results.get(i).getScore());
    }
}
```

### 5.4 RRF融合测试
```java
@Test
void testRrfFusion() {
    List<SearchResult> bm25Results = createMockResults("bm25", 5);
    List<SearchResult> vectorResults = createMockResults("vector", 5);
    
    List<SearchResult> fused = hybridSearchService.fuseResults(
        bm25Results, vectorResults, 0.3f, 0.7f);
    
    assertNotNull(fused);
    // 验证融合逻辑
}
```

## 6. 成功标准

- ✅ 支持BM25关键词检索
- ✅ 支持kNN向量检索
- ✅ 实现RRF融合算法
- ✅ 混合检索返回正确排序的结果
- ✅ 所有单元测试通过
- ✅ 代码覆盖率 > 80%

## 7. 依赖项

- Elasticsearch 8.11.3+（已有）
- EmbeddingService（已完成，PR5）
- ElasticsearchService（已完成，PR6）

## 8. 参考资料

- [Elasticsearch Hybrid Search](https://www.elastic.co/guide/en/elasticsearch/reference/8.11/knn-search.html)
- [RRF Algorithm Paper](https://plg.uwaterloo.ca/~gvcormac/cormacksigir09-rrf.pdf)
- [BGE Embedding Model](https://huggingface.co/BAAI/bge-large-zh-v1.5)

## 9. 风险与限制

### 风险
- Elasticsearch服务未部署时测试无法完整执行
- kNN查询对向量维度和质量敏感

### 限制
- 需要Elasticsearch 8.x支持原生kNN
- BGE embedding服务需要运行
- 大数据量时kNN性能可能受影响

## 10. 后续优化

- 支持重排序（Rerank）
- 查询扩展（Query Expansion）
- 结果过滤和元数据筛选
- 缓存热门查询结果
