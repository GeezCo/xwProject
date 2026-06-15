# xwBackend 全面重构 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 xwBackend 重构为分层清晰、模块化、测试友好的代码库，覆盖 Mapper 拆分、统一 HTTP 客户端、路径规范化、异常体系、SQL 迁移五个模块。

**Architecture:** 严格三层（Controller → Service → Mapper/AlgorithmClient），Service 不返回 ResultVO，构造器注入统一为 `@RequiredArgsConstructor`，异常由 GlobalExceptionHandler 统一处理。

**Tech Stack:** Spring Boot 2.7.18、JDK 8、MyBatis-Plus 3.5.5、Lombok、JUnit 5 + Mockito、Bean Validation。

**Spec 参考：** `docs/superpowers/specs/2026-06-15-xwBackend-refactor-design.md`

---

## 工作目录约定

- **项目根**：`/Users/processmonitor/Documents/IdeaProject/xwProject/xwSystem/xwBackend/`
- **包根**：`src/main/java/com/qy/dch/`
- **资源根**：`src/main/resources/`
- **测试根**：`src/test/java/com/qy/dch/`
- **JAVA_HOME**：执行任何 mvnw 前先 `export JAVA_HOME=/Users/processmonitor/Documents/jdk/jdk8/Contents/Home`

## 编译验证命令（每个 Task 完成后运行）

```bash
cd /Users/processmonitor/Documents/IdeaProject/xwProject/xwSystem/xwBackend
export JAVA_HOME=/Users/processmonitor/Documents/jdk/jdk8/Contents/Home
./mvnw clean compile 2>&1 | tail -10
```

预期：`BUILD SUCCESS`

---

# 第一波：基础设施（ErrorCode、异常、GlobalExceptionHandler）

## Task 1: 扩充 ErrorCode 枚举

**Files:**
- Modify: `src/main/java/com/qy/dch/common/ErrorCode.java`

- [ ] **Step 1: 阅读现有 ErrorCode**

Read 当前 `src/main/java/com/qy/dch/common/ErrorCode.java`，确认现有 11 个枚举值。

- [ ] **Step 2: 追加新枚举**

把 ErrorCode.java 修改为：

```java
package com.qy.dch.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ErrorCode {

    SUCCESS(0, "success"),
    SYSTEM_ERROR(1001, "系统内部错误"),
    PARAM_INVALID(1002, "参数校验失败"),
    FORBIDDEN(1003, "禁止访问"),

    RESOURCE_NOT_FOUND(2001, "资源不存在"),
    DUPLICATE_KEY(2002, "数据重复"),

    // 报文相关
    ORIGIN_TEXT_NOT_FOUND(2101, "报文不存在"),
    ORIGIN_TEXT_ALREADY_EXTRACTED(2102, "报文已抽取"),

    // 分类相关
    CATEGORY_NOT_FOUND(2201, "分类不存在"),
    CATEGORY_HAS_CHILDREN(2202, "分类下存在子节点，无法删除"),
    CATEGORY_NAME_DUPLICATE(2203, "分类名称已存在"),

    // 导入相关
    IMPORT_FILE_EMPTY(2301, "导入文件不能为空"),
    IMPORT_FILE_FORMAT_ERROR(2302, "文件格式错误"),

    RAG_INDEXING_RUNNING(3001, "索引任务正在执行中"),
    RAG_SEARCH_FAILED(3002, "语义检索失败"),

    DB_TABLE_NOT_ALLOWED(4001, "不允许操作的表"),
    DB_FIELD_PROTECTED(4002, "核心字段禁止修改"),
    DB_DDL_FAILED(4003, "DDL 执行失败"),

    // 算法服务
    ALGORITHM_SERVICE_UNAVAILABLE(5001, "算法服务不可用"),
    ALGORITHM_SERVICE_TIMEOUT(5002, "算法服务超时"),
    ALGORITHM_SERVICE_INVALID_RESPONSE(5003, "算法服务返回数据异常");

    private final int code;
    private final String message;
}
```

- [ ] **Step 3: 编译验证**

```bash
./mvnw clean compile 2>&1 | tail -5
```

预期：`BUILD SUCCESS`

- [ ] **Step 4: 提交**

```bash
git add src/main/java/com/qy/dch/common/ErrorCode.java
git commit -m "feat(common): expand ErrorCode with business and algorithm codes"
```

---

## Task 2: 新增 AlgorithmServiceException

**Files:**
- Create: `src/main/java/com/qy/dch/common/AlgorithmServiceException.java`

- [ ] **Step 1: 创建异常类**

```java
package com.qy.dch.common;

/**
 * 算法服务调用异常
 * <p>
 * 用于封装算法服务（Python LLM 服务）调用过程中出现的所有失败：
 * HTTP 错误、超时、返回数据格式异常等
 * </p>
 */
public class AlgorithmServiceException extends BusinessException {

    public AlgorithmServiceException(ErrorCode errorCode) {
        super(errorCode);
    }

    public AlgorithmServiceException(ErrorCode errorCode, String customMessage) {
        super(errorCode, customMessage);
    }

    public AlgorithmServiceException(ErrorCode errorCode, String customMessage, Throwable cause) {
        super(errorCode, customMessage);
        if (cause != null) {
            initCause(cause);
        }
    }
}
```

- [ ] **Step 2: 编译验证**

```bash
./mvnw clean compile 2>&1 | tail -5
```

预期：`BUILD SUCCESS`

- [ ] **Step 3: 提交**

```bash
git add src/main/java/com/qy/dch/common/AlgorithmServiceException.java
git commit -m "feat(common): add AlgorithmServiceException"
```

---

## Task 3: 扩展 GlobalExceptionHandler

**Files:**
- Modify: `src/main/java/com/qy/dch/config/GlobalExceptionHandler.java`

- [ ] **Step 1: 阅读现有 GlobalExceptionHandler**

Read `src/main/java/com/qy/dch/config/GlobalExceptionHandler.java`，记录已有的 @ExceptionHandler 方法。

- [ ] **Step 2: 重写 GlobalExceptionHandler**

```java
package com.qy.dch.config;

import com.qy.dch.common.AlgorithmServiceException;
import com.qy.dch.common.BusinessException;
import com.qy.dch.common.ErrorCode;
import com.qy.dch.common.ResultVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import javax.validation.ConstraintViolationException;
import java.util.stream.Collectors;

/**
 * 全局异常处理器
 * 所有 Controller 抛出的异常在此统一拦截，转换为 ResultVO 返回
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 算法服务异常 */
    @ExceptionHandler(AlgorithmServiceException.class)
    public ResultVO handleAlgorithm(AlgorithmServiceException e) {
        log.warn("算法服务异常: code={}, msg={}", e.getErrorCode().getCode(), e.getMessage());
        return ResultVO.error(e.getMessage());
    }

    /** 业务异常 */
    @ExceptionHandler(BusinessException.class)
    public ResultVO handleBusiness(BusinessException e) {
        log.warn("业务异常: code={}, msg={}", e.getErrorCode().getCode(), e.getMessage());
        return ResultVO.bizFail(e.getMessage());
    }

    /** @Valid 校验失败 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResultVO handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败: {}", message);
        return ResultVO.bizFail(ErrorCode.PARAM_INVALID.getMessage() + ": " + message);
    }

    /** @PathVariable / @RequestParam 校验失败 */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResultVO handleConstraintViolation(ConstraintViolationException e) {
        log.warn("参数校验失败: {}", e.getMessage());
        return ResultVO.bizFail(ErrorCode.PARAM_INVALID.getMessage() + ": " + e.getMessage());
    }

    /** 缺必填参数 */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResultVO handleMissingParam(MissingServletRequestParameterException e) {
        log.warn("缺少必填参数: {}", e.getParameterName());
        return ResultVO.bizFail("缺少必填参数: " + e.getParameterName());
    }

    /** 文件超限 */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResultVO handleUpload(MaxUploadSizeExceededException e) {
        log.warn("上传文件超限: {}", e.getMessage());
        return ResultVO.bizFail("文件大小超过限制");
    }

    /** 数据库唯一约束等 */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResultVO handleDataIntegrity(DataIntegrityViolationException e) {
        log.warn("数据完整性约束违反: {}", e.getMostSpecificCause().getMessage());
        return ResultVO.bizFail("数据冲突: " + e.getMostSpecificCause().getMessage());
    }

    /** 兜底异常处理 */
    @ExceptionHandler(Exception.class)
    public ResultVO handleAll(Exception e) {
        log.error("未捕获异常", e);
        return ResultVO.error(ErrorCode.SYSTEM_ERROR.getMessage());
    }
}
```

- [ ] **Step 3: 编译验证**

```bash
./mvnw clean compile 2>&1 | tail -5
```

预期：`BUILD SUCCESS`

- [ ] **Step 4: 提交**

```bash
git add src/main/java/com/qy/dch/config/GlobalExceptionHandler.java
git commit -m "feat(config): extend GlobalExceptionHandler with validation and algorithm handlers"
```

---

## Task 4: 应用启动烟雾测试

- [ ] **Step 1: 打包**

```bash
./mvnw clean package -DskipTests 2>&1 | tail -5
```

预期：`BUILD SUCCESS`

- [ ] **Step 2: 启动应用**

```bash
$JAVA_HOME/bin/java -jar target/uygur-project-0.0.1-SNAPSHOT.jar > /tmp/app.log 2>&1 &
echo $! > /tmp/app.pid
sleep 15
```

- [ ] **Step 3: 验证启动**

```bash
grep "Started DchApplication" /tmp/app.log
curl -s http://localhost:8081/api/dashboard/overview | head -c 200
```

预期：日志含 `Started DchApplication`；接口返回 `{"code":1,...}`

- [ ] **Step 4: 停止应用**

```bash
kill $(cat /tmp/app.pid) 2>/dev/null
```

- [ ] **Step 5: 提交**

无新文件改动，无需 commit。第一波完成。

---

# 第二波：拆分 UygurMapper

## Task 5: 备份 UygurMapper

**Files:**
- Modify: `.gitignore`（如已忽略 .bak 则跳过）

- [ ] **Step 1: 检查现有备份**

```bash
ls src/main/java/com/qy/dch/mapper/UygurMapper.java.bak 2>/dev/null
```

如已存在 `UygurMapper.java.bak`，跳过此 Task。

- [ ] **Step 2: 创建工作备份**

```bash
cp src/main/java/com/qy/dch/mapper/UygurMapper.java /tmp/UygurMapper.original.java
echo "备份到 /tmp/UygurMapper.original.java"
```

后续 Task 中如对方法归属有疑问，可对照此文件。

---

## Task 6: 创建 OriginTextMapper

**Files:**
- Create: `src/main/java/com/qy/dch/mapper/OriginTextMapper.java`

- [ ] **Step 1: 阅读原 UygurMapper**

Read `src/main/java/com/qy/dch/mapper/UygurMapper.java`。

- [ ] **Step 2: 创建 OriginTextMapper**

将所有**主要操作 origin_text 表**的方法迁移到新文件。注意：

包含的方法类型（按签名归类）：
- 单条查询：`selectById`、`getTextById`、`selectIsExtracted`
- 列表查询：`getList`、`getListByType`、`getListByModalType`、`getListByTypes`、`getListByModalTypes`、`getListWithFilter`
- 计数：`countByType`、`countByModalType`、`countByTypes`、`countByModalTypes`、`countWithFilter`
- 分类统计：`countByCategory`、`countBySendUnitInCategory`、`getReportsByCategoryAndSendUnit`
- 高级筛选：`filterReports`、`countFilterReports`
- 时间范围：`selectIdsByTimeRange`
- 抽取状态：`updateExtractedStatus`、`resetAllExtractedStatus`、`getExtractionStats`
- 批量删除/更新：`deleteText`、`deleteTextsBatch`、`updateTextsType`、`updateTextsByOldType`、`deleteCategoryWithTexts`（删 texts 部分）
- 单条插入（非批量导入）：`insertOriginText`（如有）

模板（精确方法签名见 `/tmp/UygurMapper.original.java`，**SQL 字符串照搬不变**）：

```java
package com.qy.dch.mapper;

import com.qy.dch.dto.OriginTextDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

/**
 * 原始文本（origin_text）数据访问层
 * <p>
 * 负责报文的查询、筛选、状态更新、批量删除等操作
 * </p>
 */
@Mapper
public interface OriginTextMapper {

    // ===== 单条查询 =====
    @Select("...")
    OriginTextDTO selectById(String sid);

    // ===== 此处粘贴所有 origin_text 相关方法及其 @Select/@Update 注解 =====
    // （从 /tmp/UygurMapper.original.java 完整复制对应方法）
}
```

- [ ] **Step 3: 编译验证**

```bash
./mvnw clean compile 2>&1 | grep -E "BUILD|ERROR" | tail -5
```

预期：`BUILD SUCCESS`（此时 UygurMapper 中的方法是重复的，但只要每个方法只在一个 Mapper 里被 Spring 扫描就 OK）

注意：新 Mapper 和旧 UygurMapper 都标注 `@Mapper`，两者方法签名重复也能编译通过——只要 Service 还在用 UygurMapper 即可。

- [ ] **Step 4: 提交**

```bash
git add src/main/java/com/qy/dch/mapper/OriginTextMapper.java
git commit -m "feat(mapper): add OriginTextMapper with methods from UygurMapper"
```

---

## Task 7: 创建 TextTypeMapper

**Files:**
- Create: `src/main/java/com/qy/dch/mapper/TextTypeMapper.java`

- [ ] **Step 1: 创建 TextTypeMapper**

迁移**所有操作 text_type 表**的方法：
- `getCategories`、`getCategoryByNameAndParent`、`addCategory`、`updateCategoryName`、`deleteCategory`、`deleteCategoriesBatch`、`addCategoryByParentId`、`getCategoryById`
- 树形结构：`selectAllCategories`、`selectChildrenByParentId`、`countReportsByCategory`
- 别名/分类关联：`getCategoryBySendUnit`（若存在）

```java
package com.qy.dch.mapper;

import com.qy.dch.dto.TextTypeDTO;
import com.qy.dch.entity.Category;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Delete;

import java.util.List;
import java.util.Map;

/**
 * 文本分类（text_type）数据访问层
 */
@Mapper
public interface TextTypeMapper {

    // ===== 此处粘贴所有 text_type 相关方法及其 SQL 注解 =====
}
```

- [ ] **Step 2: 编译验证**

```bash
./mvnw clean compile 2>&1 | tail -5
```

预期：`BUILD SUCCESS`

- [ ] **Step 3: 提交**

```bash
git add src/main/java/com/qy/dch/mapper/TextTypeMapper.java
git commit -m "feat(mapper): add TextTypeMapper"
```

---

## Task 8: 创建 ImportMapper

**Files:**
- Create: `src/main/java/com/qy/dch/mapper/ImportMapper.java`

- [ ] **Step 1: 创建 ImportMapper**

迁移**仅用于 JSONL 批量导入路径**的方法：
- `batchInsertOriginText`
- `batchInsertOriginTextWithImages`
- `insertOriginTextOnImport`（如有专门用于导入的单条插入）
- 导入时首次创建分类的 INSERT（不与 TextTypeMapper 重复）

```java
package com.qy.dch.mapper;

import com.qy.dch.dto.OriginTextDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * JSONL 批量导入路径专用数据访问层
 */
@Mapper
public interface ImportMapper {

    // ===== 此处粘贴所有批量导入相关方法 =====
}
```

- [ ] **Step 2: 编译验证**

```bash
./mvnw clean compile 2>&1 | tail -5
```

预期：`BUILD SUCCESS`

- [ ] **Step 3: 提交**

```bash
git add src/main/java/com/qy/dch/mapper/ImportMapper.java
git commit -m "feat(mapper): add ImportMapper for batch JSONL import"
```

---

## Task 9: 更新所有 Service 的 Mapper 注入

**Files:**
- Modify: 9 个 Service 实现类

- [ ] **Step 1: 列出受影响的 Service**

```bash
grep -rln "uygurMapper\|UygurMapper" src/main/java/com/qy/dch/service/impl/
```

预期：
- DirectoryServiceImpl
- DashboardServiceImpl
- UygurServiceImpl
- EventAnalysisServiceImpl
- TargetAnalysisServiceImpl
- CategoryServiceImpl
- FusionServiceImpl
- ExtractionServiceImpl

- [ ] **Step 2: 修改 DirectoryServiceImpl**

只用到 `countByCategory`、`countBySendUnitInCategory`、`getReportsByCategoryAndSendUnit`，都属于 `OriginTextMapper`：

```java
// 替换 import
import com.qy.dch.mapper.OriginTextMapper;
// 删除 import com.qy.dch.mapper.UygurMapper;

// 替换字段
@Resource
private OriginTextMapper originTextMapper;
// 之前是 private UygurMapper uygurMapper;

// 全文 uygurMapper. 替换为 originTextMapper.
```

- [ ] **Step 3: 修改 DashboardServiceImpl**

用到 `countByType`、`countByModalType`、`getCategories`（TextTypeMapper）。需要同时注入两个：

```java
@Resource
private OriginTextMapper originTextMapper;
@Resource
private TextTypeMapper textTypeMapper;

// uygurMapper.countByType() → originTextMapper.countByType()
// uygurMapper.getCategories() → textTypeMapper.getCategories()
```

- [ ] **Step 4: 修改 CategoryServiceImpl**

用到 `selectAllCategories`、`countReportsByCategory` 等 → 全部 `TextTypeMapper`：

```java
@Resource
private TextTypeMapper textTypeMapper;
// uygurMapper. → textTypeMapper.
```

- [ ] **Step 5: 修改 FusionServiceImpl、ExtractionServiceImpl、EventAnalysisServiceImpl、TargetAnalysisServiceImpl**

这些只用到 `selectById`、`selectIsExtracted`、`updateExtractedStatus`、`selectIdsByTimeRange`、`getTextById` 等 → 全部 `OriginTextMapper`：

```java
@Autowired
private OriginTextMapper originTextMapper;
// uygurMapper. → originTextMapper.
```

- [ ] **Step 6: 修改 UygurServiceImpl**

这个最复杂，需要 3 个 Mapper：

```java
@Resource
private OriginTextMapper originTextMapper;
@Resource
private TextTypeMapper textTypeMapper;
@Resource
private ImportMapper importMapper;

// 报文相关 uygurMapper.xxx → originTextMapper.xxx
// 分类相关 uygurMapper.xxx → textTypeMapper.xxx
// 批量导入 uygurMapper.batchInsert → importMapper.batchInsert
```

- [ ] **Step 7: 修改 TestController（临时保留，下一波删）**

```java
@Resource
private OriginTextMapper originTextMapper; // 或 TextTypeMapper，看用了什么方法
```

- [ ] **Step 8: 编译验证**

```bash
./mvnw clean compile 2>&1 | tail -10
```

预期：`BUILD SUCCESS`

- [ ] **Step 9: 提交**

```bash
git add src/main/java/com/qy/dch/service/impl/ src/main/java/com/qy/dch/controller/TestController.java
git commit -m "refactor(service): switch Service injections from UygurMapper to split mappers"
```

---

## Task 10: 删除 UygurMapper

**Files:**
- Delete: `src/main/java/com/qy/dch/mapper/UygurMapper.java`
- Delete: `src/main/java/com/qy/dch/mapper/UygurMapper.java.bak`

- [ ] **Step 1: 确认无引用**

```bash
grep -rn "UygurMapper" src/main/java/ src/test/java/ 2>/dev/null
```

预期：无任何引用（已全部替换）

- [ ] **Step 2: 删除文件**

```bash
rm src/main/java/com/qy/dch/mapper/UygurMapper.java
rm -f src/main/java/com/qy/dch/mapper/UygurMapper.java.bak
```

- [ ] **Step 3: 编译验证**

```bash
./mvnw clean compile 2>&1 | tail -5
```

预期：`BUILD SUCCESS`

- [ ] **Step 4: 应用启动测试**

```bash
./mvnw package -DskipTests 2>&1 | tail -3
$JAVA_HOME/bin/java -jar target/uygur-project-0.0.1-SNAPSHOT.jar > /tmp/app.log 2>&1 &
echo $! > /tmp/app.pid
sleep 15
grep "Started DchApplication" /tmp/app.log
curl -s http://localhost:8081/api/dashboard/overview | head -c 100
curl -s http://localhost:8081/api/category/tree | head -c 100
kill $(cat /tmp/app.pid)
```

预期：启动成功，两个接口返回 `{"code":1,...}`

- [ ] **Step 5: 提交**

```bash
git rm src/main/java/com/qy/dch/mapper/UygurMapper.java
git rm -f src/main/java/com/qy/dch/mapper/UygurMapper.java.bak
git commit -m "refactor(mapper): remove UygurMapper (split into 3 mappers)"
```

---

**第二波完成。继续第三波（AlgorithmClient）请告诉我，我会单独输出。**

---

# 第三波：统一 AlgorithmClient

## Task 11: 创建 AlgorithmClient 接口与 DTO 包

**Files:**
- Create: `src/main/java/com/qy/dch/service/client/AlgorithmClient.java`
- Create: `src/main/java/com/qy/dch/service/client/dto/` 目录

- [ ] **Step 1: 创建接口文件**

```java
package com.qy.dch.service.client;

import com.alibaba.fastjson.JSONObject;
import com.qy.dch.dto.AlgorithmAnalyzeRequest;
import com.qy.dch.dto.AlgorithmAnalyzeResponse;
import com.qy.dch.dto.AlgorithmFusionRequest;
import com.qy.dch.dto.AlgorithmFusionResponse;
import com.qy.dch.request.FusionCreateRequest;

/**
 * 算法服务（Python LLM 服务）统一 HTTP 客户端
 * <p>
 * 所有 Service 调用算法服务必须通过此接口，禁止直接使用 RestTemplate / HttpURLConnection
 * </p>
 */
public interface AlgorithmClient {

    /** 事件抽取 - POST /extract */
    JSONObject extract(String content, String originTextId);

    /** 报文融合 - POST /fusion/create */
    JSONObject fuse(FusionCreateRequest request);

    /** 目标分析 - POST /api/target/analyze */
    AlgorithmAnalyzeResponse analyzeTarget(AlgorithmAnalyzeRequest request);

    /** 目标融合 - POST /api/target/fusion */
    AlgorithmFusionResponse fuseTarget(AlgorithmFusionRequest request);

    /** 事件分析 - POST /eventSplit */
    JSONObject splitEvents(String content, String startDate, String endDate);
}
```

- [ ] **Step 2: 编译验证**

```bash
./mvnw clean compile 2>&1 | tail -5
```

预期：`BUILD SUCCESS`

- [ ] **Step 3: 提交**

```bash
git add src/main/java/com/qy/dch/service/client/AlgorithmClient.java
git commit -m "feat(client): add AlgorithmClient interface"
```

---

## Task 12: 实现 AlgorithmClientImpl

**Files:**
- Create: `src/main/java/com/qy/dch/service/client/AlgorithmClientImpl.java`

- [ ] **Step 1: 阅读现有 HTTP 调用代码**

Read 以下 3 处现有 HTTP 调用：
- `src/main/java/com/qy/dch/service/impl/ExtractionServiceImpl.java` 中的 `callAlgorithmService` 方法（约 230-310 行）
- `src/main/java/com/qy/dch/service/impl/FusionServiceImpl.java` 中的 `callAlgorithmService` 方法
- `src/main/java/com/qy/dch/service/impl/EventAnalysisServiceImpl.java` 中的 HTTP 调用部分（约 140-180 行）

记录每处的：URL 路径、超时设置、请求 / 响应字段名。

- [ ] **Step 2: 创建实现类**

```java
package com.qy.dch.service.client;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.qy.dch.common.AlgorithmServiceException;
import com.qy.dch.common.ErrorCode;
import com.qy.dch.dto.AlgorithmAnalyzeRequest;
import com.qy.dch.dto.AlgorithmAnalyzeResponse;
import com.qy.dch.dto.AlgorithmFusionRequest;
import com.qy.dch.dto.AlgorithmFusionResponse;
import com.qy.dch.request.FusionCreateRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class AlgorithmClientImpl implements AlgorithmClient {

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public AlgorithmClientImpl(RestTemplate restTemplate,
                                @Value("${algorithm.service.url:http://localhost:5001}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }

    @Override
    public JSONObject extract(String content, String originTextId) {
        Map<String, Object> body = new HashMap<>();
        body.put("content", content);
        body.put("originTextId", originTextId);
        return postJson("/extract", body, "extract");
    }

    @Override
    public JSONObject fuse(FusionCreateRequest request) {
        return postJson("/fusion/create", request, "fuse");
    }

    @Override
    public AlgorithmAnalyzeResponse analyzeTarget(AlgorithmAnalyzeRequest request) {
        return postTyped("/api/target/analyze", request, AlgorithmAnalyzeResponse.class, "analyzeTarget");
    }

    @Override
    public AlgorithmFusionResponse fuseTarget(AlgorithmFusionRequest request) {
        return postTyped("/api/target/fusion", request, AlgorithmFusionResponse.class, "fuseTarget");
    }

    @Override
    public JSONObject splitEvents(String content, String startDate, String endDate) {
        Map<String, Object> body = new HashMap<>();
        body.put("content", content);
        body.put("startDate", startDate);
        body.put("endDate", endDate);
        return postJson("/eventSplit", body, "splitEvents");
    }

    /** 返回 JSONObject 的通用 POST */
    private JSONObject postJson(String path, Object body, String op) {
        String url = baseUrl + path;
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Object> entity = new HttpEntity<>(body, headers);
            log.info("调用算法服务 [{}]: {}", op, url);
            ResponseEntity<String> resp = restTemplate.postForEntity(url, entity, String.class);
            if (!resp.getStatusCode().is2xxSuccessful()) {
                throw new AlgorithmServiceException(ErrorCode.ALGORITHM_SERVICE_UNAVAILABLE,
                        "算法服务返回 HTTP " + resp.getStatusCodeValue());
            }
            JSONObject result = JSON.parseObject(resp.getBody());
            if (result == null) {
                throw new AlgorithmServiceException(ErrorCode.ALGORITHM_SERVICE_INVALID_RESPONSE,
                        "算法服务返回空响应");
            }
            return result;
        } catch (ResourceAccessException e) {
            if (e.getCause() instanceof SocketTimeoutException) {
                throw new AlgorithmServiceException(ErrorCode.ALGORITHM_SERVICE_TIMEOUT,
                        "算法服务调用超时: " + op, e);
            }
            throw new AlgorithmServiceException(ErrorCode.ALGORITHM_SERVICE_UNAVAILABLE,
                    "算法服务不可达: " + op, e);
        } catch (RestClientException e) {
            throw new AlgorithmServiceException(ErrorCode.ALGORITHM_SERVICE_UNAVAILABLE,
                    "算法服务调用失败: " + op, e);
        }
    }

    /** 返回强类型对象的通用 POST */
    private <T> T postTyped(String path, Object body, Class<T> responseType, String op) {
        String url = baseUrl + path;
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Object> entity = new HttpEntity<>(body, headers);
            log.info("调用算法服务 [{}]: {}", op, url);
            ResponseEntity<T> resp = restTemplate.postForEntity(url, entity, responseType);
            if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
                throw new AlgorithmServiceException(ErrorCode.ALGORITHM_SERVICE_INVALID_RESPONSE,
                        "算法服务返回无效响应: " + op);
            }
            return resp.getBody();
        } catch (ResourceAccessException e) {
            if (e.getCause() instanceof SocketTimeoutException) {
                throw new AlgorithmServiceException(ErrorCode.ALGORITHM_SERVICE_TIMEOUT,
                        "算法服务调用超时: " + op, e);
            }
            throw new AlgorithmServiceException(ErrorCode.ALGORITHM_SERVICE_UNAVAILABLE,
                    "算法服务不可达: " + op, e);
        } catch (RestClientException e) {
            throw new AlgorithmServiceException(ErrorCode.ALGORITHM_SERVICE_UNAVAILABLE,
                    "算法服务调用失败: " + op, e);
        }
    }
}
```

- [ ] **Step 3: 编译验证**

```bash
./mvnw clean compile 2>&1 | tail -5
```

预期：`BUILD SUCCESS`

- [ ] **Step 4: 提交**

```bash
git add src/main/java/com/qy/dch/service/client/AlgorithmClientImpl.java
git commit -m "feat(client): implement AlgorithmClientImpl with unified error handling"
```

---

## Task 13: ExtractionServiceImpl 切换到 AlgorithmClient

**Files:**
- Modify: `src/main/java/com/qy/dch/service/impl/ExtractionServiceImpl.java`

- [ ] **Step 1: 注入 AlgorithmClient**

在 class 顶部增加注入：

```java
@Autowired
private com.qy.dch.service.client.AlgorithmClient algorithmClient;
```

- [ ] **Step 2: 删除原 callAlgorithmService 方法及其调用方修改**

定位原代码中类似：
```java
extractionResult = callAlgorithmService(text.getContent(), originTextId);
```

替换为：
```java
extractionResult = algorithmClient.extract(text.getContent(), originTextId);
```

- [ ] **Step 3: 删除整个 callAlgorithmService 方法和相关辅助方法**

约 90-100 行代码删除：
- `private JSONObject callAlgorithmService(String content, String originTextId) throws Exception {...}`
- 相关的 HttpURLConnection 辅助方法（如有）
- 不再需要的 import：`HttpURLConnection`、`URL`、`OutputStream`、`BufferedReader`、`InputStreamReader`

- [ ] **Step 4: 删除 @Value algorithmServiceUrl**

```java
// 删除这两行
@Value("${algorithm.service.url:http://localhost:5001}")
private String algorithmServiceUrl;
```

- [ ] **Step 5: 编译验证**

```bash
./mvnw clean compile 2>&1 | tail -5
```

预期：`BUILD SUCCESS`

- [ ] **Step 6: 提交**

```bash
git add src/main/java/com/qy/dch/service/impl/ExtractionServiceImpl.java
git commit -m "refactor(extraction): use AlgorithmClient instead of raw HTTP"
```

---

## Task 14: FusionServiceImpl 切换到 AlgorithmClient

**Files:**
- Modify: `src/main/java/com/qy/dch/service/impl/FusionServiceImpl.java`

- [ ] **Step 1: 注入 AlgorithmClient**

```java
@Autowired
private com.qy.dch.service.client.AlgorithmClient algorithmClient;
```

- [ ] **Step 2: 替换调用**

定位：
```java
JSONObject algorithmResult = callAlgorithmService(request);
```

替换为：
```java
JSONObject algorithmResult = algorithmClient.fuse(request);
```

- [ ] **Step 3: 移除兜底逻辑**

原代码：
```java
if (algorithmResult != null && algorithmResult.getInteger("code") == 1) {
    JSONObject data = algorithmResult.getJSONObject("data");
    FusionDTO fusionDTO = parseAlgorithmResult(data, reports);
    log.info("融合报告生成完成: title={}", fusionDTO.getTitle());
    return fusionDTO;
} else {
    log.error("算法服务返回失败: {}", algorithmResult);
    return createDefaultFusion(reports, request.getCustomTitle());
}
```

替换为：
```java
if (algorithmResult.getInteger("code") != 1) {
    throw new com.qy.dch.common.AlgorithmServiceException(
            com.qy.dch.common.ErrorCode.ALGORITHM_SERVICE_INVALID_RESPONSE,
            "融合算法返回失败: " + algorithmResult.getString("msg"));
}
JSONObject data = algorithmResult.getJSONObject("data");
FusionDTO fusionDTO = parseAlgorithmResult(data, reports);
log.info("融合报告生成完成: title={}", fusionDTO.getTitle());
return fusionDTO;
```

- [ ] **Step 4: 删除 callAlgorithmService 和 createDefaultFusion 方法**

整个 `callAlgorithmService(FusionCreateRequest)` 方法和 `createDefaultFusion(...)` 方法都删除（约 100+ 行）。

外层 try-catch 也删除（让异常向上传播给 GlobalExceptionHandler）。

- [ ] **Step 5: 删除 @Value 和无用 import**

```java
// 删除
@Value("${algorithm.service.url:http://localhost:5001}")
private String algorithmServiceUrl;

// 删除 import
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
```

- [ ] **Step 6: 编译验证**

```bash
./mvnw clean compile 2>&1 | tail -5
```

预期：`BUILD SUCCESS`

- [ ] **Step 7: 提交**

```bash
git add src/main/java/com/qy/dch/service/impl/FusionServiceImpl.java
git commit -m "refactor(fusion): use AlgorithmClient and remove fallback logic"
```

---

## Task 15: EventAnalysisServiceImpl 切换到 AlgorithmClient

**Files:**
- Modify: `src/main/java/com/qy/dch/service/impl/EventAnalysisServiceImpl.java`

- [ ] **Step 1: 注入 AlgorithmClient，替换 RestTemplate 直接调用**

```java
@Autowired
private com.qy.dch.service.client.AlgorithmClient algorithmClient;
```

定位 `eventSplit` 端点的 POST 调用，替换为：
```java
JSONObject result = algorithmClient.splitEvents(content, startDate, endDate);
```

- [ ] **Step 2: 删除 RestTemplate 字段、@Value、HTTP 模板代码**

```java
// 删除
@Autowired
private RestTemplate restTemplate;

@Value("${algorithm.service.url}")
private String algorithmServiceUrl;
```

- [ ] **Step 3: 编译验证**

```bash
./mvnw clean compile 2>&1 | tail -5
```

预期：`BUILD SUCCESS`

- [ ] **Step 4: 提交**

```bash
git add src/main/java/com/qy/dch/service/impl/EventAnalysisServiceImpl.java
git commit -m "refactor(event-analysis): use AlgorithmClient"
```

---

## Task 16: TargetAnalysisServiceImpl 切换到 AlgorithmClient

**Files:**
- Modify: `src/main/java/com/qy/dch/service/impl/TargetAnalysisServiceImpl.java`

- [ ] **Step 1: 注入 AlgorithmClient**

```java
@Autowired
private com.qy.dch.service.client.AlgorithmClient algorithmClient;
```

- [ ] **Step 2: 替换 restTemplate.postForEntity 调用**

原：
```java
ResponseEntity<AlgorithmAnalyzeResponse> response = restTemplate.postForEntity(
        url, entity, AlgorithmAnalyzeResponse.class);
AlgorithmAnalyzeResponse algorithmResponse = response.getBody();
```

替换为：
```java
AlgorithmAnalyzeResponse algorithmResponse = algorithmClient.analyzeTarget(request);
```

删除原 `HttpHeaders headers`、`HttpEntity entity` 等准备代码、外层 try-catch（异常向上传播）。

- [ ] **Step 3: 删除 RestTemplate 注入、@Value、algorithmServiceUrl 字段**

```java
// 删除
@Resource
private RestTemplate restTemplate;

@Value("${algorithm.service.url:http://localhost:9203}")
private String algorithmServiceUrl;
```

- [ ] **Step 4: 编译验证**

```bash
./mvnw clean compile 2>&1 | tail -5
```

预期：`BUILD SUCCESS`

- [ ] **Step 5: 提交**

```bash
git add src/main/java/com/qy/dch/service/impl/TargetAnalysisServiceImpl.java
git commit -m "refactor(target-analysis): use AlgorithmClient"
```

---

## Task 17: TargetFusionServiceImpl 切换到 AlgorithmClient

**Files:**
- Modify: `src/main/java/com/qy/dch/service/impl/TargetFusionServiceImpl.java`

- [ ] **Step 1: 注入 AlgorithmClient + 替换 + 清理**

同 Task 16 模式，调用 `algorithmClient.fuseTarget(request)`，删除 RestTemplate 和 @Value。

- [ ] **Step 2: 编译验证**

```bash
./mvnw clean compile 2>&1 | tail -5
```

预期：`BUILD SUCCESS`

- [ ] **Step 3: 提交**

```bash
git add src/main/java/com/qy/dch/service/impl/TargetFusionServiceImpl.java
git commit -m "refactor(target-fusion): use AlgorithmClient"
```

---

## Task 18: 第三波集成验证

- [ ] **Step 1: 确认无遗留 HttpURLConnection**

```bash
grep -rn "HttpURLConnection" src/main/java/com/qy/dch/service/
```

预期：无输出

- [ ] **Step 2: 确认无遗留 algorithmServiceUrl @Value**

```bash
grep -rn "algorithm.service.url" src/main/java/com/qy/dch/service/impl/
```

预期：无输出（应只在 AlgorithmClientImpl 中存在）

- [ ] **Step 3: 启动并验证**

```bash
./mvnw clean package -DskipTests 2>&1 | tail -3
$JAVA_HOME/bin/java -jar target/uygur-project-0.0.1-SNAPSHOT.jar > /tmp/app.log 2>&1 &
echo $! > /tmp/app.pid
sleep 15
grep "Started DchApplication" /tmp/app.log
curl -s http://localhost:8081/api/dashboard/overview | head -c 100
kill $(cat /tmp/app.pid)
```

预期：启动成功，接口返回 200


- [ ] **Step 4: 提交**

无新文件改动，第三波完成。

---

# 第四波：路径与命名规范化

## Task 19: 删除 TestController

**Files:**
- Delete: `src/main/java/com/qy/dch/controller/TestController.java`

- [ ] **Step 1: 确认无关键依赖**

```bash
grep -rn "TestController\|/test/queryCategory\|/test/testMultipart" src/main/java/ src/test/java/
```

预期：除 TestController.java 本身外无其他引用。

- [ ] **Step 2: 删除文件**

```bash
rm src/main/java/com/qy/dch/controller/TestController.java
```

- [ ] **Step 3: 编译验证**

```bash
./mvnw clean compile 2>&1 | tail -5
```

预期：`BUILD SUCCESS`

- [ ] **Step 4: 提交**

```bash
git rm src/main/java/com/qy/dch/controller/TestController.java
git commit -m "refactor(controller): remove TestController (debug-only)"
```

---

## Task 20: ExtractionController 路径补 /api 前缀

**Files:**
- Modify: `src/main/java/com/qy/dch/controller/ExtractionController.java`

- [ ] **Step 1: 修改路径**

```java
// 改前
@RequestMapping("/extraction")
// 改后
@RequestMapping("/api/extraction")
```

- [ ] **Step 2: 编译验证 + 启动测试**

```bash
./mvnw clean package -DskipTests 2>&1 | tail -3
$JAVA_HOME/bin/java -jar target/uygur-project-0.0.1-SNAPSHOT.jar > /tmp/app.log 2>&1 &
echo $! > /tmp/app.pid
sleep 15
curl -s -o /dev/null -w "%{http_code}" http://localhost:8081/api/extraction/result/1
kill $(cat /tmp/app.pid)
```

预期：返回 200（或合理的业务错误码，只要不是 404）

- [ ] **Step 3: 提交**

```bash
git add src/main/java/com/qy/dch/controller/ExtractionController.java
git commit -m "refactor(extraction): add /api prefix to controller path"
```

---

## Task 21: 重命名 UygurController → OriginTextController

**Files:**
- Rename: `UygurController.java` → `OriginTextController.java`
- Rename: `UygurService.java` → `OriginTextService.java`
- Rename: `UygurServiceImpl.java` → `OriginTextServiceImpl.java`

- [ ] **Step 1: git mv 文件**

```bash
git mv src/main/java/com/qy/dch/controller/UygurController.java src/main/java/com/qy/dch/controller/OriginTextController.java
git mv src/main/java/com/qy/dch/service/UygurService.java src/main/java/com/qy/dch/service/OriginTextService.java
git mv src/main/java/com/qy/dch/service/impl/UygurServiceImpl.java src/main/java/com/qy/dch/service/impl/OriginTextServiceImpl.java
```

- [ ] **Step 2: 修改类名与字段名**

在三个文件内：
```bash
# 类名
sed -i '' 's/class UygurController/class OriginTextController/' src/main/java/com/qy/dch/controller/OriginTextController.java
sed -i '' 's/interface UygurService/interface OriginTextService/' src/main/java/com/qy/dch/service/OriginTextService.java
sed -i '' 's/class UygurServiceImpl/class OriginTextServiceImpl/' src/main/java/com/qy/dch/service/impl/OriginTextServiceImpl.java
sed -i '' 's/implements UygurService/implements OriginTextService/' src/main/java/com/qy/dch/service/impl/OriginTextServiceImpl.java
```

- [ ] **Step 3: 修改其他引用**

```bash
grep -rln "UygurService\b" src/main/java/ src/test/java/ | xargs sed -i '' 's/UygurService\b/OriginTextService/g'
grep -rln "uygurService" src/main/java/ src/test/java/ | xargs sed -i '' 's/uygurService/originTextService/g'
```

- [ ] **Step 4: 修改 Controller 路径前缀**

```java
// 改前
@RequestMapping("/api/uygur")
// 改后
@RequestMapping("/api/origin-text")
```

- [ ] **Step 5: 编译验证**

```bash
./mvnw clean compile 2>&1 | tail -10
```

预期：`BUILD SUCCESS`

- [ ] **Step 6: 提交**

```bash
git add -A
git commit -m "refactor(controller): rename UygurController to OriginTextController, change path to /api/origin-text"
```

---

## Task 22: 路径 RESTful 化（OriginTextController）

**Files:**
- Modify: `src/main/java/com/qy/dch/controller/OriginTextController.java`

按下表修改方法上的 @GetMapping/@PostMapping 等：

| 旧方法路径 | 新方法路径 | HTTP 方法 |
|-----------|----------|----------|
| `/config` | `/config` | GET（保持） |
| `/savetext` | `/import-default` | POST（从 GET 改 POST，更符合写操作语义） |
| `/category` (GET) | **整方法删除**（用 `/api/text-type` 或合并 CategoryController） |
| `/category` (POST) | **整方法删除**（合并到 CategoryController） |
| `/detail/{sid}` | `/{sid}` | GET |
| `/getTextList` | `/list` | POST |
| `/resetExtracted` | `/reset-extracted` | POST |
| `/addCategory` | **整方法删除** |
| `/category/{categoryId}` (PUT/DELETE) | **整方法删除** |
| `/importFromJsonl` | `/import/jsonl` | POST |
| `/importFromJsonlWithImages` | `/import/jsonl-with-images` | POST |
| `/filter` | `` (空，即 `/api/origin-text` 根路径) | GET |
| `/category/{categoryId}/withTexts` | **整方法删除**（移到 CategoryController） |
| `/category/batchDelete` | **整方法删除** |
| `/text/{sid}` | `/{sid}` | DELETE |
| `/text/batchDelete` | `/batch-delete` | POST |
| `/text/updateByOldType` | `/batch-update-type` | PATCH |

- [ ] **Step 1: 删除分类相关方法**

定位以下方法并整体删除（含 @PostMapping/@PutMapping/@DeleteMapping 注解）：
- `addCategory`
- `addCategoryGeneral`（`/category` POST）
- `updateCategory`（`/category/{categoryId}` PUT）
- `deleteCategory`（`/category/{categoryId}` DELETE）
- `deleteCategoriesBatch`
- `deleteCategoryWithTexts`
- `getCategory`（`/category` GET）

注意：业务逻辑层的对应方法（`uygurService.addCategory` 等）也要 git grep 看是否还有其他调用方，没有则同步删除 Service 的方法实现。

- [ ] **Step 2: 改造剩余方法的注解路径**

```java
// /savetext → /import-default + POST
@PostMapping("/import-default")
public ResultVO savetext() { ... }

// /detail/{sid} → /{sid} + GET
@GetMapping("/{sid}")
public ResultVO getDetail(@PathVariable String sid) { ... }

// /getTextList → /list
@PostMapping("/list")

// /resetExtracted → /reset-extracted
@PostMapping("/reset-extracted")

// /importFromJsonl → /import/jsonl
@PostMapping("/import/jsonl")

// /importFromJsonlWithImages → /import/jsonl-with-images
@PostMapping("/import/jsonl-with-images")

// /filter → "" (映射到 /api/origin-text)
@GetMapping
public ResultVO filterReports(...)

// /text/{sid} → /{sid}
@DeleteMapping("/{sid}")

// /text/batchDelete → /batch-delete
@PostMapping("/batch-delete")

// /text/updateByOldType → /batch-update-type
@PatchMapping("/batch-update-type")
```

注意 `GET /{sid}` 和 `DELETE /{sid}` 路径相同但方法不同，Spring 能正确路由。

- [ ] **Step 3: 编译验证**

```bash
./mvnw clean compile 2>&1 | tail -5
```

预期：`BUILD SUCCESS`

- [ ] **Step 4: 提交**

```bash
git add src/main/java/com/qy/dch/controller/OriginTextController.java src/main/java/com/qy/dch/service/
git commit -m "refactor(origin-text): RESTful path normalization, remove category endpoints"
```

---

## Task 23: CategoryController 路径 RESTful 化

**Files:**
- Modify: `src/main/java/com/qy/dch/controller/CategoryController.java`

| 旧方法路径 | 新方法路径 | HTTP 方法 |
|-----------|----------|----------|
| `/tree` | `/tree` | GET（保持） |
| `/leafs` | `/leafs` | GET（保持） |
| `/create` | `` (根) | POST |
| `/update` | `/{categoryId}` | PUT |
| `/move` | `/{categoryId}/parent` | PATCH |
| `/delete/{categoryId}` | `/{categoryId}` | DELETE |
| `/detail/{categoryId}` | `/{categoryId}` | GET |

新增方法（继承自被删除的 OriginTextController 分类接口）：
- `POST /api/category/batch-delete` - 批量删除
- `DELETE /api/category/{id}?with-texts=true` - 删除分类含报文

- [ ] **Step 1: 修改注解**

```java
@PostMapping  // 原 /create
public ResultVO createCategory(@RequestBody Map<String, Object> requestBody) { ... }

@PutMapping("/{categoryId}")  // 原 /update（categoryId 从 body 改为路径变量）
public ResultVO updateCategory(@PathVariable Long categoryId, @RequestBody Map<String, Object> requestBody) {
    // 删除原 Long categoryId = ((Number) requestBody.get("categoryId")).longValue();
    String newName = (String) requestBody.get("newName");
    String newDescription = (String) requestBody.get("newDescription");
    Category updated = categoryService.updateCategory(categoryId, newName, newDescription);
    return ResultVO.success(updated);
}

@PatchMapping("/{categoryId}/parent")  // 原 /move
public ResultVO moveCategory(@PathVariable Long categoryId, @RequestBody Map<String, Object> requestBody) {
    Long newParentId = requestBody.get("newParentId") != null
            ? ((Number) requestBody.get("newParentId")).longValue()
            : null;
    Category moved = categoryService.moveCategory(categoryId, newParentId);
    return ResultVO.success(moved);
}

@DeleteMapping("/{categoryId}")  // 原 /delete/{categoryId}，增加 with-texts 参数
public ResultVO deleteCategory(@PathVariable Long categoryId,
                                @RequestParam(value = "with-texts", defaultValue = "false") boolean withTexts) {
    if (withTexts) {
        int textCount = categoryService.deleteCategoryWithTexts(categoryId);
        return ResultVO.success("成功删除分类及其下" + textCount + "条报文");
    }
    categoryService.deleteCategory(categoryId);
    return ResultVO.success();
}

@GetMapping("/{categoryId}")  // 原 /detail/{categoryId}
public ResultVO getCategoryDetail(@PathVariable Long categoryId) { ... }

@PostMapping("/batch-delete")  // 新增，从 OriginTextController 迁移
public ResultVO batchDelete(@RequestBody List<Integer> categoryIds) { ... }
```

- [ ] **Step 2: CategoryService 增加方法**

在 `CategoryService.java` 增加方法签名：
```java
int deleteCategoryWithTexts(Long categoryId);
int batchDelete(List<Integer> categoryIds);
```

在 `CategoryServiceImpl.java` 实现（从原 UygurServiceImpl 对应方法移植）。

- [ ] **Step 3: 编译验证**

```bash
./mvnw clean compile 2>&1 | tail -5
```

预期：`BUILD SUCCESS`

- [ ] **Step 4: 提交**

```bash
git add src/main/java/com/qy/dch/controller/CategoryController.java src/main/java/com/qy/dch/service/
git commit -m "refactor(category): RESTful path, absorb category endpoints from OriginTextController"
```

---

## Task 24: EventAnalysisController 路径 kebab-case 化

**Files:**
- Modify: `src/main/java/com/qy/dch/controller/EventAnalysisController.java`

- [ ] **Step 1: 修改路径前缀**

```java
@RequestMapping("/api/event-analysis")  // 原 /api/eventAnalysis
```

- [ ] **Step 2: 编译验证 + 提交**

```bash
./mvnw clean compile 2>&1 | tail -5
git add src/main/java/com/qy/dch/controller/EventAnalysisController.java
git commit -m "refactor(event-analysis): kebab-case path"
```

---

## Task 25: FusionController 路径 kebab-case 化

**Files:**
- Modify: `src/main/java/com/qy/dch/controller/FusionController.java`

- [ ] **Step 1: 修改方法路径**

```java
@PostMapping("/search-by-target")  // 原 /searchByTarget
public ResultVO searchByTarget(...)
```

- [ ] **Step 2: 编译验证 + 提交**

```bash
./mvnw clean compile 2>&1 | tail -5
git add src/main/java/com/qy/dch/controller/FusionController.java
git commit -m "refactor(fusion): kebab-case search-by-target endpoint"
```

---

## Task 26: 第四波启动测试

- [ ] **Step 1: 全量打包并启动**

```bash
./mvnw clean package -DskipTests 2>&1 | tail -3
$JAVA_HOME/bin/java -jar target/uygur-project-0.0.1-SNAPSHOT.jar > /tmp/app.log 2>&1 &
echo $! > /tmp/app.pid
sleep 15
grep "Started DchApplication" /tmp/app.log
```

预期：`Started DchApplication`

- [ ] **Step 2: 批量验证关键路径**

```bash
echo "=== /api/dashboard/overview ==="
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8081/api/dashboard/overview
echo "=== /api/origin-text/config ==="
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8081/api/origin-text/config
echo "=== /api/category/tree ==="
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8081/api/category/tree
echo "=== /api/directory/tree ==="
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8081/api/directory/tree
echo "=== /api/target/alias/list ==="
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8081/api/target/alias/list
echo "=== /api/event-analysis/status?date=2026-06-15 ==="
curl -s -o /dev/null -w "%{http_code}\n" "http://localhost:8081/api/event-analysis/status?date=2026-06-15"
echo "=== /api/extraction/result/1 ==="
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8081/api/extraction/result/1
echo "=== /api/rag/index/status ==="
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8081/api/rag/index/status
```

预期：全部返回 200

- [ ] **Step 3: 停止应用**

```bash
kill $(cat /tmp/app.pid)
```

第四波完成，无新提交。

---

# 第五波：异常处理 + 复杂 SQL 迁 XML + Bean Validation

## Task 27: 统一 DI 方式为 @RequiredArgsConstructor（Controller 层）

**Files:**
- Modify: 11 个 Controller

- [ ] **Step 1: 列出所有 Controller 的 DI 状况**

```bash
grep -B1 -A3 "@Autowired\|@Resource" src/main/java/com/qy/dch/controller/*.java | head -50
```

- [ ] **Step 2: 改造每个 Controller**

对每个 Controller 做以下操作（以 OriginTextController 为例）：

```java
// 改前
@RestController
@RequestMapping("/api/origin-text")
@Slf4j
public class OriginTextController {

    @Autowired
    OriginTextService originTextService;

    @Value("${minio.endpoint}")
    private String minioEndpoint;

// 改后
@RestController
@RequestMapping("/api/origin-text")
@RequiredArgsConstructor
@Slf4j
public class OriginTextController {

    private final OriginTextService originTextService;

    @Value("${minio.endpoint}")
    private String minioEndpoint;  // @Value 字段保留为非 final（不在构造器注入范围）
```

对所有 11 个 Controller 重复上述模式：
- 类上加 `@RequiredArgsConstructor`
- `@Autowired` / `@Resource` 字段改为 `private final`
- 删除 `@Autowired` / `@Resource` 注解
- `@Value` 字段保持原样

- [ ] **Step 3: 编译验证**

```bash
./mvnw clean compile 2>&1 | tail -5
```

预期：`BUILD SUCCESS`

- [ ] **Step 4: 提交**

```bash
git add src/main/java/com/qy/dch/controller/
git commit -m "refactor(controller): unify DI to @RequiredArgsConstructor"
```

---

## Task 28: 统一 DI 方式为 @RequiredArgsConstructor（Service 层）

**Files:**
- Modify: 12 个 Service Impl

- [ ] **Step 1: 改造每个 ServiceImpl**

对每个 ServiceImpl 类做：
- 类上确保有 `@RequiredArgsConstructor`
- `@Autowired` / `@Resource` 字段改为 `private final`
- `@Value` 字段保持非 final（Spring 通过反射注入）

对 Mapper 字段类型不能用 final 的特殊情况（如有需要 `@Lazy`），保持注解形式但加注释说明原因。

- [ ] **Step 2: 编译验证**

```bash
./mvnw clean compile 2>&1 | tail -5
```

预期：`BUILD SUCCESS`

- [ ] **Step 3: 启动测试**

```bash
./mvnw package -DskipTests 2>&1 | tail -3
$JAVA_HOME/bin/java -jar target/uygur-project-0.0.1-SNAPSHOT.jar > /tmp/app.log 2>&1 &
echo $! > /tmp/app.pid
sleep 15
grep -E "Started DchApplication|UnsatisfiedDependencyException" /tmp/app.log
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8081/api/dashboard/overview
kill $(cat /tmp/app.pid)
```

预期：启动成功，无依赖注入异常。

- [ ] **Step 4: 提交**

```bash
git add src/main/java/com/qy/dch/service/impl/
git commit -m "refactor(service): unify DI to @RequiredArgsConstructor"
```

---

## Task 29: 删除 Controller try-catch 模板

**Files:**
- Modify: 11 个 Controller

对每个 Controller 的方法，按规则改造：

**保留**：
- 方法体只是简单调用 Service 并返回 ResultVO.success(...)
- 方法体含 if 业务校验（这部分迁移到 Service）

**删除 try-catch**：

```java
// 改前
@DeleteMapping("/{sid}")
public ResultVO deleteText(@PathVariable String sid) {
    log.info("deleteText: sid={}", sid);
    try {
        boolean success = originTextService.deleteText(sid);
        if (success) {
            return ResultVO.success("删除成功");
        } else {
            return ResultVO.error("删除失败，报文不存在");
        }
    } catch (Exception e) {
        log.error("删除报文失败", e);
        return ResultVO.error("删除失败: " + e.getMessage());
    }
}

// 改后
@DeleteMapping("/{sid}")
public ResultVO deleteText(@PathVariable String sid) {
    log.info("deleteText: sid={}", sid);
    originTextService.deleteText(sid);  // Service 内：不存在时抛 BusinessException
    return ResultVO.success();
}
```

并同步修改 Service 层方法：

```java
// OriginTextServiceImpl.deleteText
public void deleteText(String sid) {
    int rows = originTextMapper.deleteById(sid);
    if (rows == 0) {
        throw new BusinessException(ErrorCode.ORIGIN_TEXT_NOT_FOUND);
    }
}
```

- [ ] **Step 1: 逐个 Controller 改造**

逐文件处理 11 个 Controller。重点关注：
- OriginTextController（最复杂，~20 个方法）
- CategoryController
- FusionController
- ExtractionController
- TargetController
- DatabaseController
- DirectoryController（已 try-catch 含错误响应，统一改异常）
- EventAnalysisController
- DocumentController
- RagController
- DashboardController

- [ ] **Step 2: 同步 Service 层抛异常**

把 Controller 中"找不到时返回 error"的语义，下沉到 Service 抛 BusinessException(ErrorCode.XXX_NOT_FOUND)。

- [ ] **Step 3: 编译验证**

```bash
./mvnw clean compile 2>&1 | tail -5
```

预期：`BUILD SUCCESS`

- [ ] **Step 4: 启动 + 测试错误路径**

```bash
./mvnw package -DskipTests 2>&1 | tail -3
$JAVA_HOME/bin/java -jar target/uygur-project-0.0.1-SNAPSHOT.jar > /tmp/app.log 2>&1 &
echo $! > /tmp/app.pid
sleep 15

# 测试不存在的资源，确认 ErrorHandler 工作
curl -s http://localhost:8081/api/origin-text/9999999 | head -c 200
# 预期：{"code":0,"msg":"报文不存在",...}

curl -s http://localhost:8081/api/category/9999999 | head -c 200
# 预期：{"code":0,"msg":"分类不存在",...}

kill $(cat /tmp/app.pid)
```

- [ ] **Step 5: 提交**

```bash
git add src/main/java/com/qy/dch/controller/ src/main/java/com/qy/dch/service/
git commit -m "refactor: remove Controller try-catch, throw BusinessException in Service"
```

---

## Task 30: 增加 Bean Validation 注解

**Files:**
- Modify: 部分 Controller（含必填参数校验的方法）

- [ ] **Step 1: 类上加 @Validated**

```java
@RestController
@RequestMapping("/api/origin-text")
@RequiredArgsConstructor
@Slf4j
@Validated  // 新增
public class OriginTextController {
```

- [ ] **Step 2: 参数加校验**

```java
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Min;

@GetMapping("/{sid}")
public ResultVO getDetail(@PathVariable @NotBlank(message = "报文ID不能为空") String sid) { ... }

@PostMapping("/search-by-target")
public ResultVO searchByTarget(@RequestParam @NotBlank(message = "目标名称不能为空") String targetName,
                                @RequestParam(defaultValue = "10") @Min(1) Integer maxReports) { ... }
```

- [ ] **Step 3: 编译验证 + 启动测试**

```bash
./mvnw clean compile 2>&1 | tail -5
./mvnw package -DskipTests 2>&1 | tail -3
$JAVA_HOME/bin/java -jar target/uygur-project-0.0.1-SNAPSHOT.jar > /tmp/app.log 2>&1 &
echo $! > /tmp/app.pid
sleep 15

# 测试参数校验
curl -s "http://localhost:8081/api/fusion/search-by-target?targetName=" | head -c 200
# 预期：{"code":0,"msg":"参数校验失败: ..."}

kill $(cat /tmp/app.pid)
```

- [ ] **Step 4: 提交**

```bash
git add src/main/java/com/qy/dch/controller/
git commit -m "refactor(controller): use @Validated + Bean Validation for params"
```

---

## Task 31: 复杂 SQL 迁移到 XML（OriginTextMapper）

**Files:**
- Create: `src/main/resources/mapper/OriginTextMapper.xml`
- Modify: `src/main/java/com/qy/dch/mapper/OriginTextMapper.java`

- [ ] **Step 1: 启用 MyBatis SQL 日志**

修改 `src/main/resources/application.yml`：
```yaml
mybatis-plus:
  type-aliases-package: com.qy.dch.dto
  mapper-locations: classpath:mapper/*.xml
  configuration:
    map-underscore-to-camel-case: true
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl  # 新增此行
```

- [ ] **Step 2: 创建 XML**

定位 OriginTextMapper 中复杂查询（>5 行 或 含 `<script>` 的方法），如 `filterReports`、`countFilterReports`、`getListByTypes`、`getListByModalTypes`、`getListWithFilter`、`countWithFilter`、`getReportsByCategoryAndSendUnit`：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.qy.dch.mapper.OriginTextMapper">

    <resultMap id="OriginTextResultMap" type="com.qy.dch.dto.OriginTextDTO">
        <id column="id" property="sid"/>
        <result column="title" property="title"/>
        <result column="content" property="content"/>
        <result column="times" property="times"/>
        <result column="type" property="type"/>
        <result column="modal_type" property="modalType"/>
        <result column="is_extracted" property="isExtracted"/>
        <result column="images" property="images"/>
        <result column="send_unit_name" property="sendUnitName"/>
        <result column="brief_type_name" property="briefTypeName"/>
        <result column="category" property="category"/>
    </resultMap>

    <sql id="originTextColumns">
        id, title, content, times, type, modal_type, is_extracted,
        images, send_unit_name, brief_type_name, category
    </sql>

    <select id="filterReports" resultMap="OriginTextResultMap">
        SELECT <include refid="originTextColumns"/>
        FROM origin_text
        <where>
            <if test="category != null and category != ''">
                AND category = #{category}
            </if>
            <if test="sendUnit != null and sendUnit != ''">
                AND send_unit_name = #{sendUnit}
            </if>
            <if test="keyword != null and keyword != ''">
                AND (title LIKE CONCAT('%', #{keyword}, '%') OR content LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            <if test="startDate != null and startDate != ''">
                AND times >= #{startDate}
            </if>
            <if test="endDate != null and endDate != ''">
                AND times &lt;= #{endDate}
            </if>
        </where>
        ORDER BY id DESC
        LIMIT #{pageSize} OFFSET #{offset}
    </select>

    <!-- 同样迁移 countFilterReports、getListByTypes、getReportsByCategoryAndSendUnit 等 -->
</mapper>
```

- [ ] **Step 3: 删除 OriginTextMapper.java 中对应方法上的 @Select 注解**

```java
// 改前
@Select("<script>...</script>")
List<OriginTextDTO> filterReports(@Param("category") String category, ...);

// 改后（只留方法签名）
List<OriginTextDTO> filterReports(@Param("category") String category, ...);
```

- [ ] **Step 4: 编译验证**

```bash
./mvnw clean compile 2>&1 | tail -5
```

预期：`BUILD SUCCESS`

- [ ] **Step 5: 启动 + SQL 日志查看**

```bash
./mvnw package -DskipTests 2>&1 | tail -3
$JAVA_HOME/bin/java -jar target/uygur-project-0.0.1-SNAPSHOT.jar > /tmp/app.log 2>&1 &
echo $! > /tmp/app.pid
sleep 15
curl -s "http://localhost:8081/api/origin-text?pageNum=1&pageSize=5" | head -c 200
# 日志应包含 SQL 输出（StdOutImpl）
grep "Preparing:" /tmp/app.log | head -3
kill $(cat /tmp/app.pid)
```

预期：接口返回 200，日志含 `==>  Preparing: SELECT ...`

- [ ] **Step 6: 提交**

```bash
git add src/main/java/com/qy/dch/mapper/OriginTextMapper.java src/main/resources/mapper/OriginTextMapper.xml src/main/resources/application.yml
git commit -m "refactor(mapper): migrate complex OriginText SQL to XML + enable SQL log"
```

---

## Task 32: 复杂 SQL 迁移到 XML（TextTypeMapper、ImportMapper、其他）

**Files:**
- Create: `src/main/resources/mapper/TextTypeMapper.xml`
- Create: `src/main/resources/mapper/ImportMapper.xml`
- Create: `src/main/resources/mapper/ExtractionMapper.xml`（仅在有复杂 SQL 时）
- Create: `src/main/resources/mapper/EventAnalysisMapper.xml`
- Create: `src/main/resources/mapper/TargetAnalysisMapper.xml`

- [ ] **Step 1: 按 Mapper 逐个判断**

对每个 Mapper 接口，定位含 `<script>` 标签、>5 行 SQL 字符串、含 JOIN 的方法：

```bash
for f in src/main/java/com/qy/dch/mapper/*.java; do
  echo "=== $f ==="
  grep -c "<script>" $f
done
```

- [ ] **Step 2: 对每个有复杂 SQL 的 Mapper 创建 XML**

参考 Task 31 的格式。**确保 namespace 与 Mapper 接口完全一致**。

- [ ] **Step 3: 编译 + 启动测试**

```bash
./mvnw clean compile 2>&1 | tail -5
./mvnw package -DskipTests 2>&1 | tail -3
$JAVA_HOME/bin/java -jar target/uygur-project-0.0.1-SNAPSHOT.jar > /tmp/app.log 2>&1 &
echo $! > /tmp/app.pid
sleep 15
grep "Started DchApplication\|Exception" /tmp/app.log | head -5
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8081/api/dashboard/overview
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8081/api/category/tree
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8081/api/origin-text?pageNum=1
kill $(cat /tmp/app.pid)
```

预期：启动成功，所有接口返回 200

- [ ] **Step 4: 提交**

```bash
git add src/main/resources/mapper/ src/main/java/com/qy/dch/mapper/
git commit -m "refactor(mapper): migrate complex SQL to XML for all mappers"
```

---

# 第六波：测试

## Task 33: CategoryClassifier 单元测试

**Files:**
- Create: `src/test/java/com/qy/dch/util/CategoryClassifierTest.java`

- [ ] **Step 1: 写测试**

```java
package com.qy.dch.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CategoryClassifierTest {

    @Test
    void classify_hzReport_byHzPattern() {
        assertEquals("HZ报", CategoryClassifier.classify("HZ123报内部", "内容"));
        assertEquals("HZ报", CategoryClassifier.classify("xxxHZ001报", "内容"));
    }

    @Test
    void classify_jzReport_byJzxContent() {
        assertEquals("JZ报", CategoryClassifier.classify("某单位", "这里有JZX字样"));
    }

    @Test
    void classify_openSource_byQbContent() {
        assertEquals("开源信息", CategoryClassifier.classify("某单位", "入网QB内容"));
    }

    @Test
    void classify_priorityHzOverJz() {
        // HZ 优先级最高，即便有 JZX 字样
        assertEquals("HZ报", CategoryClassifier.classify("HZ001报", "JZX 字样"));
    }

    @Test
    void classify_priorityJzOverOpenSource() {
        // JZ 优先于开源
        assertEquals("JZ报", CategoryClassifier.classify("某单位", "JZX 入网QB"));
    }

    @Test
    void classify_unclassified_default() {
        assertEquals("未分类", CategoryClassifier.classify("某单位", "普通内容"));
    }

    @Test
    void classify_nullInput_returnUnclassified() {
        assertEquals("未分类", CategoryClassifier.classify(null, null));
    }
}
```

- [ ] **Step 2: 运行测试**

```bash
./mvnw test -Dtest=CategoryClassifierTest 2>&1 | tail -15
```

预期：`Tests run: 7, Failures: 0, Errors: 0`

- [ ] **Step 3: 提交**

```bash
git add src/test/java/com/qy/dch/util/CategoryClassifierTest.java
git commit -m "test(util): add CategoryClassifier unit tests"
```

---

## Task 34: AlgorithmClient 单元测试

**Files:**
- Create: `src/test/java/com/qy/dch/service/client/AlgorithmClientImplTest.java`

- [ ] **Step 1: 写测试**

```java
package com.qy.dch.service.client;

import com.alibaba.fastjson.JSONObject;
import com.qy.dch.common.AlgorithmServiceException;
import com.qy.dch.common.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.http.*;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.net.SocketTimeoutException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AlgorithmClientImplTest {

    private RestTemplate restTemplate;
    private AlgorithmClientImpl client;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        client = new AlgorithmClientImpl(restTemplate, "http://test.local");
    }

    @Test
    void extract_success_returnsJson() {
        when(restTemplate.postForEntity(eq("http://test.local/extract"), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("{\"code\":1,\"data\":{\"events\":[]}}"));
        JSONObject result = client.extract("某内容", "1");
        assertEquals(1, result.getInteger("code"));
    }

    @Test
    void extract_http500_throwsUnavailable() {
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>("err", HttpStatus.INTERNAL_SERVER_ERROR));
        AlgorithmServiceException ex = assertThrows(AlgorithmServiceException.class,
                () -> client.extract("某内容", "1"));
        assertEquals(ErrorCode.ALGORITHM_SERVICE_UNAVAILABLE, ex.getErrorCode());
    }

    @Test
    void extract_timeout_throwsTimeout() {
        ResourceAccessException cause = new ResourceAccessException("timeout", new SocketTimeoutException("read timeout"));
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenThrow(cause);
        AlgorithmServiceException ex = assertThrows(AlgorithmServiceException.class,
                () -> client.extract("某内容", "1"));
        assertEquals(ErrorCode.ALGORITHM_SERVICE_TIMEOUT, ex.getErrorCode());
    }

    @Test
    void extract_emptyResponse_throwsInvalid() {
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(null));
        AlgorithmServiceException ex = assertThrows(AlgorithmServiceException.class,
                () -> client.extract("某内容", "1"));
        assertEquals(ErrorCode.ALGORITHM_SERVICE_INVALID_RESPONSE, ex.getErrorCode());
    }
}
```

- [ ] **Step 2: 运行测试**

```bash
./mvnw test -Dtest=AlgorithmClientImplTest 2>&1 | tail -15
```

预期：`Tests run: 4, Failures: 0, Errors: 0`

- [ ] **Step 3: 提交**

```bash
git add src/test/java/com/qy/dch/service/client/AlgorithmClientImplTest.java
git commit -m "test(client): add AlgorithmClient unit tests"
```

---

## Task 35: 集成测试 - 所有 Controller 路径 ping

**Files:**
- Create: `src/test/java/com/qy/dch/api/ControllerSmokeTest.java`

- [ ] **Step 1: 写集成测试**

```java
package com.qy.dch.api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ControllerSmokeTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void dashboardOverview_returns200() throws Exception {
        mockMvc.perform(get("/api/dashboard/overview"))
                .andExpect(status().isOk());
    }

    @Test
    void categoryTree_returns200() throws Exception {
        mockMvc.perform(get("/api/category/tree"))
                .andExpect(status().isOk());
    }

    @Test
    void directoryTree_returns200() throws Exception {
        mockMvc.perform(get("/api/directory/tree"))
                .andExpect(status().isOk());
    }

    @Test
    void originTextFilter_returns200() throws Exception {
        mockMvc.perform(get("/api/origin-text").param("pageNum", "1").param("pageSize", "10"))
                .andExpect(status().isOk());
    }

    @Test
    void targetAliasList_returns200() throws Exception {
        mockMvc.perform(get("/api/target/alias/list"))
                .andExpect(status().isOk());
    }

    @Test
    void ragIndexStatus_returns200() throws Exception {
        mockMvc.perform(get("/api/rag/index/status"))
                .andExpect(status().isOk());
    }

    @Test
    void invalidOriginTextId_returnsBusinessError() throws Exception {
        mockMvc.perform(get("/api/origin-text/99999999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value("报文不存在"));
    }

    @Test
    void searchByTarget_emptyParam_returnsValidationError() throws Exception {
        mockMvc.perform(post("/api/fusion/search-by-target").param("targetName", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }
}
```

- [ ] **Step 2: 运行测试**

```bash
./mvnw test -Dtest=ControllerSmokeTest 2>&1 | tail -20
```

预期：`Tests run: 8, Failures: 0`

注意：此测试需要数据库连接（连接 36.141.21.176）。若 CI 环境无法访问，可加 `@org.springframework.context.annotation.Profile("integration")` 注解过滤。

- [ ] **Step 3: 提交**

```bash
git add src/test/java/com/qy/dch/api/ControllerSmokeTest.java
git commit -m "test(api): add controller smoke tests"
```

---

## Task 36: 全量测试通过验证

- [ ] **Step 1: 运行全部测试**

```bash
./mvnw test 2>&1 | tail -20
```

预期：`Tests run: ≥17, Failures: 0`

- [ ] **Step 2: 验证打包**

```bash
./mvnw clean package 2>&1 | tail -5
```

预期：`BUILD SUCCESS`

第六波完成。

---

# 第七波：文档与最终验收

## Task 37: 更新 OpenAPI 文档

**Files:**
- Modify: `docs/献微系统 - 后端 API (xwBackend).openapi.json`
- Modify: `../../献微系统 - 后端 API (xwBackend).openapi.json`（项目根的副本）

- [ ] **Step 1: 用 Python 脚本批量改路径**

```python
import json

path_mapping = {
    '/api/uygur/config': '/api/origin-text/config',
    '/api/uygur/savetext': '/api/origin-text/import-default',
    '/api/uygur/detail/{sid}': '/api/origin-text/{sid}',
    '/api/uygur/getTextList': '/api/origin-text/list',
    '/api/uygur/resetExtracted': '/api/origin-text/reset-extracted',
    '/api/uygur/importFromJsonl': '/api/origin-text/import/jsonl',
    '/api/uygur/importFromJsonlWithImages': '/api/origin-text/import/jsonl-with-images',
    '/api/uygur/filter': '/api/origin-text',
    '/api/uygur/text/{sid}': '/api/origin-text/{sid}',
    '/api/uygur/text/batchDelete': '/api/origin-text/batch-delete',
    '/api/uygur/text/updateByOldType': '/api/origin-text/batch-update-type',
    '/api/category/create': '/api/category',
    '/api/category/update': '/api/category/{categoryId}',
    '/api/category/move': '/api/category/{categoryId}/parent',
    '/api/category/delete/{categoryId}': '/api/category/{categoryId}',
    '/api/category/detail/{categoryId}': '/api/category/{categoryId}',
    '/api/eventAnalysis/trigger': '/api/event-analysis/trigger',
    '/api/eventAnalysis/query': '/api/event-analysis/query',
    '/api/eventAnalysis/status': '/api/event-analysis/status',
    '/extraction/extract': '/api/extraction/extract',
    '/extraction/result/{originTextId}': '/api/extraction/result/{originTextId}',
    '/extraction/batch/start': '/api/extraction/batch/start',
    '/extraction/batch/progress/{taskId}': '/api/extraction/batch/progress/{taskId}',
    '/extraction/batch/stop/{taskId}': '/api/extraction/batch/stop/{taskId}',
    '/api/fusion/searchByTarget': '/api/fusion/search-by-target',
}

paths_to_delete = {
    '/api/uygur/category',
    '/api/uygur/category/{categoryId}',
    '/api/uygur/category/batchDelete',
    '/api/uygur/category/{categoryId}/withTexts',
    '/api/uygur/addCategory',
    '/test/queryCategory',
    '/test/testMultipart',
}

for src in [
    'xwSystem/xwBackend/docs/献微系统 - 后端 API (xwBackend).openapi.json',
    '献微系统 - 后端 API (xwBackend).openapi.json',
]:
    with open(src, encoding='utf-8') as f:
        data = json.load(f)
    new_paths = {}
    for p, methods in data['paths'].items():
        if p in paths_to_delete:
            continue
        new_p = path_mapping.get(p, p)
        new_paths[new_p] = methods
    data['paths'] = new_paths
    with open(src, 'w', encoding='utf-8') as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
```

- [ ] **Step 2: 提交**

```bash
git add "docs/献微系统 - 后端 API (xwBackend).openapi.json" "../../献微系统 - 后端 API (xwBackend).openapi.json"
git commit -m "docs(openapi): sync paths with refactored controllers"
```

---

## Task 38: 更新 xwBackend-complete-reference.md

**Files:**
- Modify: `docs/xwBackend-complete-reference.md`（项目根 docs/）

- [ ] **Step 1: 阅读现有内容**

Read `/Users/processmonitor/Documents/IdeaProject/xwProject/docs/xwBackend-complete-reference.md`，找出涉及以下内容的章节：
- 包结构 / 目录结构章节 → 更新拆分后的 Mapper 列表
- API 列表章节 → 同步新路径
- 异常处理章节 → 加入新 ErrorCode 与 AlgorithmServiceException
- 调用算法服务章节 → 改为通过 AlgorithmClient

- [ ] **Step 2: 文风对齐更新**

保持原有文风（表格、章节编号、术语用词），更新：
- 包结构图（增加 `service/client/`，删除 UygurMapper）
- Controller 路径表（按新路径更新）
- 异常码表（加入新增的 ORIGIN_TEXT_NOT_FOUND、ALGORITHM_SERVICE_*）
- 算法服务调用一节，改为：`所有 Service 通过 AlgorithmClient 统一调用，详见 service/client/AlgorithmClient.java`

- [ ] **Step 3: 提交**

```bash
cd /Users/processmonitor/Documents/IdeaProject/xwProject
git add docs/xwBackend-complete-reference.md
git commit -m "docs(reference): update for refactor (mappers split, AlgorithmClient, RESTful paths)"
```

---

## Task 39: 最终验收

- [ ] **Step 1: 全量编译 + 测试**

```bash
cd /Users/processmonitor/Documents/IdeaProject/xwProject/xwSystem/xwBackend
./mvnw clean package 2>&1 | tail -5
```

预期：`BUILD SUCCESS`，测试全过

- [ ] **Step 2: 验收检查清单（grep 验证）**

```bash
echo "=== 1. 无裸 RuntimeException ==="
grep -rn "throw new RuntimeException" src/main/java/ | wc -l
# 预期：0

echo "=== 2. 无 @Autowired 字段注入 ==="
grep -rn "@Autowired" src/main/java/ | wc -l
# 预期：0（或极少数特殊情况）

echo "=== 3. 无 @Resource ==="
grep -rn "@Resource" src/main/java/ | wc -l
# 预期：0

echo "=== 4. 无 HttpURLConnection ==="
grep -rn "HttpURLConnection" src/main/java/ | wc -l
# 预期：0

echo "=== 5. 无 UygurMapper / UygurService / UygurController ==="
grep -rn "UygurMapper\|UygurService\b\|UygurController" src/main/java/ | wc -l
# 预期：0

echo "=== 6. Controller 中无 try-catch 模板 ==="
grep -rln "return ResultVO.error" src/main/java/com/qy/dch/controller/
# 预期：极少（只有特殊情况）

echo "=== 7. 路径无遗留 /uygur ==="
grep -rn '"/uygur' src/main/java/com/qy/dch/controller/
# 预期：空

echo "=== 8. 路径无遗留 camelCase 在 mapping 中 ==="
grep -rn 'Mapping("/.*[A-Z]' src/main/java/com/qy/dch/controller/
# 预期：极少（一些方法名残留）
```

- [ ] **Step 3: 启动 + 全 Controller 接口烟雾测试**

```bash
$JAVA_HOME/bin/java -jar target/uygur-project-0.0.1-SNAPSHOT.jar > /tmp/app.log 2>&1 &
echo $! > /tmp/app.pid
sleep 15

for url in \
  "/api/dashboard/overview" \
  "/api/dashboard/categoryDistribution" \
  "/api/category/tree" \
  "/api/category/leafs" \
  "/api/directory/tree" \
  "/api/origin-text/config" \
  "/api/origin-text?pageNum=1&pageSize=10" \
  "/api/target/alias/list" \
  "/api/target/fusion/list" \
  "/api/rag/index/status" \
  "/api/extraction/result/1" \
; do
  code=$(curl -s -o /dev/null -w "%{http_code}" "http://localhost:8081$url")
  echo "$code $url"
done

kill $(cat /tmp/app.pid)
```

预期：全部 200。

- [ ] **Step 4: 复制最终 jar 到部署目录**

```bash
cp target/uygur-project-0.0.1-SNAPSHOT.jar /Users/processmonitor/Downloads/176-deploy/backend/
ls -la /Users/processmonitor/Downloads/176-deploy/backend/uygur-project-0.0.1-SNAPSHOT.jar
```

- [ ] **Step 5: 最终提交 + 打 tag**

```bash
cd /Users/processmonitor/Documents/IdeaProject/xwProject
git tag refactor-complete-2026-06-15
echo "✅ 全面重构完成"
```

---

# 自检清单

| 项 | 状态 |
|----|------|
| ErrorCode 扩充 | Task 1 |
| AlgorithmServiceException | Task 2 |
| GlobalExceptionHandler 扩展 | Task 3 |
| UygurMapper 拆分（OriginText + TextType + Import） | Task 5-10 |
| AlgorithmClient 创建与实现 | Task 11-12 |
| 5 个 Service 切换到 AlgorithmClient | Task 13-17 |
| 删除 TestController | Task 19 |
| ExtractionController 加 /api 前缀 | Task 20 |
| UygurController 改名 OriginTextController | Task 21 |
| 路径 RESTful 化 + kebab-case | Task 22-25 |
| 统一 @RequiredArgsConstructor | Task 27-28 |
| 删除 Controller try-catch | Task 29 |
| Bean Validation | Task 30 |
| SQL 迁 XML | Task 31-32 |
| 单元测试（Classifier、AlgorithmClient） | Task 33-34 |
| 集成测试（Controller Smoke） | Task 35 |
| OpenAPI 同步 | Task 37 |
| xwBackend-complete-reference.md 同步 | Task 38 |
| 最终验收 | Task 39 |

