package com.intel.rag.service;

import com.intel.rag.config.DocumentParserProperties;
import com.intel.rag.model.Document;
import com.intel.rag.util.IdGenerator;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * DOCX文档解析服务
 */
@Slf4j
@Service
public class DocxParserService {

    private final DocumentParserProperties parserProperties;

    public DocxParserService(DocumentParserProperties parserProperties) {
        this.parserProperties = parserProperties;
    }

    /**
     * 解析DOCX文档（纯文本）
     *
     * @param filePath 文件路径
     * @return Document对象
     * @throws IOException 文件读取异常
     */
    public Document parseSimpleDocx(String filePath) throws IOException {
        log.info("开始解析DOCX文档: {}", filePath);
        long startTime = System.currentTimeMillis();

        try (InputStream fis = new FileInputStream(filePath);
             XWPFDocument document = new XWPFDocument(fis)) {

            // 提取所有段落文本
            String content = document.getParagraphs()
                    .stream()
                    .map(XWPFParagraph::getText)
                    .filter(text -> text != null && !text.trim().isEmpty())
                    .collect(Collectors.joining("\n"));

            // 构建元数据
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("filePath", filePath);
            metadata.put("fileName", extractFileName(filePath));
            metadata.put("paragraphCount", document.getParagraphs().size());
            metadata.put("contentLength", content.length());
            metadata.put("parseTime", System.currentTimeMillis() - startTime);

            // 创建Document对象
            Document doc = Document.builder()
                    .id(IdGenerator.generateDocumentId("docx"))
                    .content(content)
                    .source("docx")
                    .type("text")
                    .metadata(metadata)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            log.info("DOCX文档解析完成: {} (耗时: {}ms, 字符数: {})",
                    filePath, metadata.get("parseTime"), content.length());

            return doc;
        }
    }

    /**
     * 从文件路径中提取文件名
     */
    private String extractFileName(String filePath) {
        int lastSlash = Math.max(filePath.lastIndexOf('/'), filePath.lastIndexOf('\\'));
        return lastSlash >= 0 ? filePath.substring(lastSlash + 1) : filePath;
    }

    /**
     * 验证文件是否为DOCX格式
     */
    public boolean isValidDocx(String filePath) {
        return filePath != null && filePath.toLowerCase().endsWith(".docx");
    }
}
