# DOCX解析库对比：Apache POI vs docx4j

## 执行摘要

**推荐选择：Apache POI**

Apache POI更适合本项目，因为它在表格提取、社区活跃度和Spring Boot集成方面表现更好，尽管docx4j在某些高级场景下有优势。

## 1. 表格提取能力

### Apache POI
- ✅ **优秀的表格支持**：`XWPFTable` API完善
- ✅ 可以轻松遍历行、列、单元格
- ✅ 支持合并单元格检测
- ✅ 可以获取表格样式信息
- 示例代码：
```java
XWPFDocument doc = new XWPFDocument(new FileInputStream("file.docx"));
for (XWPFTable table : doc.getTables()) {
    for (XWPFTableRow row : table.getRows()) {
        for (XWPFTableCell cell : row.getTableCells()) {
            String text = cell.getText();
        }
    }
}
```

### docx4j
- ✅ 支持表格解析，但API更复杂
- ✅ 使用JAXB对象模型，更接近OOXML标准
- ⚠️ 学习曲线陡峭
- 示例代码：
```java
WordprocessingMLPackage wordMLPackage = WordprocessingMLPackage.load(new File("file.docx"));
MainDocumentPart documentPart = wordMLPackage.getMainDocumentPart();
List<Object> tables = documentPart.getContent(); // 需要递归遍历查找Tbl对象
```

**结论**：Apache POI的表格API更直观易用。

## 2. 图片提取支持

### Apache POI
- ✅ **直接支持图片提取**
- ✅ `XWPFPictureData` 可获取图片字节流、格式、尺寸
- ✅ 可以定位图片在文档中的位置
- 示例代码：
```java
List<XWPFPictureData> pictures = doc.getAllPictures();
for (XWPFPictureData picture : pictures) {
    byte[] imageData = picture.getData();
    String ext = picture.getPackagePart().getPartName().getExtension();
    // 保存图片或进行OCR
}
```

### docx4j
- ✅ 支持图片提取，功能更全面
- ✅ 可以获取更详细的图片元数据（位置、锚点、包裹方式）
- ⚠️ API复杂，需要理解OOXML结构
- 示例代码：
```java
List<Object> images = documentPart.getJAXBNodesViaXPath("//w:drawing", false);
// 需要手动解析复杂的JAXB对象
```

**结论**：Apache POI的图片提取API更简单，满足OCR需求。

## 3. 性能对比

### Apache POI
- ⚠️ 内存占用较高（需要加载整个文档到内存）
- ⚠️ 大文件（>100MB）可能导致OOM
- ✅ 提供事件驱动API（SAX模式）用于大文件：`XWPFEventBasedWordExtractor`
- ✅ 处理速度快（对于中小型文件）

### docx4j
- ✅ 支持流式处理，内存占用更低
- ✅ 适合处理超大文件
- ⚠️ 初始化开销大（加载JAXB上下文）
- ⚠️ 复杂文档解析速度慢于POI

**结论**：对于本项目（预计单个DOCX <10MB），Apache POI性能足够。

## 4. 社区活跃度与维护状态

### Apache POI
- ✅ **Apache基金会项目**，维护活跃
- ✅ 最新版本：5.2.5（2023年12月）
- ✅ GitHub Stars: 2.1k+
- ✅ 大量中文教程和StackOverflow问答
- ✅ 长期支持，Bug修复快

### docx4j
- ✅ 开源项目，维护正常
- ⚠️ 更新频率低于POI
- ⚠️ GitHub Stars: 1.3k+
- ⚠️ 中文资料较少
- ⚠️ 社区规模小

**结论**：Apache POI社区更活跃，遇到问题更容易找到解决方案。

## 5. Spring Boot集成

### Apache POI
- ✅ **开箱即用**，无需额外配置
- ✅ Maven依赖简单：
```xml
<dependency>
    <groupId>org.apache.poi</groupId>
    <artifactId>poi-ooxml</artifactId>
    <version>5.2.5</version>
</dependency>
```
- ✅ 与Spring生态集成良好（如Spring Batch）

### docx4j
- ✅ 可以集成到Spring Boot
- ⚠️ 依赖复杂（需要JAXB、MOXy等）
- ⚠️ Java 9+需要额外配置JAXB模块
- Maven依赖：
```xml
<dependency>
    <groupId>org.docx4j</groupId>
    <artifactId>docx4j-JAXB-ReferenceImpl</artifactId>
    <version>11.4.9</version>
</dependency>
```

**结论**：Apache POI集成更简单，减少配置复杂度。

## 最终推荐

| 维度 | Apache POI | docx4j | 推荐 |
|------|-----------|--------|------|
| 表格提取 | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ | POI |
| 图片提取 | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ | POI |
| 性能（中小文件） | ⭐⭐⭐⭐⭐ | ⭐⭐⭐ | POI |
| 社区支持 | ⭐⭐⭐⭐⭐ | ⭐⭐⭐ | POI |
| Spring集成 | ⭐⭐⭐⭐⭐ | ⭐⭐⭐ | POI |
| 学习曲线 | ⭐⭐⭐⭐ | ⭐⭐ | POI |

**结论**：选择 **Apache POI 5.2.5** 作为DOCX解析库。

## 实施建议

1. 使用 `poi-ooxml` 依赖（包含OOXML格式支持）
2. 对于超大文件（>100MB），后续可考虑切换到事件驱动API
3. 结合 `poi-scratchpad` 处理旧版DOC格式（如需要）
4. 注意内存管理，处理完后及时关闭 `XWPFDocument` 对象
