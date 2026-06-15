# 配置-代码分离实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把后端所有环境敏感配置（DB / MinIO / ES / 算法服务 等）从仓库代码中抽离到外置 `./config/application-local.yml`，启动时强校验缺失项，并产出可直接刻录拷贝的 `176-deploy/` 部署目录。

**Architecture:** 仓库 `application.yml` 保留结构性配置，敏感项改成 `${VAR:}` 占位（默认空）。新增 `EnvironmentPostProcessor` 在 Spring 容器初始化前从 classpath 读 `application-local.yml.example` 自动提取必填字段，校验外置 yml 完整性，缺失即明确提示并 `System.exit(1)`。tests 使用 `application-test.yml` 不受影响（校验器跳过 test profile）。

**Tech Stack:** Spring Boot 2.7.18, JDK 1.8, `org.springframework.boot.env.EnvironmentPostProcessor`, `META-INF/spring.factories`

**JDK / Maven 位置（开发机）：**
- JDK: `/Users/processmonitor/Documents/jdk/jdk8/Contents/Home`
- Maven: `/Users/processmonitor/Documents/apache-maven-3.9.16`
- 命令前缀（必须）：`export JAVA_HOME=/Users/processmonitor/Documents/jdk/jdk8/Contents/Home && /Users/processmonitor/Documents/apache-maven-3.9.16/bin/mvn ...`

**目标交付目录（开发机本地）：** `/Users/processmonitor/Downloads/176-deploy/`

---

## 文件结构（全局视图）

**仓库新增：**
- `xwSystem/xwBackend/src/main/resources/application-local.yml.example` — 环境敏感项模板
- `xwSystem/xwBackend/src/main/java/com/qy/dch/config/ConfigBootstrapValidator.java` — 启动校验类
- `xwSystem/xwBackend/src/main/resources/META-INF/spring.factories` — 注册校验类
- `xwSystem/xwBackend/src/test/java/com/qy/dch/config/ConfigBootstrapValidatorTest.java` — 校验逻辑单测

**仓库修改：**
- `xwSystem/xwBackend/src/main/resources/application.yml` — 敏感项改 `${VAR:}` + 加 `spring.profiles.active: local`
- `xwSystem/xwBackend/.gitignore` — 忽略 `application-local.yml`

**部署目录（不进 git）：**
- `/Users/processmonitor/Downloads/176-deploy/xwBackend.jar`
- `/Users/processmonitor/Downloads/176-deploy/start.sh`
- `/Users/processmonitor/Downloads/176-deploy/stop.sh`
- `/Users/processmonitor/Downloads/176-deploy/README.md`
- `/Users/processmonitor/Downloads/176-deploy/config/application-local.yml.example`

---

## Task 1：新增 `application-local.yml.example` 模板

**Files:**
- Create: `xwSystem/xwBackend/src/main/resources/application-local.yml.example`

- [ ] **Step 1：写模板文件**

写入：

```yaml
# ============================================================
# 现场环境配置模板
#
# 使用方法：
#   1. 复制本文件为 application-local.yml（去掉 .example 后缀）
#   2. 把每一项 TODO_ 占位符替换成现场真实值
#   3. 放在 jar 同级 ./config/ 目录下
#   4. ./start.sh 启动
# ============================================================

# ---------- 数据库 ----------
DB_HOST: TODO_数据库IP            # 例: 192.168.1.10
DB_PORT: 9204                     # 端口
DB_NAME: uygur_project            # 库名
DB_USERNAME: TODO_数据库用户名     # 例: root
DB_PASSWORD: TODO_数据库密码

# ---------- Druid 监控页 ----------
DRUID_LOGIN_USERNAME: admin
DRUID_LOGIN_PASSWORD: TODO_监控页密码

# ---------- 报文导入文件 ----------
IMPORT_FILE_PATH: /opt/uygur-project/data/all_text_time.json

# ---------- 算法服务 ----------
ALGORITHM_SERVICE_URL: http://TODO_算法服务IP:5001

# ---------- MinIO 对象存储 ----------
MINIO_ENDPOINT: http://TODO_MinIO_IP:8522
MINIO_ACCESS_KEY: TODO_MinIO访问KEY
MINIO_SECRET_KEY: TODO_MinIO密钥
MINIO_BUCKET: xianwei-images

# ---------- Elasticsearch ----------
ES_HOST: TODO_ES_IP
ES_PORT: 9200
ES_USERNAME:
ES_PASSWORD:

# ---------- Embedding 服务 ----------
EMBEDDING_BASE_URL: http://TODO_Embedding_IP:5002

# ---------- OCR ----------
OCR_ENABLED: true
TESSERACT_DATAPATH: /usr/share/tesseract-ocr/4.00/tessdata
```

- [ ] **Step 2：校验文件已创建**

Run: `ls -la xwSystem/xwBackend/src/main/resources/application-local.yml.example`
Expected: 文件存在，大小约 1KB+

- [ ] **Step 3：grep 验证 TODO_ 行**

Run: `grep -E "^[A-Z_]+:\s*TODO_" xwSystem/xwBackend/src/main/resources/application-local.yml.example | wc -l`
Expected: `11`（DB_HOST / DB_USERNAME / DB_PASSWORD / DRUID_LOGIN_PASSWORD / ALGORITHM_SERVICE_URL / MINIO_ENDPOINT / MINIO_ACCESS_KEY / MINIO_SECRET_KEY / ES_HOST / EMBEDDING_BASE_URL —— 共 10 项。如果统计 11，说明把模板里的 `IMPORT_FILE_PATH` 也算了；按实际数为准）

注：上一步只是为了观察 grep 抓到的字段；正式必填项以代码扫描结果为准。

- [ ] **Step 4：commit**

```bash
cd xwSystem/xwBackend
git add src/main/resources/application-local.yml.example
git commit -m "feat(config): 新增 application-local.yml.example 模板"
```

---

## Task 2：在 `.gitignore` 加忽略

**Files:**
- Modify: `xwSystem/xwBackend/.gitignore`

- [ ] **Step 1：追加忽略行**

在 `xwSystem/xwBackend/.gitignore` 文件**末尾**追加：

```
# 外置真实配置不进 git
src/main/resources/application-local.yml
src/test/resources/application-local.yml
config/application-local.yml
```

- [ ] **Step 2：commit**

```bash
cd xwSystem/xwBackend
git add .gitignore
git commit -m "chore(config): .gitignore 忽略外置 application-local.yml"
```

---

## Task 3：先写校验类测试（TDD - failing first）

**Files:**
- Create: `xwSystem/xwBackend/src/test/java/com/qy/dch/config/ConfigBootstrapValidatorTest.java`

- [ ] **Step 1：写测试类**

写入完整文件：

```java
package com.qy.dch.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 注意：本测试不启动 Spring，仅测试 ConfigBootstrapValidator 的静态/纯逻辑部分。
 * EnvironmentPostProcessor 的端到端激活通过 application-test.yml + @ActiveProfiles("test")
 * 与 DchApplicationTests 间接覆盖。
 */
class ConfigBootstrapValidatorTest {

    private static final Pattern KEY_PATTERN = Pattern.compile("^([A-Z_]+):\\s*TODO_");

    @Test
    void exampleFileExistsOnClasspath() throws Exception {
        ClassPathResource res = new ClassPathResource("application-local.yml.example");
        assertTrue(res.exists(), "application-local.yml.example 必须在 classpath 根");
        try (var is = res.getInputStream()) {
            assertNotNull(is);
        }
    }

    @Test
    void extractsAllTodoMarkedKeys() throws Exception {
        Set<String> keys = new LinkedHashSet<>();
        ClassPathResource res = new ClassPathResource("application-local.yml.example");
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(res.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                Matcher m = KEY_PATTERN.matcher(line);
                if (m.find()) {
                    keys.add(m.group(1));
                }
            }
        }

        // 至少包含这些核心必填项
        assertTrue(keys.contains("DB_HOST"));
        assertTrue(keys.contains("DB_USERNAME"));
        assertTrue(keys.contains("DB_PASSWORD"));
        assertTrue(keys.contains("MINIO_ENDPOINT"));
        assertTrue(keys.contains("MINIO_ACCESS_KEY"));
        assertTrue(keys.contains("MINIO_SECRET_KEY"));
        assertTrue(keys.contains("ALGORITHM_SERVICE_URL"));
        assertTrue(keys.contains("ES_HOST"));
        assertTrue(keys.contains("EMBEDDING_BASE_URL"));
        assertTrue(keys.contains("DRUID_LOGIN_PASSWORD"));

        // 不应包含已有真实值的字段
        assertFalse(keys.contains("DB_PORT"));
        assertFalse(keys.contains("DB_NAME"));
        assertFalse(keys.contains("MINIO_BUCKET"));
        assertFalse(keys.contains("ES_PORT"));
        assertFalse(keys.contains("OCR_ENABLED"));
    }

    @Test
    void todoPatternMatchesExpectedShape() {
        Matcher m1 = KEY_PATTERN.matcher("DB_HOST: TODO_数据库IP");
        assertTrue(m1.find());
        assertEquals("DB_HOST", m1.group(1));

        Matcher m2 = KEY_PATTERN.matcher("MINIO_ENDPOINT: http://TODO_MinIO_IP:8522");
        assertFalse(m2.find(), "TODO_ 在值中间不应被识别为必填占位");

        Matcher m3 = KEY_PATTERN.matcher("DB_PORT: 9204");
        assertFalse(m3.find());
    }
}
```

注意 Step 3 中的关键点：`MINIO_ENDPOINT` 的值是 `http://TODO_MinIO_IP:8522`，模式 `^([A-Z_]+):\\s*TODO_` 不匹配（TODO_ 不在值开头），所以模板里 `MINIO_ENDPOINT` 不会被识别为必填 —— 这是 bug。下一个 Task 修复（改成 `MINIO_ENDPOINT: TODO_MinIO地址`）。

- [ ] **Step 2：跑测试（应该测试 1 / 3 通过、测试 2 失败）**

```bash
export JAVA_HOME=/Users/processmonitor/Documents/jdk/jdk8/Contents/Home
cd xwSystem/xwBackend
/Users/processmonitor/Documents/apache-maven-3.9.16/bin/mvn -o test -Dtest=ConfigBootstrapValidatorTest 2>&1 | tail -30
```

Expected: `extractsAllTodoMarkedKeys` 失败，因为模板里 `MINIO_ENDPOINT`、`ALGORITHM_SERVICE_URL`、`EMBEDDING_BASE_URL` 的 `TODO_` 在值中间，不在开头。

---

## Task 4：修正 `application-local.yml.example` 让所有必填项都以 `TODO_` 开头

**Files:**
- Modify: `xwSystem/xwBackend/src/main/resources/application-local.yml.example`

**问题：** 模板里 `ALGORITHM_SERVICE_URL: http://TODO_算法服务IP:5001` 这种写法，`TODO_` 不在值开头，校验器抓不到。需要改成简洁的字段级 TODO_。

- [ ] **Step 1：覆盖文件**

写入：

```yaml
# ============================================================
# 现场环境配置模板
#
# 使用方法：
#   1. 复制本文件为 application-local.yml（去掉 .example 后缀）
#   2. 把每一项 TODO_xxx 占位符替换成现场真实值
#   3. 放在 jar 同级 ./config/ 目录下
#   4. ./start.sh 启动
# ============================================================

# ---------- 数据库 ----------
DB_HOST: TODO_数据库IP_例如_192.168.1.10
DB_PORT: 9204
DB_NAME: uygur_project
DB_USERNAME: TODO_数据库用户名
DB_PASSWORD: TODO_数据库密码

# ---------- Druid 监控页 ----------
DRUID_LOGIN_USERNAME: admin
DRUID_LOGIN_PASSWORD: TODO_监控页密码

# ---------- 报文导入文件 ----------
IMPORT_FILE_PATH: /opt/uygur-project/data/all_text_time.json

# ---------- 算法服务（完整 URL，含 http:// 和端口） ----------
ALGORITHM_SERVICE_URL: TODO_算法服务URL_例如_http_冒号斜杠斜杠192.168.1.20冒号5001

# ---------- MinIO 对象存储 ----------
MINIO_ENDPOINT: TODO_MinIO_URL_例如_http_冒号斜杠斜杠192.168.1.30冒号8522
MINIO_ACCESS_KEY: TODO_MinIO访问KEY
MINIO_SECRET_KEY: TODO_MinIO密钥
MINIO_BUCKET: xianwei-images

# ---------- Elasticsearch ----------
ES_HOST: TODO_ES_IP
ES_PORT: 9200
ES_USERNAME:
ES_PASSWORD:

# ---------- Embedding 服务 ----------
EMBEDDING_BASE_URL: TODO_Embedding_URL_例如_http_冒号斜杠斜杠192.168.1.40冒号5002

# ---------- OCR ----------
OCR_ENABLED: true
TESSERACT_DATAPATH: /usr/share/tesseract-ocr/4.00/tessdata
```

注：用「冒号斜杠斜杠」中文转写避免 yaml 解析歧义，运维替换时清楚要填完整 URL。

- [ ] **Step 2：再跑测试**

```bash
export JAVA_HOME=/Users/processmonitor/Documents/jdk/jdk8/Contents/Home
cd xwSystem/xwBackend
/Users/processmonitor/Documents/apache-maven-3.9.16/bin/mvn -o test -Dtest=ConfigBootstrapValidatorTest 2>&1 | tail -20
```

Expected: 三个测试全部 PASS。

- [ ] **Step 3：commit**

```bash
cd xwSystem/xwBackend
git add src/main/resources/application-local.yml.example src/test/java/com/qy/dch/config/ConfigBootstrapValidatorTest.java
git commit -m "test(config): 校验类纯逻辑测试 + 修正模板让必填项 TODO_ 在值首"
```

---

## Task 5：实现 `ConfigBootstrapValidator`

**Files:**
- Create: `xwSystem/xwBackend/src/main/java/com/qy/dch/config/ConfigBootstrapValidator.java`

- [ ] **Step 1：写实现**

写入：

```java
package com.qy.dch.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Spring Boot 启动前置校验：
 *  1. 跳过 test profile（测试用 application-test.yml 自带值）
 *  2. 检查 jar 同级 ./config/application-local.yml 是否存在
 *  3. 从 classpath:application-local.yml.example 自动提取必填项（TODO_ 前缀）
 *  4. 任一必填项缺失 / 仍为 TODO_ 时打印明确指引并 System.exit(1)
 */
public class ConfigBootstrapValidator implements EnvironmentPostProcessor {

    private static final Pattern KEY_PATTERN = Pattern.compile("^([A-Z_]+):\\s*TODO_");
    private static final String EXTERNAL_CONFIG = "./config/application-local.yml";
    private static final String EXAMPLE_CLASSPATH = "application-local.yml.example";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication app) {
        // 1. 跳过测试场景
        for (String p : env.getActiveProfiles()) {
            if ("test".equalsIgnoreCase(p)) return;
        }

        // 2. 外置 yml 存在性
        File configFile = new File(EXTERNAL_CONFIG);
        if (!configFile.exists()) {
            printAndExit(
                    "未找到外置配置文件: " + configFile.getAbsolutePath(),
                    "请按以下步骤操作：",
                    "  1. 在 jar 同级创建 config/ 目录",
                    "  2. 把 application-local.yml.example 拷贝为 config/application-local.yml",
                    "  3. 按文件内 TODO_ 提示填入真实值",
                    "  4. 重新启动"
            );
        }

        // 3. 从模板自动提取必填项
        Set<String> required = extractRequiredKeys();

        // 4. 校验
        List<String> missing = new ArrayList<>();
        for (String key : required) {
            String value = env.getProperty(key);
            if (value == null || value.trim().isEmpty() || value.startsWith("TODO_")) {
                missing.add(key);
            }
        }
        if (!missing.isEmpty()) {
            List<String> lines = new ArrayList<>();
            lines.add("以下配置项未填写或仍为 TODO_ 占位符：");
            for (String k : missing) lines.add("  - " + k);
            lines.add("请编辑 " + configFile.getAbsolutePath() + " 后重新启动");
            printAndExit(lines.toArray(new String[0]));
        }
    }

    private Set<String> extractRequiredKeys() {
        Set<String> keys = new LinkedHashSet<>();
        try {
            ClassPathResource res = new ClassPathResource(EXAMPLE_CLASSPATH);
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(res.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    Matcher m = KEY_PATTERN.matcher(line);
                    if (m.find()) {
                        keys.add(m.group(1));
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("警告：未找到 " + EXAMPLE_CLASSPATH + "，跳过自动必填项检测");
        }
        return keys;
    }

    private void printAndExit(String... lines) {
        String bar = "============================================================";
        System.err.println();
        System.err.println(bar);
        System.err.println("  配置校验失败 / Configuration Check Failed");
        System.err.println(bar);
        for (String l : lines) System.err.println("  " + l);
        System.err.println(bar);
        System.err.println();
        System.exit(1);
    }
}
```

- [ ] **Step 2：编译验证**

```bash
export JAVA_HOME=/Users/processmonitor/Documents/jdk/jdk8/Contents/Home
cd xwSystem/xwBackend
/Users/processmonitor/Documents/apache-maven-3.9.16/bin/mvn -o compile 2>&1 | tail -10
```

Expected: `BUILD SUCCESS`

- [ ] **Step 3：commit**

```bash
cd xwSystem/xwBackend
git add src/main/java/com/qy/dch/config/ConfigBootstrapValidator.java
git commit -m "feat(config): 新增启动配置校验类 ConfigBootstrapValidator"
```

---

## Task 6：注册 `EnvironmentPostProcessor`

**Files:**
- Create: `xwSystem/xwBackend/src/main/resources/META-INF/spring.factories`

- [ ] **Step 1：创建目录并写文件**

```bash
mkdir -p xwSystem/xwBackend/src/main/resources/META-INF
```

写入文件 `xwSystem/xwBackend/src/main/resources/META-INF/spring.factories`：

```
org.springframework.boot.env.EnvironmentPostProcessor=\
com.qy.dch.config.ConfigBootstrapValidator
```

- [ ] **Step 2：验证文件**

Run: `cat xwSystem/xwBackend/src/main/resources/META-INF/spring.factories`
Expected: 两行内容如上。

- [ ] **Step 3：commit**

```bash
cd xwSystem/xwBackend
git add src/main/resources/META-INF/spring.factories
git commit -m "feat(config): spring.factories 注册 ConfigBootstrapValidator"
```

---

## Task 7：改造仓库 `application.yml`

**Files:**
- Modify: `xwSystem/xwBackend/src/main/resources/application.yml`

- [ ] **Step 1：用以下完整内容覆盖**

```yaml
spring:
  application:
    name: uygur-project
  profiles:
    active: local
  datasource:
    url: jdbc:mysql://${DB_HOST:}:${DB_PORT:3306}/${DB_NAME:}?useUnicode=true&characterEncoding=utf8&zeroDateTimeBehavior=convertToNull&useSSL=true&serverTimezone=GMT%2B8
    username: ${DB_USERNAME:}
    password: ${DB_PASSWORD:}
    driver-class-name: com.mysql.cj.jdbc.Driver
    type: com.alibaba.druid.pool.DruidDataSource
    druid:
      initial-size: 5
      min-idle: 5
      max-active: 50
      max-wait: 60000
      time-between-eviction-runs-millis: 60000
      min-evictable-idle-time-millis: 300000
      validation-query: SELECT 1 FROM DUAL
      test-while-idle: true
      test-on-borrow: false
      test-on-return: false
      pool-prepared-statements: true
      max-pool-prepared-statement-per-connection-size: 20
      filters: stat,wall,log4j2
      stat-view-servlet:
        enabled: true
        url-pattern: /druid/*
        login-username: ${DRUID_LOGIN_USERNAME:}
        login-password: ${DRUID_LOGIN_PASSWORD:}
      web-stat-filter:
        enabled: true
        url-pattern: /*
        exclusions: "*.js,*.gif,*.jpg,*.png,*.css,*.ico,/druid/*"
      filter:
        stat:
          slow-sql-millis: 2000
          log-slow-sql: true
  servlet:
    multipart:
      max-file-size: 100MB
      max-request-size: 100MB
    encoding:
      charset: UTF-8
      enabled: true
      force: true

server:
  port: 8081

management:
  endpoints:
    web:
      exposure:
        include: health,info
  endpoint:
    health:
      show-details: never

springdoc:
  api-docs:
    path: /v3/api-docs
  swagger-ui:
    path: /swagger-ui.html
    tags-sorter: alpha
    operations-sorter: alpha
  default-produces-media-type: application/json
  default-consumes-media-type: application/json

logging:
  level:
    root: info
    com.qy.dch: debug
  file:
    path: logs
    name: logs/application.log
  pattern:
    file: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"

mybatis-plus:
  type-aliases-package: com.qy.dch.dto
  mapper-locations: classpath:mapper/*.xml
  configuration:
    map-underscore-to-camel-case: true
  global-config:
    db-config:
      id-type: ASSIGN_ID
      logic-delete-field: deleted
      logic-delete-value: 1
      logic-not-delete-value: 0

filePath: ${IMPORT_FILE_PATH:}

algorithm:
  service:
    url: ${ALGORITHM_SERVICE_URL:}

minio:
  endpoint: ${MINIO_ENDPOINT:}
  access-key: ${MINIO_ACCESS_KEY:}
  secret-key: ${MINIO_SECRET_KEY:}
  bucket: ${MINIO_BUCKET:}

rag:
  elasticsearch:
    host: ${ES_HOST:}
    port: ${ES_PORT:9200}
    username: ${ES_USERNAME:}
    password: ${ES_PASSWORD:}
    index-name: xianwei_docs
  embedding:
    base-url: ${EMBEDDING_BASE_URL:}
    model: bge-large-zh-v1.5
    dimension: 1024
    batch-size: 32
    retry-count: 3
    retry-delay-ms: 5000
    timeout-seconds: 60
  chunk:
    short-threshold: 128
    medium-threshold: 512
    medium-size: 256
    long-size: 512
    overlap: 64
  indexing:
    max-duration-minutes: 30
    es-batch-size: 100
  search:
    bm25-weight: 0.3
    vector-weight: 0.7
    rrf-k: 60
    default-top-k: 10
  ocr:
    enabled: ${OCR_ENABLED:true}
    thread-pool-size: 4
    tesseract:
      datapath: ${TESSERACT_DATAPATH:}
      language: chi_sim
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

- [ ] **Step 2：grep 验证零明文**

Run:
```bash
grep -E "36\.141\.21\.176|jixianyuan1314|xianwei2024|xianwei-admin" xwSystem/xwBackend/src/main/resources/application.yml
```
Expected: 无输出（grep exit code 1）

- [ ] **Step 3：commit**

```bash
cd xwSystem/xwBackend
git add src/main/resources/application.yml
git commit -m "refactor(config): application.yml 敏感项全部改 \${VAR:} 占位，零明文"
```

---

## Task 8：跑现有全量测试，确认 application-test.yml 仍生效

**Files:**
- 无新增；仅运行

- [ ] **Step 1：先确认 `DchApplicationTests` 有 @ActiveProfiles("test")**

Run:
```bash
grep -n "@ActiveProfiles" xwSystem/xwBackend/src/test/java/com/qy/dch/DchApplicationTests.java
```
Expected: 命中 `@ActiveProfiles("test")`

- [ ] **Step 2：给 CategoryMapperTest / DocumentExportServiceTest / TargetExportControllerTest 也加 @ActiveProfiles("test")**

如果还没加，三个测试类的 `@SpringBootTest` 之前需要追加 `@ActiveProfiles("test")`。

校验：
```bash
for f in xwSystem/xwBackend/src/test/java/com/qy/dch/mapper/CategoryMapperTest.java \
         xwSystem/xwBackend/src/test/java/com/qy/dch/service/DocumentExportServiceTest.java \
         xwSystem/xwBackend/src/test/java/com/qy/dch/controller/TargetExportControllerTest.java; do
  echo "== $f =="
  grep -n "ActiveProfiles" "$f" || echo "(缺失)"
done
```

如果某个文件显示「(缺失)」，编辑该文件：

- 在 import 区追加 `import org.springframework.test.context.ActiveProfiles;`
- 在 `@SpringBootTest` 之后追加一行 `@ActiveProfiles("test")`

- [ ] **Step 3：跑全部测试**

```bash
export JAVA_HOME=/Users/processmonitor/Documents/jdk/jdk8/Contents/Home
cd xwSystem/xwBackend
/Users/processmonitor/Documents/apache-maven-3.9.16/bin/mvn -o test 2>&1 | tail -50
```

Expected: 所有测试 PASS（包括之前的 13 个 + 新的 3 个 ConfigBootstrapValidatorTest = 16 个）。校验类不应在 test profile 下触发 `System.exit`（如果触发，测试会异常终止）。

- [ ] **Step 4：commit（仅当 Step 2 有改动）**

```bash
cd xwSystem/xwBackend
git add src/test/java
git commit -m "test(config): 测试类显式声明 @ActiveProfiles(\"test\") 跳过外置校验"
```

---

## Task 9：手工冒烟 — 模拟"未创建 application-local.yml"场景

**Files:**
- 临时；测完清理

- [ ] **Step 1：构建 jar**

```bash
export JAVA_HOME=/Users/processmonitor/Documents/jdk/jdk8/Contents/Home
cd xwSystem/xwBackend
/Users/processmonitor/Documents/apache-maven-3.9.16/bin/mvn -o clean package -DskipTests 2>&1 | tail -10
ls -la target/*.jar
```

Expected: `BUILD SUCCESS`，`target/xwBackend-*.jar` 存在（约几十 MB）。

- [ ] **Step 2：找一个空目录，复制 jar 进去**

```bash
TMPDIR=$(mktemp -d)
cp xwSystem/xwBackend/target/xwBackend-*.jar "$TMPDIR/xwBackend.jar"
cd "$TMPDIR"
ls -la
```

Expected: 目录里只有 `xwBackend.jar`，没有 `config/`

- [ ] **Step 3：尝试启动（应失败）**

```bash
java -jar xwBackend.jar 2>&1 | head -40
echo "exit: $?"
```

Expected:
```
============================================================
  配置校验失败 / Configuration Check Failed
============================================================
  未找到外置配置文件: /path/to/tmp/xxxxx/./config/application-local.yml
  ...
============================================================
```
exit code: `1`

- [ ] **Step 4：清理**

```bash
rm -rf "$TMPDIR"
```

无 commit，仅冒烟。

---

## Task 10：手工冒烟 — 模拟"yml 含 TODO_ 占位"场景

**Files:**
- 临时

- [ ] **Step 1：在 Task 9 的 TMPDIR 基础上创建 config/ 和带 TODO_ 的 yml**

```bash
TMPDIR=$(mktemp -d)
cp xwSystem/xwBackend/target/xwBackend-*.jar "$TMPDIR/xwBackend.jar"
mkdir -p "$TMPDIR/config"
cp xwSystem/xwBackend/src/main/resources/application-local.yml.example "$TMPDIR/config/application-local.yml"
cd "$TMPDIR"
```

- [ ] **Step 2：尝试启动（应报必填项缺失）**

```bash
java -jar xwBackend.jar 2>&1 | head -40
echo "exit: $?"
```

Expected:
```
============================================================
  配置校验失败 / Configuration Check Failed
============================================================
  以下配置项未填写或仍为 TODO_ 占位符：
    - DB_HOST
    - DB_USERNAME
    - DB_PASSWORD
    - DRUID_LOGIN_PASSWORD
    - ALGORITHM_SERVICE_URL
    - MINIO_ENDPOINT
    - MINIO_ACCESS_KEY
    - MINIO_SECRET_KEY
    - ES_HOST
    - EMBEDDING_BASE_URL
  请编辑 .../config/application-local.yml 后重新启动
============================================================
```
exit code: `1`

- [ ] **Step 3：清理**

```bash
rm -rf "$TMPDIR"
```

---

## Task 11：组装 `/Users/processmonitor/Downloads/176-deploy/` 部署目录

**Files:**
- Create: `/Users/processmonitor/Downloads/176-deploy/start.sh`
- Create: `/Users/processmonitor/Downloads/176-deploy/stop.sh`
- Create: `/Users/processmonitor/Downloads/176-deploy/README.md`
- Create: `/Users/processmonitor/Downloads/176-deploy/xwBackend.jar`
- Create: `/Users/processmonitor/Downloads/176-deploy/config/application-local.yml.example`

- [ ] **Step 1：创建目录结构**

```bash
DEPLOY=/Users/processmonitor/Downloads/176-deploy
mkdir -p "$DEPLOY/config" "$DEPLOY/logs"
```

- [ ] **Step 2：写 start.sh**

写入 `/Users/processmonitor/Downloads/176-deploy/start.sh`：

```bash
#!/bin/bash
cd "$(dirname "$0")"

JAR="xwBackend.jar"
PID_FILE="app.pid"
LOG_FILE="logs/startup.log"

mkdir -p logs

if [ -f "$PID_FILE" ] && kill -0 "$(cat $PID_FILE)" 2>/dev/null; then
    echo "服务已在运行，PID: $(cat $PID_FILE)"
    exit 1
fi

if [ ! -f "config/application-local.yml" ]; then
    echo "ERROR: 缺少 config/application-local.yml"
    echo "请执行: cp config/application-local.yml.example config/application-local.yml"
    echo "然后按文件内 TODO_ 提示填入真实配置值"
    exit 1
fi

nohup java -Xms512m -Xmx2g -jar "$JAR" > "$LOG_FILE" 2>&1 &
echo $! > "$PID_FILE"
echo "服务已启动，PID: $(cat $PID_FILE)"
echo "日志: tail -f $LOG_FILE"
```

- [ ] **Step 3：写 stop.sh**

写入 `/Users/processmonitor/Downloads/176-deploy/stop.sh`：

```bash
#!/bin/bash
cd "$(dirname "$0")"
PID_FILE="app.pid"

if [ ! -f "$PID_FILE" ]; then
    echo "PID 文件不存在，服务可能未运行"
    exit 0
fi

PID=$(cat "$PID_FILE")
if kill -0 "$PID" 2>/dev/null; then
    kill "$PID"
    echo "已发送停止信号，PID: $PID"
    rm -f "$PID_FILE"
else
    echo "进程 $PID 不存在，清理 PID 文件"
    rm -f "$PID_FILE"
fi
```

- [ ] **Step 4：写 README.md**

写入 `/Users/processmonitor/Downloads/176-deploy/README.md`：

````markdown
# xwBackend 部署使用说明

## 前置要求

- JDK 1.8（目标机执行 `java -version` 确认）
- 数据库 MySQL、MinIO、Elasticsearch、算法服务、Embedding 服务均已就绪并可联通

## 首次部署

1. 解压本目录到目标机器（例：`/opt/xianwei/176-deploy`）
2. 进入目录：`cd /opt/xianwei/176-deploy`
3. 复制配置模板：
   ```bash
   cp config/application-local.yml.example config/application-local.yml
   ```
4. 编辑配置，把所有 `TODO_xxx` 替换成现场真实值：
   ```bash
   vim config/application-local.yml
   ```
5. 启动：`./start.sh`
6. 查看日志：`tail -f logs/startup.log`

## 常用操作

| 操作 | 命令 |
|---|---|
| 启动 | `./start.sh` |
| 停止 | `./stop.sh` |
| 重启 | `./stop.sh && ./start.sh` |
| 看日志 | `tail -f logs/startup.log` |

**修改配置后必须重启才生效。**

## 更新版本

1. `./stop.sh`
2. 替换 `xwBackend.jar`（`config/` 目录保留不动）
3. `./start.sh`

## 常见错误

| 错误信息 | 解决 |
|---|---|
| `未找到外置配置文件: ./config/application-local.yml` | 按"首次部署"步骤 3 复制模板 |
| `以下配置项未填写或仍为 TODO_ 占位符` | 按提示编辑 yml 填入真实值 |
| `Communications link failure` | 检查 `DB_HOST/DB_PORT` 与目标机网络连通性 |
| `Connection refused: localhost:5001` | 算法服务未启动，检查 `ALGORITHM_SERVICE_URL` |
| `端口 8081 已被占用` | `lsof -i:8081` 找占用进程，或修改 yml 里的 `server.port` |

## 安全建议

部署完成后，限制配置文件权限：
```bash
chmod 600 config/application-local.yml
```
````

- [ ] **Step 5：拷贝 jar 和模板**

```bash
DEPLOY=/Users/processmonitor/Downloads/176-deploy
cp xwSystem/xwBackend/target/xwBackend-*.jar "$DEPLOY/xwBackend.jar"
cp xwSystem/xwBackend/src/main/resources/application-local.yml.example "$DEPLOY/config/"
chmod +x "$DEPLOY/start.sh" "$DEPLOY/stop.sh"
ls -la "$DEPLOY/"
ls -la "$DEPLOY/config/"
```

Expected: `xwBackend.jar` + `start.sh`(可执行) + `stop.sh`(可执行) + `README.md` + `config/application-local.yml.example`

---

## Task 12：在 `176-deploy/` 里真实端到端启动一次

**Files:**
- 临时：`/Users/processmonitor/Downloads/176-deploy/config/application-local.yml`（测完保留或删，由 SugarDaddy 决定）

- [ ] **Step 1：拷贝模板为真实 yml 并填入开发机可达的真值**

```bash
DEPLOY=/Users/processmonitor/Downloads/176-deploy
cp "$DEPLOY/config/application-local.yml.example" "$DEPLOY/config/application-local.yml"
```

编辑 `$DEPLOY/config/application-local.yml`，把 `TODO_` 全部替换为当前开发环境真实值：

```yaml
DB_HOST: 36.141.21.176
DB_PORT: 9204
DB_NAME: uygur_project
DB_USERNAME: root
DB_PASSWORD: jixianyuan1314

DRUID_LOGIN_USERNAME: admin
DRUID_LOGIN_PASSWORD: admin

IMPORT_FILE_PATH: /opt/uygur-project/data/all_text_time.json

ALGORITHM_SERVICE_URL: http://36.141.21.176:9203

MINIO_ENDPOINT: http://36.141.21.176:9205
MINIO_ACCESS_KEY: xianwei-admin
MINIO_SECRET_KEY: xianwei2024
MINIO_BUCKET: xianwei-images

ES_HOST: 36.141.21.176
ES_PORT: 9200
ES_USERNAME:
ES_PASSWORD:

EMBEDDING_BASE_URL: http://36.141.21.176:5002

OCR_ENABLED: false
TESSERACT_DATAPATH: /usr/share/tesseract-ocr/4.00/tessdata
```

- [ ] **Step 2：启动**

```bash
cd /Users/processmonitor/Downloads/176-deploy
./start.sh
sleep 15
cat logs/startup.log | tail -50
```

Expected:
- 没有「配置校验失败」横幅
- 出现 Spring Boot banner 和 `Started DchApplication in X seconds`
- 端口 `8081` 监听

- [ ] **Step 3：探活**

```bash
curl -sf http://localhost:8081/actuator/health
echo
```

Expected: `{"status":"UP"}`

- [ ] **Step 4：停止**

```bash
cd /Users/processmonitor/Downloads/176-deploy
./stop.sh
```

Expected: `已发送停止信号，PID: xxx`

- [ ] **Step 5：清理（可选）**

由 SugarDaddy 决定是否保留 `config/application-local.yml`。如要清理：

```bash
rm -f /Users/processmonitor/Downloads/176-deploy/config/application-local.yml
rm -f /Users/processmonitor/Downloads/176-deploy/app.pid
rm -rf /Users/processmonitor/Downloads/176-deploy/logs
```

无 commit。

---

## Task 13：清理 + 最终 push

**Files:**
- 无新增

- [ ] **Step 1：跑一遍 git status 检查**

```bash
cd xwSystem/xwBackend
git status
```

Expected: working tree clean

- [ ] **Step 2：列出所有本任务相关 commit**

```bash
cd xwSystem/xwBackend
git log --oneline -10
```

应包含（按从新到旧）：
- `test(config): 测试类显式声明 @ActiveProfiles("test") 跳过外置校验`（可能无）
- `refactor(config): application.yml 敏感项全部改 ${VAR:} 占位，零明文`
- `feat(config): spring.factories 注册 ConfigBootstrapValidator`
- `feat(config): 新增启动配置校验类 ConfigBootstrapValidator`
- `test(config): 校验类纯逻辑测试 + 修正模板让必填项 TODO_ 在值首`
- `chore(config): .gitignore 忽略外置 application-local.yml`
- `feat(config): 新增 application-local.yml.example 模板`

- [ ] **Step 3：push 子模块**

```bash
cd xwSystem/xwBackend
git push origin wqZhang 2>&1 | tail -5
```

Expected: 推送成功，无冲突

- [ ] **Step 4：外层仓库更新子模块指针并 push**

```bash
cd /Users/processmonitor/Documents/IdeaProject/xwProject
git add xwSystem/xwBackend
git commit -m "chore: 更新 xwBackend 子模块（配置-代码分离）"
git push origin main 2>&1 | tail -5
```

Expected: 推送成功

---

## Self-Review

**Spec coverage:** 逐节比对 spec：
- §1 背景目标 → Task 1-13 全覆盖
- §2 总体方案 A → Task 5+6+7 实现
- §3 文件结构 → 全 13 Task 涉及
- §4 配置项清单（含 OCR_ENABLED / TESSERACT_DATAPATH）→ Task 1+4+7 覆盖
- §5.1 改造后 yml → Task 7
- §5.2 example 模板 → Task 1+4
- §5.3 校验类 → Task 5
- §5.4 spring.factories → Task 6
- §5.5 .gitignore → Task 2
- §6 部署目录 → Task 11
- §7 测试 → Task 3+8（单测 + 端到端冒烟在 Task 9/10/12）
- §8 安全 → Task 11 README 提及 chmod 600
- §9 兼容性 → Task 8（验证 application-test.yml + @ActiveProfiles）
- §10 风险 → 校验器跳过 test profile 在 Task 5 代码体现

**Placeholder scan:** 无 TBD / TODO（代码块里的 `TODO_` 是设计有意保留的占位符）。
**Type consistency:** `KEY_PATTERN`、`EXTERNAL_CONFIG`、`EXAMPLE_CLASSPATH` 三个常量在 Task 3/5 一致。
