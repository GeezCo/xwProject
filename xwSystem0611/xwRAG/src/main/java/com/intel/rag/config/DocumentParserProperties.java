package com.intel.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 文档解析配置属性
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "document.parser")
public class DocumentParserProperties {

    private ChunkStrategyProperties chunkStrategy = new ChunkStrategyProperties();

    private TableProperties table = new TableProperties();

    private ImageProperties image = new ImageProperties();

    @Data
    public static class ChunkStrategyProperties {
        private int shortTextMaxLength = 256;
        private int mediumChunkLength = 512;
        private int mediumChunkMaxLength = 768;
        private int longChunkLength = 1024;
        private int longChunkOverlap = 128;
    }

    @Data
    public static class TableProperties {
        private String format = "markdown"; // markdown 或 json
    }

    @Data
    public static class ImageProperties {
        private boolean enableOcr = true;
        private int maxSizeMb = 10;
    }
}
