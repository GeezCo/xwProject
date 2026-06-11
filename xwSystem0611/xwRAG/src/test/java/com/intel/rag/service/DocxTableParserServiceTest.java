package com.intel.rag.service;

import com.intel.rag.model.Document;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DOCX表格解析服务测试
 */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration"
})
class DocxTableParserServiceTest {

    @Autowired
    private DocxTableParserService tableParserService;

    @TempDir
    Path tempDir;

    @Test
    void testParseTablesFromDocx_NoTables() throws IOException {
        // 创建一个没有表格的DOCX文档
        Path docxFile = tempDir.resolve("no_table.docx");
        try (XWPFDocument doc = new XWPFDocument();
             FileOutputStream out = new FileOutputStream(docxFile.toFile())) {
            doc.createParagraph().createRun().setText("这是一个没有表格的文档");
            doc.write(out);
        }

        List<Document> tables = tableParserService.parseTablesFromDocx(docxFile.toString());

        assertTrue(tables.isEmpty());
        assertFalse(tableParserService.hasTable(docxFile.toString()));
    }

    @Test
    void testParseTablesFromDocx_WithSimpleTable() throws IOException {
        // 创建一个包含简单表格的DOCX文档
        Path docxFile = tempDir.resolve("simple_table.docx");
        try (XWPFDocument doc = new XWPFDocument();
             FileOutputStream out = new FileOutputStream(docxFile.toFile())) {

            // 创建3x3表格
            XWPFTable table = doc.createTable(3, 3);

            // 填充表头
            XWPFTableRow headerRow = table.getRow(0);
            headerRow.getCell(0).setText("姓名");
            headerRow.getCell(1).setText("年龄");
            headerRow.getCell(2).setText("城市");

            // 填充数据行
            XWPFTableRow row1 = table.getRow(1);
            row1.getCell(0).setText("张三");
            row1.getCell(1).setText("25");
            row1.getCell(2).setText("北京");

            XWPFTableRow row2 = table.getRow(2);
            row2.getCell(0).setText("李四");
            row2.getCell(1).setText("30");
            row2.getCell(2).setText("上海");

            doc.write(out);
        }

        List<Document> tables = tableParserService.parseTablesFromDocx(docxFile.toString());

        assertEquals(1, tables.size());
        assertTrue(tableParserService.hasTable(docxFile.toString()));

        Document tableDoc = tables.get(0);
        assertNotNull(tableDoc.getId());
        assertEquals("docx", tableDoc.getSource());
        assertEquals("table", tableDoc.getType());
        assertNotNull(tableDoc.getContent());

        // 验证内容包含表格数据
        String content = tableDoc.getContent();
        assertTrue(content.contains("姓名"));
        assertTrue(content.contains("张三"));
        assertTrue(content.contains("李四"));

        // 验证元数据
        assertEquals(0, tableDoc.getMetadata().get("tableIndex"));
        assertEquals(3, tableDoc.getMetadata().get("rowCount"));
    }

    @Test
    void testParseTablesFromDocx_MultipleTablesMarkdown() throws IOException {
        // 创建包含多个表格的DOCX文档
        Path docxFile = tempDir.resolve("multiple_tables.docx");
        try (XWPFDocument doc = new XWPFDocument();
             FileOutputStream out = new FileOutputStream(docxFile.toFile())) {

            // 第一个表格
            XWPFTable table1 = doc.createTable(2, 2);
            table1.getRow(0).getCell(0).setText("A");
            table1.getRow(0).getCell(1).setText("B");
            table1.getRow(1).getCell(0).setText("1");
            table1.getRow(1).getCell(1).setText("2");

            doc.createParagraph().createRun().setText("段落分隔");

            // 第二个表格
            XWPFTable table2 = doc.createTable(2, 2);
            table2.getRow(0).getCell(0).setText("C");
            table2.getRow(0).getCell(1).setText("D");
            table2.getRow(1).getCell(0).setText("3");
            table2.getRow(1).getCell(1).setText("4");

            doc.write(out);
        }

        List<Document> tables = tableParserService.parseTablesFromDocx(docxFile.toString());

        assertEquals(2, tables.size());

        // 验证第一个表格
        Document table1Doc = tables.get(0);
        assertEquals(0, table1Doc.getMetadata().get("tableIndex"));
        assertTrue(table1Doc.getContent().contains("A"));
        assertTrue(table1Doc.getContent().contains("B"));

        // 验证第二个表格
        Document table2Doc = tables.get(1);
        assertEquals(1, table2Doc.getMetadata().get("tableIndex"));
        assertTrue(table2Doc.getContent().contains("C"));
        assertTrue(table2Doc.getContent().contains("D"));
    }

    @Test
    void testMarkdownFormat() throws IOException {
        // 创建表格并验证Markdown格式
        Path docxFile = tempDir.resolve("markdown_table.docx");
        try (XWPFDocument doc = new XWPFDocument();
             FileOutputStream out = new FileOutputStream(docxFile.toFile())) {

            XWPFTable table = doc.createTable(2, 2);
            table.getRow(0).getCell(0).setText("列1");
            table.getRow(0).getCell(1).setText("列2");
            table.getRow(1).getCell(0).setText("值1");
            table.getRow(1).getCell(1).setText("值2");

            doc.write(out);
        }

        List<Document> tables = tableParserService.parseTablesFromDocx(docxFile.toString());

        assertEquals(1, tables.size());
        String content = tables.get(0).getContent();

        // 验证Markdown格式
        assertTrue(content.contains("|"));
        assertTrue(content.contains("---"));
        assertTrue(content.contains("列1"));
        assertTrue(content.contains("列2"));
        assertTrue(content.contains("值1"));
        assertTrue(content.contains("值2"));
    }
}
