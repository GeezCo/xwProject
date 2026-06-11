package com.qy.dch.service.impl;

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
            Map<String, Object> result = new HashMap<>();
            result.put("taskId", "rag-0");
            result.put("status", "COMPLETED");
            result.put("totalCount", 0);
            result.put("message", "没有需要索引的报文");
            return ResultVO.success(result);
        }

        String taskId = "rag-" + System.currentTimeMillis();
        indexingRunning.set(true);

        new Thread(() -> {
            try {
                doIndexing(ids);
            } finally {
                indexingRunning.set(false);
            }
        }, "rag-indexing-" + taskId).start();

        Map<String, Object> result = new HashMap<>();
        result.put("taskId", taskId);
        result.put("status", "RUNNING");
        result.put("totalCount", ids.size());
        result.put("message", "任务已在后台执行，预计需要 5-10 分钟");
        return ResultVO.success(result);
    }

    private void doIndexing(List<Long> ids) {
        log.info("开始索引任务: 共 {} 篇报文", ids.size());
        int successCount = 0;
        int skippedCount = 0;
        int failedCount = 0;

        for (Long sid : ids) {
            try {
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
        Map<String, Object> result = new HashMap<>();
        result.put("total", 0);
        result.put("list", Collections.emptyList());
        return ResultVO.success(result);
    }

    @Override
    public void scheduledIndexing() {
        log.info("========== 开始每日 RAG 索引任务 ==========");
        try {
            esVectorStore.ensureIndex();

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
