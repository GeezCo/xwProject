package com.qy.dch.rag.parser;

import com.qy.dch.rag.config.OcrProperties;
import com.qy.dch.rag.model.ParsedDocument;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * DOCX 图文混排解析器
 * 支持从 DOCX 中提取文本段落和嵌入图片（通过 OCR）
 */
@Slf4j
@Service
public class DocxMixedParserService {

    private static final String DOCX_MIME_TYPE = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    private final OcrService ocrService;
    private final OcrProperties ocrProperties;

    public DocxMixedParserService(
            OcrService ocrService,
            OcrProperties ocrProperties) {
        this.ocrService = ocrService;
        this.ocrProperties = ocrProperties;
    }

    /**
     * 解析图文混排 DOCX 文件
     *
     * @param filePath DOCX 文件路径
     * @return 解析结果
     * @throws IOException 解析失败
     */
    public ParsedDocument parseMixedDocx(String filePath) throws IOException {
        long startTime = System.currentTimeMillis();
        Path path = Paths.get(filePath);
        int imageCount = 0;
        int ocrTextLength = 0;

        try (InputStream fis = new FileInputStream(path.toFile());
             XWPFDocument document = new XWPFDocument(fis)) {

            StringBuilder content = new StringBuilder();
            boolean ocrEnabled = ocrProperties.getImage().isEnableOcr();

            // 遍历所有段落和图片
            for (IBodyElement element : document.getBodyElements()) {
                if (element instanceof XWPFParagraph) {
                    XWPFParagraph paragraph = (XWPFParagraph) element;

                    // 提取段落文本
                    String text = paragraph.getText();
                    if (text != null && !text.trim().isEmpty()) {
                        content.append(text).append("\n");
                    }

                    // 检查段落中的嵌入图片
                    List<XWPFRun> runs = paragraph.getRuns();
                    if (runs != null && ocrEnabled) {
                        for (XWPFRun run : runs) {
                            List<XWPFPicture> pictures = run.getEmbeddedPictures();
                            if (pictures != null && !pictures.isEmpty()) {
                                for (XWPFPicture picture : pictures) {
                                    imageCount++;
                                    String ocrText = extractImageText(picture);
                                    if (ocrText != null && !ocrText.isEmpty()) {
                                        content.append("\n[图片内容]\n").append(ocrText).append("\n[/图片内容]\n\n");
                                        ocrTextLength += ocrText.length();
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 构建元数据
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("filePath", path.toString());
            metadata.put("fileName", path.getFileName().toString());
            metadata.put("paragraphCount", document.getParagraphs().size());
            metadata.put("imageCount", imageCount);
            metadata.put("ocrTextLength", ocrTextLength);
            metadata.put("ocrEnabled", ocrEnabled);
            metadata.put("contentLength", content.length());
            metadata.put("parseTime", System.currentTimeMillis() - startTime);

            log.info("DOCX解析完成: file={}, paragraphs={}, images={}, ocrTextLength={}, time={}ms",
                    path.getFileName(), document.getParagraphs().size(),
                    imageCount, ocrTextLength, System.currentTimeMillis() - startTime);

            return ParsedDocument.builder()
                    .id("docx_mixed_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16))
                    .content(content.toString())
                    .source("docx")
                    .type("mixed")
                    .metadata(metadata)
                    .build();
        }
    }

    /**
     * 提取 DOCX 中所有嵌入图片的字节数据
     *
     * @param filePath DOCX 文件路径
     * @return 图片字节列表
     * @throws IOException 读取失败
     */
    public List<byte[]> extractImages(String filePath) throws IOException {
        Path path = Paths.get(filePath);
        List<byte[]> images = new ArrayList<>();

        try (InputStream fis = new FileInputStream(path.toFile());
             XWPFDocument document = new XWPFDocument(fis)) {

            for (IBodyElement element : document.getBodyElements()) {
                if (element instanceof XWPFParagraph) {
                    XWPFParagraph paragraph = (XWPFParagraph) element;
                    List<XWPFRun> runs = paragraph.getRuns();
                    if (runs != null) {
                        for (XWPFRun run : runs) {
                            List<XWPFPicture> pictures = run.getEmbeddedPictures();
                            if (pictures != null) {
                                for (XWPFPicture picture : pictures) {
                                    images.add(picture.getPictureData().getData());
                                }
                            }
                        }
                    }
                }
            }
        }

        return images;
    }

    /**
     * 从图片中提取文本（OCR）
     *
     * @param picture XWPF图片对象
     * @return OCR识别的文本，失败返回空字符串
     */
    private String extractImageText(XWPFPicture picture) {
        try {
            XWPFPictureData pictureData = picture.getPictureData();
            byte[] imageBytes = pictureData.getData();

            // 检查图片大小限制
            int maxSizeMb = ocrProperties.getImage().getMaxSizeMb();
            if (imageBytes.length > maxSizeMb * 1024 * 1024) {
                log.warn("图片过大，跳过OCR: size={}MB, max={}MB",
                        imageBytes.length / 1024 / 1024, maxSizeMb);
                return "";
            }

            // 调用 OCR 服务
            String text = ocrService.recognizeText(imageBytes);
            log.debug("OCR识别成功: textLength={}", text != null ? text.length() : 0);
            return text != null ? text : "";

        } catch (Exception e) {
            // OCR 失败时降级：记录日志但不中断解析流程
            log.warn("图片OCR识别失败，跳过该图片: {}", e.getMessage());
            return "";
        }
    }

    /**
     * 检查是否支持该 MIME 类型
     *
     * @param mimeType MIME类型
     * @return 是否支持
     */
    public boolean supports(String mimeType) {
        return DOCX_MIME_TYPE.equals(mimeType);
    }
}
