package com.qy.dch.rag.parser;

import com.qy.dch.rag.config.DocumentParserProperties;
import com.qy.dch.rag.model.ParsedDocument;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
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
public class DocxParserService {

    private final DocumentParserProperties parserProperties;

    public DocxParserService(DocumentParserProperties parserProperties) {
        this.parserProperties = parserProperties;
    }

    public ParsedDocument parseSimpleDocx(String filePath) throws IOException {
        long startTime = System.currentTimeMillis();
        try (InputStream fis = new FileInputStream(filePath);
             XWPFDocument document = new XWPFDocument(fis)) {

            String content = document.getParagraphs().stream()
                    .map(XWPFParagraph::getText)
                    .filter(t -> t != null && !t.trim().isEmpty())
                    .collect(Collectors.joining("\n"));

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("filePath", filePath);
            metadata.put("fileName", extractFileName(filePath));
            metadata.put("paragraphCount", document.getParagraphs().size());
            metadata.put("contentLength", content.length());
            metadata.put("parseTime", System.currentTimeMillis() - startTime);

            return ParsedDocument.builder()
                    .id("docx_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16))
                    .content(content)
                    .source("docx")
                    .type("text")
                    .metadata(metadata)
                    .build();
        }
    }

    public boolean isValidDocx(String filePath) {
        return filePath != null && filePath.toLowerCase().endsWith(".docx");
    }

    private String extractFileName(String filePath) {
        int lastSlash = Math.max(filePath.lastIndexOf('/'), filePath.lastIndexOf('\\'));
        return lastSlash >= 0 ? filePath.substring(lastSlash + 1) : filePath;
    }
}
