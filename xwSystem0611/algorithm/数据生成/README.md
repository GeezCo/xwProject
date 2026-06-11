# 数据生成模块

基于LLM的文本数据生成器，通过仿写样例文本批量生成相似风格的文本数据。

## 算法思路

```
样例目录 → 读取所有txt文件 → 对每个样例：
                              ├─ 从候选池随机选择内容
                              ├─ 补全占位符（XX、某人、某国等）
                              ├─ 随机增加事件条目（正偏离）
                              └─ 保持样例结构和风格
        → 并行生成指定数量 → 保存为独立txt文件
```

**核心功能**：
1. **候选池随机化**：时间、地点、人物、组织、装备、行为都从候选池随机选择
2. **条目数量正偏离**：可在样例基础上随机增加1-5个事件条目
3. **占位符补全**：自动识别并替换各类占位符
4. **批量生成**：支持指定每个样例生成的总数
5. **并行加速**：多线程并行调用LLM，提升生成效率

## 目录结构

```
数据生成/
├── data_generator.py      # 主程序
├── candidate_pools.py     # 候选池配置
├── requirements.txt       # 依赖列表
├── LLM_api.txt           # API密钥
├── 样例/                 # 输入样例目录
│   └── 示例新闻.txt
└── 输出/                 # 生成结果目录
    ├── generation_report.json
    └── 示例新闻/
        ├── 习近平在巴黎同马克龙举行会谈.txt
        ├── 习近平在巴黎爱丽舍宫同马克龙举行会晤.txt
        └── ...
```

## 使用方法

### 命令行调用

```bash
cd 算法/数据生成

# 基本用法：每个样例生成10篇，中等多样性，使用默认GLM-5模型
python data_generator.py 样例 输出

# 指定每个样例生成100篇，并行5个，高多样性
python data_generator.py 样例 输出 --total 100 --parallel 5 --diversity high

# 【推荐】细分多样性配置：条目保守 + 内容丰富
python data_generator.py 样例 输出 --item_diversity low --content_diversity high

# 生成指定日期的数据
python data_generator.py 样例 输出 --start_date 2026-04-19 --end_date 2026-04-19

# 生成日期范围内的数据（每个日期生成100条）
python data_generator.py 样例 输出 --start_date 2026-04-15 --end_date 2026-04-19 --total 100 --output_format jsonl

# 指定自定义模型
python data_generator.py 样例 输出 --base_url https://api.deepseek.com --model deepseek-chat --api_key sk-xxx

# 完整参数示例
python data_generator.py 样例 输出 \
    --total 100 \
    --parallel 5 \
    --base_url https://api.openai.com/v1 \
    --model gpt-4 \
    --api_key sk-xxx \
    --item_diversity high \
    --content_diversity high
```

### 参数说明

| 参数 | 说明 | 默认值 |
|------|------|--------|
| `sample_dir` | 样例目录路径（必需） | - |
| `output_dir` | 输出目录路径（必需） | - |
| `--total` | 每个样例生成的总数 | 10 |
| `--parallel` | 并行生成数量 | 1 |
| `--base_url` | API基础URL | https://llmapi.paratera.com |
| `--model` | 模型名称 | GLM-5 |
| `--api_key` | API密钥 | 内置默认密钥 |
| `--diversity` | 多样性级别（旧接口，同时设置条目和内容） | - |
| `--item_diversity` | 条目多样性级别 | medium |
| `--content_diversity` | 候选内容多样性级别 | medium |
| `--start_date` | 开始日期（格式：YYYY-MM-DD） | 今天 |
| `--end_date` | 结束日期（格式：YYYY-MM-DD） | 今天 |
| `--output_format` | 输出格式（txt/jsonl） | txt |

### 多样性级别（细分配置）

支持两个独立维度的多样性控制：

#### 条目多样性 (`--item_diversity`)

控制事件条目数量的增加范围。

| 级别 | 条目增加范围 | 说明 |
|------|-------------|------|
| low | 0-1 | 几乎不增加条目，保持样例原有结构 |
| medium | 1-3 | 适度增加条目，内容稍丰富 |
| high | 2-5 | 大幅增加条目，内容显著丰富 |

#### 候选内容多样性 (`--content_diversity`)

控制候选池大小和生成温度。

| 级别 | 温度 | 候选池大小 | 说明 |
|------|------|-----------|------|
| low | 0.6 | 小 | 候选少，生成保守，更贴近样例 |
| medium | 0.8 | 中 | 平衡多样性和一致性 |
| high | 0.95 | 大 | 候选多，生成随机，内容更丰富 |

#### 组合示例

| 条目多样性 | 内容多样性 | 效果 |
|-----------|-----------|------|
| low | low | 最保守，几乎完全复刻样例 |
| low | high | 结构保守，但用词丰富多样 |
| high | low | 结构扩展，但用词贴近样例 |
| high | high | 最丰富，结构和内容都大幅变化 |

### 日期范围功能 (`--start_date` / `--end_date`)

支持指定日期范围生成数据，每个日期生成 `{total}` 条数据：

```bash
# 生成今天的数据（默认）
python data_generator.py 样例 输出

# 生成指定日期的数据
python data_generator.py 样例 输出 --start_date 2026-04-19 --end_date 2026-04-19

# 生成日期范围内的数据（每个日期生成100条）
python data_generator.py 样例 输出 --start_date 2026-04-15 --end_date 2026-04-19 --total 100

# 生成过去7天的数据
python data_generator.py 样例 输出 --start_date 2026-04-12 --end_date 2026-04-19 --output_format jsonl
```

**参数说明：**
| 参数 | 说明 | 默认值 |
|------|------|--------|
| `--start_date` | 开始日期（格式：YYYY-MM-DD） | 今天 |
| `--end_date` | 结束日期（格式：YYYY-MM-DD） | 今天 |

**计算公式：** 总数据量 = `日期天数 × 样例数 × total`

例如：`--start_date 2026-04-15 --end_date 2026-04-19 --total 100`，5天 × 7个样例 × 100 = 3500条

### 输出格式 (`--output_format`)

支持两种输出格式：

#### txt格式（默认）
每条数据保存为单独的txt文件：
```
输出/
└── 样例名/
    ├── 标题1.txt
    ├── 标题2.txt
    └── ...
```

#### jsonl格式（推荐用于数据处理）
所有数据汇总在一个jsonl文件中，每条数据包含3个字段：
- `时间`：生成日期（基于start_date/end_date参数）
- `标题`：文本标题
- `内容`：正文内容

```bash
# 使用jsonl格式输出
python data_generator.py 样例 输出 --output_format jsonl

# 生成当日数据汇总
python data_generator.py 样例 输出 --output_format jsonl

# 生成日期范围内的数据汇总
python data_generator.py 样例 输出 --start_date 2026-04-15 --end_date 2026-04-19 --output_format jsonl
```

输出示例：
```
输出/
└── data_20260415_20260419.jsonl   # 包含日期范围内所有数据
```

jsonl文件内容示例：
```json
{"时间": "2026年04月15日", "标题": "美军加强印太地区防御部署", "内容": "4月15日情况：\n美国空军多型飞机在亚太地区活动..."}
{"时间": "2026年04月16日", "标题": "多国军事动向综合报告", "内容": "4月16日（15日18时至16日14时）情况：\n..."}
```

**推荐组合**：
```bash
# 生成当日情报数据汇总（最适合定时任务）
python data_generator.py 样例 输出 --output_format jsonl --item_diversity low --content_diversity high --total 100
```

### 代码调用

```python
from data_generator import DataGenerator

# 方式1：使用默认配置（GLM-5）
generator = DataGenerator()

# 方式2：指定自定义模型
generator = DataGenerator(
    base_url="https://api.openai.com/v1",
    model="gpt-4",
    api_key="sk-xxx"
)

# 方式3：使用细分多样性配置
generator = DataGenerator(
    api_key="sk-xxx",
    base_url="https://llmapi.paratera.com",
    model="GLM-5",
    item_diversity="low",      # 条目保守
    content_diversity="high"   # 内容丰富
)

# 方式4：指定日期范围
generator = DataGenerator(
    api_key="sk-xxx",
    start_date="2026-04-15",   # 开始日期
    end_date="2026-04-19",     # 结束日期（每个日期生成{total}条）
    output_format="jsonl"      # 输出为jsonl汇总文件
)

# 方式5：旧接口（同时设置两个维度）
generator = DataGenerator(api_key="sk-xxx", diversity="high")

# 执行生成
summary = generator.generate_all(
    sample_dir="样例",
    output_dir="输出",
    total=100,      # 每个样例生成100篇
    parallel=5      # 并行5个线程
)

# 打印摘要
generator.print_summary(summary)
```

## 候选池配置

候选池定义在 `candidate_pools.py` 中，包含以下类别：

### 时间候选池
- 日期：1-12月 × 1-28日组合
- 时刻：00-23时 × 00/15/30/45分
- 时间段：X时至X时

### 地点候选池
- 海域：南海、东海、波斯湾、红海、地中海等
- 城市：北京、华盛顿、东京、首尔等
- 基地：关岛、横须贺、嘉手纳、吉布提莱蒙尼尔营等
- 空域：东海防空识别区、南海空域等

### 人物候选池
- 美国政要：拜登、哈里斯、布林肯、奥斯汀等
- 中国政要：习近平、李强、王毅等
- 日本政要：岸田文雄、岸信夫等
- 其他：普京、马克龙、泽连斯基等

### 组织候选池
- 国家：美国、中国、日本、俄罗斯、伊朗等
- 军事组织：美军、自卫队、北约、第七舰队等
- 政治组织：白宫、五角大楼、联合国等

### 装备候选池
- 飞机：MQ-9A无人机、P-8A巡逻机、F-35战斗机等
- 舰船：驱逐舰、巡洋舰、航母、潜艇等
- 导弹：爱国者、萨德、战斧等

### 行为候选池
- 侦察类：侦察、抵近侦察、监视、巡逻等
- 军事行动：演习、打击、拦截、部署等
- 外交行为：会晤、会谈、签署、声明等

## 样例文件格式

样例文件为普通txt文件，可以包含占位符：

```
【标题】某国总统访问某地

某年某月某日，某国总统在某地与某人举行会晤。
双方就某问题进行了深入讨论，并达成某项协议。
...
```

占位符将被从候选池中随机选择替换：
- `XX`、`某人` → 人物候选池
- `某单位`、`某机构` → 组织候选池
- `某国` → 国家候选池
- `某地`、`某地市`、`某基地` → 地点候选池
- `某年`、`某月`、`某日`、`X日`、`X时` → 时间候选池
- `某型飞机`、`某型舰船` → 装备候选池
- `XX任务`、`XX活动` → 行为候选池

## 输出格式

### 生成的文本文件

每篇生成的文本以标题命名，保存为独立txt文件：

```
输出/
└── 示例新闻/
    ├── 拜登与王毅在白宫举行会晤.txt
    ├── 习近平在巴黎同马克龙举行会谈.txt
    └── ...
```

### 生成报告

`generation_report.json` 记录生成统计信息：

```json
{
  "start_time": "2024-01-01 10:00:00",
  "end_time": "2024-01-01 10:30:00",
  "duration_seconds": 1800,
  "sample_count": 2,
  "total_per_sample": 100,
  "parallel_count": 5,
  "model": "GLM-5",
  "base_url": "https://llmapi.paratera.com",
  "diversity": "medium",
  "diversity_params": {
    "temperature": 0.8,
    "extra_items_range": [1, 3],
    "item_diversity": "medium",
    "content_diversity": "medium"
  },
  "sample_stats": [...],
  "total_generated": 200,
  "total_failed": 0
}
```

## 依赖安装

```bash
pip install openai
```

## LLM API配置

支持自定义大模型配置，可通过命令行参数或代码指定：

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `base_url` | https://llmapi.paratera.com | API基础URL |
| `model` | GLM-5 | 模型名称 |
| `api_key` | 内置默认密钥 | API密钥 |
| `temperature` | 根据diversity动态调整 | 生成温度 |

### 支持的模型示例

```bash
# 使用GLM-5（默认）
python data_generator.py 样例 输出

# 使用DeepSeek
python data_generator.py 样例 输出 --base_url https://api.deepseek.com --model deepseek-chat --api_key sk-xxx

# 使用OpenAI
python data_generator.py 样例 输出 --base_url https://api.openai.com/v1 --model gpt-4 --api_key sk-xxx

# 使用本地Ollama
python data_generator.py 样例 输出 --base_url http://localhost:11434/v1 --model llama3 --api_key ollama
```

## 注意事项

1. 生成速度取决于并行数量和网络延迟，默认 `--parallel 1`，可根据需要调高
2. 生成的文本会及时保存，即使中断也不会丢失已完成的部分
3. 标题会自动清理非法字符，过长的标题会被截断
4. 相同标题的文件会自动添加序号后缀避免覆盖
5. 每次生成都会重新从候选池随机抽取，确保生成内容的多样性

## 扩展候选池

可在 `candidate_pools.py` 中扩展候选池内容：

```python
# 添加新的人物
class PersonPool:
    US_LEADERS = [
        "拜登", "哈里斯", "布林肯",
        # 添加新人物...
    ]

# 添加新的地点
class LocationPool:
    SEAS = [
        "南海", "东海", "波斯湾",
        # 添加新海域...
    ]
```