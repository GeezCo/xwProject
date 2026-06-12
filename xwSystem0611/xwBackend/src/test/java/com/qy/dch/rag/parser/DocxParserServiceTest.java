package com.qy.dch.rag.parser;

import com.qy.dch.rag.config.DocumentParserProperties;
import com.qy.dch.rag.model.ParsedDocument;
import org.junit.jupiter.api.Test;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

class DocxParserServiceTest {

    @Test
    void parseSimpleDocx_extractsAllParagraphs() throws Exception {
        File tmp = File.createTempFile("test-", ".docx");
        try (XWPFDocument doc = new XWPFDocument()) {
            XWPFParagraph p1 = doc.createParagraph();
            p1.createRun().setText("第一段内容");
            XWPFParagraph p2 = doc.createParagraph();
            p2.createRun().setText("第二段内容");
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                doc.write(out);
            }
        }

        DocxParserService service = new DocxParserService(new DocumentParserProperties());
        ParsedDocument result = service.parseSimpleDocx(tmp.getAbsolutePath());

        assertTrue(result.getContent().contains("第一段内容"));
        assertTrue(result.getContent().contains("第二段内容"));
        assertEquals("docx", result.getSource());
        assertEquals("text", result.getType());
        Files.deleteIfExists(tmp.toPath());
    }
}
