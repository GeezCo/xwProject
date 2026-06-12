package com.qy.dch.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "rag.parser")
public class DocumentParserProperties {

    private int maxFileSizeMb = 50;
    private String tempDir = "/tmp/xianwei-uploads";
    private boolean strictDocxOnly = true;
}