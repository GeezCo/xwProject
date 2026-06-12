# xwRAG 并入 xwBackend 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 删除 xwRAG 独立模块，将 OCR 与 DOCX 解析能力移植到 xwBackend，统一数据库为 MySQL + ES，使部署只剩 6 个容器（原 8 个）。

**Architecture:** xwBackend 已有完整 ES dense_vector RAG 实现（chunk、embed、search、store），本次仅移植 xwRAG 的解析层（OcrService、DocxParser*）到 `com.qy.dch.rag.parser` 包，删除 PostgreSQL 与独立 RAG 服务。

**Tech Stack:** Spring Boot 2.7.18 / JDK 8 / MySQL 8 / Elasticsearch 7.17 / Tess4j 5.9.0 / POI 5.2.5 / MyBatis 2.3.2

**Spec:** `docs/superpowers/specs/2026-06-12-merge-rag-into-backend-design.md`

---

## File Structure

**新建（xwBackend）：**
- `xwBackend/src/main/java/com/qy/dch/rag/config/OcrProperties.java`
- `xwBackend/src/main/java/com/qy/dch/rag/config/DocumentParserProperties.java`
- `xwBackend/src/main/java/com/qy/dch/rag/config/AsyncConfiguration.java`
- `xwBackend/src/main/java/com/qy/dch/rag/parser/OcrService.java`
- `xwBackend/src/main/java/com/qy/dch/rag/parser/DocxParserService.java`
- `xwBackend/src/main/java/com/qy/dch/rag/parser/DocxMixedParserService.java`
- `xwBackend/src/main/java/com/qy/dch/rag/parser/DocxTableParserService.java`
- `xwBackend/src/main/java/com/qy/dch/rag/model/RagDocument.java`
- `xwBackend/src/main/java/com/qy/dch/dto/RagDocumentDTO.java`
- `xwBackend/src/main/java/com/qy/dch/mapper/RagDocumentMapper.java`
- `xwBackend/src/main/java/com/qy/dch/controller/DocumentController.java`
- `xwBackend/src/main/resources/mapper/RagDocumentMapper.xml`
- `xwBackend/src/main/resources/db/rag_document_ddl.sql`
- 测试：`xwBackend/src/test/java/com/qy/dch/rag/parser/*Test.java`

**修改：**
- `xwBackend/pom.xml` — 加 Tess4j 5.9.0 + imgscalr 4.2
- `xwBackend/src/main/resources/application.yml` — 加 `rag.ocr` 与 `rag.parser` 配置块
- `xwBackend/src/main/java/com/qy/dch/service/RagService.java` — 加 `uploadAndIndex` 方法
- `xwBackend/src/main/java/com/qy/dch/service/impl/RagServiceImpl.java` — 实现 `uploadAndIndex`
- `xwSystem0611/deploy/docker-compose.yml` — 删 postgres + xianwei-rag
- `xwSystem0611/CLAUDE.md` — 移除 xwRAG 说明

**删除：**
- `xwSystem0611/xwRAG/` 整个目录（46 个 Java 文件）

---

## Task 1: 准备分支与依赖

**Files:**
- Modify: `xwSystem0611/xwBackend/pom.xml`

- [ ] **Step 1: 创建分支**

```bash
cd /Users/processmonitor/Documents/IdeaProject/xwProject
git tag v1-before-merge-rag
git checkout -b feature/merge-rag-into-backend
```

- [ ] **Step 2: 在 xwBackend/pom.xml 的 `</dependencies>` 标签前添加依赖**

定位：在 `xwSystem0611/xwBackend/pom.xml` 第 144 行（`</dependencies>` 之前）。

```xml
        <!-- Tess4j OCR (JDK 8 兼容) -->
        <dependency>
            <groupId>net.sourceforge.tess4j</groupId>
            <artifactId>tess4j</artifactId>
            <version>5.9.0</version>
        </dependency>

        <!-- 图片处理 -->
        <dependency>
            <groupId>org.imgscalr</groupId>
            <artifactId>imgscalr-lib</artifactId>
            <version>4.2</version>
        </dependency>
```

- [ ] **Step 3: 验证 Maven 解析依赖**

```bash
cd xwSystem0611/xwBackend
mvn dependency:resolve -DexcludeTransitive=false 2>&1 | tail -20
```

Expected: 输出包含 `tess4j-5.9.0.jar` 与 `imgscalr-lib-4.2.jar`，无 `BUILD FAILURE`。

- [ ] **Step 4: 验证编译通过**

```bash
mvn -pl . compile -DskipTests
```

Expected: `BUILD SUCCESS`。

- [ ] **Step 5: 提交**

```bash
git add xwSystem0611/xwBackend/pom.xml
git commit -m "build: 在 xwBackend 添加 Tess4j 与 imgscalr 依赖"
```

---

## Task 2: 移植 OcrProperties 与 DocumentParserProperties

**Files:**
- Create: `xwSystem0611/xwBackend/src/main/java/com/qy/dch/rag/config/OcrProperties.java`
- Create: `xwSystem0611/xwBackend/src/main/java/com/qy/dch/rag/config/DocumentParserProperties.java`

- [ ] **Step 1: 创建 OcrProperties.java**

```java
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
```

- [ ] **Step 2: 创建 DocumentParserProperties.java**

```java
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
```

- [ ] **Step 3: 编译验证**

```bash
cd xwSystem0611/xwBackend
mvn compile -DskipTests
```

Expected: `BUILD SUCCESS`。

- [ ] **Step 4: 提交**

```bash
git add xwSystem0611/xwBackend/src/main/java/com/qy/dch/rag/config/
git commit -m "feat(rag): 添加 OCR 与文档解析配置类"
```

---

## Task 3: 新建 AsyncConfiguration（独立 ocrExecutor 线程池）

**Files:**
- Create: `xwSystem0611/xwBackend/src/main/java/com/qy/dch/rag/config/AsyncConfiguration.java`

- [ ] **Step 1: 创建 AsyncConfiguration.java**

```java
package com.qy.dch.rag.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfiguration {

    @Autowired
    private OcrProperties ocrProperties;

    @Bean(name = "ocrExecutor")
    public Executor ocrExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        int poolSize = ocrProperties.getThreadPoolSize();
        executor.setCorePoolSize(poolSize);
        executor.setMaxPoolSize(poolSize * 2);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("ocr-");
        executor.setKeepAliveSeconds(60);
        executor.initialize();
        return executor;
    }
}
```

- [ ] **Step 2: 编译验证**

```bash
mvn compile -DskipTests
```

Expected: `BUILD SUCCESS`。

- [ ] **Step 3: 提交**

```bash
git add xwSystem0611/xwBackend/src/main/java/com/qy/dch/rag/config/AsyncConfiguration.java
git commit -m "feat(rag): 添加独立 ocrExecutor 线程池配置"
```

---

## Task 4: 移植 OcrService（含测试）

**Files:**
- Create: `xwSystem0611/xwBackend/src/main/java/com/qy/dch/rag/parser/OcrService.java`
- Test: `xwSystem0611/xwBackend/src/test/java/com/qy/dch/rag/parser/OcrServiceTest.java`

- [ ] **Step 1: 写失败的测试**

`xwSystem0611/xwBackend/src/test/java/com/qy/dch/rag/parser/OcrServiceTest.java`:

```java
package com.qy.dch.rag.parser;

import com.qy.dch.rag.config.OcrProperties;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class OcrServiceTest {

    @Test
    void recognizeText_emptyBytes_returnsEmptyString() {
        OcrProperties props = new OcrProperties();
        OcrService service = new OcrService(props, Executors.newSingleThreadExecutor());
        assertEquals("", service.recognizeText(new byte[0]));
        assertEquals("", service.recognizeText((byte[]) null));
    }

    @Test
    void recognizeText_invalidBytes_returnsEmptyString() {
        OcrProperties props = new OcrProperties();
        OcrService service = new OcrService(props, Executors.newSingleThreadExecutor());
        assertEquals("", service.recognizeText(new byte[]{1, 2, 3, 4}));
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

```bash
mvn test -Dtest=OcrServiceTest -pl xwSystem0611/xwBackend
```

Expected: FAIL — `cannot find symbol class OcrService`。

- [ ] **Step 3: 创建 OcrService.java**

```java
package com.qy.dch.rag.parser;

import com.qy.dch.rag.config.OcrProperties;
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

@Slf4j
@Service
public class OcrService {

    private final OcrProperties ocrProperties;
    private final Executor ocrExecutor;
    private final Tesseract tesseract;

    public OcrService(OcrProperties ocrProperties,
                      org.springframework.beans.factory.annotation.Qualifier("ocrExecutor") Executor ocrExecutor) {
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
```

- [ ] **Step 4: 运行测试验证通过**

```bash
mvn test -Dtest=OcrServiceTest -pl xwSystem0611/xwBackend
```

Expected: PASS（2 tests passed）。注意：测试用空字节，不会触发真实 Tesseract，因此即使本地没装 tesseract 也能通过。

- [ ] **Step 5: 提交**

```bash
git add xwSystem0611/xwBackend/src/main/java/com/qy/dch/rag/parser/OcrService.java \
        xwSystem0611/xwBackend/src/test/java/com/qy/dch/rag/parser/OcrServiceTest.java
git commit -m "feat(rag): 移植 OcrService 至 xwBackend，独立线程池"
```

---

## Task 5: 移植 DocxParserService（纯文本解析）

**Files:**
- Create: `xwSystem0611/xwBackend/src/main/java/com/qy/dch/rag/parser/DocxParserService.java`
- Create: `xwSystem0611/xwBackend/src/main/java/com/qy/dch/rag/model/ParsedDocument.java`
- Test: `xwSystem0611/xwBackend/src/test/java/com/qy/dch/rag/parser/DocxParserServiceTest.java`

- [ ] **Step 1: 创建 ParsedDocument.java（轻量替换 xwRAG Document）**

```java
package com.qy.dch.rag.model;

import lombok.Builder;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
@Builder
public class ParsedDocument {
    private String id;
    private String content;
    private String source;       // "docx"
    private String type;         // "text" / "mixed" / "table"
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();
}
```

- [ ] **Step 2: 写失败的测试**

```java
package com.qy.dch.rag.parser;

import com.qy.dch.rag.config.DocumentParserProperties;
import com.qy.dch.rag.model.ParsedDocument;
import org.junit.jupiter.api.Test;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

class DocxParserServiceTest {

    @Test
    void parseSimpleDocx_extractsAllParagraphs() throws Exception {
        File tmp = File.createTempFile("test-", ".docx");
        try (XWPFDocument doc = new XWPFDocument()) {
            XWPFParagraph p1 = doc.createParagraph();
            p1.createRun().setText("第一段内容");
            XWPFParagraph p2 = doc.createParagraph();
            p2.createRun().setText("第二段内容");
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                doc.write(out);
            }
        }

        DocxParserService service = new DocxParserService(new DocumentParserProperties());
        ParsedDocument result = service.parseSimpleDocx(tmp.getAbsolutePath());

        assertTrue(result.getContent().contains("第一段内容"));
        assertTrue(result.getContent().contains("第二段内容"));
        assertEquals("docx", result.getSource());
        assertEquals("text", result.getType());
        Files.deleteIfExists(tmp.toPath());
    }
}
```

- [ ] **Step 3: 运行测试验证失败**

```bash
mvn test -Dtest=DocxParserServiceTest -pl xwSystem0611/xwBackend
```

Expected: FAIL — `cannot find symbol class DocxParserService`。

- [ ] **Step 4: 创建 DocxParserService.java**

```java
package com.qy.dch.rag.parser;

import com.qy.dch.rag.config.DocumentParserProperties;
import com.qy.dch.rag.model.ParsedDocument;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
public class DocxParserService {

    private final DocumentParserProperties parserProperties;

    public DocxParserService(DocumentParserProperties parserProperties) {
        this.parserProperties = parserProperties;
    }

    public ParsedDocument parseSimpleDocx(String filePath) throws IOException {
        long startTime = System.currentTimeMillis();
        try (InputStream fis = new FileInputStream(filePath);
             XWPFDocument document = new XWPFDocument(fis)) {

            String content = document.getParagraphs().stream()
                    .map(XWPFParagraph::getText)
                    .filter(t -> t != null && !t.trim().isEmpty())
                    .collect(Collectors.joining("\n"));

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("filePath", filePath);
            metadata.put("fileName", extractFileName(filePath));
            metadata.put("paragraphCount", document.getParagraphs().size());
            metadata.put("contentLength", content.length());
            metadata.put("parseTime", System.currentTimeMillis() - startTime);

            return ParsedDocument.builder()
                    .id("docx_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16))
                    .content(content)
                    .source("docx")
                    .type("text")
                    .metadata(metadata)
                    .build();
        }
    }

    public boolean isValidDocx(String filePath) {
        return filePath != null && filePath.toLowerCase().endsWith(".docx");
    }

    private String extractFileName(String filePath) {
        int lastSlash = Math.max(filePath.lastIndexOf('/'), filePath.lastIndexOf('\\'));
        return lastSlash >= 0 ? filePath.substring(lastSlash + 1) : filePath;
    }
}
```

- [ ] **Step 5: 运行测试验证通过**

```bash
mvn test -Dtest=DocxParserServiceTest -pl xwSystem0611/xwBackend
```

Expected: PASS。

- [ ] **Step 6: 提交**

```bash
git add xwSystem0611/xwBackend/src/main/java/com/qy/dch/rag/model/ParsedDocument.java \
        xwSystem0611/xwBackend/src/main/java/com/qy/dch/rag/parser/DocxParserService.java \
        xwSystem0611/xwBackend/src/test/java/com/qy/dch/rag/parser/DocxParserServiceTest.java
git commit -m "feat(rag): 移植 DocxParserService 与 ParsedDocument 模型"
```

---

## Task 6: 移植 DocxMixedParserService（含 OCR）

**Files:**
- Create: `xwSystem0611/xwBackend/src/main/java/com/qy/dch/rag/parser/DocxMixedParserService.java`
- Test: `xwSystem0611/xwBackend/src/test/java/com/qy/dch/rag/parser/DocxMixedParserServiceTest.java`

- [ ] **Step 1: 写失败的测试（用 mock 的 OcrService）**

```java
package com.qy.dch.rag.parser;

import com.qy.dch.rag.config.OcrProperties;
import com.qy.dch.rag.model.ParsedDocument;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class DocxMixedParserServiceTest {

    @Test
    void parseMixedDocx_noImages_returnsPlainText() throws Exception {
        File tmp = File.createTempFile("mixed-", ".docx");
        try (XWPFDocument doc = new XWPFDocument()) {
            XWPFParagraph p = doc.createParagraph();
            p.createRun().setText("纯文本段落");
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                doc.write(out);
            }
        }

        OcrProperties ocrProps = new OcrProperties();
        ocrProps.getImage().setEnableOcr(false); // 禁用 OCR 以免触发 native
        OcrService ocr = new OcrService(ocrProps, Executors.newSingleThreadExecutor());
        DocxMixedParserService service = new DocxMixedParserService(ocr, ocrProps);

        ParsedDocument result = service.parseMixedDocx(tmp.getAbsolutePath());

        assertTrue(result.getContent().contains("纯文本段落"));
        assertEquals(0, result.getMetadata().get("imageCount"));
        assertEquals("mixed", result.getType());
        Files.deleteIfExists(tmp.toPath());
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

```bash
mvn test -Dtest=DocxMixedParserServiceTest -pl xwSystem0611/xwBackend
```

Expected: FAIL — `cannot find symbol class DocxMixedParserService`。

- [ ] **Step 3: 创建 DocxMixedParserService.java**

```java
package com.qy.dch.rag.parser;

import com.qy.dch.rag.config.OcrProperties;
import com.qy.dch.rag.model.ParsedDocument;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class DocxMixedParserService {

    private final OcrService ocrService;
    private final OcrProperties ocrProperties;

    public DocxMixedParserService(OcrService ocrService, OcrProperties ocrProperties) {
        this.ocrService = ocrService;
        this.ocrProperties = ocrProperties;
    }

    public ParsedDocument parseMixedDocx(String filePath) throws IOException {
        long startTime = System.currentTimeMillis();

        try (InputStream fis = new FileInputStream(filePath);
             XWPFDocument document = new XWPFDocument(fis)) {

            StringBuilder contentBuilder = new StringBuilder();
            int imageCount = 0;
            int ocrTextLength = 0;

            for (XWPFParagraph paragraph : document.getParagraphs()) {
                String paraText = paragraph.getText();
                if (paraText != null && !paraText.trim().isEmpty()) {
                    contentBuilder.append(paraText).append("\n");
                }

                for (XWPFRun run : paragraph.getRuns()) {
                    List<XWPFPicture> pictures = run.getEmbeddedPictures();
                    for (XWPFPicture picture : pictures) {
                        imageCount++;
                        if (!ocrProperties.getImage().isEnableOcr()) {
                            continue;
                        }
                        try {
                            byte[] imageData = picture.getPictureData().getData();
                            double sizeMb = imageData.length / (1024.0 * 1024.0);
                            if (sizeMb > ocrProperties.getImage().getMaxSizeMb()) {
                                log.warn("图片超过 {}MB，跳过 OCR", ocrProperties.getImage().getMaxSizeMb());
                                continue;
                            }
                            String ocrText = ocrService.recognizeText(imageData);
                            if (!ocrText.isEmpty()) {
                                contentBuilder.append("\n[图片内容]\n").append(ocrText).append("\n[/图片内容]\n\n");
                                ocrTextLength += ocrText.length();
                            }
                        } catch (Exception e) {
                            log.error("图片 OCR 失败，降级为只索引文本", e);
                        }
                    }
                }
            }

            String content = contentBuilder.toString();
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("filePath", filePath);
            metadata.put("paragraphCount", document.getParagraphs().size());
            metadata.put("imageCount", imageCount);
            metadata.put("ocrEnabled", ocrProperties.getImage().isEnableOcr());
            metadata.put("ocrTextLength", ocrTextLength);
            metadata.put("contentLength", content.length());
            metadata.put("parseTime", System.currentTimeMillis() - startTime);

            return ParsedDocument.builder()
                    .id("docx_mixed_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16))
                    .content(content)
                    .source("docx")
                    .type("mixed")
                    .metadata(metadata)
                    .build();
        }
    }

    public List<byte[]> extractImages(String filePath) throws IOException {
        List<byte[]> images = new ArrayList<>();
        try (InputStream fis = new FileInputStream(filePath);
             XWPFDocument document = new XWPFDocument(fis)) {
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                for (XWPFRun run : paragraph.getRuns()) {
                    for (XWPFPicture picture : run.getEmbeddedPictures()) {
                        images.add(picture.getPictureData().getData());
                    }
                }
            }
        }
        return images;
    }
}
```

- [ ] **Step 4: 运行测试验证通过**

```bash
mvn test -Dtest=DocxMixedParserServiceTest -pl xwSystem0611/xwBackend
```

Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git add xwSystem0611/xwBackend/src/main/java/com/qy/dch/rag/parser/DocxMixedParserService.java \
        xwSystem0611/xwBackend/src/test/java/com/qy/dch/rag/parser/DocxMixedParserServiceTest.java
git commit -m "feat(rag): 移植 DocxMixedParserService 支持图文混排 + OCR 降级"
```

---

## Task 7: 移植 DocxTableParserService（提取表格）

**Files:**
- Create: `xwSystem0611/xwBackend/src/main/java/com/qy/dch/rag/parser/DocxTableParserService.java`
- Test: `xwSystem0611/xwBackend/src/test/java/com/qy/dch/rag/parser/DocxTableParserServiceTest.java`

- [ ] **Step 1: 写失败的测试**

```java
package com.qy.dch.rag.parser;

import com.qy.dch.rag.model.ParsedDocument;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

class DocxTableParserServiceTest {

    @Test
    void parseTableDocx_extractsTableCells() throws Exception {
        File tmp = File.createTempFile("table-", ".docx");
        try (XWPFDocument doc = new XWPFDocument()) {
            XWPFTable table = doc.createTable(2, 2);
            XWPFTableRow row0 = table.getRow(0);
            row0.getCell(0).setText("姓名");
            row0.getCell(1).setText("年龄");
            XWPFTableRow row1 = table.getRow(1);
            row1.getCell(0).setText("张三");
            row1.getCell(1).setText("30");
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                doc.write(out);
            }
        }

        DocxTableParserService service = new DocxTableParserService();
        ParsedDocument result = service.parseTableDocx(tmp.getAbsolutePath());

        assertTrue(result.getContent().contains("姓名"));
        assertTrue(result.getContent().contains("张三"));
        assertEquals("table", result.getType());
        Files.deleteIfExists(tmp.toPath());
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

```bash
mvn test -Dtest=DocxTableParserServiceTest -pl xwSystem0611/xwBackend
```

Expected: FAIL。

- [ ] **Step 3: 创建 DocxTableParserService.java**

```java
package com.qy.dch.rag.parser;

import com.qy.dch.rag.model.ParsedDocument;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class DocxTableParserService {

    public ParsedDocument parseTableDocx(String filePath) throws IOException {
        long startTime = System.currentTimeMillis();
        try (InputStream fis = new FileInputStream(filePath);
             XWPFDocument document = new XWPFDocument(fis)) {

            StringBuilder sb = new StringBuilder();
            int tableCount = 0;

            for (XWPFParagraph p : document.getParagraphs()) {
                String t = p.getText();
                if (t != null && !t.trim().isEmpty()) {
                    sb.append(t).append("\n");
                }
            }

            for (XWPFTable table : document.getTables()) {
                tableCount++;
                sb.append("\n[表格").append(tableCount).append("]\n");
                for (XWPFTableRow row : table.getRows()) {
                    StringBuilder rowBuilder = new StringBuilder();
                    for (XWPFTableCell cell : row.getTableCells()) {
                        String text = cell.getText();
                        rowBuilder.append(text == null ? "" : text).append(" | ");
                    }
                    sb.append(rowBuilder.toString()).append("\n");
                }
                sb.append("[/表格").append(tableCount).append("]\n\n");
            }

            String content = sb.toString();
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("filePath", filePath);
            metadata.put("tableCount", tableCount);
            metadata.put("contentLength", content.length());
            metadata.put("parseTime", System.currentTimeMillis() - startTime);

            return ParsedDocument.builder()
                    .id("docx_table_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16))
                    .content(content)
                    .source("docx")
                    .type("table")
                    .metadata(metadata)
                    .build();
        }
    }
}
```

- [ ] **Step 4: 运行测试验证通过**

```bash
mvn test -Dtest=DocxTableParserServiceTest -pl xwSystem0611/xwBackend
```

Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git add xwSystem0611/xwBackend/src/main/java/com/qy/dch/rag/parser/DocxTableParserService.java \
        xwSystem0611/xwBackend/src/test/java/com/qy/dch/rag/parser/DocxTableParserServiceTest.java
git commit -m "feat(rag): 移植 DocxTableParserService 提取表格内容"
```

---

## Task 8: 添加 application.yml 配置块

**Files:**
- Modify: `xwSystem0611/xwBackend/src/main/resources/application.yml`

- [ ] **Step 1: 读取当前 application.yml 末尾**

```bash
tail -30 xwSystem0611/xwBackend/src/main/resources/application.yml
```

定位文件末尾，准备追加。

- [ ] **Step 2: 在文件末尾追加 rag.ocr 与 rag.parser 配置**

在 `xwSystem0611/xwBackend/src/main/resources/application.yml` 文件末尾追加：

```yaml

# RAG 模块 OCR 与解析配置
rag:
  ocr:
    enabled: true
    thread-pool-size: 4
    tesseract:
      datapath: /usr/share/tesseract-ocr/4.00/tessdata
      language: chi_sim+eng
      page-seg-mode: 1
      ocr-engine-mode: 1
    image:
      enable-ocr: true
      max-size-mb: 10
  parser:
    max-file-size-mb: 50
    temp-dir: /tmp/xianwei-uploads
    strict-docx-only: true
```

注：如果文件中已有 `rag:` 配置块（已有 `rag.es` / `rag.embedding` 等），将上述 `ocr` 与 `parser` 节点合并到现有 `rag:` 下而不是新建顶级 `rag:`。

- [ ] **Step 3: 启动验证 Spring 能加载配置**

```bash
cd xwSystem0611/xwBackend
mvn spring-boot:run -DskipTests &
sleep 30
curl -s http://localhost:8081/actuator/health | head -5
pkill -f spring-boot:run
```

Expected: `{"status":"UP"}` 或类似响应；启动日志无 `OcrProperties` 绑定错误。

- [ ] **Step 4: 提交**

```bash
git add xwSystem0611/xwBackend/src/main/resources/application.yml
git commit -m "config(rag): 添加 rag.ocr 与 rag.parser 配置块"
```

---

## Task 9: 创建 rag_document 表与 Mapper

**Files:**
- Create: `xwSystem0611/xwBackend/src/main/resources/db/rag_document_ddl.sql`
- Create: `xwSystem0611/xwBackend/src/main/java/com/qy/dch/rag/model/RagDocument.java`
- Create: `xwSystem0611/xwBackend/src/main/java/com/qy/dch/mapper/RagDocumentMapper.java`
- Create: `xwSystem0611/xwBackend/src/main/resources/mapper/RagDocumentMapper.xml`

- [ ] **Step 1: 创建 DDL 文件**

`xwSystem0611/xwBackend/src/main/resources/db/rag_document_ddl.sql`:

```sql
CREATE TABLE IF NOT EXISTS rag_document (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    doc_id VARCHAR(64) NOT NULL UNIQUE COMMENT '文档唯一ID',
    filename VARCHAR(255) COMMENT '原始文件名',
    file_size BIGINT COMMENT '文件字节数',
    chunk_count INT DEFAULT 0 COMMENT '切片数量',
    status VARCHAR(20) NOT NULL DEFAULT 'pending' COMMENT 'pending/indexed/failed',
    error_msg TEXT COMMENT '失败原因',
    upload_time DATETIME NOT NULL,
    indexed_time DATETIME,
    INDEX idx_status (status),
    INDEX idx_upload_time (upload_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RAG 上传文档元数据';
```

- [ ] **Step 2: 创建实体 RagDocument.java**

```java
package com.qy.dch.rag.model;

import lombok.Data;

import java.util.Date;

@Data
public class RagDocument {
    private Long id;
    private String docId;
    private String filename;
    private Long fileSize;
    private Integer chunkCount;
    private String status;       // pending / indexed / failed
    private String errorMsg;
    private Date uploadTime;
    private Date indexedTime;
}
```

- [ ] **Step 3: 创建 Mapper 接口**

`xwSystem0611/xwBackend/src/main/java/com/qy/dch/mapper/RagDocumentMapper.java`:

```java
package com.qy.dch.mapper;

import com.qy.dch.rag.model.RagDocument;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface RagDocumentMapper {

    int insert(RagDocument doc);

    RagDocument selectByDocId(@Param("docId") String docId);

    int updateStatus(@Param("docId") String docId,
                     @Param("status") String status,
                     @Param("chunkCount") Integer chunkCount,
                     @Param("errorMsg") String errorMsg);
}
```

- [ ] **Step 4: 创建 Mapper XML**

`xwSystem0611/xwBackend/src/main/resources/mapper/RagDocumentMapper.xml`:

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.qy.dch.mapper.RagDocumentMapper">

    <resultMap id="ragDocumentMap" type="com.qy.dch.rag.model.RagDocument">
        <id column="id" property="id"/>
        <result column="doc_id" property="docId"/>
        <result column="filename" property="filename"/>
        <result column="file_size" property="fileSize"/>
        <result column="chunk_count" property="chunkCount"/>
        <result column="status" property="status"/>
        <result column="error_msg" property="errorMsg"/>
        <result column="upload_time" property="uploadTime"/>
        <result column="indexed_time" property="indexedTime"/>
    </resultMap>

    <insert id="insert" parameterType="com.qy.dch.rag.model.RagDocument" useGeneratedKeys="true" keyProperty="id">
        INSERT INTO rag_document (doc_id, filename, file_size, chunk_count, status, error_msg, upload_time, indexed_time)
        VALUES (#{docId}, #{filename}, #{fileSize}, #{chunkCount}, #{status}, #{errorMsg}, #{uploadTime}, #{indexedTime})
    </insert>

    <select id="selectByDocId" resultMap="ragDocumentMap">
        SELECT * FROM rag_document WHERE doc_id = #{docId}
    </select>

    <update id="updateStatus">
        UPDATE rag_document
        SET status = #{status},
            chunk_count = COALESCE(#{chunkCount}, chunk_count),
            error_msg = #{errorMsg},
            indexed_time = CASE WHEN #{status} = 'indexed' THEN NOW() ELSE indexed_time END
        WHERE doc_id = #{docId}
    </update>
</mapper>
```

- [ ] **Step 5: 在 MySQL 上执行 DDL**

```bash
mysql -h 36.103.234.242 -P 8010 -u uygur_user -puygur_2024 uygur_project \
  < xwSystem0611/xwBackend/src/main/resources/db/rag_document_ddl.sql
```

Expected: 无输出，命令退出码 0。

验证：
```bash
mysql -h 36.103.234.242 -P 8010 -u uygur_user -puygur_2024 uygur_project \
  -e "DESC rag_document;"
```

Expected: 输出表结构，包含 doc_id / status / upload_time 等字段。

- [ ] **Step 6: 编译验证**

```bash
mvn compile -DskipTests
```

Expected: `BUILD SUCCESS`。

- [ ] **Step 7: 提交**

```bash
git add xwSystem0611/xwBackend/src/main/resources/db/rag_document_ddl.sql \
        xwSystem0611/xwBackend/src/main/java/com/qy/dch/rag/model/RagDocument.java \
        xwSystem0611/xwBackend/src/main/java/com/qy/dch/mapper/RagDocumentMapper.java \
        xwSystem0611/xwBackend/src/main/resources/mapper/RagDocumentMapper.xml
git commit -m "feat(rag): 新增 rag_document 表及 MyBatis Mapper"
```

---

## Task 10: 扩展 RagService — uploadAndIndex 方法

**Files:**
- Modify: `xwSystem0611/xwBackend/src/main/java/com/qy/dch/service/RagService.java`
- Modify: `xwSystem0611/xwBackend/src/main/java/com/qy/dch/service/impl/RagServiceImpl.java`

- [ ] **Step 1: 在 RagService 接口添加方法**

定位 `xwSystem0611/xwBackend/src/main/java/com/qy/dch/service/RagService.java`，在最后一个方法签名后添加：

```java
    /**
     * 上传文档（DOCX）→ 解析 → 分块 → 向量化 → 索引到 ES
     *
     * @param file 上传的 multipart 文件
     * @param withOcr 是否启用 OCR（若 DOCX 含图片）
     * @return ResultVO，data 含 docId / chunkCount / elapsedMs
     */
    com.qy.dch.common.ResultVO uploadAndIndex(org.springframework.web.multipart.MultipartFile file, boolean withOcr);

    /**
     * 仅解析返回文本（调试用，不入库不向量化）
     */
    com.qy.dch.common.ResultVO parseOnly(org.springframework.web.multipart.MultipartFile file, boolean withOcr);

    /**
     * 查询单次上传文档的状态
     */
    com.qy.dch.common.ResultVO getDocumentStatus(String docId);
```

- [ ] **Step 2: 在 RagServiceImpl 添加依赖注入**

在 `RagServiceImpl` 类字段区添加：

```java
    @Autowired
    private com.qy.dch.rag.parser.DocxParserService docxParserService;

    @Autowired
    private com.qy.dch.rag.parser.DocxMixedParserService docxMixedParserService;

    @Autowired
    private com.qy.dch.rag.chunk.ChunkService chunkService;

    @Autowired
    private com.qy.dch.rag.embed.EmbeddingService embeddingService;

    @Autowired
    private com.qy.dch.rag.store.EsVectorStore esVectorStore;

    @Autowired
    private com.qy.dch.mapper.RagDocumentMapper ragDocumentMapper;

    @Autowired
    private com.qy.dch.rag.config.DocumentParserProperties parserProperties;
```

- [ ] **Step 3: 实现 uploadAndIndex**

在 `RagServiceImpl` 类内添加方法：

```java
    @Override
    public com.qy.dch.common.ResultVO uploadAndIndex(
            org.springframework.web.multipart.MultipartFile file, boolean withOcr) {
        long startTime = System.currentTimeMillis();

        if (file == null || file.isEmpty()) {
            return com.qy.dch.common.ResultVO.error("文件为空");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".docx")) {
            return com.qy.dch.common.ResultVO.error("仅支持 DOCX 格式文件");
        }
        long maxBytes = parserProperties.getMaxFileSizeMb() * 1024L * 1024L;
        if (file.getSize() > maxBytes) {
            return com.qy.dch.common.ResultVO.error(
                "文件超过 " + parserProperties.getMaxFileSizeMb() + "MB 限制");
        }

        java.io.File tempFile;
        try {
            tempFile = java.io.File.createTempFile("upload_", ".docx");
            file.transferTo(tempFile);
        } catch (java.io.IOException e) {
            log.error("保存临时文件失败", e);
            return com.qy.dch.common.ResultVO.error("保存临时文件失败: " + e.getMessage());
        }

        com.qy.dch.rag.model.RagDocument doc = new com.qy.dch.rag.model.RagDocument();
        String docId = "upload_" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        doc.setDocId(docId);
        doc.setFilename(filename);
        doc.setFileSize(file.getSize());
        doc.setStatus("pending");
        doc.setUploadTime(new java.util.Date());
        ragDocumentMapper.insert(doc);

        try {
            com.qy.dch.rag.model.ParsedDocument parsed = withOcr
                    ? docxMixedParserService.parseMixedDocx(tempFile.getAbsolutePath())
                    : docxParserService.parseSimpleDocx(tempFile.getAbsolutePath());

            java.util.Map<String, Object> metadata = new java.util.HashMap<>();
            metadata.put("title", filename);
            metadata.put("source", "upload");
            metadata.put("docId", docId);

            java.util.List<com.qy.dch.rag.model.DocumentChunk> chunks =
                    chunkService.chunkDocument(docId, parsed.getContent(), metadata);

            java.util.List<String> texts = new java.util.ArrayList<>();
            for (com.qy.dch.rag.model.DocumentChunk c : chunks) texts.add(c.getContent());
            java.util.List<float[]> vectors = embeddingService.embedBatch(texts);
            if (vectors == null || vectors.size() != chunks.size()) {
                throw new IllegalStateException("向量化结果数量与切片不匹配");
            }
            for (int i = 0; i < chunks.size(); i++) {
                chunks.get(i).setEmbedding(vectors.get(i));
            }

            esVectorStore.ensureIndex();
            esVectorStore.bulkIndex(chunks);

            ragDocumentMapper.updateStatus(docId, "indexed", chunks.size(), null);

            java.util.Map<String, Object> data = new java.util.HashMap<>();
            data.put("docId", docId);
            data.put("chunkCount", chunks.size());
            data.put("elapsedMs", System.currentTimeMillis() - startTime);
            return com.qy.dch.common.ResultVO.success(data);

        } catch (Exception e) {
            log.error("文档上传索引失败: docId={}", docId, e);
            ragDocumentMapper.updateStatus(docId, "failed", null, e.getMessage());
            return com.qy.dch.common.ResultVO.error("索引失败: " + e.getMessage());
        } finally {
            if (!tempFile.delete()) {
                log.warn("临时文件删除失败: {}", tempFile.getAbsolutePath());
            }
        }
    }

    @Override
    public com.qy.dch.common.ResultVO parseOnly(
            org.springframework.web.multipart.MultipartFile file, boolean withOcr) {
        if (file == null || file.isEmpty()) {
            return com.qy.dch.common.ResultVO.error("文件为空");
        }
        java.io.File tempFile;
        try {
            tempFile = java.io.File.createTempFile("parse_", ".docx");
            file.transferTo(tempFile);
        } catch (java.io.IOException e) {
            return com.qy.dch.common.ResultVO.error("保存临时文件失败: " + e.getMessage());
        }
        try {
            com.qy.dch.rag.model.ParsedDocument parsed = withOcr
                    ? docxMixedParserService.parseMixedDocx(tempFile.getAbsolutePath())
                    : docxParserService.parseSimpleDocx(tempFile.getAbsolutePath());
            java.util.Map<String, Object> data = new java.util.HashMap<>();
            data.put("content", parsed.getContent());
            data.put("metadata", parsed.getMetadata());
            return com.qy.dch.common.ResultVO.success(data);
        } catch (Exception e) {
            return com.qy.dch.common.ResultVO.error("解析失败: " + e.getMessage());
        } finally {
            tempFile.delete();
        }
    }

    @Override
    public com.qy.dch.common.ResultVO getDocumentStatus(String docId) {
        com.qy.dch.rag.model.RagDocument doc = ragDocumentMapper.selectByDocId(docId);
        if (doc == null) {
            return com.qy.dch.common.ResultVO.error("docId 不存在: " + docId);
        }
        return com.qy.dch.common.ResultVO.success(doc);
    }
```

- [ ] **Step 4: 编译验证**

```bash
mvn compile -DskipTests
```

Expected: `BUILD SUCCESS`。

- [ ] **Step 5: 提交**

```bash
git add xwSystem0611/xwBackend/src/main/java/com/qy/dch/service/RagService.java \
        xwSystem0611/xwBackend/src/main/java/com/qy/dch/service/impl/RagServiceImpl.java
git commit -m "feat(rag): RagService 新增上传索引、解析、状态查询方法"
```

---

## Task 11: 新增 DocumentController

**Files:**
- Create: `xwSystem0611/xwBackend/src/main/java/com/qy/dch/controller/DocumentController.java`

- [ ] **Step 1: 创建 DocumentController.java**

```java
package com.qy.dch.controller;

import com.qy.dch.common.ResultVO;
import com.qy.dch.service.RagService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/rag/document")
@Slf4j
public class DocumentController {

    @Autowired
    private RagService ragService;

    @PostMapping("/upload")
    public ResultVO upload(@RequestParam("file") MultipartFile file) {
        log.info("文档上传: filename={}, size={}", file.getOriginalFilename(), file.getSize());
        return ragService.uploadAndIndex(file, false);
    }

    @PostMapping("/upload/mixed")
    public ResultVO uploadMixed(@RequestParam("file") MultipartFile file) {
        log.info("文档上传（含 OCR）: filename={}, size={}", file.getOriginalFilename(), file.getSize());
        return ragService.uploadAndIndex(file, true);
    }

    @PostMapping("/parse")
    public ResultVO parse(@RequestParam("file") MultipartFile file,
                          @RequestParam(value = "withOcr", defaultValue = "false") boolean withOcr) {
        log.info("仅解析（不入库）: filename={}, withOcr={}", file.getOriginalFilename(), withOcr);
        return ragService.parseOnly(file, withOcr);
    }

    @GetMapping("/status/{docId}")
    public ResultVO status(@PathVariable("docId") String docId) {
        return ragService.getDocumentStatus(docId);
    }
}
```

- [ ] **Step 2: 编译验证**

```bash
mvn compile -DskipTests
```

Expected: `BUILD SUCCESS`。

- [ ] **Step 3: 启动 + curl 验证（端到端冒烟）**

```bash
cd xwSystem0611/xwBackend
mvn spring-boot:run -DskipTests &
sleep 30

# 状态查询不存在的 docId
curl -s http://localhost:8081/api/rag/document/status/nonexistent

pkill -f spring-boot:run
```

Expected: 返回 `{"code":0,"data":null,"msg":"docId 不存在: nonexistent",...}`，表示路由与 Service 都通了。

- [ ] **Step 4: 提交**

```bash
git add xwSystem0611/xwBackend/src/main/java/com/qy/dch/controller/DocumentController.java
git commit -m "feat(rag): 新增 DocumentController 提供 4 个文档接口"
```

---

## Task 12: 删除 xwRAG 模块

**Files:**
- Delete: `xwSystem0611/xwRAG/` 整个目录

- [ ] **Step 1: 检查 xwRAG 中 data_record 表是否有生产数据**

⚠️ **执行前用户必须确认**：

```bash
# 询问用户：xwRAG 关联的 PostgreSQL data_record 表是否需要导出？
# 若需要：先 pg_dump 备份，再继续此 task
echo "请确认 PostgreSQL data_record 表已备份或确认无生产数据"
```

如果用户回答"无数据"或"已备份"，继续 Step 2；否则中断并先做备份。

- [ ] **Step 2: 删除 xwRAG 目录**

```bash
cd /Users/processmonitor/Documents/IdeaProject/xwProject
rm -rf xwSystem0611/xwRAG/
```

- [ ] **Step 3: 验证 xwBackend 仍能编译并通过测试**

```bash
cd xwSystem0611/xwBackend
mvn clean test
```

Expected: `BUILD SUCCESS`，所有测试通过（包括 Task 4-7 的解析测试）。

- [ ] **Step 4: 提交**

```bash
cd /Users/processmonitor/Documents/IdeaProject/xwProject
git add -A xwSystem0611/xwRAG
git commit -m "chore(rag): 删除 xwRAG 独立模块，能力已并入 xwBackend"
```

---

## Task 13: 更新 docker-compose 与 Dockerfile

**Files:**
- Modify: `xwSystem0611/deploy/docker-compose.yml`
- Modify: xwBackend Dockerfile（路径需先定位）

- [ ] **Step 1: 定位 xwBackend Dockerfile**

```bash
find xwSystem0611/deploy -name "Dockerfile*" -o -name "*.dockerfile" 2>/dev/null
```

如果在 deploy 下无 Dockerfile，应在 `xwSystem0611/xwBackend/` 下找：
```bash
find xwSystem0611/xwBackend -name "Dockerfile*"
```

- [ ] **Step 2: 在 xwBackend Dockerfile 中添加 tesseract 安装**

在 `FROM openjdk:8-jdk-slim`（或类似基础镜像）之后立即添加：

```dockerfile
# 安装 Tesseract OCR + 中文简体 + 英文
RUN apt-get update && apt-get install -y \
    tesseract-ocr \
    tesseract-ocr-chi-sim \
    tesseract-ocr-eng \
    && rm -rf /var/lib/apt/lists/*
```

如果基础镜像不是 debian/ubuntu 系列（例如 alpine），需要改用 `apk add tesseract-ocr` 等价命令。

- [ ] **Step 3: 修改 docker-compose.yml — 删除 postgres 与 xianwei-rag 两个 service**

打开 `xwSystem0611/deploy/docker-compose.yml`，定位并删除：

1. `xianwei-rag` 整个 service 块（包含 image、ports、depends_on 等）
2. `postgres` 或 `xianwei-postgres` 整个 service 块
3. 顶层 `volumes:` 下与 PostgreSQL 相关的 volume 声明（如 `postgres_data`）
4. 任何其他 service 的 `depends_on` 中对 postgres / xianwei-rag 的引用

- [ ] **Step 4: 验证 docker-compose 配置语法**

```bash
cd xwSystem0611/deploy
docker compose config > /dev/null
```

Expected: 无输出，退出码 0。如果报错，对照错误信息修复。

- [ ] **Step 5: 提交**

```bash
git add xwSystem0611/deploy/docker-compose.yml \
        xwSystem0611/xwBackend/Dockerfile  # 路径以 Step 1 找到的为准
git commit -m "deploy: 移除 postgres 与 xianwei-rag 容器，xwBackend 镜像加装 Tesseract"
```

---

## Task 14: 更新文档 CLAUDE.md

**Files:**
- Modify: `xwSystem0611/CLAUDE.md`

- [ ] **Step 1: 移除 CLAUDE.md 中所有 xwRAG 相关章节**

打开 `xwSystem0611/CLAUDE.md`，删除：
- 所有提到 "xwRAG" 的段落
- "PostgreSQL" 与 "pgvector" 的引用
- "LangChain4j" 的引用

替换容器列表为新列表（6 个容器，无 postgres / xianwei-rag）。

- [ ] **Step 2: 在 CLAUDE.md 添加 RAG 模块新接口章节**

在合适位置（建议在"报文融合模块"之后）添加：

```markdown
### 五、RAG 知识库模块（已合并至 xwBackend）

#### 后端接口 (RagController + DocumentController)
| 路径 | 方法 | 功能 |
|------|------|------|
| `/api/rag/index/status` | GET | 索引状态 |
| `/api/rag/index/trigger` | POST | 触发批量索引 |
| `/api/rag/index/log` | GET | 索引日志 |
| `/api/rag/search` | POST | 混合检索（BM25 + 向量 RRF） |
| `/api/rag/document/upload` | POST | 上传 DOCX 索引 |
| `/api/rag/document/upload/mixed` | POST | 上传含图片 DOCX（含 OCR） |
| `/api/rag/document/parse` | POST | 仅解析（调试用） |
| `/api/rag/document/status/{docId}` | GET | 查询上传任务状态 |

#### 存储
- ES `xianwei_docs` 索引，dense_vector 1024 维
- MySQL `rag_document` 表记录上传元数据
```

- [ ] **Step 3: 提交**

```bash
git add xwSystem0611/CLAUDE.md
git commit -m "docs: 更新 CLAUDE.md 移除 xwRAG，新增合并后的 RAG 接口列表"
```

---

## Task 15: 端到端集成验证

**Files:**
- 无文件修改（仅运行命令）

- [ ] **Step 1: 全量测试**

```bash
cd xwSystem0611/xwBackend
mvn clean test
```

Expected: 所有测试通过，包括：
- `OcrServiceTest`（2 tests）
- `DocxParserServiceTest`（1 test）
- `DocxMixedParserServiceTest`（1 test）
- `DocxTableParserServiceTest`（1 test）
- 已有的 `DchApplicationTests` 与 `RagModuleTest`

- [ ] **Step 2: 本地启动后端，回归现有 RAG 检索接口**

```bash
mvn spring-boot:run -DskipTests &
sleep 40

# 回归：原有 ES 检索接口
curl -s -X POST http://localhost:8081/api/rag/search \
    -H "Content-Type: application/json" \
    -d '{"query":"测试","topK":3,"hybrid":true}'

# 回归：原有索引状态接口
curl -s http://localhost:8081/api/rag/index/status

# 回归：主业务接口未受影响
curl -s http://localhost:8081/uygur/category

pkill -f spring-boot:run
```

Expected:
- `/api/rag/search` 返回 `{"code":1,...}` 包含 `results` 数组（即使为空也是 code=1）
- `/api/rag/index/status` 返回 `{"code":1,...}`
- `/uygur/category` 返回 `{"code":1,...}` 包含分类树

- [ ] **Step 3: 上传冒烟（如果本机装了 tesseract）**

```bash
# 准备一个简单 docx
python3 -c "
from docx import Document
d = Document()
d.add_paragraph('集成测试文本内容。包含中文。')
d.save('/tmp/smoke.docx')
" 2>/dev/null || echo "python-docx 不可用，跳过上传冒烟"

# 上传（如 docx 存在）
if [ -f /tmp/smoke.docx ]; then
    mvn spring-boot:run -DskipTests &
    sleep 40
    curl -s -X POST http://localhost:8081/api/rag/document/upload \
        -F "file=@/tmp/smoke.docx"
    pkill -f spring-boot:run
fi
```

Expected: 返回 `{"code":1,"data":{"docId":"upload_...","chunkCount":N,...}}` 表示完整链路打通。

如果嵌入服务未启动，会返回 `code:0` 但 `rag_document` 表里能查到 status=failed 的记录，也算管道接通。

- [ ] **Step 4: 合并前 PR**

```bash
git log --oneline feature/merge-rag-into-backend ^main | head
git checkout main
git merge --no-ff feature/merge-rag-into-backend -m "feat: 合并 xwRAG 至 xwBackend，统一为 MySQL + ES 架构"
```

如使用 PR 流程，改为 `git push origin feature/merge-rag-into-backend` 并在 GitLab/Gitea/GitHub 上发起合并请求。

---

## 完成标准

全部 Task 完成后，应满足：

1. ✅ `xwSystem0611/xwRAG/` 已删除
2. ✅ `docker compose config` 输出无 postgres / xianwei-rag
3. ✅ `xwBackend` 所有单元测试通过
4. ✅ 现有 RAG 检索接口（`/api/rag/search`、`/api/rag/index/*`）行为不变
5. ✅ 新接口 `/api/rag/document/*` 4 个全部可调用
6. ✅ `rag_document` 表已在 MySQL 创建
7. ✅ xwBackend Docker 镜像加装了 tesseract-ocr + chi_sim
8. ✅ CLAUDE.md 更新无 xwRAG 引用
9. ✅ 主业务接口（uygur / extraction / fusion）回归通过

