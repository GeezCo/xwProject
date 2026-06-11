package com.intel.rag.service;

import com.intel.rag.model.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DOCX解析服务测试
 */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration"
})
class DocxParserServiceTest {

    @Autowired
    private DocxParserService docxParserService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        // 测试前准备
    }

    @Test
    void testIsValidDocx() {
        assertTrue(docxParserService.isValidDocx("test.docx"));
        assertTrue(docxParserService.isValidDocx("TEST.DOCX"));
        assertTrue(docxParserService.isValidDocx("/path/to/file.docx"));

        assertFalse(docxParserService.isValidDocx("test.doc"));
        assertFalse(docxParserService.isValidDocx("test.pdf"));
        assertFalse(docxParserService.isValidDocx(null));
    }

    @Test
    void testParseSimpleDocx_FileNotFound() {
        String nonExistentFile = tempDir.resolve("non_existent.docx").toString();

        assertThrows(IOException.class, () -> {
            docxParserService.parseSimpleDocx(nonExistentFile);
        });
    }

    @Test
    void testParseSimpleDocx_InvalidFile() throws IOException {
        // 创建一个非DOCX的文件
        Path invalidFile = tempDir.resolve("invalid.docx");
        Files.writeString(invalidFile, "This is not a valid DOCX file");

        assertThrows(Exception.class, () -> {
            docxParserService.parseSimpleDocx(invalidFile.toString());
        });
    }

    // 注意：实际的DOCX解析测试需要真实的测试文件
    // 用户需要在 src/main/resources/testdata/docx/simple/ 目录下放置测试文件
    // 然后可以编写类似下面的测试：

    /*
    @Test
    void testParseSimpleDocx_Success() throws IOException {
        String testFile = "src/main/resources/testdata/docx/simple/sample.docx";

        Document document = docxParserService.parseSimpleDocx(testFile);

        assertNotNull(document);
        assertNotNull(document.getId());
        assertNotNull(document.getContent());
        assertEquals("docx", document.getSource());
        assertEquals("text", document.getType());
        assertNotNull(document.getMetadata());
        assertTrue(document.getMetadata().containsKey("fileName"));
        assertTrue(document.getMetadata().containsKey("paragraphCount"));
    }
    */
}
