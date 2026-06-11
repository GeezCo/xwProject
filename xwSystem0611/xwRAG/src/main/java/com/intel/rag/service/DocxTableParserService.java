package com.intel.rag.service;

import com.intel.rag.config.DocumentParserProperties;
import com.intel.rag.model.Document;
import com.intel.rag.util.IdGenerator;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * DOCX表格解析服务
 */
@Slf4j
@Service
public class DocxTableParserService {

    private final DocumentParserProperties parserProperties;

    public DocxTableParserService(DocumentParserProperties parserProperties) {
        this.parserProperties = parserProperties;
    }

    /**
     * 解析DOCX文档中的表格
     *
     * @param filePath 文件路径
     * @return Document对象列表（每个表格一个Document）
     * @throws IOException 文件读取异常
     */
    public List<Document> parseTablesFromDocx(String filePath) throws IOException {
        log.info("开始解析DOCX文档中的表格: {}", filePath);
        long startTime = System.currentTimeMillis();

        try (InputStream fis = new FileInputStream(filePath);
             XWPFDocument document = new XWPFDocument(fis)) {

            List<XWPFTable> tables = document.getTables();
            if (tables.isEmpty()) {
                log.info("文档中没有表格");
                return Collections.emptyList();
            }

            List<Document> tableDocuments = new ArrayList<>();
            String format = parserProperties.getTable().getFormat();

            for (int i = 0; i < tables.size(); i++) {
                XWPFTable table = tables.get(i);
                String tableContent = convertTableToFormat(table, format);

                // 构建元数据
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("filePath", filePath);
                metadata.put("fileName", extractFileName(filePath));
                metadata.put("tableIndex", i);
                metadata.put("rowCount", table.getNumberOfRows());
                metadata.put("format", format);

                // 创建Document对象
                Document doc = Document.builder()
                        .id(IdGenerator.generateDocumentId("docx_table"))
                        .content(tableContent)
                        .source("docx")
                        .type("table")
                        .metadata(metadata)
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build();

                tableDocuments.add(doc);
            }

            log.info("DOCX表格解析完成: {} (耗时: {}ms, 表格数: {})",
                    filePath, System.currentTimeMillis() - startTime, tables.size());

            return tableDocuments;
        }
    }

    /**
     * 将表格转换为指定格式
     */
    private String convertTableToFormat(XWPFTable table, String format) {
        if ("markdown".equalsIgnoreCase(format)) {
            return convertTableToMarkdown(table);
        } else if ("json".equalsIgnoreCase(format)) {
            return convertTableToJson(table);
        } else {
            log.warn("未知的表格格式: {}, 使用默认Markdown格式", format);
            return convertTableToMarkdown(table);
        }
    }

    /**
     * 将表格转换为Markdown格式
     */
    private String convertTableToMarkdown(XWPFTable table) {
        List<XWPFTableRow> rows = table.getRows();
        if (rows.isEmpty()) {
            return "";
        }

        StringBuilder markdown = new StringBuilder();

        // 第一行作为表头
        XWPFTableRow headerRow = rows.get(0);
        List<String> headers = extractRowCells(headerRow);

        // 表头行
        markdown.append("| ").append(String.join(" | ", headers)).append(" |\n");

        // 分隔行
        markdown.append("|");
        for (int i = 0; i < headers.size(); i++) {
            markdown.append(" --- |");
        }
        markdown.append("\n");

        // 数据行
        for (int i = 1; i < rows.size(); i++) {
            XWPFTableRow row = rows.get(i);
            List<String> cells = extractRowCells(row);

            // 补齐或截断到表头列数
            while (cells.size() < headers.size()) {
                cells.add("");
            }
            if (cells.size() > headers.size()) {
                cells = cells.subList(0, headers.size());
            }

            markdown.append("| ").append(String.join(" | ", cells)).append(" |\n");
        }

        return markdown.toString();
    }

    /**
     * 将表格转换为JSON格式
     */
    private String convertTableToJson(XWPFTable table) {
        List<XWPFTableRow> rows = table.getRows();
        if (rows.isEmpty()) {
            return "[]";
        }

        // 第一行作为键
        XWPFTableRow headerRow = rows.get(0);
        List<String> headers = extractRowCells(headerRow);

        // 构建JSON数组
        StringBuilder json = new StringBuilder("[");

        for (int i = 1; i < rows.size(); i++) {
            XWPFTableRow row = rows.get(i);
            List<String> cells = extractRowCells(row);

            json.append("{");
            for (int j = 0; j < headers.size(); j++) {
                String key = headers.get(j).replace("\"", "\\\"");
                String value = j < cells.size() ? cells.get(j).replace("\"", "\\\"") : "";

                json.append("\"").append(key).append("\":\"").append(value).append("\"");
                if (j < headers.size() - 1) {
                    json.append(",");
                }
            }
            json.append("}");

            if (i < rows.size() - 1) {
                json.append(",");
            }
        }

        json.append("]");
        return json.toString();
    }

    /**
     * 提取行中的所有单元格文本
     */
    private List<String> extractRowCells(XWPFTableRow row) {
        return row.getTableCells()
                .stream()
                .map(XWPFTableCell::getText)
                .map(text -> text == null ? "" : text.trim())
                .collect(Collectors.toList());
    }

    /**
     * 从文件路径中提取文件名
     */
    private String extractFileName(String filePath) {
        int lastSlash = Math.max(filePath.lastIndexOf('/'), filePath.lastIndexOf('\\'));
        return lastSlash >= 0 ? filePath.substring(lastSlash + 1) : filePath;
    }

    /**
     * 检查文档是否包含表格
     */
    public boolean hasTable(String filePath) throws IOException {
        try (InputStream fis = new FileInputStream(filePath);
             XWPFDocument document = new XWPFDocument(fis)) {
            return !document.getTables().isEmpty();
        }
    }
}
