# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 模块概述

基于LLM的文本数据生成器，通过仿写样例文本批量生成相似风格的文本数据。

核心功能：
- 候选池随机化：时间、地点、人物、组织、装备、行为从候选池随机选择
- 条目数量正偏离：可在样例基础上随机增加事件条目
- 占位符补全：自动识别并替换"某"、"XX"等占位符
- 实时时间：支持使用当前日期而非随机日期

## 运行命令

```bash
# 基本用法
python data_generator.py 样例 输出

# 指定数量和并行度
python data_generator.py 样例 输出 --total 100 --parallel 5

# 使用实时时间（生成当日情报）
python data_generator.py 样例 输出 --use_real_time

# 自定义模型
python data_generator.py 样例 输出 --model Qwen3.5-122B-A10B --base_url https://llmapi.paratera.com

# 推荐配置：条目保守 + 内容丰富
python data_generator.py 样例 输出 --item_diversity low --content_diversity high

# 使用jsonl输出格式（所有数据汇总在一个文件）
python data_generator.py 样例 输出 --output_format jsonl

# 生成当日数据汇总（推荐用于定时任务）
python data_generator.py 样例 输出 --use_real_time --output_format jsonl
```

## 参数说明

| 参数 | 说明 | 默认值 |
|------|------|--------|
| `--total` | 每个样例生成数量 | 10 |
| `--parallel` | 并行线程数 | 3 |
| `--model` | 模型名称 | GLM-5 |
| `--base_url` | API地址 | https://llmapi.paratera.com |
| `--item_diversity` | 条目多样性 (low/medium/high) | medium |
| `--content_diversity` | 内容多样性 (low/medium/high) | medium |
| `--use_real_time` | 使用实时时间 | 关闭 |

## 多样性配置

条目多样性控制事件增加范围：
- low: 0-1个，几乎不增加
- medium: 1-3个，适度增加
- high: 2-5个，大幅增加

内容多样性控制候选池大小和温度：
- low: 温度0.6，候选少，生成保守
- medium: 温度0.8，平衡
- high: 温度0.95，候选多，生成丰富

## 架构

```
data_generator.py          # 主程序：DataGenerator类
    ├── BASE_SYSTEM_PROMPT      # 系统提示词（占位符替换规则）
    ├── USER_PROMPT_TEMPLATE    # 用户提示词模板
    ├── generate_one()          # 单条生成（调用LLM）
    └── generate_all()          # 批量并行生成
        │
        └── candidate_pools.py   # 候选池配置
            ├── TimePool         # 时间候选池
            ├── LocationPool     # 地点候选池
            ├── PersonPool       # 人物候选池
            ├── OrganizationPool # 组织候选池
            ├── EquipmentPool    # 装备候选池
            └── ActionPool       # 行为候选池
```

## 占位符替换规则

样例中的占位符会被候选池内容替换：
- `XX`、`某人` → 人物
- `某单位`、`某机构` → 组织
- `某国` → 国家
- `某地`、`某基地` → 地点
- `某年`、`某月`、`某日` → 时间
- `某型飞机`、`某型舰船` → 装备
- `某号船`、`某号舰` → 舰船名称（如"里根"号）
- `XX任务`、`XX活动` → 行为

**重要**：所有"某"字占位符必须替换为具体内容，绝不能保留"某"字。

## 输出格式

生成文件：
```
输出/
├── generation_report.json   # 生成统计报告
└── 样例名/
    ├── 标题1.txt
    ├── 标题2.txt
    └── ...
```

每篇文件格式：
```
【标题】xxx

正文内容...
```

## 依赖

```bash
pip install openai
```

## 注意事项

- 生成速度取决于并行数和网络延迟，建议 `--parallel` 3-5
- 标题自动清理非法字符，过长标题会被截断
- 相同标题文件自动添加序号后缀避免覆盖
- 每次生成从候选池随机抽取，确保多样性