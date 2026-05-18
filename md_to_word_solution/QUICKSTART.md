# 快速使用指南

## 一、环境安装（首次使用）

### 方法A：自动安装
双击运行 `install.bat`，脚本会自动检查和安装所需工具。

### 方法B：手动安装

1. **安装 Pandoc**
   - 下载：https://pandoc.org/installing.html
   - 或使用 chocolatey：`choco install pandoc`

2. **安装 Node.js**
   - 下载：https://nodejs.org/ (选择 LTS 版本)

3. **安装 Mermaid CLI**
   ```bash
   npm install -g @mermaid-js/mermaid-cli
   ```

---

## 二、基本使用

### 一键转换
```bash
python md_to_word.py input.md output.docx
```

### 使用模板（推荐）
```bash
# 1. 先创建模板
python create_template.py

# 2. 手动修改 template.docx 的样式（字体、颜色等）

# 3. 使用模板转换
python md_to_word.py input.md output.docx --template template.docx
```

---

## 三、支持的图表类型

| 类型 | 语法示例 | 说明 |
|-----|---------|------|
| 流程图 | `graph TD` | 支持上下左右方向 |
| 甘特图 | `gantt` | 时间线图表 |
| ER图 | `erDiagram` | 数据库实体关系 |
| 类图 | `classDiagram` | UML类图 |
| 时序图 | `sequenceDiagram` | 交互流程 |
| 状态图 | `stateDiagram-v2` | 状态转换 |
| 饼图 | `pie` | 数据占比 |
| 思维导图 | `mindmap` | 层级结构 |

---

## 四、常见问题

### Q1: 中文显示乱码？
**解决**：确保MD文件是UTF-8编码，使用模板时设置中文字体。

### Q2: 图片不显示？
**解决**：检查 `diagrams` 目录下是否有PNG文件生成。

### Q3: 表格边框歪斜？
**解决**：使用自定义Word模板，预设表格样式。

### Q4: mmdc命令找不到？
**解决**：运行 `npm install -g @mermaid-js/mermaid-cli`

---

## 五、进阶用法

### 批量转换
```python
# batch_convert.py
import glob
import subprocess

for md_file in glob.glob("*.md"):
    docx_file = md_file.replace('.md', '.docx')
    subprocess.run(['python', 'md_to_word.py', md_file, docx_file])
```

### 自定义图片分辨率
编辑 `md_to_word.py` 中的 `width` 参数：
```python
width=2400  # 更高分辨率
```

### 修改图表主题
```python
theme='dark'  # 可选: default, dark, forest, neutral
```

---

## 六、输出质量检查清单

转换完成后请检查：

- [ ] 所有图表正确渲染为图片
- [ ] 图片清晰无锯齿
- [ ] 表格边框整齐无歪斜
- [ ] 中文无乱码、无缺失
- [ ] 标题层级正确
- [ ] 代码块格式正确
- [ ] 页码、页眉页脚正确（如使用模板）

---

## 七、文件结构

```
md_to_word_solution/
├── md_to_word.py        ← 主转换脚本（运行这个）
├── create_template.py   ← 模板创建工具
├── install.bat          ← 环境安装脚本
├── README.md            ← 详细文档
├── QUICKSTART.md        ← 本文档
└── examples/
    └── example.md       ← 示例文件
```