# PR9: REST API接口实现

## 1. 需求概述

为RAG知识库系统提供完整的REST API接口，支持文档导入、检索查询、健康检查等功能，并提供Swagger API文档。

## 2. 功能目标

### 2.1 核心接口
- **文档导入接口**：支持DOCX文件上传和MySQL数据导入
- **检索查询接口**：支持混合检索、BM25检索、向量检索
- **健康检查接口**：检查系统各组件状态
- **API文档**：Swagger UI自动生成

### 2.2 非功能需求
- 统一的响应格式
- 完善的异常处理
- 请求参数校验
- 接口访问日志
- 跨域支持（CORS）

## 3. 技术设计

### 3.1 架构设计

```
REST API层（Controller）
    ↓
业务服务层（Service）
    ↓
数据访问层（Repository）
    ↓
外部服务（ES、Embedding、OCR）
```

### 3.2 接口设计

#### 3.2.1 文档导入接口

**上传DOCX文件**
```
POST /api/v1/documents/upload
Content-Type: multipart/form-data

Request:
- file: DOCX文件

Response:
{
  "code": 200,
  "message": "success",
  "data": {
    "documentId": "doc-uuid-xxx",
    "filename": "example.docx",
    "chunks": 15,
    "status": "success"
  }
}
```

**从MySQL导入**
```
POST /api/v1/documents/import/mysql

Request:
{
  "type": "all",           // all | byType | byIds
  "typeId": 1,             // 仅当type=byType时
  "ids": [188, 609, 1031]  // 仅当type=byIds时
}

Response:
{
  "code": 200,
  "message": "success",
  "data": {
    "totalRecords": 100,
    "successCount": 98,
    "failCount": 2,
    "elapsedTime": 12500
  }
}
```

#### 3.2.2 检索查询接口

**混合检索**
```
POST /api/v1/search

Request:
{
  "query": "阿里·萨利姆空军基地",
  "topK": 10,
  "searchType": "hybrid",  // hybrid | bm25 | vector
  "bm25Weight": 0.3,
  "vectorWeight": 0.7
}

Response:
{
  "code": 200,
  "message": "success",
  "data": {
    "query": "阿里·萨利姆空军基地",
    "total": 10,
    "results": [
      {
        "chunkId": "chunk-uuid-xxx",
        "documentId": "doc-uuid-xxx",
        "content": "阿里·萨利姆空军基地概况...",
        "score": 0.95,
        "bm25Score": 10.5,
        "vectorScore": 0.92,
        "rank": 1,
        "metadata": {
          "title": "...",
          "chunkIndex": 0
        }
      }
    ]
  }
}
```

#### 3.2.3 健康检查接口

**系统健康检查**
```
GET /api/v1/health

Response:
{
  "code": 200,
  "message": "success",
  "data": {
    "status": "UP",
    "components": {
      "elasticsearch": {
        "status": "UP",
        "host": "192.168.57.10:9200"
      },
      "embedding": {
        "status": "UP",
        "url": "http://192.168.57.10:8000"
      },
      "database": {
        "status": "UP",
        "driver": "MySQL"
      },
      "ocr": {
        "status": "UP",
        "tesseract": "5.x"
      }
    },
    "timestamp": "2026-06-05T16:45:00Z"
  }
}
```

### 3.3 统一响应格式

**ApiResponse**
```java
@Data
@Builder
public class ApiResponse<T> {
    private Integer code;       // 状态码
    private String message;     // 消息
    private T data;             // 数据
    private Long timestamp;     // 时间戳
    
    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .code(200)
                .message("success")
                .data(data)
                .timestamp(System.currentTimeMillis())
                .build();
    }
    
    public static <T> ApiResponse<T> error(Integer code, String message) {
        return ApiResponse.<T>builder()
                .code(code)
                .message(message)
                .timestamp(System.currentTimeMillis())
                .build();
    }
}
```

### 3.4 异常处理

**GlobalExceptionHandler**
```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    @ExceptionHandler(IllegalArgumentException.class)
    public ApiResponse<?> handleIllegalArgument(IllegalArgumentException e) {
        return ApiResponse.error(400, e.getMessage());
    }
    
    @ExceptionHandler(Exception.class)
    public ApiResponse<?> handleException(Exception e) {
        log.error("系统异常", e);
        return ApiResponse.error(500, "系统异常");
    }
}
```

### 3.5 Swagger配置

**添加依赖（pom.xml）**
```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.2.0</version>
</dependency>
```

**SwaggerConfig**
```java
@Configuration
public class SwaggerConfig {
    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("RAG知识库系统API")
                        .version("1.0.0")
                        .description("基于Elasticsearch和BGE的RAG检索系统"));
    }
}
```

## 4. 实现步骤

### Step 1: 创建响应模型
- `ApiResponse.java` - 统一响应格式
- `DocumentUploadResponse.java` - 文档上传响应
- `ImportResponse.java` - 数据导入响应
- `SearchResponse.java` - 检索响应
- `HealthCheckResponse.java` - 健康检查响应

### Step 2: 创建请求模型
- `DocumentUploadRequest.java` - 文档上传请求
- `MySQLImportRequest.java` - MySQL导入请求
- `SearchQueryRequest.java` - 检索请求

### Step 3: 实现Controller
- `DocumentController.java` - 文档导入接口
- `SearchController.java` - 检索查询接口
- `HealthController.java` - 健康检查接口

### Step 4: 异常处理和配置
- `GlobalExceptionHandler.java` - 全局异常处理
- `SwaggerConfig.java` - Swagger配置
- `CorsConfig.java` - 跨域配置

### Step 5: 集成测试
- `DocumentControllerTest.java`
- `SearchControllerTest.java`
- `HealthControllerTest.java`

## 5. 测试用例

### 5.1 文档上传测试
```java
@Test
void testUploadDocument() throws Exception {
    MockMultipartFile file = new MockMultipartFile(
        "file", "test.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "test content".getBytes());
    
    mockMvc.perform(multipart("/api/v1/documents/upload")
            .file(file))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.documentId").exists());
}
```

### 5.2 检索查询测试
```java
@Test
void testSearch() throws Exception {
    SearchQueryRequest request = new SearchQueryRequest();
    request.setQuery("测试查询");
    request.setTopK(10);
    request.setSearchType("hybrid");
    
    mockMvc.perform(post("/api/v1/search")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.results").isArray());
}
```

### 5.3 健康检查测试
```java
@Test
void testHealthCheck() throws Exception {
    mockMvc.perform(get("/api/v1/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.status").value("UP"));
}
```

## 6. API文档访问

启动应用后访问：
- Swagger UI: http://localhost:8080/api/swagger-ui.html
- API文档JSON: http://localhost:8080/api/v3/api-docs

## 7. 成功标准

- ✅ 所有REST接口实现完成
- ✅ 统一响应格式
- ✅ 完善的异常处理
- ✅ Swagger文档可访问
- ✅ 所有单元测试通过
- ✅ 集成测试通过

## 8. 依赖项

**新增依赖**:
- springdoc-openapi-starter-webmvc-ui（Swagger）
- spring-boot-starter-validation（参数校验）

**已有依赖**:
- spring-boot-starter-web
- 所有Service层（已完成）

## 9. 配置更新

**application.yml**:
```yaml
# API文档配置
springdoc:
  api-docs:
    path: /v3/api-docs
  swagger-ui:
    path: /swagger-ui.html
    enabled: true

# 文件上传配置
spring:
  servlet:
    multipart:
      max-file-size: 50MB
      max-request-size: 50MB
```

## 10. 后续优化

- 接口限流
- 接口鉴权（JWT）
- 请求日志记录
- 响应缓存
- 异步任务处理
- WebSocket实时通知
