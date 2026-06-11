package com.intel.rag.service;

import com.intel.rag.config.OcrProperties;
import lombok.extern.slf4j.Slf4j;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * OCR文字识别服务
 * 基于Tesseract实现，支持异步处理
 */
@Slf4j
@Service
public class OcrService {

    private final OcrProperties ocrProperties;
    private final Tesseract tesseract;
    private final Executor ocrExecutor;

    public OcrService(OcrProperties ocrProperties, Executor ocrExecutor) {
        this.ocrProperties = ocrProperties;
        this.ocrExecutor = ocrExecutor;
        this.tesseract = initTesseract();
    }

    /**
     * 初始化Tesseract实例
     */
    private Tesseract initTesseract() {
        Tesseract tess = new Tesseract();

        String datapath = ocrProperties.getTesseract().getDatapath();
        String language = ocrProperties.getTesseract().getLanguage();
        int pageSegMode = ocrProperties.getTesseract().getPageSegMode();
        int ocrEngineMode = ocrProperties.getTesseract().getOcrEngineMode();

        tess.setDatapath(datapath);
        tess.setLanguage(language);
        tess.setPageSegMode(pageSegMode);
        tess.setOcrEngineMode(ocrEngineMode);

        log.info("Tesseract初始化完成: datapath={}, language={}, PSM={}, OEM={}",
                datapath, language, pageSegMode, ocrEngineMode);

        return tess;
    }

    /**
     * 识别图片中的文字（同步）
     *
     * @param imageBytes 图片字节数组
     * @return 识别出的文字
     */
    public String recognizeText(byte[] imageBytes) {
        if (imageBytes == null || imageBytes.length == 0) {
            log.warn("图片数据为空，无法进行OCR识别");
            return "";
        }

        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (image == null) {
                log.error("无法解析图片数据");
                return "";
            }

            return doOcr(image);

        } catch (IOException e) {
            log.error("图片读取失败", e);
            return "";
        }
    }

    /**
     * 识别图片中的文字（异步）
     *
     * @param imageBytes 图片字节数组
     * @return 异步识别结果
     */
    @Async("ocrExecutor")
    public CompletableFuture<String> recognizeTextAsync(byte[] imageBytes) {
        String result = recognizeText(imageBytes);
        return CompletableFuture.completedFuture(result);
    }

    /**
     * 识别BufferedImage中的文字
     *
     * @param image BufferedImage对象
     * @return 识别出的文字
     */
    public String recognizeText(BufferedImage image) {
        if (image == null) {
            log.warn("图片对象为空，无法进行OCR识别");
            return "";
        }

        return doOcr(image);
    }

    /**
     * 批量识别图片中的文字（异步并行）
     *
     * @param imageBytesList 图片字节数组列表
     * @return 识别出的文字列表
     */
    public CompletableFuture<List<String>> recognizeTextBatchAsync(List<byte[]> imageBytesList) {
        if (imageBytesList == null || imageBytesList.isEmpty()) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }

        log.info("开始批量OCR识别: 图片数量={}", imageBytesList.size());
        long startTime = System.currentTimeMillis();

        // 并行执行OCR识别
        List<CompletableFuture<String>> futures = new ArrayList<>();
        for (byte[] imageBytes : imageBytesList) {
            CompletableFuture<String> future = CompletableFuture.supplyAsync(
                () -> recognizeText(imageBytes), ocrExecutor
            );
            futures.add(future);
        }

        // 等待所有任务完成
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(v -> {
                List<String> results = new ArrayList<>();
                for (CompletableFuture<String> future : futures) {
                    try {
                        results.add(future.join());
                    } catch (Exception e) {
                        log.error("OCR识别失败", e);
                        results.add("");
                    }
                }

                long totalTime = System.currentTimeMillis() - startTime;
                log.info("批量OCR识别完成: 图片数量={}, 总耗时={}ms, 平均耗时={}ms",
                    imageBytesList.size(), totalTime, totalTime / imageBytesList.size());

                return results;
            });
    }

    /**
     * 批量识别图片中的文字（同步，保留兼容性）
     *
     * @param imageBytesList 图片字节数组列表
     * @return 识别出的文字列表
     */
    public List<String> recognizeTextBatch(List<byte[]> imageBytesList) {
        if (imageBytesList == null || imageBytesList.isEmpty()) {
            return Collections.emptyList();
        }

        log.info("开始批量OCR识别: 图片数量={}", imageBytesList.size());
        long startTime = System.currentTimeMillis();

        List<String> results = new ArrayList<>();
        for (int i = 0; i < imageBytesList.size(); i++) {
            String text = recognizeText(imageBytesList.get(i));
            results.add(text);
            log.debug("图片{}识别完成: 字符数={}", i, text.length());
        }

        long totalTime = System.currentTimeMillis() - startTime;
        log.info("批量OCR识别完成: 图片数量={}, 总耗时={}ms, 平均耗时={}ms",
                imageBytesList.size(), totalTime, totalTime / imageBytesList.size());

        return results;
    }

    /**
     * 执行OCR识别
     */
    private String doOcr(BufferedImage image) {
        try {
            long startTime = System.currentTimeMillis();
            String text = tesseract.doOCR(image);
            long elapsedTime = System.currentTimeMillis() - startTime;

            log.debug("OCR识别完成: 耗时={}ms, 字符数={}", elapsedTime, text != null ? text.length() : 0);

            return text != null ? text.trim() : "";

        } catch (TesseractException e) {
            log.error("Tesseract OCR识别失败", e);
            return "";
        }
    }

    /**
     * 检查Tesseract是否可用
     */
    public boolean isAvailable() {
        try {
            BufferedImage testImage = new BufferedImage(100, 30, BufferedImage.TYPE_INT_RGB);
            tesseract.doOCR(testImage);
            return true;
        } catch (Exception e) {
            log.error("Tesseract不可用", e);
            return false;
        }
    }

    /**
     * 获取Tesseract版本信息
     */
    public String getVersion() {
        try {
            return "Tesseract OCR (version unknown)";
        } catch (Exception e) {
            return "Unknown";
        }
    }
}
