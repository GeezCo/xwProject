# Word 文档处理工具集

## 工具说明

### 1. format_word.py - Word文档格式化工具
自动将Word文档格式化为专业的软件设计说明格式。

**功能：**
- 自动识别标题（1, 1.1, 1.1.1等）
- 自动应用标题样式
- 自动格式化正文
- 自动设置三线表样式
- 自动转换将来时态
- 支持配置文件自定义样式

**使用方法：**
```bash
python format_word.py input.docx output.docx
python format_word.py input.docx output.docx --config word_format.conf
```

### 2. md_to_word.py - Markdown转Word工具
将Markdown文档转换为Word，支持Mermaid图表。

**功能：**
- 支持Mermaid图表渲染（SVG/PNG/PDF）
- 支持Markdown表格
- 无乱码转换
- 支持自定义Word模板

**使用方法：**
```bash
python md_to_word.py input.md output.docx
python md_to_word.py input.md output.docx --format svg
```

### 3. word_format.conf - 样式配置文件
定义Word文档的样式规范，优先级最高。

**配置项：**
- 字体、字号、行距
- 标题样式（1-6级）
- 表格样式（三线表）
- 将来时态转换规则

## 目录结构

```
tools/
├── format_word.py      # Word格式化工具
├── md_to_word.py       # Markdown转Word工具
├── word_format.conf    # 样式配置文件
├── README.md           # 本说明文件
├── examples/           # 示例文件
│   └── example.md
└── diagrams/           # 图表输出目录
```

## 环境要求

```bash
# Python依赖
pip install python-docx

# Markdown转Word额外依赖
# Pandoc: https://pandoc.org/installing.html
# Mermaid CLI: npm install -g @mermaid-js/mermaid-cli
```