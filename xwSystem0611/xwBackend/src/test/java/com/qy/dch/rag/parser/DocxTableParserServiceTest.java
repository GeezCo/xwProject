package com.qy.dch.rag.parser;

import com.qy.dch.rag.config.DocumentParserProperties;
import com.qy.dch.rag.model.ParsedDocument;
import org.junit.jupiter.api.Test;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

class DocxTableParserServiceTest {

    @Test
    void parseTableDocx_extracts2x2Table() throws Exception {
        File tmp = File.createTempFile("test-table-", ".docx");
        try (XWPFDocument doc = new XWPFDocument()) {
            XWPFTable table = doc.createTable(2, 2);

            // Header row
            XWPFTableRow row0 = table.getRow(0);
            row0.getCell(0).setText("姓名");
            row0.getCell(1).setText("年龄");

            // Data row
            XWPFTableRow row1 = table.getRow(1);
            row1.getCell(0).setText("张三");
            row1.getCell(1).setText("30");

            try (FileOutputStream out = new FileOutputStream(tmp)) {
                doc.write(out);
            }
        }

        DocxTableParserService service = new DocxTableParserService(new DocumentParserProperties());
        ParsedDocument result = service.parseTableDocx(tmp.getAbsolutePath());

        assertTrue(result.getContent().contains("姓名 | 年龄"));
        assertTrue(result.getContent().contains("张三 | 30"));
        assertEquals("docx", result.getSource());
        assertEquals("table", result.getType());
        Files.deleteIfExists(tmp.toPath());
    }
}
