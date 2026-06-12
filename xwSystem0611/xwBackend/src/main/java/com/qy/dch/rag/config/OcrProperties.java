package com.qy.dch.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "rag.ocr")
public class OcrProperties {

    private boolean enabled = true;
    private TesseractProperties tesseract = new TesseractProperties();
    private ImageProperties image = new ImageProperties();
    private int threadPoolSize = 4;

    @Data
    public static class TesseractProperties {
        private String datapath = "/usr/share/tesseract-ocr/4.00/tessdata";
        private String language = "chi_sim+eng";
        private int pageSegMode = 1;
        private int ocrEngineMode = 1;
    }

    @Data
    public static class ImageProperties {
        private boolean enableOcr = true;
        private int maxSizeMb = 10;
    }
}
