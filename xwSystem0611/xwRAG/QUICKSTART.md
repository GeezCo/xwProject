# 快速开始指南

本指南帮助你快速启动RAG知识库系统的开发和测试。

## 前置条件

确保你的开发环境已安装：

- **JDK 17+**: `java -version`
- **Maven 3.8+**: `mvn -version`
- **Git**: `git --version`

## 快速启动

### 1. 克隆或进入项目目录

```bash
cd /Users/adam/Documents/IdeaProjects/xwRAG
```

### 2. 编译项目

```bash
mvn clean compile
```

预期输出：
```
[INFO] BUILD SUCCESS
```

### 3. 运行测试

```bash
mvn test
```

预期输出：
```
Tests run: 14, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

### 4. 打包应用

```bash
mvn clean package
```

生成的JAR文件位于：`target/rag-knowledge-base-1.0.0-SNAPSHOT.jar`

## 使用示例

### 示例1：解析纯文本DOCX

```java
@Autowired
private DocxParserService docxParserService;

// 解析DOCX文档
Document doc = docxParserService.parseSimpleDocx("/path/to/document.docx");

System.out.println("文档ID: " + doc.getId());
System.out.println("内容: " + doc.getContent());
System.out.println("来源: " + doc.getSource());
```

### 示例2：文档切片

```java
@Autowired
private DocumentChunkService chunkService;

// 对文档内容进行切片
String documentId = "doc_001";
String content = "这是一段需要切片的长文本内容...";
Map<String, Object> metadata = new HashMap<>();

List<DocumentChunk> chunks = chunkService.chunkDocument(documentId, content, metadata);

chunks.forEach(chunk -> {
    System.out.println("切片ID: " + chunk.getId());
    System.out.println("切片类型: " + chunk.getChunkType());
    System.out.println("内容: " + chunk.getContent());
});
```

### 示例3：解析表格

```java
@Autowired
private DocxTableParserService tableParserService;

// 解析DOCX中的所有表格
List<Document> tables = tableParserService.parseTablesFromDocx("/path/to/document.docx");

tables.forEach(table -> {
    System.out.println("表格索引: " + table.getMetadata().get("tableIndex"));
    System.out.println("表格内容(Markdown):\n" + table.getContent());
});
```

## 配置说明

### application.yml 配置

编辑 `src/main/resources/application.yml`：

```yaml
# 数据库配置
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/data_db
    username: your_username
    password: your_password

# Elasticsearch配置
elasticsearch:
  host: 192.168.57.10
  port: 9200
  username: elastic
  password: your_es_password

# Embedding服务配置
embedding:
  service:
    url: http://192.168.57.10:8000

# OCR配置
ocr:
  tesseract:
    datapath: /usr/share/tessdata
    language: chi_sim
```

### 切片策略配置

```yaml
document:
  parser:
    chunk-strategy:
      short-text-max-length: 256      # 短文本阈值
      medium-chunk-length: 512        # 中等文本切片长度
      medium-chunk-max-length: 768    # 中等文本上限
      long-chunk-length: 1024         # 长文本切片长度
      long-chunk-overlap: 128         # 长文本重叠长度
```

## 测试数据准备

### 准备DOCX测试文件

1. **纯文本文档** (simple/)
   - 放置2-3个纯文本DOCX文件
   - 路径：`src/main/resources/testdata/docx/simple/`

2. **包含表格的文档** (table/)
   - 放置2-3个包含表格的DOCX文件
   - 路径：`src/main/resources/testdata/docx/table/`

3. **图文混排文档** (mixed/)
   - 放置2-3个图文混排的DOCX文件
   - 路径：`src/main/resources/testdata/docx/mixed/`

详细说明见：`src/main/resources/testdata/README.md`

## 开发工具推荐

### IDE配置

**IntelliJ IDEA**:
1. 打开项目：File → Open → 选择项目目录
2. 等待Maven导入依赖
3. 安装Lombok插件：Settings → Plugins → 搜索"Lombok"
4. 启用注解处理：Settings → Build → Compiler → Annotation Processors → 勾选"Enable annotation processing"

**VS Code**:
1. 安装扩展：Java Extension Pack
2. 安装扩展：Spring Boot Extension Pack
3. 打开项目文件夹

### Maven命令速查

```bash
# 清理编译
mvn clean compile

# 运行测试
mvn test

# 运行单个测试类
mvn test -Dtest=DocxParserServiceTest

# 打包（跳过测试）
mvn package -DskipTests

# 运行应用
mvn spring-boot:run

# 查看依赖树
mvn dependency:tree
```

## 常见问题

### Q1: 测试时出现MySQL连接错误

**解决方案**: 测试类已配置禁用数据库自动配置，如果仍有问题，检查测试类是否添加了：

```java
@SpringBootTest(properties = {
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration"
})
```

### Q2: Lombok注解不生效

**解决方案**: 
1. 确保安装了Lombok插件
2. 启用注解处理器
3. 重启IDE

### Q3: 编码问题（中文乱码）

**解决方案**: 
1. 设置Maven编译编码：`pom.xml` 中已配置 `<project.build.sourceEncoding>UTF-8`
2. 设置IDE文件编码为UTF-8

### Q4: 无法创建测试DOCX文件

**解决方案**: 
- 检查Apache POI依赖是否正确导入
- 确保有写入临时目录的权限

## 项目结构说明

```
xwRAG/
├── src/main/java/           # Java源代码
│   └── com/intel/rag/
│       ├── config/          # 配置类
│       ├── model/           # 数据模型
│       ├── service/         # 业务服务
│       └── util/            # 工具类
├── src/main/resources/      # 资源文件
│   ├── application.yml      # 应用配置
│   └── testdata/            # 测试数据目录
├── src/test/java/           # 测试代码
├── pom.xml                  # Maven配置
├── README.md                # 项目说明
├── PROGRESS.md              # 开发进度
└── QUICKSTART.md            # 快速开始（本文档）
```

## 下一步

1. ✅ 阅读 `README.md` 了解项目详情
2. ✅ 查看 `PROGRESS.md` 了解开发进度
3. ✅ 准备测试数据（参考 `src/main/resources/testdata/README.md`）
4. 🔄 开始实现OCR功能（PR4）
5. 🔄 集成Embedding服务（PR5）

## 获取帮助

- 项目文档：`README.md`
- 开发进度：`PROGRESS.md`
- 测试数据说明：`src/main/resources/testdata/README.md`
- 技术研究文档：`.trellis/tasks/06-04-xinjiang-rag/research/`

---

**祝开发顺利！** 🚀
