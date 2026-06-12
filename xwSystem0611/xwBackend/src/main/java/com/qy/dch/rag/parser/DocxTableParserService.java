package com.qy.dch.rag.parser;

import com.qy.dch.rag.config.DocumentParserProperties;
import com.qy.dch.rag.model.ParsedDocument;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
public class DocxTableParserService {

    private final DocumentParserProperties parserProperties;

    public DocxTableParserService(DocumentParserProperties parserProperties) {
        this.parserProperties = parserProperties;
    }

    public ParsedDocument parseTableDocx(String filePath) throws IOException {
        long startTime = System.currentTimeMillis();
        try (InputStream fis = new FileInputStream(filePath);
             XWPFDocument document = new XWPFDocument(fis)) {

            StringBuilder tableContent = new StringBuilder();
            int tableCount = 0;

            for (XWPFTable table : document.getTables()) {
                if (tableCount > 0) {
                    tableContent.append("\n\n");
                }
                tableContent.append(extractTable(table));
                tableCount++;
            }

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("filePath", filePath);
            metadata.put("fileName", extractFileName(filePath));
            metadata.put("tableCount", tableCount);
            metadata.put("contentLength", tableContent.length());
            metadata.put("parseTime", System.currentTimeMillis() - startTime);

            return ParsedDocument.builder()
                    .id("docx_table_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16))
                    .content(tableContent.toString())
                    .source("docx")
                    .type("table")
                    .metadata(metadata)
                    .build();
        }
    }

    private String extractTable(XWPFTable table) {
        return table.getRows().stream()
                .map(this::extractRow)
                .filter(row -> !row.trim().isEmpty())
                .collect(Collectors.joining("\n"));
    }

    private String extractRow(XWPFTableRow row) {
        return row.getTableCells().stream()
                .map(XWPFTableCell::getText)
                .map(text -> text == null ? "" : text.trim())
                .collect(Collectors.joining(" | "));
    }

    private String extractFileName(String filePath) {
        int lastSlash = Math.max(filePath.lastIndexOf('/'), filePath.lastIndexOf('\\'));
        return lastSlash >= 0 ? filePath.substring(lastSlash + 1) : filePath;
    }
}
