package com.qy.dch.rag.parser;

import com.qy.dch.rag.config.OcrProperties;
import lombok.extern.slf4j.Slf4j;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.springframework.beans.factory.annotation.Qualifier;
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

@Slf4j
@Service
public class OcrService {

    private final OcrProperties ocrProperties;
    private final Executor ocrExecutor;
    private final Tesseract tesseract;

    public OcrService(OcrProperties ocrProperties,
                      @Qualifier("ocrExecutor") Executor ocrExecutor) {
        this.ocrProperties = ocrProperties;
        this.ocrExecutor = ocrExecutor;
        this.tesseract = initTesseract();
    }

    private Tesseract initTesseract() {
        Tesseract tess = new Tesseract();
        OcrProperties.TesseractProperties t = ocrProperties.getTesseract();
        tess.setDatapath(t.getDatapath());
        tess.setLanguage(t.getLanguage());
        tess.setPageSegMode(t.getPageSegMode());
        tess.setOcrEngineMode(t.getOcrEngineMode());
        log.info("Tesseract初始化完成: datapath={}, language={}", t.getDatapath(), t.getLanguage());
        return tess;
    }

    public String recognizeText(byte[] imageBytes) {
        if (imageBytes == null || imageBytes.length == 0) {
            return "";
        }
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (image == null) {
                return "";
            }
            return doOcr(image);
        } catch (IOException e) {
            log.error("图片读取失败", e);
            return "";
        }
    }

    @Async("ocrExecutor")
    public CompletableFuture<String> recognizeTextAsync(byte[] imageBytes) {
        return CompletableFuture.completedFuture(recognizeText(imageBytes));
    }

    public List<String> recognizeTextBatch(List<byte[]> imageBytesList) {
        if (imageBytesList == null || imageBytesList.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> results = new ArrayList<>();
        for (byte[] bytes : imageBytesList) {
            results.add(recognizeText(bytes));
        }
        return results;
    }

    private String doOcr(BufferedImage image) {
        try {
            String text = tesseract.doOCR(image);
            return text != null ? text.trim() : "";
        } catch (TesseractException e) {
            log.error("Tesseract OCR 识别失败", e);
            return "";
        } catch (UnsatisfiedLinkError e) {
            log.error("Tesseract native 库未加载，跳过 OCR", e);
            return "";
        }
    }

    public boolean isAvailable() {
        try {
            BufferedImage testImage = new BufferedImage(100, 30, BufferedImage.TYPE_INT_RGB);
            tesseract.doOCR(testImage);
            return true;
        } catch (Throwable e) {
            return false;
        }
    }
}
