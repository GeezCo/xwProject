package com.intel.rag.controller;

import com.intel.rag.dto.ApiResponse;
import com.intel.rag.dto.DocumentUploadResponse;
import com.intel.rag.dto.MySQLImportRequest;
import com.intel.rag.model.Document;
import com.intel.rag.service.DataImportService;
import com.intel.rag.service.DocxMixedParserService;
import com.intel.rag.service.DocumentChunkService;
import com.intel.rag.service.PgVectorService;
import com.intel.rag.util.IdGenerator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 文档导入接口
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/documents")
@Tag(name = "文档接口", description = "文档导入相关接口")
@Validated
public class DocumentController {

    private final DocxMixedParserService docxParser;
    private final DocumentChunkService chunkService;
    private final PgVectorService pgVectorService;
    private final DataImportService dataImportService;

    public DocumentController(DocxMixedParserService docxParser,
                              DocumentChunkService chunkService,
                              PgVectorService pgVectorService,
                              @org.springframework.lang.Nullable DataImportService dataImportService) {
        this.docxParser = docxParser;
        this.chunkService = chunkService;
        this.pgVectorService = pgVectorService;
        this.dataImportService = dataImportService;
    }

    /**
     * 上传DOCX文件
     */
    @PostMapping("/upload")
    @Operation(summary = "上传DOCX文件", description = "上传DOCX文件并自动解析、切片、向量化、存储")
    public ApiResponse<DocumentUploadResponse> uploadDocument(
            @RequestParam("file") MultipartFile file) throws IOException {

        log.info("收到文件上传请求: filename={}, size={}", file.getOriginalFilename(), file.getSize());

        if (file.isEmpty()) {
            return ApiResponse.error(400, "文件为空");
        }

        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".docx")) {
            return ApiResponse.error(400, "仅支持DOCX格式文件");
        }

        long startTime = System.currentTimeMillis();

        // 保存临时文件
        File tempFile = File.createTempFile("upload_", ".docx");
        file.transferTo(tempFile);

        try {
            // 解析文档
            Document document = docxParser.parseMixedDocx(tempFile.getAbsolutePath());
            document.setId(IdGenerator.generateDocumentId("upload"));
            document.setSource("upload");
            document.setCreatedAt(LocalDateTime.now());
            document.setUpdatedAt(LocalDateTime.now());

            // 添加文件名到元数据
            Map<String, Object> metadata = document.getMetadata();
            if (metadata == null) {
                metadata = new HashMap<>();
            }
            metadata.put("filename", filename);
            metadata.put("fileSize", file.getSize());
            document.setMetadata(metadata);

            // 切片
            List<com.intel.rag.model.DocumentChunk> chunks = chunkService.chunkDocument(
                    document.getId(), document.getContent(), document.getMetadata());

            // 存储到PostgreSQL
            pgVectorService.storeChunks(chunks);
            int chunkCount = chunks.size();

            long elapsedTime = System.currentTimeMillis() - startTime;

            DocumentUploadResponse response = DocumentUploadResponse.builder()
                    .documentId(document.getId())
                    .filename(filename)
                    .chunks(chunkCount)
                    .status("success")
                    .elapsedTime(elapsedTime)
                    .metadata(metadata)
                    .build();

            log.info("文件上传处理完成: documentId={}, chunks={}, 耗时{}ms",
                    document.getId(), chunkCount, elapsedTime);

            return ApiResponse.success(response);

        } finally {
            // 删除临时文件
            if (tempFile.exists()) {
                tempFile.delete();
            }
        }
    }

    /**
     * 从MySQL导入数据
     */
    @PostMapping("/import/mysql")
    @Operation(summary = "从MySQL导入数据", description = "从MySQL数据库导入文档数据")
    public ApiResponse<Map<String, Object>> importFromMySQL(
            @Valid @RequestBody MySQLImportRequest request) {

        if (dataImportService == null) {
            return ApiResponse.error(503, "MySQL数据导入服务不可用（DataRecordRepository未配置）");
        }

        log.info("收到MySQL导入请求: type={}", request.getType());

        DataImportService.ImportResult result;

        switch (request.getType().toLowerCase()) {
            case "all":
                result = dataImportService.importAll();
                break;
            case "bytype":
                if (request.getTypeId() == null) {
                    return ApiResponse.error(400, "类型ID不能为空");
                }
                result = dataImportService.importByType(request.getTypeId());
                break;
            case "byids":
                if (request.getIds() == null || request.getIds().isEmpty()) {
                    return ApiResponse.error(400, "ID列表不能为空");
                }
                result = dataImportService.importByIds(request.getIds());
                break;
            default:
                return ApiResponse.error(400, "不支持的导入类型: " + request.getType());
        }

        Map<String, Object> responseData = new HashMap<>();
        responseData.put("totalRecords", result.getTotalRecords());
        responseData.put("successCount", result.getSuccessCount());
        responseData.put("failCount", result.getFailCount());
        responseData.put("elapsedTime", result.getElapsedTime());

        log.info("MySQL导入完成: 总数={}, 成功={}, 失败={}, 耗时={}ms",
                result.getTotalRecords(), result.getSuccessCount(),
                result.getFailCount(), result.getElapsedTime());

        return ApiResponse.success(responseData);
    }
}