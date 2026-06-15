# A1 + A2 + A3 三合一设计：融合详情导出 + Category 模块迁移

**主题**：xwBackend 后端三个并行需求的统一设计稿。

| 编号 | 简称 | 内容 |
|------|------|------|
| A1 | PDF 导出 | 「融合生成目标报」弹窗 → 后端生成正规 PDF 报告 |
| A2 | Word 导出 | 「融合生成目标报」弹窗 → 后端生成正规 .docx（替换前端伪装 .doc） |
| A3 | CategoryMapper 迁移 | 把 Category 相关 SQL 从 `UygurMapper` 拆为独立 `CategoryMapper` |

**统一原因**：A1 / A2 共享同一份融合详情数据 + 同一份导出文档结构；A3 是先行清理模块边界，让 A1/A2 在干净的代码上落地。

---

## 1. 背景与现状

### 1.1 数据源

`TargetFusionService.getFusionDetail(targetName)` 已实现（详见 `xwSystem/xwBackend/src/main/java/com/qy/dch/service/impl/TargetFusionServiceImpl.java`），返回结构：

```json
{
  "basicInfo": {
    "targetName": "哈尔科夫弹药库",
    "sourceCount": 3,
    "sources": [
      { "sendUnit": "HZ123报", "foundTime": "2024-01-01",
        "description": "...", "url": "http://minio/.../img.jpg" }
    ]
  },
  "fusionResult": {
    "analysis":            "综合描述...",
    "difference":          "变化分析...",
    "regionFusionResult":  "区域融合结果..."
  }
}
```

A1/A2 直接复用该方法的返回值。

### 1.2 前端现状

- `xwSystem/xwFrontend/frontend最新HTML/05-3-target-fusion.html:849` 有 `exportDocx()` 函数，使用 `Blob([html], { type: 'application/msword' })` 生成的是**HTML 文件改 .doc 后缀**（Word 能打开但不是真 .docx）。
- `03-search.html` 暂无导出按钮。

### 1.3 后端现状

- `pom.xml` 已含 `poi-ooxml 5.2.5`、`poi 5.2.5`，可直接生成 .docx。
- `pom.xml` **未含** PDF 库（无 iText、OpenPDF、PDFBox），需新增。
- `CategoryServiceImpl`（398 行）依赖 `UygurMapper`，Category 相关方法散落在 Uygur 通用 Mapper 中，违反模块边界。

### 1.4 用户已确认决策

| 决策点 | 选择 |
|--------|------|
| Word 真实性 | 真 .docx（Apache POI） |
| PDF 库 | iText 5.5.13.3 + 自带开源中文字体（Noto Sans SC） |
| A3 范围 | 创建独立 CategoryMapper，迁移所有 Category 方法 |
| 触发位置 | 仅在「融合生成目标报」弹窗 |
| 文档内容 | 完整报告（封面 + 基础信息 + 融合结果 + 图片嵌入） |
| 图片策略 | 尝试下载嵌入，失败则跳过保留 URL 链接 |
| 接口风格 | 独立端点 `GET /api/target/fusion/export?targetName=xxx&format=docx\|pdf` |

---

## 2. 总体架构

### 2.1 依赖关系

```
A3 先行（清理 Category 边界）
   ↓
A2 + A1 并行（共享 DocumentExportService）
   ├─ DocumentExportService 接口（定义 exportFusionAsDocx / exportFusionAsPdf）
   ├─ DocumentExportServiceImpl（POI + iText 双实现合一）
   ├─ TargetFusionController.export(...)（新接口）
   └─ 前端 05-3-target-fusion.html 调整（exportDocx/exportPdf 改为 GET URL 触发下载）
```

### 2.2 文件清单

**A3（CategoryMapper 迁移）**：

| 操作 | 文件 |
|------|------|
| 新建 | `xwSystem/xwBackend/src/main/java/com/qy/dch/mapper/CategoryMapper.java` |
| 修改 | `xwSystem/xwBackend/src/main/java/com/qy/dch/service/impl/CategoryServiceImpl.java` |
| 修改 | `xwSystem/xwBackend/src/main/java/com/qy/dch/mapper/UygurMapper.java` |
| 新建 | `xwSystem/xwBackend/src/test/java/com/qy/dch/mapper/CategoryMapperTest.java` |

**A2 + A1（文档导出）**：

| 操作 | 文件 |
|------|------|
| 新建 | `xwSystem/xwBackend/src/main/java/com/qy/dch/service/DocumentExportService.java` |
| 新建 | `xwSystem/xwBackend/src/main/java/com/qy/dch/service/impl/DocumentExportServiceImpl.java` |
| 新建 | `xwSystem/xwBackend/src/main/java/com/qy/dch/config/PdfFontProvider.java` |
| 修改 | `xwSystem/xwBackend/src/main/java/com/qy/dch/controller/TargetController.java` |
| 修改 | `xwSystem/xwBackend/pom.xml`（新增 itextpdf 5.5.13.3 + itext-asian 5.2.0） |
| 新建 | `xwSystem/xwBackend/src/main/resources/fonts/NotoSansSC-Regular.ttf` |
| 新建 | `xwSystem/xwBackend/src/test/java/com/qy/dch/service/DocumentExportServiceTest.java` |
| 新建 | `xwSystem/xwBackend/src/test/java/com/qy/dch/controller/TargetExportControllerTest.java` |
| 修改 | `xwSystem/xwFrontend/frontend最新HTML/05-3-target-fusion.html`（替换 exportDocx，新增 exportPdf 按钮） |

### 2.3 不改动的部分

- `TargetFusionService.getFusionDetail(...)`：保持原签名和返回结构。
- 数据库结构（无新表、无新字段）。
- `GlobalExceptionHandler`、`ResultVO`：沿用现有错误处理。
- 其他 Controller / Service 不受影响。

---

## 3. A3 — CategoryMapper 迁移

### 3.1 目标

把 `Category` 相关 SQL 从 `UygurMapper` 拆出，建立独立的 `CategoryMapper`。完成后：

- `CategoryServiceImpl` 仅注入 `CategoryMapper`，不再依赖 `UygurMapper`。
- `UygurMapper` 仅负责 `origin_text` 表，不再混入 Category 方法。

### 3.2 待迁移方法清单

迁移前先读取 `UygurMapper.java`，列出所有名称含 `Category` 的方法，全部搬入 `CategoryMapper`。已知应包含（实际以源码为准）：

- `selectAllCategories()` — 查所有节点
- `selectLeafCategories()` — 查所有叶子节点
- `selectCategoryById(String id)` — 按 ID 查
- `selectCategoryByName(String name)` — 按名称查（唯一性校验）
- `countReportsByCategory()` — 统计每节点报文数
- `insertCategory(Category)` — 新增
- `updateCategory(Category)` — 更新
- `deleteCategoryById(String id)` — 删除
- `selectChildrenByParentId(String parentId)` — 查子节点（用于级联）
- 任何 `findOrCreate*` / `selectByName*` 等其他 Category 相关方法

### 3.3 新建 `CategoryMapper`

- 风格：注解式（与 `TargetFusionMapper` 保持一致），用 `@Select` / `@Insert` / `@Update` / `@Delete`。
- 字段命名：SQL 严格 snake_case；Java 字段 camelCase；依赖 MyBatis 的 `map-underscore-to-camel-case=true` 自动映射（项目已开启）。
- 类注解：`@Mapper`，包路径 `com.qy.dch.mapper.CategoryMapper`。

### 3.4 迁移步骤（保证可回滚）

1. **复制阶段**：在 `CategoryMapper` 中重新声明每个方法，签名与 `UygurMapper` 一致；编译通过即可。
2. **切换阶段**：`CategoryServiceImpl` 把 `private final UygurMapper uygurMapper;` 改为 `private final CategoryMapper categoryMapper;`，调用点同步替换；run 单元测试与 `/api/category/*` 接口冒烟。
3. **清理阶段**：从 `UygurMapper` 删除已迁移的 Category 方法；再次跑测试确认无遗漏。

### 3.5 风险点

- `findOrCreateLeafBySendUnitName(...)` 涉及报文写入分类映射，确认仅查 / 写 `text_type` 表，不需要跨 Mapper。如有跨表事务，由 Service 层用 `@Transactional` 协调。
- 如存在 MyBatis XML（`resources/mapper/UygurMapper.xml`），同步迁移到 `resources/mapper/CategoryMapper.xml`。

### 3.6 验收标准

- `mvn test` 全绿，`CategoryMapperTest` 覆盖 8 个方法。
- 启动后访问下列接口结果与迁移前一致：
  - `GET /api/category/tree`
  - `GET /api/category/leafs`
  - `POST /api/category/create`
  - `PUT /api/category/update`
  - `POST /api/category/move`
  - `DELETE /api/category/delete/{id}`
  - `GET /api/category/detail/{id}`
- `UygurMapper.java` 中不再含名称包含 `Category` 的方法。

---

## 4. A2 — Word 导出（.docx）

### 4.1 接口

```
GET /api/target/fusion/export?targetName={name}&format=docx

Headers:
  Content-Type:        application/vnd.openxmlformats-officedocument.wordprocessingml.document
  Content-Disposition: attachment; filename*=UTF-8''<urlencoded>-融合报告-yyyyMMdd.docx

Body: 文件字节流（XWPFDocument）
```

### 4.2 文档结构

| 块 | 内容 | 字体 | 字号 |
|----|------|------|------|
| 封面 标题 | 「目标融合分析报告」 | 华文中宋（fallback 宋体） | 24pt 粗体 居中 |
| 封面 副标题 | targetName | 黑体 | 16pt 粗体 居中 |
| 封面 日期 | `yyyy 年 M 月 d 日` | 仿宋_GB2312 | 12pt 居中 |
| 分页 | `XWPFParagraph.setPageBreak(true)` | — | — |
| H1 一级标题 | 「目标基础信息」 | 黑体 | 16pt 粗体 |
| 表格 1 | 2 列：属性 / 值。行：目标名称、来源篇数 | 仿宋_GB2312 | 12pt |
| H2 二级标题 | 「来源列表」 | 黑体 | 14pt 粗体 |
| sources 循环 | 每条一段：sendUnit / foundTime / description / 图片或 URL | 仿宋_GB2312 | 12pt |
| H1 一级标题 | 「融合分析」 | 黑体 | 16pt 粗体 |
| H2 + 段落 | 「综合描述」 + analysis | 黑体 / 仿宋_GB2312 | 14pt / 12pt |
| H2 + 段落 | 「变化分析」 + difference | 黑体 / 仿宋_GB2312 | 14pt / 12pt |
| H2 + 段落 | 「区域融合结果」 + regionFusionResult | 黑体 / 仿宋_GB2312 | 14pt / 12pt |
| 页脚 | 「献微目标融合系统 — 第 X 页 / 共 Y 页」 | 仿宋_GB2312 | 10pt 居中 |

### 4.3 字体策略

- POI 写字体名（如 `仿宋_GB2312`），Word/WPS 渲染时自动按系统字体回退。
- 离线终端常见 Windows 客户端均自带这三种字体；缺失时 Word 自动回退到 `宋体`，可读性不受影响。
- 不嵌入字体到 .docx（避免文件膨胀）。

### 4.4 图片嵌入流程

```
1. 通过 RestTemplate.getForObject(url, byte[].class) 下载（超时 5s）
2. 用 XWPFDocument.createPicture(bytes, ...) 嵌入 PNG/JPEG
3. 控制宽度 ≤ 400px，等比缩放
4. 任何异常 → catch 后写入文字段落「[图片：${url}]」
5. 单图失败不阻塞整体，按顺序继续处理下一图
```

### 4.5 RestTemplate 配置

复用现有 `com.qy.dch.config.RestTemplateConfig`（如已存在），或在 `DocumentExportServiceImpl` 内构造一个独立的 `RestTemplate`（connectTimeout=2s，readTimeout=5s）。

### 4.6 服务接口

```java
public interface DocumentExportService {
    /** 生成融合详情 Word 文档（.docx）字节流；targetName 不存在时返回 null */
    byte[] exportFusionAsDocx(String targetName);

    /** 生成融合详情 PDF 文档字节流；targetName 不存在时返回 null */
    byte[] exportFusionAsPdf(String targetName);
}
```

`null` 由 Controller 翻译为 404 ResultVO。

---

## 5. A1 — PDF 导出

### 5.1 接口

```
GET /api/target/fusion/export?targetName={name}&format=pdf

Headers:
  Content-Type:        application/pdf
  Content-Disposition: attachment; filename*=UTF-8''<urlencoded>-融合报告-yyyyMMdd.pdf

Body: 文件字节流（com.itextpdf.text.Document）
```

### 5.2 字体处理（关键）

- 把 `NotoSansSC-Regular.ttf`（Google Noto，SIL OFL 协议，约 5MB）放入 `src/main/resources/fonts/`。
- 新建 Spring Bean `PdfFontProvider`，`@PostConstruct` 时一次性读 TTF 字节并构造字体常量，避免每次请求重新加载。
- 启动期失败 fail-fast：抛 `IllegalStateException` 阻断启动，提示 SugarDaddy 字体缺失。

```java
@Component
public class PdfFontProvider {
    private Font fontTitle, fontH1, fontH2, fontBody, fontFooter;

    @PostConstruct
    public void init() throws Exception {
        try (InputStream is = getClass().getResourceAsStream("/fonts/NotoSansSC-Regular.ttf")) {
            if (is == null) {
                throw new IllegalStateException(
                    "缺少字体文件 fonts/NotoSansSC-Regular.ttf，请检查打包");
            }
            byte[] fontBytes = IOUtils.toByteArray(is);
            BaseFont base = BaseFont.createFont(
                "NotoSansSC.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED,
                true, fontBytes, null);
            this.fontTitle  = new Font(base, 24, Font.BOLD);
            this.fontH1     = new Font(base, 16, Font.BOLD);
            this.fontH2     = new Font(base, 14, Font.BOLD);
            this.fontBody   = new Font(base, 12, Font.NORMAL);
            this.fontFooter = new Font(base, 10, Font.NORMAL);
        }
    }
    // 5 个字体的 getter
}
```

### 5.3 文档结构（与 Word 对齐）

复用 `getFusionDetail` 的同一份数据，按相同的章节组织：

```
封面页（PageSize.A4，居中）
  - Title「目标融合分析报告」     fontTitle
  - Subtitle: targetName          fontH1
  - 生成日期 yyyy年M月d日          fontBody
  - document.newPage()

正文页
  - PageEvent: 每页页眉「目标融合分析报告 - ${targetName}」、页脚「第 X 页 / 共 Y 页 - 献微目标融合系统」
  - H1「目标基础信息」              fontH1
  - PdfPTable 2 列（属性 / 值）：targetName、sourceCount
  - H2「来源列表」                 fontH2
  - PdfPTable 4 列（sendUnit / foundTime / description / 图片单元格）
  - H1「融合分析」                  fontH1
  - H2「综合描述」 + analysis       fontH2 / fontBody
  - H2「变化分析」 + difference     fontH2 / fontBody
  - H2「区域融合结果」 + regionFusionResult  fontH2 / fontBody
```

### 5.4 图片嵌入

```java
try {
    byte[] imgBytes = restTemplate.getForObject(url, byte[].class);
    Image img = Image.getInstance(imgBytes);
    img.scaleToFit(400, 300);
    cell.addElement(img);
} catch (Exception e) {
    log.warn("PDF 图片下载失败，保留 URL 链接: {}", url, e);
    cell.addElement(new Paragraph("[图片：" + url + "]", fontBody));
}
```

策略与 Word 完全一致。

### 5.5 pom.xml 新增依赖

```xml
<dependency>
    <groupId>com.itextpdf</groupId>
    <artifactId>itextpdf</artifactId>
    <version>5.5.13.3</version>
</dependency>
<dependency>
    <groupId>com.itextpdf</groupId>
    <artifactId>itext-asian</artifactId>
    <version>5.2.0</version>
</dependency>
```

### 5.6 协议合规

- iText 5.5.13.x：AGPL 协议，自托管 + 不分发源码可商用；项目内网部署不构成"分发"，符合公司内部使用场景。
- Noto Sans SC：SIL OFL 协议，免费商用且允许嵌入到 PDF。
- 决定：使用 iText 5.5.13.3 + Noto Sans SC 嵌入，不引入额外授权风险。

---

## 6. 接口规约（A1 + A2 共用）

### 6.1 端点

```
GET /api/target/fusion/export

Query 参数：
  - targetName  必填，String，目标名称（前端 URL 编码）
  - format      必填，String，取值 docx | pdf
```

### 6.2 成功响应

```
HTTP/1.1 200 OK
Content-Type:        application/vnd.openxmlformats-officedocument.wordprocessingml.document
                     | application/pdf
Content-Disposition: attachment; filename*=UTF-8''<urlencoded>-融合报告-yyyyMMdd.<docx|pdf>

<binary body>
```

### 6.3 失败响应

| 场景 | HTTP | Body |
|------|------|------|
| targetName 为空 | 200 | `{"code":400,"msg":"targetName 不能为空"}` |
| format 既不是 docx 也不是 pdf | 200 | `{"code":400,"msg":"format 仅支持 docx 或 pdf"}` |
| `getFusionDetail` 返回 null | 200 | `{"code":404,"msg":"目标不存在或无任何分析记录"}` |
| 文档生成异常（POI/iText IOException） | 200 | `{"code":500,"msg":"导出失败：xxx"}` |
| 字体加载失败（Bean 启动期） | 应用启动失败 | 控制台异常，不进入运行态 |

错误响应统一通过现有 `ResultVO` 风格返回 JSON（与其他接口保持一致），由 `GlobalExceptionHandler` 兜底。

### 6.4 Controller 契约

`TargetController.java` 新增方法：

```java
@GetMapping("/fusion/export")
public ResponseEntity<byte[]> exportFusionReport(
        @RequestParam("targetName") String targetName,
        @RequestParam("format") String format) {
    // 1. 校验 targetName / format（非法 → 抛 new BusinessException(ErrorCode.PARAM_INVALID, "msg")）
    // 2. 调用 documentExportService.exportFusionAsDocx 或 exportFusionAsPdf
    // 3. byte[] == null → 抛 new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "目标不存在或无任何分析记录")
    // 4. 组装 HttpHeaders（Content-Type + Content-Disposition）
    // 5. 返回 ResponseEntity<byte[]>
}
```

`BusinessException` 与 `ErrorCode` 已存在于 `com.qy.dch.common` 包，由 `GlobalExceptionHandler` 统一捕获并以 `ResultVO` 形式返回 JSON 错误体。

---

## 7. 前端调整

### 7.1 修改文件

`xwSystem/xwFrontend/frontend最新HTML/05-3-target-fusion.html`

### 7.2 替换 `exportDocx()` 函数

旧实现（约 849 行）：使用 Blob + HTML 伪造 .doc。
新实现：直接触发后端 GET 接口下载。

```javascript
function exportDocx(targetName){
  const url = '/api/target/fusion/export?targetName=' + encodeURIComponent(targetName) + '&format=docx';
  window.location.href = url;
}
function exportPdf(targetName){
  const url = '/api/target/fusion/export?targetName=' + encodeURIComponent(targetName) + '&format=pdf';
  window.location.href = url;
}
```

### 7.3 工具栏新增 PDF 按钮

约第 844 行 `bodyHtml += '<div class="toolbar">...'` 处：

```html
<div class="toolbar">
  <button class="btn" onclick="exportDocx('${targetName}')">📄 导出 docx</button>
  <button class="btn" onclick="exportPdf('${targetName}')">📕 导出 PDF</button>
  <button class="btn btn-default" onclick="window.close()">关闭</button>
</div>
```

注意：`targetName` 需在外层闭包传入 / 或写入 dataset，确保点击时能拿到当前目标名。

### 7.4 行为差异

- 旧：浏览器内合成 HTML，扩展名 `.doc`，体积 < 50KB
- 新：从后端下载真 .docx / .pdf；若后端报错，浏览器显示 JSON 错误体（前端不解析），SugarDaddy 可后续加 try/catch 包装

---

## 8. 测试策略

### 8.1 单元测试

**A. `CategoryMapperTest`**（A3）
- 路径：`src/test/java/com/qy/dch/mapper/CategoryMapperTest.java`
- 注解：`@SpringBootTest` + `@Transactional`（测试后回滚，不污染数据库）
- 覆盖方法：3.2 列出的 8 个方法各至少 1 个用例
- 断言：返回结果类型、记录数、字段值

**B. `DocumentExportServiceTest`**（A2/A1）
- 路径：`src/test/java/com/qy/dch/service/DocumentExportServiceTest.java`
- Mock `TargetFusionService` 返回三种数据：
  1. 完整数据（basicInfo + fusionResult 都有）
  2. 仅 basicInfo（fusionResult 三字段为空字符串）
  3. null（目标不存在）
- 用例：
  - `exportFusionAsDocx_full_returnsValidDocx()` — byte[] 不为空，可被 `XWPFDocument(new ByteArrayInputStream(bytes))` 重新打开，含目标名段落
  - `exportFusionAsDocx_emptyFusion_stillSucceeds()` — fusion 为空时仍生成文档
  - `exportFusionAsDocx_targetNotFound_returnsNull()` — Mock 返回 null 时方法返回 null
  - `exportFusionAsPdf_full_returnsValidPdf()` — 用 `PdfReader(bytes)` 重新打开，验证页数 ≥ 2
  - `exportFusionAsPdf_imageDownloadFails_fallsBackToText()` — Mock RestTemplate 抛异常，断言 PDF 仍生成

**C. `TargetExportControllerTest`**（A1/A2）
- 路径：`src/test/java/com/qy/dch/controller/TargetExportControllerTest.java`
- 用 `MockMvc`：
  - `GET /api/target/fusion/export?targetName=test&format=docx` → 200 + Content-Type 正确 + 非空 body
  - `GET .../format=pdf` → 200 + application/pdf
  - `GET .../format=invalid` → 200 + JSON `{code:400,...}`
  - `GET .../targetName=空` → 200 + JSON `{code:400,...}`
  - `GET .../targetName=不存在` → 200 + JSON `{code:404,...}`

### 8.2 手动验收

1. 启动后端：`./mvnw spring-boot:run`
2. 浏览器访问：
   - `http://localhost:8081/api/target/fusion/export?targetName=哈尔科夫弹药库&format=docx` → 浏览器触发下载
   - 同 URL 但 `format=pdf` → 触发 PDF 下载
3. 用 Word/WPS 打开 .docx：
   - 中文显示正常（封面、表格、段落）
   - 图片或 URL 链接位置正确
4. 用 Adobe Reader / Foxit 打开 .pdf：
   - 中文显示正常（无方块/乱码）
   - 页眉页脚、页码正常
5. 前端 `05-3-target-fusion.html` 弹窗点击「📄 导出 docx」「📕 导出 PDF」按钮，浏览器开始下载
6. 验证 Category 接口未受影响：`GET /api/category/tree` 返回与迁移前一致

---

## 9. 验收标准

- [ ] `mvn test` 全绿
- [ ] `UygurMapper.java` 不再含名称含 `Category` 的方法
- [ ] `CategoryServiceImpl` 仅依赖 `CategoryMapper`，不再依赖 `UygurMapper`
- [ ] `pom.xml` 新增 itextpdf 5.5.13.3 + itext-asian 5.2.0
- [ ] `src/main/resources/fonts/NotoSansSC-Regular.ttf` 存在且打包进 jar
- [ ] `GET /api/target/fusion/export?format=docx` 返回真 .docx 字节流
- [ ] `GET /api/target/fusion/export?format=pdf` 返回真 PDF 字节流
- [ ] 中文字符在两种格式下都正确显示
- [ ] 图片下载失败时文档仍能生成（保留 URL 文字）
- [ ] 前端按钮触发下载流程通畅
- [ ] `docs/xwBackend-complete-reference.md` 中 §10.10 接口表新增 `/fusion/export` 行
- [ ] `docs/xwBackend-complete-reference.md` 中 §17.2 增加导出说明
- [ ] 全部任务一次性 commit（不写 Co-Authored-By 行）

---

## 10. 风险与缓解

| 风险 | 等级 | 缓解 |
|------|------|------|
| iText 5 字体在某些 JDK 版本上报 NullPointerException | 中 | 用单例 PdfFontProvider；启动期加载，运行期复用，避免并发竞态 |
| 大字体 TTF 让 jar 体积增加 ~5MB | 低 | 接受；离线部署对体积不敏感 |
| MinIO 图片下载阻塞导出请求 | 中 | RestTemplate connect=2s, read=5s；单图失败不阻塞整体 |
| Category 方法迁移遗漏 | 中 | 三阶段提交（复制 → 切换 → 清理），每阶段跑测试 |
| 前端按钮 targetName 闭包问题 | 低 | 通过 dataset 或显式参数传递；测试时确认弹窗中点击的目标名正确 |
| iText 5 AGPL 协议 | 低 | 内部部署不构成 AGPL 意义上的"分发"，符合公司内网使用场景 |

---

## 11. 实施顺序

按依赖与风险递增排序：

1. **A3 — CategoryMapper 迁移**（独立，不影响其他功能）
2. **基础设施**：pom.xml 加依赖 + 字体文件 + PdfFontProvider
3. **A2 — Word 导出**（POI 已有依赖，先验证基础流程）
4. **A1 — PDF 导出**（在 Word 跑通后复用同一份数据结构）
5. **Controller 接入 + 前端调整**
6. **文档同步**：更新 `xwBackend-complete-reference.md` §10.10 / §17.2

---

> 本设计稿是 A1 + A2 + A3 三个并行需求的统一权威说明。实施时按 §11 的顺序进行，每完成一项 commit 一次，不要混合提交。
