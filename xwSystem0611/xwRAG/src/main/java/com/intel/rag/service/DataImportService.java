package com.intel.rag.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intel.rag.entity.DataRecord;
import com.intel.rag.model.Document;
import com.intel.rag.model.DocumentChunk;
import com.intel.rag.repository.DataRecordRepository;
import com.intel.rag.util.IdGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * MySQL数据导入服务
 * 只在DataRecordRepository存在时才创建
 */
@Slf4j
@Service
@ConditionalOnBean(DataRecordRepository.class)
public class DataImportService {

    private final DataRecordRepository dataRecordRepository;
    private final DocumentChunkService documentChunkService;
    private final PgVectorService pgVectorService;
    private final ObjectMapper objectMapper;

    public DataImportService(
            DataRecordRepository dataRecordRepository,
            DocumentChunkService documentChunkService,
            PgVectorService pgVectorService) {
        this.dataRecordRepository = dataRecordRepository;
        this.documentChunkService = documentChunkService;
        this.pgVectorService = pgVectorService;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 导入所有数据
     */
    @Transactional(readOnly = true)
    public ImportResult importAll() {
        log.info("开始导入所有数据");
        long startTime = System.currentTimeMillis();

        long totalRecords = dataRecordRepository.countAll();
        log.info("待导入记录总数: {}", totalRecords);

        int batchSize = 100;
        int totalBatches = (int) Math.ceil((double) totalRecords / batchSize);
        int successCount = 0;
        int failCount = 0;

        for (int i = 0; i < totalBatches; i++) {
            try {
                Pageable pageable = PageRequest.of(i, batchSize);
                Page<DataRecord> page = dataRecordRepository.findAll(pageable);

                List<DataRecord> records = page.getContent();
                log.info("正在处理批次 {}/{}: 记录数={}", i + 1, totalBatches, records.size());

                int batchSuccess = importBatch(records);
                successCount += batchSuccess;
                failCount += (records.size() - batchSuccess);

                log.info("批次 {}/{} 完成: 成功={}, 失败={}",
                        i + 1, totalBatches, batchSuccess, records.size() - batchSuccess);

            } catch (Exception e) {
                log.error("批次 {}/{} 导入失败", i + 1, totalBatches, e);
                failCount += batchSize;
            }
        }

        long elapsedTime = System.currentTimeMillis() - startTime;

        ImportResult result = ImportResult.builder()
                .totalRecords(totalRecords)
                .successCount(successCount)
                .failCount(failCount)
                .elapsedTime(elapsedTime)
                .build();

        log.info("数据导入完成: 总数={}, 成功={}, 失败={}, 耗时={}ms",
                totalRecords, successCount, failCount, elapsedTime);

        return result;
    }

    /**
     * 批量导入数据记录
     */
    private int importBatch(List<DataRecord> records) {
        int successCount = 0;

        try {
            // 转换为Document对象
            List<Document> documents = records.stream()
                    .map(this::convertToDocument)
                    .collect(Collectors.toList());

            // 对每个Document进行切片
            List<DocumentChunk> allChunks = new ArrayList<>();
            for (Document document : documents) {
                List<DocumentChunk> chunks = documentChunkService.chunkDocument(
                        document.getId(),
                        document.getContent(),
                        document.getMetadata()
                );
                allChunks.addAll(chunks);
            }

            // 批量保存到PostgreSQL
            if (!allChunks.isEmpty()) {
                pgVectorService.storeChunks(allChunks);
                successCount = records.size();
            }

        } catch (Exception e) {
            log.error("批量导入失败", e);
        }

        return successCount;
    }

    /**
     * 导入指定类型的数据
     */
    @Transactional(readOnly = true)
    public ImportResult importByType(Integer type) {
        log.info("开始导入类型数据: {}", type);
        long startTime = System.currentTimeMillis();

        int batchSize = 100;
        int pageNumber = 0;
        int successCount = 0;
        int failCount = 0;
        long totalRecords = 0;

        while (true) {
            Pageable pageable = PageRequest.of(pageNumber, batchSize);
            Page<DataRecord> page = dataRecordRepository.findByType(type, pageable);

            if (page.isEmpty()) {
                break;
            }

            totalRecords = page.getTotalElements();
            List<DataRecord> records = page.getContent();

            int batchSuccess = importBatch(records);
            successCount += batchSuccess;
            failCount += (records.size() - batchSuccess);

            log.info("批次 {} 完成: 成功={}, 失败={}", pageNumber + 1, batchSuccess, records.size() - batchSuccess);

            if (!page.hasNext()) {
                break;
            }

            pageNumber++;
        }

        long elapsedTime = System.currentTimeMillis() - startTime;

        ImportResult result = ImportResult.builder()
                .totalRecords(totalRecords)
                .successCount(successCount)
                .failCount(failCount)
                .elapsedTime(elapsedTime)
                .build();

        log.info("类型数据导入完成: 类型={}, 总数={}, 成功={}, 失败={}, 耗时={}ms",
                type, totalRecords, successCount, failCount, elapsedTime);

        return result;
    }

    /**
     * 导入指定ID列表的数据
     */
    @Transactional(readOnly = true)
    public ImportResult importByIds(List<Integer> ids) {
        log.info("开始导入指定ID数据: 数量={}", ids.size());
        long startTime = System.currentTimeMillis();

        List<DataRecord> records = dataRecordRepository.findBySidIn(ids);
        int successCount = importBatch(records);
        int failCount = records.size() - successCount;

        long elapsedTime = System.currentTimeMillis() - startTime;

        ImportResult result = ImportResult.builder()
                .totalRecords(records.size())
                .successCount(successCount)
                .failCount(failCount)
                .elapsedTime(elapsedTime)
                .build();

        log.info("指定ID数据导入完成: 总数={}, 成功={}, 失败={}, 耗时={}ms",
                records.size(), successCount, failCount, elapsedTime);

        return result;
    }

    /**
     * 转换DataRecord为Document
     */
    private Document convertToDocument(DataRecord record) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("recordId", record.getSid());
        metadata.put("title", record.getTitle());
        metadata.put("type", record.getType());
        metadata.put("times", record.getTimes());
        metadata.put("modalType", record.getModalType());
        metadata.put("isExtracted", record.getIsExtracted());

        // 扩展字段
        if (record.getExtend1() != null) {
            metadata.put("extend1", record.getExtend1());
        }
        if (record.getExtend2() != null) {
            metadata.put("extend2", record.getExtend2());
        }
        if (record.getExtend3() != null) {
            metadata.put("extend3", record.getExtend3());
        }

        // 组合标题和内容
        StringBuilder contentBuilder = new StringBuilder();
        if (record.getTitle() != null && !record.getTitle().isEmpty()) {
            contentBuilder.append("# ").append(record.getTitle()).append("\n\n");
        }
        if (record.getContent() != null) {
            contentBuilder.append(record.getContent());
        }

        return Document.builder()
                .id(IdGenerator.generateDocumentId("origin_text_" + record.getSid()))
                .content(contentBuilder.toString())
                .source("mysql_origin_text")
                .type(record.getModalType() != null ? record.getModalType() : "text")
                .metadata(metadata)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    /**
     * 获取数据统计信息
     */
    public DataStatistics getStatistics() {
        long totalCount = dataRecordRepository.countAll();

        return DataStatistics.builder()
                .totalRecords(totalCount)
                .build();
    }

    /**
     * 导入结果
     */
    @lombok.Builder
    @lombok.Data
    public static class ImportResult {
        private long totalRecords;
        private int successCount;
        private int failCount;
        private long elapsedTime;

        public double getSuccessRate() {
            return totalRecords > 0 ? (double) successCount / totalRecords * 100 : 0;
        }
    }

    /**
     * 数据统计
     */
    @lombok.Builder
    @lombok.Data
    public static class DataStatistics {
        private long totalRecords;
    }
}
