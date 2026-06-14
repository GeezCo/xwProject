---
title: xwBackend 混合式架构优化设计
date: 2026-06-14
author: Claude Code
status: approved
---

# xwBackend 混合式架构优化设计

## 一、背景与目标

### 1.1 现状分析

**当前技术栈：**
- Spring Boot 2.7.18 + MyBatis 2.3.2
- MySQL 8 + Druid 连接池
- 混合使用 MyBatis 注解（`@Select`/`@Update`）和 XML Mapper
- RAG 模块集成 Elasticsearch、MinIO、OCR

**存在问题：**
1. ❌ SQL 直接暴露在代码中（`RagMapper.java` 内嵌 SQL 字符串拼接）
2. ❌ `DatabaseServiceImpl` 手工拼接 DDL，存在注入风险
3. ⚠️ 缺少统一查询构建器（无流式 API）
4. ⚠️ 异常处理粗糙（直接 throw RuntimeException）
5. ⚠️ 事务管理不够细化
6. ⚠️ 缺少参数校验

### 1.2 优化目标

**优先级排序：**
1. **SQL 安全 & 查询优化**（最高优先级）
2. **异常处理 & 统一响应**
3. **参数校验**
4. **连接池优化**
5. **日志增强**
6. **领域模型解耦**（后续优化）

**预期收益：**
- ✅ 消除 SQL 注入风险
- ✅ 减少 80% Mapper 代码量
- ✅ 提升 API 健壮性
- ✅ 提高连接池利用率 30%+
- ✅ 统一异常处理和日志规范

## 二、方案选型

### 2.1 方案对比

经过评估，共有三种方案：

| 方案 | 优点 | 缺点 | 工期 |
|------|------|------|------|
| **A. 渐进式优化** | 改动最小，风险可控 | 新旧代码并存 | 2-3 天 |
| **B. 激进式重构** | 代码最干净，性能最优 | 工作量大，风险高 | 7-10 天 |
| **C. 混合式迁移** | 重点突破，平衡收益与风险 | 代码风格不统一 | 4-5 天 |

### 2.2 最终选择：方案 C（混合式迁移）

**理由：**
1. **DatabaseServiceImpl 的 DDL 功能特殊**，用 JdbcTemplate 合理，只需加强防护
2. **RagMapper 风险最高**（SQL 字符串拼接），必须优先重构
3. **XML Mapper 的复杂查询**（如 FusionMapper）已经安全，不需强行迁移
4. **务实主义**：优先解决核心痛点，为后续优化留空间

### 2.3 核心技术选型

**ORM 框架：** MyBatis-Plus 3.5.5
- 完全兼容现有 MyBatis 代码
- 提供 `LambdaQueryWrapper` 流式查询 API
- 自动生成 CRUD，减少 80% 代码量
- 支持分页、逻辑删除、乐观锁

## 三、架构设计

### 3.1 分层架构（保持不变）

```
┌─────────────────────────────────────────┐
│         Controller 层（API 入口）         │  ← 参数校验 @Valid
├─────────────────────────────────────────┤
│          Service 层（业务逻辑）           │  ← 事务管理 @Transactional
├─────────────────────────────────────────┤
│     Mapper 层（数据访问）                 │  ← MyBatis-Plus + XML 混合
│  ┌──────────┬──────────┬──────────┐     │
│  │ 重构区域 │ 保留区域 │  新增模块 │     │
│  │(MP方式) │(XML保留)│ (强制MP)  │     │
│  └──────────┴──────────┴──────────┘     │
├─────────────────────────────────────────┤
│         DTO/Entity（数据对象）            │
└─────────────────────────────────────────┘
```

### 3.2 模块划分策略

**重构区域（高优先级 - MyBatis-Plus）**

| 模块 | 原因 | 重构方式 |
|------|------|---------|
| `RagMapper` | SQL 字符串拼接，高注入风险 | 继承 `BaseMapper<OriginText>` + `LambdaQueryWrapper` |
| `DatabaseServiceImpl` | 手工拼接 DDL，注入风险 | 保留 JdbcTemplate，加强白名单+参数化 |
| `ExtractionMapper` | 使用 `ON DUPLICATE KEY UPDATE` | 改用 MyBatis-Plus `insertOrUpdate` |

**保留区域（低风险 - XML Mapper）**

| 模块 | 原因 | 处理方式 |
|------|------|---------|
| `FusionMapper.xml` | 纯 XML，无动态拼接，安全 | 保持不变 |
| `RagDocumentMapper.xml` | 复杂 ResultMap 映射 | 保持不变 |

**新增模块（强制规范）**
- 所有新建 Mapper 必须继承 `BaseMapper<T>`
- 禁止使用 `@Select` 等注解内嵌 SQL
- 复杂查询优先用 `LambdaQueryWrapper`

## 四、技术实现细节

### 4.1 MyBatis-Plus 配置

**pom.xml 依赖调整：**

```xml
<!-- 新增 MyBatis-Plus -->
<dependency>
    <groupId>com.baomidou</groupId>
    <artifactId>mybatis-plus-boot-starter</artifactId>
    <version>3.5.5</version>
</dependency>

<!-- 注释掉原 MyBatis Starter -->
<!--
<dependency>
    <groupId>org.mybatis.spring.boot</groupId>
    <artifactId>mybatis-spring-boot-starter</artifactId>
    <version>2.3.2</version>
</dependency>
-->
```

**配置类 `MybatisPlusConfig.java`：**

```java
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
        interceptor.addInnerInterceptor(paginationInterceptor);
        
        return interceptor;
    }
}
```

**application.yml 配置：**

```yaml
mybatis-plus:
  mapper-locations: classpath:mapper/*.xml  # 兼容原有 XML
  type-aliases-package: com.qy.dch.dto,com.qy.dch.entity
  configuration:
    map-underscore-to-camel-case: true
    log-impl: org.apache.ibatis.logging.slf4j.Slf4jImpl
  global-config:
    db-config:
      id-type: auto  # 主键自增
      logic-delete-field: deleted
      logic-delete-value: 1
      logic-not-delete-value: 0
```

### 4.2 实体基类设计

**`BaseEntity.java`：**

```java
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

**自动填充处理器：**

```java
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

### 4.3 RagMapper 重构（核心）

**原代码（高风险）：**

```java
@Select("<script>" +
        "SELECT id as sid FROM origin_text " +
        "WHERE is_indexed = 0 " +
        "<if test='startDate != null'>" +
        "AND times &gt;= #{startDate} " +
        "</if>" +
        // ... 字符串拼接 SQL
        "</script>")
List<Long> selectUnindexedIds(@Param("startDate") String startDate, ...);
```

**重构后（MyBatis-Plus）：**

```java
@Mapper
public interface RagMapper extends BaseMapper<OriginText> {
    
    /**
     * 条件查询未索引 ID
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
}
```

**优势：**
- ✅ 无 SQL 字符串拼接，类型安全
- ✅ 参数自动转义，防注入
- ✅ 可读性强，流式 API

### 4.4 DatabaseServiceImpl 安全加固

**核心加固点：**
1. 标识符白名单校验（表名、字段名、数据类型）
2. 正则严格匹配，防止特殊字符注入
3. Comment 字符转义
4. 不可变集合防止运行时篡改

**关键代码：**

```java
@Service
public class DatabaseServiceImpl implements DatabaseService {

    /** 不可变白名单 */
    private static final Set<String> ALLOWED_TABLES = 
        Collections.unmodifiableSet(new HashSet<>(Arrays.asList("origin_text")));
    
    private static final Set<String> CORE_FIELDS = 
        Collections.unmodifiableSet(new HashSet<>(Arrays.asList("id", "title", "content")));
    
    private static final Set<String> ALLOWED_DATA_TYPES = 
        Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "VARCHAR", "INT", "BIGINT", "TEXT", "DATETIME")));

    /**
     * 标识符清理（严格正则）
     */
    private String sanitizeIdentifier(String identifier) {
        if (!identifier.matches("^[a-zA-Z_][a-zA-Z0-9_]{0,63}$")) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "非法标识符");
        }
        return identifier;
    }

    /**
     * 转义 COMMENT
     */
    private String escapeComment(String comment) {
        return comment.replace("'", "''")
                      .replace("\\", "\\\\")
                      .replace("\n", " ");
    }
}
```

## 五、异常处理体系

### 5.1 错误码枚举

```java
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
    DB_FIELD_PROTECTED(4002, "核心字段禁止修改");
    
    private final int code;
    private final String message;
}
```

### 5.2 业务异常类

```java
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
}
```

### 5.3 全局异常处理器

```java
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResultVO handleBusinessException(BusinessException e) {
        log.warn("业务异常: code={}, message={}", e.getErrorCode().getCode(), e.getMessage());
        return ResultVO.error(e.getErrorCode().getCode(), e.getMessage());
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
            return ResultVO.error(ErrorCode.DUPLICATE_KEY.getCode(), "数据重复");
        }
        
        return ResultVO.error(ErrorCode.SYSTEM_ERROR.getCode(), "数据库操作失败");
    }

    @ExceptionHandler(Exception.class)
    public ResultVO handleException(Exception e) {
        log.error("系统异常", e);
        return ResultVO.error(ErrorCode.SYSTEM_ERROR.getCode(), "系统内部错误");
    }
}
```

## 六、参数校验

### 6.1 Controller 层启用校验

```java
@RestController
@RequestMapping("/api/rag")
@RequiredArgsConstructor
@Validated  // 启用参数校验
public class RagController {

    @PostMapping("/index/trigger")
    public ResultVO triggerIndexing(@Valid @RequestBody RagIndexTriggerRequest request) {
        return ragService.triggerIndexing(request.getStartDate(), request.getEndDate());
    }
}
```

### 6.2 DTO 添加校验注解

```java
@Data
public class RagIndexTriggerRequest {
    
    @NotBlank(message = "startDate 不能为空")
    @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "startDate 格式必须为 yyyy-MM-dd")
    private String startDate;
    
    @NotBlank(message = "endDate 不能为空")
    @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "endDate 格式必须为 yyyy-MM-dd")
    private String endDate;
}

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

## 七、连接池优化

### 7.1 Druid 配置调整

```yaml
spring:
  datasource:
    druid:
      # 连接池优化
      initial-size: 10           # 初始连接数（从 5 提升）
      min-idle: 10               # 最小空闲连接
      max-active: 50             # 最大活跃连接（从 20 提升）
      max-wait: 30000            # 获取连接超时 30s
      
      # 连接保活
      time-between-eviction-runs-millis: 60000
      min-evictable-idle-time-millis: 300000
      max-evictable-idle-time-millis: 600000
      
      # 连接有效性检测
      validation-query: SELECT 1
      test-while-idle: true
      test-on-borrow: false
      test-on-return: false
      
      # PSCache（MySQL 建议关闭）
      pool-prepared-statements: false
      
      # 监控与防火墙
      filters: stat,wall,slf4j
      stat-view-servlet:
        enabled: true
        url-pattern: /druid/*
        login-username: admin
        login-password: ${DRUID_ADMIN_PASSWORD:admin123}
        reset-enable: false
      
      # 慢 SQL 告警
      filter:
        stat:
          enabled: true
          slow-sql-millis: 2000
          log-slow-sql: true
        wall:
          enabled: true
          config:
            delete-where-none-check: true  # 禁止无 WHERE 的 DELETE
```

## 八、编码规范

### 8.1 Mapper 层规范

| 场景 | 规范 | 示例 |
|------|------|------|
| 简单 CRUD | 继承 `BaseMapper<T>` | `ragMapper.selectById(1L)` |
| 条件查询 | `LambdaQueryWrapper` + default 方法 | `wrapper.eq(OriginText::getType, 1)` |
| 批量操作 | `LambdaUpdateWrapper` + `update()` | `wrapper.in(OriginText::getId, ids)` |
| 复杂查询 | XML Mapper | `FusionMapper.xml` |
| 统计查询 | `@Select` 注解（纯静态 SQL） | `@Select("SELECT COUNT(*) ...")` |

**禁止：**
- ❌ 字符串拼接 SQL
- ❌ 动态 `<if>` 标签嵌在 `@Select` 注解里

### 8.2 Service 层规范

**事务管理：**
```java
@Transactional(rollbackFor = Exception.class)  // 所有异常都回滚
public void batchUpdate(List<Long> ids) {
    // 业务逻辑
}
```

**异常处理：**
```java
// ✅ 推荐：抛出业务异常
if (ids.isEmpty()) {
    throw new BusinessException(ErrorCode.PARAM_INVALID, "ids 不能为空");
}

// ❌ 禁止：直接返回 null
```

### 8.3 Controller 层规范

**简洁原则：**
```java
@PostMapping("/search")
public ResultVO search(@Valid @RequestBody RagSearchRequest request) {
    // Controller 只做参数接收，业务逻辑下沉到 Service
    return ragService.search(request);
}
```

## 九、迁移路线图

### 阶段一：基础设施（1 天）

**任务清单：**
- [ ] 添加 MyBatis-Plus 依赖，移除旧 MyBatis Starter
- [ ] 配置 `MybatisPlusConfig`（分页插件、日志）
- [ ] 创建 `BaseEntity` 和 `MetaObjectHandlerConfig`
- [ ] 定义 `ErrorCode` 枚举和 `BusinessException`
- [ ] 实现 `GlobalExceptionHandler`
- [ ] 优化 `ResultVO` 响应体
- [ ] 调整 `application.yml`（Druid 连接池优化）

**验证方式：**
```bash
mvn spring-boot:run
http://localhost:8081/druid/login.html
```

### 阶段二：核心模块重构（2 天）

**Day 1：RagMapper 重构**
- [ ] 创建 `OriginText` 实体类（继承 `BaseEntity`）
- [ ] `RagMapper` 继承 `BaseMapper<OriginText>`
- [ ] 用 `LambdaQueryWrapper` 重写所有查询方法
- [ ] 删除原 `@Select` 注解 SQL
- [ ] 更新 `RagServiceImpl` 调用方式
- [ ] 编写单元测试 `RagMapperTest`

**Day 2：DatabaseServiceImpl 安全加固**
- [ ] 添加数据类型白名单
- [ ] 实现 `sanitizeIdentifier()` 标识符校验
- [ ] 实现 `escapeComment()` 转义方法
- [ ] 重构 `buildAddFieldSql()` 方法
- [ ] 统一异常抛出（改为 `BusinessException`）
- [ ] 编写单元测试（含 SQL 注入测试用例）

### 阶段三：参数校验（0.5 天）

**任务清单：**
- [ ] 所有 Request DTO 添加校验注解
- [ ] Controller 方法参数加 `@Valid`
- [ ] Controller 类加 `@Validated`
- [ ] 测试参数校验异常处理

### 阶段四：其他模块处理（0.5 天）

**任务清单：**
- [ ] `ExtractionMapper` 的 `ON DUPLICATE KEY UPDATE` 改为 MyBatis-Plus
- [ ] `UygurMapper` 部分高风险注解 SQL 迁移（可选）
- [ ] 保留 `FusionMapper.xml` 和 `RagDocumentMapper.xml`
- [ ] 编写编码规范文档

### 阶段五：测试 & 上线（1 天）

**任务清单：**
- [ ] 全量回归测试（所有 API 端点）
- [ ] 性能测试（对比优化前后 QPS）
- [ ] 代码审查（重点检查 SQL 安全）
- [ ] 编写迁移文档
- [ ] 灰度发布（测试环境 1 周观察期）

**性能对比测试：**
```bash
wrk -t10 -c100 -d30s http://localhost:8081/api/rag/index/status

# 对比指标：
# - QPS（每秒请求数）
# - P99 延迟
# - 连接池利用率
```

## 十、风险控制

### 10.1 回滚方案

**Git 分支策略：**
```bash
# 主分支保护
main (protected)
  └── feature/mybatis-plus-migration

# 提交前打标签
git tag v1.0-before-migration
git push origin v1.0-before-migration

# 如需回滚
git reset --hard v1.0-before-migration
```

**数据库回滚：**
- DDL 变更前备份表结构：`SHOW CREATE TABLE origin_text;`
- 记录所有 DDL 操作及回滚 SQL

### 10.2 灰度发布

**发布流程：**
1. 测试环境全量部署（1 周观察期）
2. 生产环境单机部署（10% 流量）
3. 监控关键指标
4. 无异常后全量部署

**监控指标：**

| 指标 | 阈值 | 告警方式 |
|------|------|---------|
| 接口错误率 | > 1% | 钉钉/邮件 |
| P99 响应时间 | > 3s | 钉钉 |
| 慢 SQL 数量 | > 10/min | 日志告警 |
| 连接池耗尽 | 活跃连接 > 45 | 钉钉 |

## 十一、FAQ

**Q1：MyBatis-Plus 会影响性能吗？**
A：不会。底层仍是 MyBatis，只是自动生成 SQL。复杂查询仍可用 XML。

**Q2：原有的 XML Mapper 需要全部删除吗？**
A：不需要。MyBatis-Plus 与 XML 完全兼容，FusionMapper.xml 保留。

**Q3：`@Select` 注解的 SQL 必须迁移吗？**
A：高风险的（如 RagMapper）必须迁移，纯静态 SQL 可以保留。

**Q4：DatabaseService 的 DDL 功能能用 MyBatis-Plus 实现吗？**
A：不能。DDL 不是 MyBatis-Plus 的设计目标，保留 JdbcTemplate 合理。

**Q5：迁移后如何保证兼容性？**
A：分阶段上线 + 充分测试。测试环境运行 1 周后再上生产。

**Q6：团队成员不熟悉 MyBatis-Plus 怎么办？**
A：提供培训文档、示例代码、编码规范，Code Review 时重点检查。

## 十二、总结

**本设计的核心价值：**
1. ✅ **安全第一**：消除 SQL 注入风险
2. ✅ **渐进式**：保留稳定模块，只重构高风险部分
3. ✅ **可落地**：4-5 天完成核心改造
4. ✅ **可扩展**：为后续优化（读写分离、分库分表）留下空间
5. ✅ **高可用**：连接池优化 + 异常处理 + 日志增强

**预期收益：**
- 减少 80% Mapper 代码量
- 消除所有 SQL 注入风险
- 提升连接池利用率 30%+
- 统一异常处理和响应格式
- 提高代码可读性和可维护性

---

**设计完成，待审批通过后进入实施阶段。**

