# 配置-代码分离设计稿（外置 application-local.yml）

**作者：** SugarDaddy + Claude
**日期：** 2026-06-15
**状态：** 待实施

## 1. 背景与目标

### 1.1 痛点

当前 `xwBackend/src/main/resources/application.yml` 中包含大量环境敏感配置（DB / MinIO / ES / 算法服务 IP、账号、密码等），且默认值直接写着开发环境的真实地址和密码：

- `DB_HOST` 默认 `36.141.21.176`，`DB_PASSWORD` 默认 `jixianyuan1314`
- `MINIO_ENDPOINT` 默认 `http://36.141.21.176:8522`，`MINIO_SECRET_KEY` 默认 `xianwei2024`

每次切换环境（开发机 ↔ 现场内网机 176）都需要：
- 改 yml → 重新打包 → 传 jar
- 或在目标机内网上深入代码修改（不现实，目标机无 mvn）

### 1.2 目标

- **仓库零明文：** git 仓库里不再出现任何真实 IP、账号、密码
- **配置外置：** 真实配置以单独文件形式放在部署目录，jar 不重打
- **强校验：** 缺配置启动失败，错误提示明确
- **运维友好：** 部署目录即拿即用，仅需改一个 yml + `./start.sh`
- **加新配置免改代码：** 加配置项只需在模板文件加一行

### 1.3 部署形态

- **开发机：** macOS，有 mvn、JDK 8
- **目标机：** 内网 176，仅 JRE，无 mvn、无外网、需通过光盘刻录拷贝部署包

### 1.4 范围

- 仅后端 `xwBackend` 模块
- 不涉及前端、不涉及 algorithm 子模块
- 不涉及 Spring profile 多档（dev/test/prod 等）—— 只用单一 `local` profile

## 2. 总体方案

**方案 A · 单一外置 `application-local.yml`：**

- 仓库 `application.yml` 中的环境敏感项全部改为 `${VAR:}` 占位（默认空）
- `spring.profiles.active: local` 写死，启动时自动加载 jar 同级 `./config/application-local.yml` 覆盖默认空值
- 仓库提供 `application-local.yml.example` 模板，运维拷贝改名后填值
- 自定义 `EnvironmentPostProcessor` 在 Spring 容器初始化前校验配置，缺失即退出
- 校验所需的必填项清单由 `.example` 模板自动扫描得出（识别 `TODO_` 前缀），新增配置无需改代码

## 3. 文件结构

### 3.1 仓库内（进 git）

```
xwBackend/
├── src/main/resources/
│   ├── application.yml                  # 改造后：结构 + ${VAR:} 占位，零明文
│   ├── application-local.yml.example    # 新增：环境敏感项模板，带 TODO_ 占位
│   └── META-INF/
│       └── spring.factories             # 新增：注册 EnvironmentPostProcessor
├── src/main/java/com/qy/dch/config/
│   └── ConfigBootstrapValidator.java    # 新增：启动前置校验类
└── .gitignore                           # 新增忽略：src/main/resources/application-local.yml
```

### 3.2 部署目录（不进 git，最终打包给现场）

```
/Users/processmonitor/Downloads/176-deploy/
├── xwBackend.jar                        # mvn package 产物
├── start.sh                             # 启动脚本
├── stop.sh                              # 停止脚本
├── config/
│   ├── application-local.yml.example    # 从仓库 src/main/resources 拷出
│   └── application-local.yml            # 首次部署需复制 example 并填值
├── logs/                                # 运行时自动生成
└── README.md                            # 部署使用说明
```

### 3.3 职责划分

| 文件 | 内容 | 谁改 |
|---|---|---|
| 仓库 `application.yml` | 全部配置 key，敏感项 `${VAR:}` 默认空 | 开发者（改结构时） |
| 仓库 `application-local.yml.example` | 仅环境敏感项模板 + 注释 | 开发者（新增环境项时） |
| 部署 `config/application-local.yml` | 真实环境值 | 运维 |

## 4. 配置项清单

### 4.1 必须外置（共 18 项）

| 字段 | 类型 | 说明 |
|---|---|---|
| `DB_HOST` | DB | 数据库 IP |
| `DB_PORT` | DB | 数据库端口（默认 3306） |
| `DB_NAME` | DB | 库名 |
| `DB_USERNAME` | DB | DB 用户名 |
| `DB_PASSWORD` | DB | DB 密码 |
| `DRUID_LOGIN_USERNAME` | Druid | 监控页用户名 |
| `DRUID_LOGIN_PASSWORD` | Druid | 监控页密码 |
| `IMPORT_FILE_PATH` | 文件 | 报文导入路径 |
| `ALGORITHM_SERVICE_URL` | 算法 | 算法服务 URL |
| `MINIO_ENDPOINT` | MinIO | MinIO 服务地址 |
| `MINIO_ACCESS_KEY` | MinIO | MinIO Access Key |
| `MINIO_SECRET_KEY` | MinIO | MinIO Secret Key |
| `MINIO_BUCKET` | MinIO | 桶名 |
| `ES_HOST` | ES | Elasticsearch IP |
| `ES_PORT` | ES | Elasticsearch 端口（默认 9200） |
| `ES_USERNAME` | ES | ES 用户名（可空） |
| `ES_PASSWORD` | ES | ES 密码（可空） |
| `EMBEDDING_BASE_URL` | Embedding | Embedding 服务 URL |
| `OCR_ENABLED` | OCR | OCR 开关（默认 true） |
| `TESSERACT_DATAPATH` | OCR | Tesseract 数据路径 |

### 4.2 保留在仓库（结构性配置）

- `server.port: 8081`
- Druid 连接池调优（`initial-size`、`max-active`、`max-wait` 等）
- MyBatis-Plus 配置（`map-underscore-to-camel-case`、`id-type` 等）
- Springdoc / Swagger UI 路径
- 日志 pattern
- RAG 算法参数（chunk threshold、search weights、retry-count 等）

## 5. 详细设计

### 5.1 改造后的仓库 `application.yml`

```yaml
spring:
  application:
    name: uygur-project
  profiles:
    active: local                    # 自动从 ./config/application-local.yml 加载
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

### 5.2 `application-local.yml.example`（仓库 + 部署目录都有一份）

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

**字段命名说明：** 字段名用大写下划线（如 `DB_HOST`），与仓库 `application.yml` 占位符 `${DB_HOST:}` 同名。Spring Boot 自动把这些 key 注册为 environment property，从而填入对应位置。运维看到的是平面 key，不需要懂 `spring.datasource.url` 嵌套结构。

### 5.3 `ConfigBootstrapValidator.java`（启动校验类）

```java
package com.qy.dch.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ConfigBootstrapValidator implements EnvironmentPostProcessor {

    /** 从 example 文件自动提取必填字段（以 TODO_ 开头的字段） */
    private static final Pattern KEY_PATTERN = Pattern.compile("^([A-Z_]+):\\s*TODO_");

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication app) {
        // 1. 检查外置 yml 是否存在
        File configFile = new File("./config/application-local.yml");
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

        // 2. 从 example 文件提取必填字段清单
        Set<String> required = extractRequiredKeys();

        // 3. 扫描必填项
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

    /** 从 classpath:application-local.yml.example 提取带 TODO_ 的字段名 */
    private Set<String> extractRequiredKeys() {
        Set<String> keys = new LinkedHashSet<>();
        try {
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
        } catch (IOException e) {
            System.err.println("警告：未找到 application-local.yml.example，跳过自动必填项检测");
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

**为什么用 `EnvironmentPostProcessor`：**
- 在 Spring 容器创建之前执行，避免 Druid 在配置缺失时尝试连接 DB 抛出晦涩的 `CommunicationsException`
- 错误信息整洁清晰，不会被 Spring Boot banner 和 stacktrace 淹没

### 5.4 `META-INF/spring.factories`

```
org.springframework.boot.env.EnvironmentPostProcessor=\
com.qy.dch.config.ConfigBootstrapValidator
```

### 5.5 `.gitignore` 新增行

```
# 外置真实配置不进 git
src/main/resources/application-local.yml
```

## 6. 部署目录（176-deploy）

### 6.1 `start.sh`

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

### 6.2 `stop.sh`

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

### 6.3 `README.md`

````markdown
# xwBackend 部署使用说明

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
5. 检查 JDK 1.8 已安装：`java -version`
6. 启动：`./start.sh`
7. 查看日志：`tail -f logs/startup.log`

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
| `端口 8081 已被占用` | 用 `lsof -i:8081` 找占用进程或修改 yml 里的 `server.port` |
````

### 6.4 开发机打包流程

```bash
cd /Users/processmonitor/Documents/IdeaProject/xwProject/xwSystem/xwBackend
mvn clean package -DskipTests

# 拷贝产物到 176-deploy 目录
DEPLOY=/Users/processmonitor/Downloads/176-deploy
mkdir -p $DEPLOY/config $DEPLOY/logs
cp target/xwBackend-*.jar $DEPLOY/xwBackend.jar
cp src/main/resources/application-local.yml.example $DEPLOY/config/

# 给脚本可执行权限
chmod +x $DEPLOY/start.sh $DEPLOY/stop.sh

# 光盘刻录前可压缩
cd /Users/processmonitor/Downloads
zip -r 176-deploy.zip 176-deploy/
```

## 7. 测试方案

### 7.1 单元测试

- `ConfigBootstrapValidatorTest`（新增）：
  - 测试 1：`application-local.yml.example` 中含 `TODO_` 的字段被正确提取
  - 测试 2：缺少 yml 文件时抛错信息正确（需 mock `System.exit`）
  - 测试 3：所有必填项就绪时不抛错

### 7.2 手工冒烟（开发机）

1. 改造完成后，**不创建** `./config/application-local.yml` → `java -jar` 应立即报「未找到外置配置文件」并退出 1
2. 创建但保留 `TODO_` → 报「以下配置项未填写或仍为 TODO_ 占位符」并列出缺失项
3. 全部填好真实值 → 正常启动，能调通 `/api/target/fusion/list` 等接口

## 8. 安全考虑

- `application-local.yml` 加入 `.gitignore`，防误提交
- 仓库内 `application.yml` 改造后不再有任何明文密码/真实 IP，commit 历史中的旧密码视情况决定是否 BFG 清理（本次设计不强制）
- 部署目录里 `config/application-local.yml` 应限制权限：`chmod 600`，README 中提示

## 9. 与现有功能的兼容性

- 仅修改 `application.yml` 结构与新增校验类，**不改业务代码**
- 现有 13 个测试（CategoryMapperTest / DocumentExportServiceTest / TargetExportControllerTest）依赖的 DB 连接通过 Spring Boot test profile（已有 `src/test/resources/application.yml` 或测试时通过 `@TestPropertySource` 注入）—— 若没有，需补一份 `src/test/resources/application-local.yml` 供测试使用

## 10. 风险与缓解

| 风险 | 缓解 |
|---|---|
| `EnvironmentPostProcessor` 不触发 | `spring.factories` 是 Spring Boot 标准扩展点，验证方式：故意删 example 文件，启动应报警告 |
| `TODO_` 模式过于死板 | 模板字段命名约定即可，如未来需自定义占位符模式可改为 `^([A-Z_]+):\s*<<.+>>` 等 |
| 测试环境跑不起来 | 在 `src/test/resources/` 下放一份 `application-local.yml`（不进 git），或测试时用 `@TestPropertySource` 覆盖 |
| 运维误把 `.example` 删了 | README 强调「.example 是仓库提供的，不要删」；启动校验仅依赖此文件提取必填项 |

## 11. 不做的事（YAGNI）

- 不引入 Spring Cloud Config / Apollo / Nacos 等配置中心（离线内网用不上）
- 不引入加密（如 jasypt）—— 外置 yml + 文件权限已够，加密会增加运维难度
- 不做 dev/test/prod 多 profile 切换（明确说了只有一台开发机 + 一台现场机）
- 不做配置热更新（修改后重启即可）

