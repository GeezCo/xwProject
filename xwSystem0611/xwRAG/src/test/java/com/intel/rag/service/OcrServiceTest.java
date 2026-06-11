package com.intel.rag.service;

import com.intel.rag.config.OcrProperties;
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
 * OCR服务测试
 */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration",
        "ocr.tesseract.datapath=/usr/share/tessdata"
})
class OcrServiceTest {

    @Autowired
    private OcrService ocrService;

    @Autowired
    private OcrProperties ocrProperties;

    @Test
    void testOcrServiceInitialization() {
        assertNotNull(ocrService);
        assertNotNull(ocrProperties);
    }

    @Test
    void testRecognizeText_EmptyImage() {
        String result = ocrService.recognizeText(new byte[0]);
        assertEquals("", result);
    }

    @Test
    void testRecognizeText_NullImage() {
        String result = ocrService.recognizeText((byte[]) null);
        assertEquals("", result);
    }

    @Test
    void testRecognizeText_NullBufferedImage() {
        String result = ocrService.recognizeText((BufferedImage) null);
        assertEquals("", result);
    }

    @Test
    void testRecognizeTextBatch_EmptyList() {
        List<String> results = ocrService.recognizeTextBatch(null);
        assertTrue(results.isEmpty());

        results = ocrService.recognizeTextBatch(List.of());
        assertTrue(results.isEmpty());
    }

    // 注意：实际的OCR功能测试需要Tesseract安装和配置
    // 以下测试在Tesseract未安装时会跳过或返回空字符串

    /*
    @Test
    void testRecognizeText_SimpleImage() throws IOException {
        // 创建一个包含文字的简单图片
        BufferedImage image = new BufferedImage(200, 50, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 200, 50);
        g.setColor(Color.BLACK);
        g.setFont(new Font("Arial", Font.PLAIN, 20));
        g.drawString("Hello World", 10, 30);
        g.dispose();

        // 转换为字节数组
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);
        byte[] imageBytes = baos.toByteArray();

        // OCR识别
        String result = ocrService.recognizeText(imageBytes);

        // 如果Tesseract可用，应该能识别出文字
        if (ocrService.isAvailable()) {
            assertNotNull(result);
            assertTrue(result.toLowerCase().contains("hello") || result.toLowerCase().contains("world"));
        }
    }
    */
}
