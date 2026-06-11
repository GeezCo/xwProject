# 事件拆分算法

## 功能说明

将包含多个事件的文本拆分为独立的原子化事件，每个事件保留为一段完整文字。

### 核心特性

1. **智能拆分**：识别文本中的多个事件并拆开
2. **要素补全**：自动识别全局时间/地点，补全到每个事件中
3. **规则+LLM结合**：规则预处理 + LLM精细处理，兼顾效率与准确性

## 算法思路

```
输入文本
    ↓
┌─────────────────────────────┐
│  阶段1：规则预处理           │
│  - 按空行分割                │
│  - 按编号分割                │
│  - 粗拆为段落                │
└─────────────────────────────┘
    ↓
┌─────────────────────────────┐
│  阶段2：LLM智能拆分          │
│  - 识别全局时间/地点         │
│  - 拆分为独立事件            │
│  - 每个事件补全要素          │
└─────────────────────────────┘
    ↓
┌─────────────────────────────┐
│  阶段3：结果验证             │
│  - 过滤空事件                │
│  - 格式化输出                │
└─────────────────────────────┘
    ↓
输出JSON
```

## 使用方法

### 命令行调用

```bash
# 基本用法
python event_splitter.py <input.txt>

# 指定输出文件
python event_splitter.py <input.txt> <output.json>

# 示例
python event_splitter.py sample.txt result.json
```

### 代码调用

```python
from event_splitter import EventSplitter

# 创建拆分器
splitter = EventSplitter()

# 方式1：直接处理文本
text = "4月28日，美军在日本海进行军事演习，同日在菲律宾海进行侦察活动。"
result = splitter.split(text)

# 方式2：处理文件
from event_splitter import process_file
result = process_file("input.txt", "output.json")

# 获取拆分后的事件
for event in result['events']:
    print(f"事件{event['event_id']}: {event['content']}")
```

## 输出格式

```json
{
  "source_text": "原文内容",
  "source_file": "源文件名",
  "global_time": "4月28日",
  "global_location": null,
  "total_events": 2,
  "split_time": "2024-04-28 10:30:00",
  "events": [
    {
      "event_id": 1,
      "content": "4月28日，美军在日本海进行军事演习。"
    },
    {
      "event_id": 2,
      "content": "4月28日，美军在菲律宾海进行侦察活动。"
    }
  ]
}
```

## 依赖安装

```bash
pip install openai
```

## 配置说明

LLM API配置在类中定义：

```python
BASE_URL = "https://llmapi.paratera.com"
MODEL = "GLM-5"
```

如需使用其他API密钥：

```python
splitter = EventSplitter(api_key="your-api-key")
```

## 示例

**输入：**
```
4月28日7时至17时，有关渠道掌握，美关岛、夏威夷、冲绳的"爱国者"防空系统保持战备状态。

10月14日，美国海军第七舰队所属"林肯号"航母打击群在菲律宾海与日本海上自卫队进行联合演习。
```

**输出：**
- 事件1：4月28日7时至17时，有关渠道掌握，美关岛、夏威夷、冲绳的"爱国者"防空系统保持战备状态。
- 事件2：10月14日，美国海军第七舰队所属"林肯号"航母打击群在菲律宾海与日本海上自卫队进行联合演习。