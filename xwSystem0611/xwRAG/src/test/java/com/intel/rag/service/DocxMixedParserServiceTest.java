package com.intel.rag.service;

import com.intel.rag.model.Document;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DOCX图文混排解析服务测试
 */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration",
        "ocr.image.enable-ocr=false"
})
class DocxMixedParserServiceTest {

    @Autowired
    private DocxMixedParserService mixedParserService;

    @TempDir
    Path tempDir;

    @Test
    void testParseMixedDocx_NoImages() throws IOException {
        // 创建一个没有图片的DOCX文档
        Path docxFile = tempDir.resolve("no_images.docx");
        try (XWPFDocument doc = new XWPFDocument();
             FileOutputStream out = new FileOutputStream(docxFile.toFile())) {
            doc.createParagraph().createRun().setText("这是一个没有图片的文档");
            doc.write(out);
        }

        Document document = mixedParserService.parseMixedDocx(docxFile.toString());

        assertNotNull(document);
        assertEquals("docx", document.getSource());
        assertEquals("mixed", document.getType());
        assertEquals(0, document.getMetadata().get("imageCount"));
        assertFalse(mixedParserService.hasImages(docxFile.toString()));
    }

    @Test
    void testParseMixedDocx_WithImages() throws Exception {
        // 创建一个包含图片的DOCX文档
        Path docxFile = tempDir.resolve("with_images.docx");

        // 创建一个简单的图片
        BufferedImage image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 100, 100);
        g.setColor(Color.BLACK);
        g.drawString("Test", 40, 50);
        g.dispose();

        // 转换为字节数组
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);
        byte[] imageBytes = baos.toByteArray();

        // 创建DOCX并插入图片
        try (XWPFDocument doc = new XWPFDocument();
             FileOutputStream out = new FileOutputStream(docxFile.toFile())) {

            XWPFParagraph paragraph = doc.createParagraph();
            paragraph.createRun().setText("图片前的文字");

            XWPFRun run = paragraph.createRun();
            run.addPicture(new ByteArrayInputStream(imageBytes),
                    XWPFDocument.PICTURE_TYPE_PNG,
                    "test.png",
                    Units.toEMU(100),
                    Units.toEMU(100));

            doc.createParagraph().createRun().setText("图片后的文字");
            doc.write(out);
        }

        Document document = mixedParserService.parseMixedDocx(docxFile.toString());

        assertNotNull(document);
        assertEquals("docx", document.getSource());
        assertEquals("mixed", document.getType());
        assertEquals(1, document.getMetadata().get("imageCount"));
        assertTrue(document.getContent().contains("图片前的文字"));
        assertTrue(document.getContent().contains("图片后的文字"));
        assertTrue(mixedParserService.hasImages(docxFile.toString()));
    }

    @Test
    void testExtractImages() throws Exception {
        // 创建包含多个图片的DOCX
        Path docxFile = tempDir.resolve("multiple_images.docx");

        // 创建两个不同的图片
        BufferedImage image1 = createTestImage(Color.RED, "Image1");
        BufferedImage image2 = createTestImage(Color.BLUE, "Image2");

        ByteArrayOutputStream baos1 = new ByteArrayOutputStream();
        ImageIO.write(image1, "png", baos1);
        byte[] imageBytes1 = baos1.toByteArray();

        ByteArrayOutputStream baos2 = new ByteArrayOutputStream();
        ImageIO.write(image2, "png", baos2);
        byte[] imageBytes2 = baos2.toByteArray();

        // 创建DOCX并插入两个图片
        try (XWPFDocument doc = new XWPFDocument();
             FileOutputStream out = new FileOutputStream(docxFile.toFile())) {

            XWPFParagraph p1 = doc.createParagraph();
            XWPFRun run1 = p1.createRun();
            run1.addPicture(new ByteArrayInputStream(imageBytes1),
                    XWPFDocument.PICTURE_TYPE_PNG,
                    "image1.png",
                    Units.toEMU(100),
                    Units.toEMU(100));

            XWPFParagraph p2 = doc.createParagraph();
            XWPFRun run2 = p2.createRun();
            run2.addPicture(new ByteArrayInputStream(imageBytes2),
                    XWPFDocument.PICTURE_TYPE_PNG,
                    "image2.png",
                    Units.toEMU(100),
                    Units.toEMU(100));

            doc.write(out);
        }

        List<byte[]> images = mixedParserService.extractImages(docxFile.toString());

        assertEquals(2, images.size());
        assertTrue(images.get(0).length > 0);
        assertTrue(images.get(1).length > 0);
    }

    @Test
    void testExtractImages_NoImages() throws IOException {
        Path docxFile = tempDir.resolve("no_images_extract.docx");
        try (XWPFDocument doc = new XWPFDocument();
             FileOutputStream out = new FileOutputStream(docxFile.toFile())) {
            doc.createParagraph().createRun().setText("纯文本文档");
            doc.write(out);
        }

        List<byte[]> images = mixedParserService.extractImages(docxFile.toString());

        assertTrue(images.isEmpty());
    }

    /**
     * 创建测试图片
     */
    private BufferedImage createTestImage(Color bgColor, String text) {
        BufferedImage image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(bgColor);
        g.fillRect(0, 0, 100, 100);
        g.setColor(Color.WHITE);
        g.drawString(text, 30, 50);
        g.dispose();
        return image;
    }
}
