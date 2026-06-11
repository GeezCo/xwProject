# 数据抽取模块

本模块提供从文本中抽取结构化事件信息的功能，支持三种抽取方式。

## 模块组成

| 文件 | 抽取方式 | 特点 |
|------|----------|------|
| `hierarchical_extractor.py` | 层次化抽取 | 规则筛选 + LLM精确抽取，节省约50% API成本 |
| `llm_event_extractor.py` | 纯LLM抽取 | 精度高但成本较高，适合事件密集型文本 |
| `element_extractor.py` | 规则抽取 | 纯规则+分词，无需调用LLM，速度快但精度有限 |

---

## 一、层次化事件抽取（推荐）

### 算法思路

```
输入文本 → 分段 → 第一层筛选（规则） → 有事件特征？
                                    ├─ 否 → 跳过
                                    └─ 是 → 第二层（LLM精确抽取）
        → 合并去重 → 输出JSON
```

**第一层筛选规则**：
- 检测时间表达（正则匹配日期、相对时间词等）
- 检测行为动词（预定义200+动词词典）
- 检测主体（人物姓名模式、组织名称模式）
- 筛选条件：有行为动词 + (有时间 或 有主体)

**成本节省**：
- 新闻报道（事件密集）：节省约20%
- 评论文章（事件稀疏）：节省约70%
- 混合文档：节省约50%

### 使用方法

**命令行调用**：
```bash
cd 算法/数据抽取

# 基本用法（输出到 input_hierarchical_events.json）
python hierarchical_extractor.py input.txt

# 指定输出文件
python hierarchical_extractor.py input.txt output.json

# 指定API密钥
python hierarchical_extractor.py input.txt output.json sk-xxx
```

**代码调用**：
```python
from hierarchical_extractor import HierarchicalEventExtractor

# 创建抽取器
extractor = HierarchicalEventExtractor(api_key="sk-xxx")

# 方式1：直接处理文本
result = extractor.extract_all(text)

# 方式2：处理文件
text = extractor.read_txt_file("input.txt")
result = extractor.extract_all(text)
extractor.save_to_json(result, "output.json")
```

### 输出格式

```json
{
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
      "original_text": "2024年3月15日，拜登在白宫签署了..."
    }
  ],
  "skipped_paragraphs": [...]
}
```

---

## 二、纯LLM事件抽取

### 算法思路

对每个段落直接调用大模型进行事件抽取，通过精心设计的提示词引导模型输出结构化JSON。

**特点**：
- 精度高，适合复杂文本
- 成本较高，每个段落都调用LLM
- 支持并行处理提升速度

### 使用方法

**命令行调用**：
```bash
cd 算法/数据抽取

# 基本用法
python llm_event_extractor.py input.txt

# 指定输出文件
python llm_event_extractor.py input.txt output.json
```

**代码调用**：
```python
from llm_event_extractor import LLMEventExtractor

extractor = LLMEventExtractor(api_key="sk-xxx")
result = extractor.extract_all(text)
```

---

## 三、规则要素抽取

### 算法思路

使用jieba分词 + 正则表达式，无需调用LLM。

**抽取要素**：
- 时间：日期格式、相对时间词
- 地点：地名后缀、地点指示词
- 主体：人名模式、组织名称
- 行为：动词词典、行为模式

**特点**：
- 速度快，无API成本
- 精度有限，适合快速预览

### 使用方法

**命令行调用**：
```bash
cd 算法/数据抽取

python element_extractor.py input.txt
```

**代码调用**：
```python
from element_extractor import ElementExtractor

extractor = ElementExtractor()
result = extractor.extract_all(text)
```

---

## 依赖安装

```bash
pip install openai jieba
```

## LLM API配置

模型配置（实际使用Qwen3.5-122B-A10B，兜底GLM-5）：

| 配置项 | 值 |
|--------|-----|
| BASE_URL | https://llmapi.paratera.com |
| MODEL | Qwen3.5-122B-A10B（主）/ GLM-5（兜底） |
| API_KEY | 见 `LLM_api.txt` |

**注意**：GLM-5返回 `reasoning_content`，Qwen返回标准 `content`，代码已做兼容处理。
