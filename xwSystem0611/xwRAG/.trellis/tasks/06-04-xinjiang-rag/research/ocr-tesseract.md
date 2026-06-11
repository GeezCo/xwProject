# Tesseract OCR集成指南（Tess4j）

## 执行摘要

Tess4j是Tesseract OCR的Java封装，适合本地部署的OCR方案。对于军用情报场景，本地OCR确保数据安全，无需依赖云服务。

## 1. 中文语言支持（chi_sim配置）

### Tesseract语言包下载

Tesseract需要单独的语言数据文件（traineddata）：

```bash
# 下载中文简体语言包
wget https://github.com/tesseract-ocr/tessdata/raw/main/chi_sim.traineddata

# 或使用快速版本（速度更快，精度略低）
wget https://github.com/tesseract-ocr/tessdata_fast/raw/main/chi_sim.traineddata

# 或使用最佳版本（精度最高，速度最慢）
wget https://github.com/tesseract-ocr/tessdata_best/raw/main/chi_sim.traineddata
```

**推荐**：使用 `tessdata_fast` 版本（平衡速度和精度）

### Java配置（Tess4j）

```java
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;

public class OCRService {
    private final Tesseract tesseract;
    
    public OCRService() {
        tesseract = new Tesseract();
        // 设置语言包路径
        tesseract.setDatapath("/usr/share/tessdata");
        // 设置识别语言（中文简体）
        tesseract.setLanguage("chi_sim");
        // 设置页面分割模式（PSM）
        tesseract.setPageSegMode(1); // 1 = Automatic page segmentation with OSD
        // 设置OCR引擎模式（OEM）
        tesseract.setOcrEngineMode(1); // 1 = Neural nets LSTM engine only
    }
    
    public String recognize(File imageFile) throws TesseractException {
        return tesseract.doOCR(imageFile);
    }
}
```

### 关键配置参数

| 参数 | 说明 | 推荐值 |
|------|------|--------|
| `language` | 识别语言 | `chi_sim`（中文简体） |
| `PageSegMode` | 页面分割模式 | `1`（自动分割）或`3`（全自动） |
| `OcrEngineMode` | OCR引擎模式 | `1`（LSTM神经网络） |

## 2. 性能优化技术

### 2.1 图片预处理

OCR识别率和速度受图片质量影响极大，预处理可显著提升效果：

```java
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;

public class ImagePreprocessor {
    
    // 灰度化
    public Mat toGrayscale(Mat image) {
        Mat gray = new Mat();
        Imgproc.cvtColor(image, gray, Imgproc.COLOR_BGR2GRAY);
        return gray;
    }
    
    // 二值化（阈值处理）
    public Mat binarize(Mat gray) {
        Mat binary = new Mat();
        Imgproc.threshold(gray, binary, 0, 255, 
            Imgproc.THRESH_BINARY | Imgproc.THRESH_OTSU);
        return binary;
    }
    
    // 降噪（中值滤波）
    public Mat denoise(Mat image) {
        Mat denoised = new Mat();
        Imgproc.medianBlur(image, denoised, 3);
        return denoised;
    }
    
    // 倾斜校正
    public Mat deskew(Mat image) {
        // 使用霍夫变换检测倾斜角度
        // 旋转校正
        // （实现略复杂，可使用OpenCV）
        return image;
    }
    
    // 完整预处理流程
    public Mat preprocess(Mat original) {
        Mat gray = toGrayscale(original);
        Mat denoised = denoise(gray);
        Mat binary = binarize(denoised);
        return binary;
    }
}
```

### 2.2 多线程并行处理

```java
import java.util.concurrent.*;
import java.util.List;
import java.util.ArrayList;

public class ParallelOCRService {
    private final ExecutorService executor;
    private final int threadCount;
    
    public ParallelOCRService(int threadCount) {
        this.threadCount = threadCount;
        this.executor = Executors.newFixedThreadPool(threadCount);
    }
    
    public List<String> recognizeBatch(List<File> images) throws InterruptedException {
        List<Future<String>> futures = new ArrayList<>();
        
        for (File image : images) {
            Future<String> future = executor.submit(() -> {
                Tesseract tesseract = createTesseractInstance();
                return tesseract.doOCR(image);
            });
            futures.add(future);
        }
        
        List<String> results = new ArrayList<>();
        for (Future<String> future : futures) {
            try {
                results.add(future.get());
            } catch (ExecutionException e) {
                results.add(""); // 识别失败
            }
        }
        return results;
    }
    
    private Tesseract createTesseractInstance() {
        // 每个线程创建独立的Tesseract实例（非线程安全）
        Tesseract tesseract = new Tesseract();
        tesseract.setDatapath("/usr/share/tessdata");
        tesseract.setLanguage("chi_sim");
        return tesseract;
    }
    
    public void shutdown() {
        executor.shutdown();
    }
}
```

### 2.3 内存管理

```java
public class OptimizedOCRService {
    
    // 使用BufferedImage池避免频繁创建对象
    private final BlockingQueue<BufferedImage> imagePool;
    
    public OptimizedOCRService(int poolSize) {
        imagePool = new ArrayBlockingQueue<>(poolSize);
        for (int i = 0; i < poolSize; i++) {
            imagePool.offer(new BufferedImage(1024, 1024, BufferedImage.TYPE_BYTE_GRAY));
        }
    }
    
    public String recognizeOptimized(File imageFile) throws Exception {
        BufferedImage pooledImage = imagePool.take();
        try {
            BufferedImage actualImage = ImageIO.read(imageFile);
            // 使用pooledImage进行处理
            Tesseract tesseract = new Tesseract();
            tesseract.setDatapath("/usr/share/tessdata");
            tesseract.setLanguage("chi_sim");
            return tesseract.doOCR(actualImage);
        } finally {
            imagePool.offer(pooledImage); // 归还到池
        }
    }
}
```

## 3. 批量处理方案

### 3.1 流式批处理

```java
import java.util.stream.Stream;

public class StreamOCRProcessor {
    
    public void processBatch(List<File> images, Consumer<OCRResult> resultHandler) {
        images.parallelStream()
              .map(this::recognizeWithMetadata)
              .forEach(resultHandler);
    }
    
    private OCRResult recognizeWithMetadata(File image) {
        try {
            Tesseract tesseract = new Tesseract();
            tesseract.setDatapath("/usr/share/tessdata");
            tesseract.setLanguage("chi_sim");
            
            long startTime = System.currentTimeMillis();
            String text = tesseract.doOCR(image);
            long duration = System.currentTimeMillis() - startTime;
            
            return new OCRResult(image.getName(), text, duration, true);
        } catch (Exception e) {
            return new OCRResult(image.getName(), "", 0, false);
        }
    }
}

class OCRResult {
    String fileName;
    String text;
    long durationMs;
    boolean success;
    
    // 构造函数和getter省略
}
```

### 3.2 错误处理与重试

```java
public class ResilientOCRService {
    private final int maxRetries = 3;
    
    public String recognizeWithRetry(File image) {
        int attempt = 0;
        Exception lastException = null;
        
        while (attempt < maxRetries) {
            try {
                return doOCR(image);
            } catch (Exception e) {
                lastException = e;
                attempt++;
                try {
                    Thread.sleep(1000 * attempt); // 指数退避
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        
        // 所有重试失败，记录日志
        log.error("OCR failed after {} attempts: {}", maxRetries, image.getName(), lastException);
        return ""; // 返回空字符串或抛出异常
    }
}
```

## 4. Docker部署考虑

### 4.1 Dockerfile

```dockerfile
FROM openjdk:17-slim

# 安装Tesseract和中文语言包
RUN apt-get update && apt-get install -y \
    tesseract-ocr \
    tesseract-ocr-chi-sim \
    libtesseract-dev \
    libleptonica-dev \
    && rm -rf /var/lib/apt/lists/*

# 验证Tesseract安装
RUN tesseract --version

# 设置语言包路径
ENV TESSDATA_PREFIX=/usr/share/tesseract-ocr/4.00/tessdata

# 复制应用
COPY target/rag-app.jar /app/app.jar

WORKDIR /app
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### 4.2 Docker Compose配置

```yaml
version: '3.8'

services:
  rag-app:
    build: .
    environment:
      - TESSDATA_PREFIX=/usr/share/tesseract-ocr/4.00/tessdata
      - TESSERACT_THREADS=4
    volumes:
      - ./data:/app/data
      - ./tessdata:/usr/share/tessdata:ro  # 自定义语言包
    mem_limit: 4g
    cpus: 2.0
```

### 4.3 资源限制建议

| 资源 | 推荐值 | 说明 |
|------|--------|------|
| 内存 | 2-4GB | OCR内存占用较高 |
| CPU | 2-4核 | 并行处理需要多核 |
| 磁盘 | 10GB+ | 存储临时图片和语言包 |

## 5. 常见问题与解决方案

### 问题1：中文识别率低

**原因**：
- 图片质量差（模糊、倾斜、噪点）
- 字体特殊（艺术字、手写体）
- 语言包版本不匹配

**解决方案**：
- 使用图片预处理（灰度化、二值化、降噪）
- 尝试 `tessdata_best` 语言包（精度更高）
- 设置正确的PSM模式：
  ```java
  tesseract.setPageSegMode(6); // 6 = Assume a single uniform block of text
  ```

### 问题2：内存溢出（OOM）

**原因**：
- 大图片占用内存过多
- 并发线程过多
- Tesseract实例未释放

**解决方案**：
- 限制图片尺寸（缩放到合理大小）：
  ```java
  BufferedImage resized = Scalr.resize(original, 2000); // 最大宽度2000px
  ```
- 控制并发线程数（不超过CPU核心数）
- 确保及时释放资源

### 问题3：识别速度慢

**原因**：
- 图片分辨率过高
- 使用 `tessdata_best` 语言包
- PSM模式不合适

**解决方案**：
- 降低图片分辨率（DPI 300已足够）
- 使用 `tessdata_fast` 语言包
- 设置合适的PSM模式（避免不必要的页面分析）

### 问题4：Docker容器中Tesseract找不到

**原因**：
- 语言包路径配置错误
- Tesseract未正确安装

**解决方案**：
- 在Dockerfile中验证安装：
  ```dockerfile
  RUN tesseract --list-langs  # 应显示chi_sim
  ```
- 正确设置环境变量：
  ```java
  tesseract.setDatapath(System.getenv("TESSDATA_PREFIX"));
  ```

### 问题5：特殊字符识别错误

**原因**：
- 字符集配置不完整
- 语言包版本问题

**解决方案**：
- 使用UTF-8编码
- 配置白名单/黑名单字符：
  ```java
  tesseract.setTessVariable("tessedit_char_whitelist", "0123456789ABCDEFabcdef");
  ```

### 问题6：多线程并发错误

**原因**：
- Tesseract实例非线程安全
- 共享同一个实例导致冲突

**解决方案**：
- 每个线程创建独立的Tesseract实例：
  ```java
  private static final ThreadLocal<Tesseract> tesseractThreadLocal = 
      ThreadLocal.withInitial(() -> {
          Tesseract t = new Tesseract();
          t.setDatapath("/usr/share/tessdata");
          t.setLanguage("chi_sim");
          return t;
      });
  ```

## 最终建议

1. **语言包选择**：使用 `tessdata_fast/chi_sim.traineddata`（平衡速度和精度）
2. **预处理**：灰度化 + 二值化 + 降噪（必选）
3. **并发处理**：线程数 = CPU核心数，每线程独立Tesseract实例
4. **Docker部署**：预装Tesseract，挂载自定义语言包
5. **性能监控**：记录每张图片的识别时间，识别慢的图片单独处理
6. **错误处理**：重试机制 + 降级策略（识别失败则跳过）

## Maven依赖

```xml
<dependency>
    <groupId>net.sourceforge.tess4j</groupId>
    <artifactId>tess4j</artifactId>
    <version>5.9.0</version>
</dependency>

<!-- 图片预处理（可选） -->
<dependency>
    <groupId>org.imgscalr</groupId>
    <artifactId>imgscalr-lib</artifactId>
    <version>4.2</version>
</dependency>
```
