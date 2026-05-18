# Markdown 转 Word 完整解决方案

## 稳定、无乱码的MD转Word方案（支持Mermaid图表、表格等）

---

## 一、环境准备

### 1. 必需工具

```bash
# 1. 安装 Pandoc（核心转换工具）
# Windows: 下载安装包 https://pandoc.org/installing.html
# 或使用 chocolatey
choco install pandoc

# 2. 安装 Node.js（用于Mermaid渲染）
# 下载 https://nodejs.org/

# 3. 安装 Mermaid CLI（图表渲染工具）
npm install -g @mermaid-js/mermaid-cli

# 4. 验证安装
pandoc --version
mmdc --version  # Mermaid CLI
```

### 2. 可选工具（增强功能）

```bash
# ImageMagick（图片处理，可选）
choco install imagemagick

# 用于公式渲染（如果MD中有LaTeX公式）
choco install miktex
```

---

## 二、转换流程

### 流程图

```
原始MD文件 (input.md)
       │
       ▼
  ┌─────────────────────────────┐
  │ 步骤1: 提取图表代码         │
  │   - 识别 ```mermaid 代码块  │
  │   - 识别其他图表代码块      │
  └─────────────────────────────┘
       │
       ▼
  ┌─────────────────────────────┐
  │ 步骤2: 渲染图表为图片       │
  │   - 使用 mmdc 渲染 Mermaid  │
  │   - 输出 PNG 格式图片       │
  └─────────────────────────────┘
       │
       ▼
  ┌─────────────────────────────┐
  │ 步骤3: 生成中间MD文件       │
  │   - 替换图表代码为图片引用  │
  │   - 保留表格、文本等原样    │
  └─────────────────────────────┘
       │
       ▼
  ┌─────────────────────────────┐
  │ 步骤4: 使用Pandoc转Word     │
  │   - 保持UTF-8编码           │
  │   - 应用Word模板（可选）    │
  └─────────────────────────────┘
       │
       ▼
  最终Word文档 (output.docx)
```

---

## 三、使用方法

### 快速开始（推荐）

```bash
# 一键转换
python md_to_word.py input.md output.docx

# 带自定义模板
python md_to_word.py input.md output.docx --template custom.docx

# 指定输出目录（存放中间图片）
python md_to_word.py input.md output.docx --output-dir ./images
```

### 分步执行（调试用）

```bash
# 步骤1: 提取并渲染图表
python extract_diagrams.py input.md --output-dir ./diagrams

# 步骤2: 生成替换后的MD
python replace_diagrams.py input.md diagrams/ intermediate.md

# 步骤3: 转换为Word
pandoc intermediate.md -o output.docx --from markdown --to docx
```

---

## 四、支持的图表类型

| 图表类型 | Mermaid关键字 | 支持状态 | 说明 |
|---------|--------------|---------|------|
| 流程图 | `graph` / `flowchart` | ✅ 完全支持 | |
| 时序图 | `sequenceDiagram` | ✅ 完全支持 | |
| 甘特图 | `gantt` | ✅ 完全支持 | |
| ER图 | `erDiagram` | ✅ 完全支持 | |
| 类图 | `classDiagram` | ✅ 完全支持 | |
| 状态图 | `stateDiagram` | ✅ 完全支持 | |
| 饼图 | `pie` | ✅ 完全支持 | |
| Git图 | `gitgraph` | ✅ 完全支持 | |
| 表格 | Markdown表格 | ✅ 完全支持 | 直接转换 |
| 数学公式 | LaTeX公式 | ✅ 支持 | 需安装MiKTeX |

---

## 五、常见问题解决

### 问题1: 中文乱码

**原因**: 编码不一致

**解决方案**:
```bash
# 确保MD文件是UTF-8编码
# 在Pandoc命令中指定编码
pandoc input.md -o output.docx --metadata lang=zh-CN
```

### 问题2: 图片显示为空白

**原因**: 图片路径问题或渲染失败

**解决方案**:
```bash
# 检查图片是否正确生成
ls -la ./images/

# 使用绝对路径
pandoc input.md -o output.docx --resource-path="./images"
```

### 问题3: 表格边框歪斜

**原因**: Word模板样式问题

**解决方案**:
```bash
# 使用自定义Word模板
pandoc input.md -o output.docx --reference-doc=template.docx
```

### 问题4: 字体不统一

**解决方案**: 使用参考文档模板，预定义样式

---

## 六、Word模板定制（重要）

为了确保输出格式的稳定性和美观性，强烈建议使用自定义Word模板。

### 模板制作步骤

1. **创建基准文档**
```bash
# 导出Pandoc默认模板
pandoc -o custom-reference.docx --print-default-data-file reference.docx
```

2. **编辑样式**
   - 打开 `custom-reference.docx`
   - 修改各级标题样式（标题1-6）
   - 修改正文样式
   - 修改表格样式
   - 修改代码块样式
   - 保存

3. **使用模板**
```bash
pandoc input.md -o output.docx --reference-doc=custom-reference.docx
```

### 推荐模板样式设置

| 元素 | 推荐设置 |
|-----|---------|
| 正文字体 | 微软雅黑 / 宋体，10.5pt |
| 标题1 | 黑体，16pt，加粗 |
| 标题2 | 黑体，14pt，加粗 |
| 标题3 | 黑体，12pt，加粗 |
| 表格 | 边框1pt实线，浅灰底色表头 |
| 代码块 | Consolas字体，浅灰背景 |
| 图片 | 居中对齐，自动缩放 |

---

## 七、项目结构

```
md_to_word_solution/
├── README.md                   # 本说明文件
├── md_to_word.py              # 主转换脚本
├── extract_diagrams.py        # 图表提取工具
├── replace_diagrams.py        # 图表替换工具
├── template.docx              # Word模板文件
├── examples/                  # 示例文件
│   ├── example.md            # 示例MD文件
│   ├── example_output.docx   # 示例输出
│   └── diagrams/             # 示例图表图片
│       ├── diagram_001.png
│       └── diagram_002.png
└── tests/                    # 测试用例
    ├── test_chinese.md
    ├── test_tables.md
    └── test_mermaid.md
```

---

## 八、高级用法

### 1. 批量转换

```python
import glob
from md_to_word import convert_md_to_word

for md_file in glob.glob("*.md"):
    output = md_file.replace('.md', '.docx')
    convert_md_to_word(md_file, output)
```

### 2. 自定义图片处理

```python
# 修改图片分辨率
convert_md_to_word(
    'input.md', 
    'output.docx',
    image_dpi=300,  # 高清输出
    image_format='png'
)
```

### 3. 添加水印/页眉页脚

使用Word模板实现，在模板中设置页眉页脚即可。

---

## 九、对比其他方案

| 方案 | 优点 | 缺点 | 推荐度 |
|-----|------|------|--------|
| **本方案** | 稳定、无乱码、图表完整 | 需要安装多个工具 | ⭐⭐⭐⭐⭐ |
| Typora导出 | 简单易用 | 批量不便、样式难控 | ⭐⭐⭐ |
| 直接复制粘贴 | 快速 | 格式易错、不可复现 | ⭐⭐ |
| 在线转换工具 | 无需安装 | 隐私问题、不稳定 | ⭐⭐ |

---

## 十、验证检查清单

转换完成后，请检查：

- [ ] 所有图表正确显示
- [ ] 表格边框完整无歪斜
- [ ] 中文无乱码
- [ ] 标题层级正确
- [ ] 图片位置正确
- [ ] 代码块格式正常
- [ ] 页眉页脚正确（如使用模板）

---

## 联系支持

如遇到问题，请提供：
1. 原始MD文件
2. 转换命令
3. 错误信息
4. 系统环境（pandoc版本、node版本等）