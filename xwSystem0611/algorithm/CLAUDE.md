# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Module Overview

算法模块 - Python LLM处理核心，包含以下功能子模块：

- **算法服务/** - Flask REST API服务 (端口5001)
- **数据抽取/** - 层次化事件抽取算法
- **数据生成/** - 基于样例的文本生成
- **事件拆分/** - 多事件文本拆分为原子事件
- **报文融合/** - 多报文融合生成综合报告

## Build & Run Commands

### 算法服务 (必须先启动)
```bash
cd 算法服务
pip install -r requirements.txt
python app.py
```
服务启动后访问：
- `GET  /health` - 健康检查
- `POST /extract` - 完整事件抽取
- `POST /extract/simple` - 简化事件抽取

### 命令行工具

事件抽取：
```bash
cd 数据抽取
pip install -r requirements.txt
python hierarchical_extractor.py <input.txt> [output.json]
```

数据生成：
```bash
cd 数据生成
pip install -r requirements.txt
python data_generator.py <样例目录> <输出目录> --total 100 --parallel 5 --diversity medium
```

事件拆分：
```bash
cd 事件拆分
python event_splitter.py <input.txt> [output.json]
```

## Architecture

### 层次化处理架构 (核心优化)

```
输入文本
    │
    ▼
┌─────────────────────────────┐
│  第一层：规则筛选 (快速)      │
│  - 时间表达式正则匹配         │
│  - 行为动词列表检测           │
│  - 主体(人物/组织)识别        │
│  - 判断：有行为动词 + (时间或主体) │
└─────────────────────────────┘
    │
    ├─ 无事件特征 → 跳过 (节省LLM调用)
    │
    ▼ 有事件特征
┌─────────────────────────────┐
│  第二层：LLM精确抽取 (慢速)   │
│  - 调用GLM-5进行结构化抽取    │
│  - 输出：时间、地点、主体、行为 │
└─────────────────────────────┘
    │
    ▼
合并去重 → JSON输出
```

**成本节省**：新闻报道~20%，评论文章~70%，混合文档~50%

### 模块依赖关系

```
算法服务/app.py
    └── 数据抽取/hierarchical_extractor.py
            └── 数据抽取/llm_event_extractor.py

数据生成/data_generator.py
    └── 数据生成/candidate_pools.py

事件拆分/event_splitter.py (独立)
```

## LLM API Configuration

```python
BASE_URL = "https://llmapi.paratera.com"
API_KEY = "sk-Sf3cCx7aSWyk_4KUFoi8Tw"
```

**实际使用模型**（配置于 `数据抽取/config.json`）：
- **属性抽取、报文融合**：`Qwen3.5-122B-A10B`（122B参数，通义千问3.5）
- **兜底模型**：`GLM-5`

**兼容处理**：GLM-5返回 `reasoning_content`，Qwen返回标准 `content`，代码自动适配：
```python
if (content is None or not content.strip()) and hasattr(msg, 'reasoning_content'):
    content = msg.reasoning_content
```

## Key Patterns

### 1. 多编码文件读取
依次尝试：UTF-8 → GBK → GB2312 → UTF-16

### 2. JSON响应解析 (三重策略)
1. 直接解析
2. 提取markdown代码块 (` ```json ... ``` `)
3. 花括号计数提取完整对象

### 3. API调用重试机制
- 默认重试3次
- 每次失败后等待2秒
- 温度参数：0.1-0.3 (低温度保证稳定性)

### 4. 事件数据结构
```json
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
```

## Dependencies

- `openai>=1.0.0` - LLM API客户端
- `flask>=2.0.0` - REST API框架
- `flask-cors>=3.0.0` - 跨域支持
- `jieba>=0.42.1` - 中文分词 (数据抽取)
- `requests>=2.28.0` - HTTP请求

## Notes

- 算法服务必须先于后端启动 (端口5001)
- 抽取操作耗时2-5分钟 (取决于文本长度)
- 层次化抽取可节省约50% LLM调用成本
- 支持已抽取结果缓存，避免重复处理