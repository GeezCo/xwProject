package com.intel.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * OCR配置属性
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "ocr")
public class OcrProperties {

    private TesseractProperties tesseract = new TesseractProperties();

    private ImageProperties image = new ImageProperties();

    private int threadPoolSize = 4;

    @Data
    public static class TesseractProperties {
        private String datapath = "/usr/share/tessdata";
        private String language = "chi_sim";
        private int pageSegMode = 1;
        private int ocrEngineMode = 1;
    }

    @Data
    public static class ImageProperties {
        private boolean enableOcr = true;
        private int maxSizeMb = 10;
    }
}
