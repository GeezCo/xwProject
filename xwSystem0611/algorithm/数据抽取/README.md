# 数据抽取模块

从非结构化文本中自动抽取结构化事件信息的算法模块。

## 主要功能

### 事件抽取

从文本中识别并抽取原子化事件，每个事件包含以下要素：

| 要素 | 说明 | 示例 |
|------|------|------|
| time | 事件发生时间 | "2024年3月15日" |
| location | 事件发生地点 | ["横须贺", "关岛", "南海"] |
| subject | 事件参与者 | ["萨德反导系统", "第七舰队"] |
| action | 核心行为 | "保持战备状态" |
| original_text | 原文句子 | 完整原文 |
| labels | 分类标签 | ["军事"] |

### 层次化处理架构

采用两层筛选架构，显著降低LLM调用成本：

```
输入文本
    │
    ▼
┌─────────────────────────────────────┐
│  第一层：规则筛选（快速、低成本）      │
│  ├─ 时间表达式正则匹配（13种模式）    │
│  ├─ 行为动词检测（200+动词词典）      │
│  ├─ 主体识别（人物/组织/武器/部队）   │
│  └─ 筛选条件：有主体 OR 有时间        │
└─────────────────────────────────────┘
    │
    ├─ 无事件特征 → 跳过（节省LLM调用）
    │
    ▼ 有事件特征
┌─────────────────────────────────────┐
│  第二层：LLM精确抽取（慢速、高精度）   │
│  └─ 调用大模型输出结构化JSON          │
└─────────────────────────────────────┘
    │
    ▼
添加标签 → 合并去重 → JSON输出
```

**成本节省效果**：

| 文本类型 | 节省比例 |
|----------|----------|
| 新闻报道（事件密集） | 10-20% |
| 评论文章（事件稀疏） | 40-70% |
| 混合文档 | 30-50% |

## 快速开始

### 安装依赖

```bash
cd 算法/数据抽取
pip install -r requirements.txt
```

### 配置API密钥

复制配置模板并修改：

```bash
cp config.example.json config.json
```

编辑 `config.json`：

```json
{
    "llm": {
        "base_url": "https://llmapi.paratera.com",
        "model": "Qwen3.5-122B-A10B",
        "api_key": "YOUR_API_KEY_HERE",
        "temperature": 0.1,
        "max_tokens": 16000
    },
    "labels": {
        "default": "军事"
    }
}
```

### 命令行使用

```bash
# 基本用法
python hierarchical_extractor.py input.txt

# 指定输出文件
python hierarchical_extractor.py input.txt output.json

# 批量处理
python batch_test.py 测试数据/
```

### 代码调用

```python
from hierarchical_extractor import HierarchicalEventExtractor

# 创建抽取器（自动从config.json读取配置）
extractor = HierarchicalEventExtractor()

# 处理文本
result = extractor.extract_all(text)

# 或处理文件
text = extractor.read_txt_file("input.txt")
result = extractor.extract_all(text)
extractor.save_to_json(result, "output.json")
```

## 输出格式

```json
{
  "extraction_time": "2024-04-14 10:00:00",
  "text_length": 5000,
  "paragraph_count": 15,
  "llm_calls": 8,
  "llm_calls_saved": 7,
  "total_events": 12,
  "events": [
    {
      "event_id": 1,
      "time": "2024年3月15日",
      "location": ["横须贺", "关岛"],
      "subject": ["萨德反导系统", "标准导弹"],
      "action": "保持战备状态",
      "original_text": "美横须贺、关岛的萨德反导系统...",
      "labels": ["军事"]
    }
  ],
  "labels": ["军事"],
  "skipped_paragraphs": [...]
}
```

## 模块组成

| 文件 | 功能 | 说明 |
|------|------|------|
| `hierarchical_extractor.py` | 层次化抽取 | 主入口，规则筛选 + LLM精确抽取 |
| `llm_event_extractor.py` | LLM抽取 | 第二层处理，调用大模型 |
| `element_extractor.py` | 规则抽取 | 纯规则+分词，无需LLM |
| `batch_test.py` | 批量测试 | 批量处理多个文件 |
| `config.json` | 配置文件 | API密钥、模型参数等 |

## 配置说明

### config.json 配置项

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| llm.base_url | API服务地址 | https://llmapi.paratera.com |
| llm.model | 模型名称 | GLM-5 |
| llm.api_key | API密钥 | 需要配置 |
| llm.temperature | 温度参数 | 0.1 |
| llm.max_tokens | 最大输出token | 16000 |
| extraction.min_paragraph_length | 最小段落长度 | 20 |
| extraction.retry_count | 重试次数 | 3 |
| labels.default | 默认标签 | 军事 |

### 环境变量支持

也可以通过环境变量配置API密钥：

```bash
export LLM_API_KEY="your-api-key"
```

## 技术特点

1. **多编码支持**：自动识别 UTF-8、GBK、GB2312、UTF-16 编码
2. **API重试机制**：失败自动重试3次，每次间隔2秒
3. **JSON解析容错**：三重解析策略，兼容不同模型输出格式
4. **事件去重**：基于原文标准化比较，避免重复事件
5. **预编译正则**：启动时编译正则表达式，提高匹配性能

## 依赖

- `openai>=1.0.0` - LLM API客户端
- `jieba>=0.42.1` - 中文分词（规则抽取）

## 注意事项

1. 配置文件 `config.json` 包含API密钥，已添加到 `.gitignore`，不会提交到版本控制
2. GLM-5模型返回内容在 `reasoning_content` 字段，代码已做兼容处理
3. 处理时间取决于文本长度和LLM响应速度，通常2-5分钟