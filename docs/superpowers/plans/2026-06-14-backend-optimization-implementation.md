# Backend 混合式架构优化实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 采用 MyBatis-Plus 重构高风险模块，消除 SQL 注入风险，统一异常处理和参数校验，提升代码质量和系统安全性

**Architecture:** 混合式迁移策略 - RagMapper/DatabaseServiceImpl 等高风险模块用 MyBatis-Plus 彻底重构，FusionMapper 等稳定 XML Mapper 保留不动，新增全局异常处理和参数校验体系

**Tech Stack:** MyBatis-Plus 3.5.5, Spring Boot 2.7.18, Druid 1.2.20, JSR-303 Validation

---

## 阶段一：基础设施搭建（预计 1 天）

### Task 1: 升级依赖和配置 MyBatis-Plus

**Files:**
- Modify: `xwSystem/xwBackend/pom.xml`
- Create: `xwSystem/xwBackend/src/main/java/com/qy/dch/config/MybatisPlusConfig.java`
- Modify: `xwSystem/xwBackend/src/main/resources/application.yml`

- [ ] **Step 1: 修改 pom.xml 依赖**

在 `xwSystem/xwBackend/pom.xml` 的 `<dependencies>` 区块中：

```xml
<!-- 注释掉原 MyBatis Starter -->
<!--
<dependency>
    <groupId>org.mybatis.spring.boot</groupId>
    <artifactId>mybatis-spring-boot-starter</artifactId>
    <version>2.3.2</version>
</dependency>
-->

<!-- 新增 MyBatis-Plus -->
<dependency>
    <groupId>com.baomidou</groupId>
    <artifactId>mybatis-plus-boot-starter</artifactId>
    <version>3.5.5</version>
</dependency>

<!-- 确保 validation 依赖存在 -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>
```

- [ ] **Step 2: 创建 MybatisPlusConfig 配置类**

新建 `xwSystem/xwBackend/src/main/java/com/qy/dch/config/MybatisPlusConfig.java`：

```java
package com.qy.dch.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("com.qy.dch.mapper")
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        
        // 分页插件
        PaginationInnerInterceptor paginationInterceptor = 
            new PaginationInnerInterceptor(DbType.MYSQL);
        paginationInterceptor.setMaxLimit(500L);
        paginationInterceptor.setOverflow(false);
        interceptor.addInnerInterceptor(paginationInterceptor);
        
        // 乐观锁插件
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        
        return interceptor;
    }
}
```

- [ ] **Step 3: 修改 application.yml 配置**

在 `xwSystem/xwBackend/src/main/resources/application.yml` 中，将 `mybatis:` 配置块替换为：

```yaml
mybatis-plus:
  mapper-locations: classpath:mapper/*.xml
  type-aliases-package: com.qy.dch.dto,com.qy.dch.entity
  configuration:
    map-underscore-to-camel-case: true
    log-impl: org.apache.ibatis.logging.slf4j.Slf4jImpl
  global-config:
    db-config:
      id-type: auto
      logic-delete-field: deleted
      logic-delete-value: 1
      logic-not-delete-value: 0
```

同时优化 Druid 连接池配置（在 `spring.datasource.druid` 下）：

```yaml
spring:
  datasource:
    druid:
      initial-size: 10
      min-idle: 10
      max-active: 50
      max-wait: 30000
      time-between-eviction-runs-millis: 60000
      min-evictable-idle-time-millis: 300000
      validation-query: SELECT 1
      test-while-idle: true
      test-on-borrow: false
      pool-prepared-statements: false
      filters: stat,wall,slf4j
      filter:
        stat:
          enabled: true
          slow-sql-millis: 2000
          log-slow-sql: true
        wall:
          enabled: true
          config:
            delete-where-none-check: true
```

- [ ] **Step 4: 启动项目验证配置**

运行：
```bash
cd xwSystem/xwBackend
mvn clean compile
```

预期输出：`BUILD SUCCESS`，无 MyBatis 或 MyBatis-Plus 相关错误

- [ ] **Step 5: 提交基础配置**

```bash
git add xwSystem/xwBackend/pom.xml
git add xwSystem/xwBackend/src/main/java/com/qy/dch/config/MybatisPlusConfig.java
git add xwSystem/xwBackend/src/main/resources/application.yml
git commit -m "feat: 升级至 MyBatis-Plus 3.5.5 并优化连接池配置

- 注释原 MyBatis Starter，引入 MyBatis-Plus
- 配置分页和乐观锁插件
- Druid 连接池优化：max-active 50，慢 SQL 告警 2s

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 2: 创建基础实体类和异常体系

**Files:**
- Create: `xwSystem/xwBackend/src/main/java/com/qy/dch/entity/BaseEntity.java`
- Create: `xwSystem/xwBackend/src/main/java/com/qy/dch/config/MetaObjectHandlerConfig.java`
- Create: `xwSystem/xwBackend/src/main/java/com/qy/dch/common/ErrorCode.java`
- Create: `xwSystem/xwBackend/src/main/java/com/qy/dch/common/BusinessException.java`

- [ ] **Step 1: 创建 BaseEntity 基类**

新建 `xwSystem/xwBackend/src/main/java/com/qy/dch/entity/BaseEntity.java`：

```java
package com.qy.dch.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public abstract class BaseEntity implements Serializable {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    
    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;
}
```

- [ ] **Step 2: 创建字段自动填充处理器**

新建 `xwSystem/xwBackend/src/main/java/com/qy/dch/config/MetaObjectHandlerConfig.java`：

```java
package com.qy.dch.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class MetaObjectHandlerConfig implements MetaObjectHandler {
    
    @Override
    public void insertFill(MetaObject metaObject) {
        this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, LocalDateTime.now());
        this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
        this.strictInsertFill(metaObject, "deleted", Integer.class, 0);
    }
    
    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
    }
}
```

- [ ] **Step 3: 创建错误码枚举**

新建 `xwSystem/xwBackend/src/main/java/com/qy/dch/common/ErrorCode.java`：

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
    
    RAG_INDEXING_RUNNING(3001, "索引任务正在执行中"),
    RAG_SEARCH_FAILED(3002, "语义检索失败"),
    
    DB_TABLE_NOT_ALLOWED(4001, "不允许操作的表"),
    DB_FIELD_PROTECTED(4002, "核心字段禁止修改"),
    DB_DDL_FAILED(4003, "DDL 执行失败");
    
    private final int code;
    private final String message;
}
```

- [ ] **Step 4: 创建业务异常类**

新建 `xwSystem/xwBackend/src/main/java/com/qy/dch/common/BusinessException.java`：

```java
package com.qy.dch.common;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {
    
    private final ErrorCode errorCode;
    private final Object data;
    
    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.data = null;
    }
    
    public BusinessException(ErrorCode errorCode, String customMessage) {
        super(customMessage);
        this.errorCode = errorCode;
        this.data = null;
    }
    
    public BusinessException(ErrorCode errorCode, String customMessage, Object data) {
        super(customMessage);
        this.errorCode = errorCode;
        this.data = data;
    }
}
```

- [ ] **Step 5: 编译验证新增类**

运行：
```bash
cd xwSystem/xwBackend
mvn clean compile
```

预期输出：`BUILD SUCCESS`

- [ ] **Step 6: 提交基础实体和异常体系**

```bash
git add xwSystem/xwBackend/src/main/java/com/qy/dch/entity/BaseEntity.java
git add xwSystem/xwBackend/src/main/java/com/qy/dch/config/MetaObjectHandlerConfig.java
git add xwSystem/xwBackend/src/main/java/com/qy/dch/common/ErrorCode.java
git add xwSystem/xwBackend/src/main/java/com/qy/dch/common/BusinessException.java
git commit -m "feat: 新增 BaseEntity 和业务异常体系

- BaseEntity 支持自动填充 createTime/updateTime/deleted
- ErrorCode 枚举统一错误码
- BusinessException 业务异常类

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 3: 创建全局异常处理器和增强 ResultVO

**Files:**
- Create: `xwSystem/xwBackend/src/main/java/com/qy/dch/config/GlobalExceptionHandler.java`
- Modify: `xwSystem/xwBackend/src/main/java/com/qy/dch/common/ResultVO.java`

- [ ] **Step 1: 创建全局异常处理器**

新建 `xwSystem/xwBackend/src/main/java/com/qy/dch/config/GlobalExceptionHandler.java`：

```java
package com.qy.dch.config;

import com.qy.dch.common.BusinessException;
import com.qy.dch.common.ErrorCode;
import com.qy.dch.common.ResultVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.sql.SQLIntegrityConstraintViolationException;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResultVO handleBusinessException(BusinessException e) {
        log.warn("业务异常: code={}, message={}", e.getErrorCode().getCode(), e.getMessage());
        return ResultVO.error(e.getErrorCode().getCode(), e.getMessage(), e.getData());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResultVO handleValidationException(MethodArgumentNotValidException e) {
        String errorMsg = e.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .collect(Collectors.joining("; "));
        
        log.warn("参数校验失败: {}", errorMsg);
        return ResultVO.error(ErrorCode.PARAM_INVALID.getCode(), errorMsg);
    }

    @ExceptionHandler(DataAccessException.class)
    public ResultVO handleDataAccessException(DataAccessException e) {
        log.error("数据库访问异常", e);
        
        if (e.getCause() instanceof SQLIntegrityConstraintViolationException) {
            return ResultVO.error(ErrorCode.DUPLICATE_KEY.getCode(), "数据重复或违反约束");
        }
        
        return ResultVO.error(ErrorCode.SYSTEM_ERROR.getCode(), "数据库操作失败");
    }

    @ExceptionHandler(Exception.class)
    public ResultVO handleException(Exception e) {
        log.error("系统异常", e);
        return ResultVO.error(ErrorCode.SYSTEM_ERROR.getCode(), "系统内部错误: " + e.getMessage());
    }
}
```

- [ ] **Step 2: 增强 ResultVO 响应类**

修改 `xwSystem/xwBackend/src/main/java/com/qy/dch/common/ResultVO.java`，添加新方法：

```java
package com.qy.dch.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResultVO {
    
    private Integer code;
    private Object data;
    private String msg;
    private Object flag;
    
    // 保留原有 success() 和 success(Object data) 方法
    public static ResultVO success() {
        return new ResultVO(1, null, "success", "true");
    }
    
    public static ResultVO success(Object data) {
        return new ResultVO(1, data, "success", "true");
    }
    
    // 保留原有 error(String msg) 方法
    public static ResultVO error(String msg) {
        return new ResultVO(0, null, msg, "false");
    }
    
    // 新增方法：支持错误码
    public static ResultVO error(Integer code, String message) {
        return new ResultVO(code, null, message, "false");
    }
    
    public static ResultVO error(Integer code, String message, Object data) {
        return new ResultVO(code, data, message, "false");
    }
    
    public static ResultVO error(ErrorCode errorCode) {
        return new ResultVO(errorCode.getCode(), null, errorCode.getMessage(), "false");
    }
}
```

- [ ] **Step 3: 编译验证**

```bash
cd xwSystem/xwBackend
mvn clean compile
```

预期：`BUILD SUCCESS`

- [ ] **Step 4: 提交异常处理器**

```bash
git add xwSystem/xwBackend/src/main/java/com/qy/dch/config/GlobalExceptionHandler.java
git add xwSystem/xwBackend/src/main/java/com/qy/dch/common/ResultVO.java
git commit -m "feat: 新增全局异常处理器并增强 ResultVO

- GlobalExceptionHandler 统一处理业务异常、参数校验异常、数据库异常
- ResultVO 支持 ErrorCode 错误码

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

## 阶段二：核心模块重构（预计 2 天）

### Task 4: 重构 RagMapper - 第一部分（创建实体类和基础查询）

**Files:**
- Create: `xwSystem/xwBackend/src/main/java/com/qy/dch/entity/OriginText.java`
- Modify: `xwSystem/xwBackend/src/main/java/com/qy/dch/mapper/RagMapper.java`

- [ ] **Step 1: 创建 OriginText 实体类**

新建 `xwSystem/xwBackend/src/main/java/com/qy/dch/entity/OriginText.java`：

```java
package com.qy.dch.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("origin_text")
public class OriginText extends BaseEntity {
    
    @TableField("title")
    private String title;
    
    @TableField("content")
    private String content;
    
    @TableField("times")
    private LocalDateTime times;
    
    @TableField("type")
    private Long type;
    
    @TableField("modal_type")
    private String modalType;
    
    @TableField("is_indexed")
    private Integer isIndexed;
    
    @TableField("is_extracted")
    private Integer isExtracted;
    
    @TableField("sendUnitName")
    private String sendUnitName;
    
    @TableField("briefTypeName")
    private String briefTypeName;
    
    @TableField("images")
    private String images;
}
```

- [ ] **Step 2: 重写 RagMapper（替换为 MyBatis-Plus 方式）**

完全替换 `xwSystem/xwBackend/src/main/java/com/qy/dch/mapper/RagMapper.java` 的内容为：

```java
package com.qy.dch.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.qy.dch.dto.OriginTextDTO;
import com.qy.dch.entity.OriginText;
import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Mapper
public interface RagMapper extends BaseMapper<OriginText> {
    
    /**
     * 查询未索引的文档 ID（带日期范围筛选）
     */
    default List<Long> selectUnindexedIds(String startDate, String endDate) {
        LambdaQueryWrapper<OriginText> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(OriginText::getId)
               .eq(OriginText::getIsIndexed, 0)
               .ge(StringUtils.isNotBlank(startDate), OriginText::getTimes, startDate)
               .le(StringUtils.isNotBlank(endDate), OriginText::getTimes, endDate)
               .orderByAsc(OriginText::getId);
        
        return this.selectList(wrapper).stream()
                   .map(OriginText::getId)
                   .collect(Collectors.toList());
    }
    
    /**
     * 查询所有未索引的文档 ID
     */
    default List<Long> selectAllUnindexedIds() {
        return selectUnindexedIds(null, null);
    }
    
    /**
     * 根据 ID 列表查询文档（返回 DTO）
     */
    default List<OriginTextDTO> selectByIds(List<Long> ids) {
        if (CollectionUtils.isEmpty(ids)) {
            return java.util.Collections.emptyList();
        }
        
        LambdaQueryWrapper<OriginText> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(OriginText::getId, ids)
               .orderByAsc(OriginText::getId);
        
        return this.selectList(wrapper).stream()
                   .map(this::convertToDTO)
                   .collect(Collectors.toList());
    }
    
    /**
     * 批量更新索引状态
     */
    default int updateIndexedStatus(List<Long> ids) {
        if (CollectionUtils.isEmpty(ids)) {
            return 0;
        }
        
        LambdaUpdateWrapper<OriginText> wrapper = new LambdaUpdateWrapper<>();
        wrapper.set(OriginText::getIsIndexed, 1)
               .in(OriginText::getId, ids);
        
        return this.update(null, wrapper);
    }
    
    /**
     * 单个更新索引状态
     */
    default int updateIndexedStatusById(Long sid) {
        LambdaUpdateWrapper<OriginText> wrapper = new LambdaUpdateWrapper<>();
        wrapper.set(OriginText::getIsIndexed, 1)
               .eq(OriginText::getId, sid);
        
        return this.update(null, wrapper);
    }
    
    /**
     * Entity 转 DTO
     */
    private OriginTextDTO convertToDTO(OriginText entity) {
        OriginTextDTO dto = new OriginTextDTO();
        dto.setSid(entity.getId());
        dto.setTitle(entity.getTitle());
        dto.setContent(entity.getContent());
        dto.setTimes(entity.getTimes());
        dto.setType(entity.getType());
        dto.setModalType(entity.getModalType());
        dto.setIsIndexed(entity.getIsIndexed());
        return dto;
    }
    
    /**
     * 统计查询（保留原 SQL）
     */
    @Select("SELECT COUNT(*) as total, " +
            "SUM(CASE WHEN is_indexed=1 THEN 1 ELSE 0 END) as indexedCount, " +
            "SUM(CASE WHEN is_indexed=0 THEN 1 ELSE 0 END) as unindexedCount " +
            "FROM origin_text")
    Map<String, Object> getIndexStats();
    
    @Select("SELECT DATE(times) as date, COUNT(*) as total, " +
            "SUM(CASE WHEN is_indexed=1 THEN 1 ELSE 0 END) as indexedCount, " +
            "SUM(CASE WHEN is_indexed=0 THEN 1 ELSE 0 END) as unindexedCount " +
            "FROM origin_text " +
            "WHERE times IS NOT NULL " +
            "GROUP BY DATE(times) " +
            "ORDER BY date DESC " +
            "LIMIT 30")
    List<Map<String, Object>> getIndexStatsByDate();
}
```

- [ ] **Step 3: 编译验证**

```bash
cd xwSystem/xwBackend
mvn clean compile
```

预期：`BUILD SUCCESS`

- [ ] **Step 4: 启动项目并测试 RagMapper**

```bash
mvn spring-boot:run
```

预期：项目正常启动，访问 `http://localhost:8081/api/rag/index/status` 返回正常响应

- [ ] **Step 5: 提交 RagMapper 重构**

```bash
git add xwSystem/xwBackend/src/main/java/com/qy/dch/entity/OriginText.java
git add xwSystem/xwBackend/src/main/java/com/qy/dch/mapper/RagMapper.java
git commit -m "refactor: RagMapper 迁移至 MyBatis-Plus

- 创建 OriginText 实体类继承 BaseEntity
- 用 LambdaQueryWrapper/LambdaUpdateWrapper 替换所有注解 SQL
- 消除 SQL 字符串拼接注入风险
- 保留统计查询的 @Select 注解（纯静态 SQL）

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 5: 安全加固 DatabaseServiceImpl

**Files:**
- Modify: `xwSystem/xwBackend/src/main/java/com/qy/dch/service/impl/DatabaseServiceImpl.java`

- [ ] **Step 1: 重写 DatabaseServiceImpl 加强安全校验**

完全替换 `xwSystem/xwBackend/src/main/java/com/qy/dch/service/impl/DatabaseServiceImpl.java` 的内容（保留包名和导入），关键修改：

```java
package com.qy.dch.service.impl;

import com.qy.dch.common.BusinessException;
import com.qy.dch.common.ErrorCode;
import com.qy.dch.dto.FieldInfoDTO;
import com.qy.dch.request.AddFieldRequest;
import com.qy.dch.request.DeleteFieldRequest;
import com.qy.dch.request.ModifyFieldRequest;
import com.qy.dch.service.DatabaseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class DatabaseServiceImpl implements DatabaseService {

    private final JdbcTemplate jdbcTemplate;
    
    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    /** 不可变白名单 */
    private static final Set<String> ALLOWED_TABLES = 
        Collections.unmodifiableSet(new HashSet<>(Arrays.asList("origin_text")));
    
    private static final Set<String> CORE_FIELDS = 
        Collections.unmodifiableSet(new HashSet<>(Arrays.asList("id", "title", "content", "times", "type")));
    
    private static final Set<String> ALLOWED_DATA_TYPES = 
        Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "VARCHAR", "INT", "BIGINT", "TEXT", "DATETIME", "TIMESTAMP", "DECIMAL")));

    @Override
    public List<FieldInfoDTO> getTableStructure(String tableName) {
        validateTableName(tableName);
        
        String dbName = extractDatabaseName();
        String sql = "SELECT COLUMN_NAME, DATA_TYPE, CHARACTER_MAXIMUM_LENGTH, " +
                     "COLUMN_DEFAULT, IS_NULLABLE, COLUMN_COMMENT, COLUMN_KEY " +
                     "FROM INFORMATION_SCHEMA.COLUMNS " +
                     "WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? " +
                     "ORDER BY ORDINAL_POSITION";
        
        return jdbcTemplate.query(sql, new Object[]{dbName, tableName}, (rs, rowNum) -> {
            FieldInfoDTO field = new FieldInfoDTO();
            field.setFieldName(rs.getString("COLUMN_NAME"));
            field.setDataType(rs.getString("DATA_TYPE"));
            field.setMaxLength(rs.getInt("CHARACTER_MAXIMUM_LENGTH"));
            if (rs.wasNull()) {
                field.setMaxLength(null);
            }
            field.setDefaultValue(rs.getString("COLUMN_DEFAULT"));
            field.setIsNullable(rs.getString("IS_NULLABLE"));
            field.setComment(rs.getString("COLUMN_COMMENT"));
            field.setColumnKey(rs.getString("COLUMN_KEY"));
            return field;
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addField(AddFieldRequest request) {
        validateTableName(request.getTableName());
        validateFieldName(request.getFieldName());
        validateDataType(request.getDataType());
        
        String sql = buildAddFieldSql(request);
        
        try {
            log.info("执行 DDL: {}", sql);
            jdbcTemplate.execute(sql);
            log.info("字段添加成功: table={}, field={}", request.getTableName(), request.getFieldName());
        } catch (Exception e) {
            log.error("DDL 执行失败", e);
            throw new BusinessException(ErrorCode.DB_DDL_FAILED, "字段添加失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void modifyField(ModifyFieldRequest request) {
        validateTableName(request.getTableName());
        validateFieldName(request.getFieldName());
        validateDataType(request.getDataType());
        
        if (CORE_FIELDS.contains(request.getFieldName())) {
            throw new BusinessException(ErrorCode.DB_FIELD_PROTECTED, "禁止修改核心字段: " + request.getFieldName());
        }
        
        StringBuilder sql = new StringBuilder("ALTER TABLE ");
        sql.append(sanitizeIdentifier(request.getTableName()));
        sql.append(" MODIFY COLUMN ");
        sql.append(sanitizeIdentifier(request.getFieldName()));
        sql.append(" ").append(request.getDataType());
        
        if ("VARCHAR".equalsIgnoreCase(request.getDataType()) && request.getLength() != null) {
            if (request.getLength() <= 0 || request.getLength() > 65535) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "VARCHAR 长度必须在 1-65535 之间");
            }
            sql.append("(").append(request.getLength()).append(")");
        }
        
        if (StringUtils.isNotBlank(request.getComment())) {
            sql.append(" COMMENT '").append(escapeComment(request.getComment())).append("'");
        }
        
        try {
            log.info("执行 DDL: {}", sql);
            jdbcTemplate.execute(sql.toString());
            log.info("字段修改成功: table={}, field={}", request.getTableName(), request.getFieldName());
        } catch (Exception e) {
            log.error("DDL 执行失败", e);
            throw new BusinessException(ErrorCode.DB_DDL_FAILED, "字段修改失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteField(DeleteFieldRequest request) {
        validateTableName(request.getTableName());
        validateFieldName(request.getFieldName());
        
        if (CORE_FIELDS.contains(request.getFieldName())) {
            throw new BusinessException(ErrorCode.DB_FIELD_PROTECTED, "禁止删除核心字段: " + request.getFieldName());
        }
        
        String sql = "ALTER TABLE " + sanitizeIdentifier(request.getTableName()) +
                     " DROP COLUMN " + sanitizeIdentifier(request.getFieldName());
        
        try {
            log.info("执行 DDL: {}", sql);
            jdbcTemplate.execute(sql);
            log.info("字段删除成功: table={}, field={}", request.getTableName(), request.getFieldName());
        } catch (Exception e) {
            log.error("DDL 执行失败", e);
            throw new BusinessException(ErrorCode.DB_DDL_FAILED, "字段删除失败: " + e.getMessage());
        }
    }

    private String buildAddFieldSql(AddFieldRequest request) {
        StringBuilder sql = new StringBuilder("ALTER TABLE ");
        sql.append(sanitizeIdentifier(request.getTableName()));
        sql.append(" ADD COLUMN ");
        sql.append(sanitizeIdentifier(request.getFieldName()));
        sql.append(" ").append(request.getDataType());
        
        if ("VARCHAR".equalsIgnoreCase(request.getDataType()) && request.getLength() != null) {
            if (request.getLength() <= 0 || request.getLength() > 65535) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "VARCHAR 长度必须在 1-65535 之间");
            }
            sql.append("(").append(request.getLength()).append(")");
        }
        
        sql.append(Boolean.FALSE.equals(request.getNullable()) ? " NOT NULL" : " DEFAULT NULL");
        
        if (StringUtils.isNotBlank(request.getComment())) {
            sql.append(" COMMENT '").append(escapeComment(request.getComment())).append("'");
        }
        
        return sql.toString();
    }

    private String sanitizeIdentifier(String identifier) {
        if (!identifier.matches("^[a-zA-Z_][a-zA-Z0-9_]{0,63}$")) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "非法标识符: " + identifier);
        }
        return identifier;
    }

    private String escapeComment(String comment) {
        if (comment == null) return "";
        return comment.replace("'", "''")
                      .replace("\\", "\\\\")
                      .replace("\n", " ")
                      .replace("\r", " ");
    }

    private void validateTableName(String tableName) {
        if (!ALLOWED_TABLES.contains(tableName)) {
            throw new BusinessException(ErrorCode.DB_TABLE_NOT_ALLOWED, "不允许操作的表: " + tableName);
        }
    }

    private void validateFieldName(String fieldName) {
        if (!fieldName.matches("^[a-zA-Z_][a-zA-Z0-9_]{0,63}$")) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "字段名格式不合法: " + fieldName);
        }
    }

    private void validateDataType(String dataType) {
        if (!ALLOWED_DATA_TYPES.contains(dataType.toUpperCase())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "不支持的数据类型: " + dataType);
        }
    }

    private String extractDatabaseName() {
        try {
            String url = datasourceUrl;
            int start = url.lastIndexOf("/") + 1;
            int end = url.indexOf("?");
            if (end == -1) end = url.length();
            return url.substring(start, end);
        } catch (Exception e) {
            log.warn("提取数据库名失败，使用默认值", e);
            return "uygur_project";
        }
    }
}
```

- [ ] **Step 2: 编译验证**

```bash
cd xwSystem/xwBackend
mvn clean compile
```

预期：`BUILD SUCCESS`

- [ ] **Step 3: 提交 DatabaseServiceImpl 安全加固**

```bash
git add xwSystem/xwBackend/src/main/java/com/qy/dch/service/impl/DatabaseServiceImpl.java
git commit -m "refactor: DatabaseServiceImpl 安全加固

- 添加不可变白名单（表名、字段名、数据类型）
- sanitizeIdentifier 严格正则校验
- escapeComment 防止 SQL 注入
- 统一抛出 BusinessException

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

## 阶段三：参数校验（预计 0.5 天）

### Task 6: 添加 DTO 参数校验

**Files:**
- Modify: `xwSystem/xwBackend/src/main/java/com/qy/dch/request/RagIndexTriggerRequest.java`
- Modify: `xwSystem/xwBackend/src/main/java/com/qy/dch/request/RagSearchRequest.java`
- Modify: `xwSystem/xwBackend/src/main/java/com/qy/dch/controller/RagController.java`

- [ ] **Step 1: 为 RagIndexTriggerRequest 添加校验注解**

在 `xwSystem/xwBackend/src/main/java/com/qy/dch/request/RagIndexTriggerRequest.java` 的字段上添加注解：

```java
package com.qy.dch.request;

import lombok.Data;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;

@Data
public class RagIndexTriggerRequest {
    
    @NotBlank(message = "startDate 不能为空")
    @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "startDate 格式必须为 yyyy-MM-dd")
    private String startDate;
    
    @NotBlank(message = "endDate 不能为空")
    @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "endDate 格式必须为 yyyy-MM-dd")
    private String endDate;
}
```

- [ ] **Step 2: 为 RagSearchRequest 添加校验注解**

在 `xwSystem/xwBackend/src/main/java/com/qy/dch/request/RagSearchRequest.java` 的字段上添加注解：

```java
package com.qy.dch.request;

import lombok.Data;
import javax.validation.constraints.*;

@Data
public class RagSearchRequest {
    
    @NotBlank(message = "query 不能为空")
    @Size(min = 1, max = 500, message = "query 长度必须在 1-500 之间")
    private String query;
    
    @Min(value = 1, message = "topK 最小为 1")
    @Max(value = 100, message = "topK 最大为 100")
    private Integer topK = 10;
    
    private Boolean hybrid = false;
}
```

- [ ] **Step 3: 在 RagController 启用参数校验**

修改 `xwSystem/xwBackend/src/main/java/com/qy/dch/controller/RagController.java`，在类上添加 `@Validated`，方法参数加 `@Valid`：

```java
package com.qy.dch.controller;

import com.qy.dch.common.ResultVO;
import com.qy.dch.request.RagIndexTriggerRequest;
import com.qy.dch.request.RagSearchRequest;
import com.qy.dch.service.RagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@RestController
@RequestMapping("/api/rag")
@RequiredArgsConstructor
@Slf4j
@Validated  // 新增
public class RagController {

    private final RagService ragService;

    @GetMapping("/index/status")
    public ResultVO getIndexStatus() {
        log.info("查询索引状态");
        return ragService.getIndexStatus();
    }

    @PostMapping("/index/trigger")
    public ResultVO triggerIndexing(@Valid @RequestBody RagIndexTriggerRequest request) {  // 新增 @Valid
        String startDate = request.getStartDate();
        String endDate = request.getEndDate();
        log.info("手动触发索引: startDate={}, endDate={}", startDate, endDate);
        return ragService.triggerIndexing(startDate, endDate);
    }

    @PostMapping("/search")
    public ResultVO search(@Valid @RequestBody RagSearchRequest request) {  // 新增 @Valid
        Integer topK = request.getTopK() != null ? request.getTopK() : 10;
        Boolean hybrid = request.getHybrid() != null ? request.getHybrid() : false;
        log.info("语义检索: query={}, topK={}, hybrid={}", request.getQuery(), topK, hybrid);
        return ragService.search(request.getQuery(), topK, hybrid);
    }

    @GetMapping("/index/log")
    public ResultVO getIndexLog(@RequestParam(defaultValue = "1") Integer pageNum,
                                 @RequestParam(defaultValue = "20") Integer pageSize) {
        log.info("查询索引日志: pageNum={}, pageSize={}", pageNum, pageSize);
        return ragService.getIndexLog(pageNum, pageSize);
    }
}
```

- [ ] **Step 4: 测试参数校验**

启动项目，测试空参数：
```bash
curl -X POST http://localhost:8081/api/rag/search \
  -H "Content-Type: application/json" \
  -d '{"query": "", "topK": 10}'
```

预期响应：
```json
{
  "code": 1002,
  "data": null,
  "msg": "query: 不能为空",
  "flag": "false"
}
```

- [ ] **Step 5: 提交参数校验**

```bash
git add xwSystem/xwBackend/src/main/java/com/qy/dch/request/
git add xwSystem/xwBackend/src/main/java/com/qy/dch/controller/RagController.java
git commit -m "feat: 添加 DTO 参数校验

- RagIndexTriggerRequest/RagSearchRequest 添加 JSR-303 注解
- RagController 启用 @Validated 和 @Valid
- GlobalExceptionHandler 自动处理校验异常

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

## 阶段四：测试与上线（预计 1 天）

### Task 7: 全量测试与性能验证

**Files:**
- 无文件修改

- [ ] **Step 1: 全量回归测试**

测试所有 API 端点：
```bash
# RAG 模块
curl http://localhost:8081/api/rag/index/status
curl -X POST http://localhost:8081/api/rag/index/trigger -H "Content-Type: application/json" -d '{"startDate":"2024-01-01","endDate":"2024-12-31"}'
curl -X POST http://localhost:8081/api/rag/search -H "Content-Type: application/json" -d '{"query":"测试","topK":10}'

# 数据库管理
curl http://localhost:8081/api/database/structure?tableName=origin_text
```

预期：所有接口返回正常响应，无 500 错误

- [ ] **Step 2: 性能基准测试**

使用 wrk 压测：
```bash
wrk -t10 -c100 -d30s http://localhost:8081/api/rag/index/status
```

记录关键指标：
- QPS（每秒请求数）
- P99 延迟
- 错误率

- [ ] **Step 3: 检查 Druid 监控**

访问 `http://localhost:8081/druid/login.html`（admin/admin123）

检查：
- 连接池利用率（活跃连接应 < 45）
- 慢 SQL 日志（应无超过 2s 的查询）
- SQL 防火墙拦截记录

- [ ] **Step 4: 代码审查**

重点检查：
1. 所有 Mapper 是否消除了 SQL 字符串拼接
2. DatabaseServiceImpl 白名单是否完整
3. 异常处理是否统一使用 BusinessException
4. 参数校验是否覆盖所有 Controller

- [ ] **Step 5: 编写迁移文档**

创建 `docs/migration-guide.md`，记录：
- 本次迁移的关键改动
- 回滚方案
- 已知风险点
- 后续优化方向

---

## 自查清单

**Spec 覆盖：**
- ✅ MyBatis-Plus 配置和依赖
- ✅ BaseEntity 和字段自动填充
- ✅ 错误码枚举和业务异常
- ✅ 全局异常处理器
- ✅ RagMapper 重构（消除 SQL 注入）
- ✅ DatabaseServiceImpl 安全加固
- ✅ 参数校验（JSR-303）
- ✅ Druid 连接池优化
- ✅ 测试和性能验证

**占位符扫描：** 无 TBD、TODO、"implement later"

**类型一致性：** 
- `OriginText` 实体类在 Task 4 定义，Task 4/5 中使用一致
- `ErrorCode` 在 Task 2 定义，Task 3/5 中使用一致
- `BusinessException` 在 Task 2 定义，Task 3/5 中使用一致

**文件路径完整性：** 所有文件路径均为绝对路径

---

## 执行说明

**预计工期：** 4-5 天

**阶段划分：**
- 阶段一（1 天）：Task 1-3 基础设施
- 阶段二（2 天）：Task 4-5 核心重构
- 阶段三（0.5 天）：Task 6 参数校验
- 阶段四（1 天）：Task 7 测试上线

**风险控制：**
- 每个 Task 完成后立即提交，便于回滚
- 测试环境部署 1 周观察期后再上生产
- 保留 Git 标签：`v1.0-before-migration`

---

计划完成并保存至 `docs/superpowers/plans/2026-06-14-backend-optimization-implementation.md`。

**两种执行方式：**

**1. Subagent-Driven（推荐）** - 每个 Task 派发独立 subagent，任务间 review，快速迭代

**2. Inline Execution** - 在当前会话执行，批量操作带检查点

选择哪种方式？

