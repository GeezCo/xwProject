package com.intel.rag.service;

import com.intel.rag.config.OcrProperties;
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

/**
 * DOCX图文混排解析服务
 * 提取文本、表格和图片，并进行OCR识别
 */
@Slf4j
@Service
public class DocxMixedParserService {

    private final OcrService ocrService;
    private final OcrProperties ocrProperties;

    public DocxMixedParserService(OcrService ocrService, OcrProperties ocrProperties) {
        this.ocrService = ocrService;
        this.ocrProperties = ocrProperties;
    }

    /**
     * 解析包含图片的DOCX文档
     *
     * @param filePath 文件路径
     * @return Document对象
     * @throws IOException 文件读取异常
     */
    public Document parseMixedDocx(String filePath) throws IOException {
        log.info("开始解析图文混排DOCX文档: {}", filePath);
        long startTime = System.currentTimeMillis();

        try (InputStream fis = new FileInputStream(filePath);
             XWPFDocument document = new XWPFDocument(fis)) {

            StringBuilder contentBuilder = new StringBuilder();
            int imageCount = 0;
            int ocrTextLength = 0;

            // 遍历所有段落和图片
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                // 提取段落文本
                String paraText = paragraph.getText();
                if (paraText != null && !paraText.trim().isEmpty()) {
                    contentBuilder.append(paraText).append("\n");
                }

                // 提取段落中的图片
                List<XWPFRun> runs = paragraph.getRuns();
                for (XWPFRun run : runs) {
                    List<XWPFPicture> pictures = run.getEmbeddedPictures();
                    for (XWPFPicture picture : pictures) {
                        imageCount++;

                        if (ocrProperties.getImage().isEnableOcr()) {
                            try {
                                byte[] imageData = picture.getPictureData().getData();

                                // 检查图片大小
                                double sizeMb = imageData.length / (1024.0 * 1024.0);
                                if (sizeMb > ocrProperties.getImage().getMaxSizeMb()) {
                                    log.warn("图片大小超过限制: {}MB > {}MB，跳过OCR",
                                            String.format("%.2f", sizeMb),
                                            ocrProperties.getImage().getMaxSizeMb());
                                    continue;
                                }

                                // OCR识别
                                String ocrText = ocrService.recognizeText(imageData);
                                if (!ocrText.isEmpty()) {
                                    contentBuilder.append("\n[图片内容]\n")
                                            .append(ocrText)
                                            .append("\n[/图片内容]\n\n");
                                    ocrTextLength += ocrText.length();
                                    log.debug("图片OCR识别成功: 字符数={}", ocrText.length());
                                }
                            } catch (Exception e) {
                                log.error("图片OCR识别失败", e);
                            }
                        }
                    }
                }
            }

            String content = contentBuilder.toString();

            // 构建元数据
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("filePath", filePath);
            metadata.put("fileName", extractFileName(filePath));
            metadata.put("paragraphCount", document.getParagraphs().size());
            metadata.put("imageCount", imageCount);
            metadata.put("ocrEnabled", ocrProperties.getImage().isEnableOcr());
            metadata.put("ocrTextLength", ocrTextLength);
            metadata.put("contentLength", content.length());
            metadata.put("parseTime", System.currentTimeMillis() - startTime);

            // 创建Document对象
            Document doc = Document.builder()
                    .id(IdGenerator.generateDocumentId("docx_mixed"))
                    .content(content)
                    .source("docx")
                    .type("mixed")
                    .metadata(metadata)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            log.info("DOCX图文混排文档解析完成: {} (耗时: {}ms, 图片数: {}, OCR文字: {}字符)",
                    filePath, metadata.get("parseTime"), imageCount, ocrTextLength);

            return doc;
        }
    }

    /**
     * 提取文档中的所有图片
     *
     * @param filePath 文件路径
     * @return 图片字节数组列表
     * @throws IOException 文件读取异常
     */
    public List<byte[]> extractImages(String filePath) throws IOException {
        log.info("开始提取DOCX文档中的图片: {}", filePath);

        List<byte[]> images = new ArrayList<>();

        try (InputStream fis = new FileInputStream(filePath);
             XWPFDocument document = new XWPFDocument(fis)) {

            for (XWPFParagraph paragraph : document.getParagraphs()) {
                for (XWPFRun run : paragraph.getRuns()) {
                    List<XWPFPicture> pictures = run.getEmbeddedPictures();
                    for (XWPFPicture picture : pictures) {
                        byte[] imageData = picture.getPictureData().getData();
                        images.add(imageData);
                    }
                }
            }

            log.info("图片提取完成: 图片数量={}", images.size());
            return images;
        }
    }

    /**
     * 检查文档是否包含图片
     */
    public boolean hasImages(String filePath) throws IOException {
        try (InputStream fis = new FileInputStream(filePath);
             XWPFDocument document = new XWPFDocument(fis)) {

            for (XWPFParagraph paragraph : document.getParagraphs()) {
                for (XWPFRun run : paragraph.getRuns()) {
                    if (!run.getEmbeddedPictures().isEmpty()) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    /**
     * 从文件路径中提取文件名
     */
    private String extractFileName(String filePath) {
        int lastSlash = Math.max(filePath.lastIndexOf('/'), filePath.lastIndexOf('\\'));
        return lastSlash >= 0 ? filePath.substring(lastSlash + 1) : filePath;
    }
}
