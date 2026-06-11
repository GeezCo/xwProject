# 算法服务模块

基于Flask的REST API服务，将数据抽取算法封装为HTTP接口，供后端服务调用。

## 算法思路

```
后端服务 → HTTP POST请求 → Flask服务 → 调用抽取器 → 返回JSON结果
                ↓
         接收文本内容      ↓
                    层次化事件抽取
                    （规则筛选+LLM）
```

**服务架构**：
- 采用懒加载模式，首次请求时初始化抽取器
- 支持跨域访问（CORS），方便前端直接调用
- 统一的返回格式，便于后端解析

## 目录结构

```
算法服务/
├── app.py           # Flask主程序
├── requirements.txt # 依赖列表
└── logs/           # 日志目录（运行时自动创建）
```

## 启动服务

```bash
cd 算法/算法服务

# 安装依赖
pip install -r requirements.txt

# 启动服务
python app.py
```

启动成功后显示：
```
==================================================
算法服务启动中...
==================================================
API接口:
  - GET  /health      健康检查
  - POST /extract     事件抽取(完整)
  - POST /extract/simple  事件抽取(简化)
==================================================
端口: 5001
==================================================
```

服务默认运行在 `http://localhost:5001`

## API接口

### 1. 健康检查

```
GET /health
```

**用途**：验证服务是否正常运行，监控系统健康状态。

**返回示例**：
```json
{
  "status": "ok",
  "service": "algorithm-service",
  "time": "2024-01-01 12:00:00"
}
```

### 2. 完整事件抽取（推荐）

```
POST /extract
Content-Type: application/json
```

**请求体**：
```json
{
  "text": "要抽取的文本内容...",
  "origin_text_id": 123
}
```

| 参数 | 类型 | 必需 | 说明 |
|------|------|------|------|
| text | string | 是 | 要抽取的文本内容 |
| origin_text_id | integer | 否 | 文本ID，用于日志记录 |

**返回示例**：
```json
{
  "code": 1,
  "msg": "success",
  "data": {
    "extraction_time": "2024-01-01 12:00:00",
    "text_length": 5000,
    "paragraph_count": 15,
    "llm_calls": 8,
    "llm_calls_saved": 7,
    "total_events": 12,
    "events": [
      {
        "event_id": 1,
        "time": "2024年3月15日",
        "location": "白宫",
        "subject": {
          "persons": ["拜登"],
          "organizations": ["白宫"]
        },
        "action": "签署行政命令",
        "original_text": "原文句子..."
      }
    ]
  }
}
```

### 3. 简化事件抽取

```
POST /extract/simple
Content-Type: application/json
```

**请求体**：
```json
{
  "text": "要抽取的文本内容..."
}
```

**返回示例**：
```json
{
  "events": [...],
  "total": 12
}
```

## 后端调用示例

后端通过HTTP调用算法服务：

```java
// Java后端调用示例
HttpURLConnection conn = (HttpURLConnection) new URL("http://localhost:5001/extract").openConnection();
conn.setRequestMethod("POST");
conn.setRequestProperty("Content-Type", "application/json");
conn.setDoOutput(true);
conn.setReadTimeout(300000);  // 5分钟超时（LLM处理较慢）

// 发送请求
JSONObject body = new JSONObject();
body.put("text", content);
body.put("origin_text_id", id);
try (OutputStream os = conn.getOutputStream()) {
    os.write(body.toJSONString().getBytes(StandardCharsets.UTF_8));
}

// 读取响应
try (BufferedReader reader = new BufferedReader(
        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
    StringBuilder response = new StringBuilder();
    String line;
    while ((line = reader.readLine()) != null) {
        response.append(line);
    }
    JSONObject result = JSON.parseObject(response.toString());
}
```

## 依赖安装

```bash
pip install flask flask-cors openai
```

## 配置说明

| 配置项 | 值 | 说明 |
|--------|-----|------|
| 端口 | 5001 | 默认端口，可在 `app.py` 末尾修改 |
| API_KEY | 见代码 | LLM接口密钥 |
| 模型 | Qwen3.5-122B-A10B | 实际使用（配置于 数据抽取/config.json） |
| debug | False | 生产环境关闭调试模式 |

## 注意事项

1. **启动顺序**：算法服务必须先于后端启动，否则后端调用会失败
2. **超时设置**：LLM处理较慢，建议设置5分钟超时
3. **错误处理**：服务会捕获异常并返回500错误码，错误信息在 `msg` 字段
4. **日志**：运行日志保存在 `logs/` 目录

## 与后端的通信

```
┌─────────────┐     HTTP POST      ┌─────────────┐     HTTP      ┌─────────────┐
│   前端      │ ─────────────────▶ │   后端      │ ───────────▶ │  算法服务   │
│  Vue 2      │                    │ Spring Boot │              │   Flask     │
│  :8080      │                    │   :8081     │              │   :5001     │
└─────────────┘                    └─────────────┘              └─────────────┘
                                         │                              │
                                         ▼                              ▼
                                   ┌─────────────┐              ┌─────────────┐
                                   │   MySQL     │              │  Qwen/GLM   │
                                   │  Database   │              │   LLM API   │
                                   └─────────────┘              └─────────────┘
```

后端作为中间层：
- 接收前端请求
- 调用算法服务
- 保存结果到数据库
- 返回结果给前端
