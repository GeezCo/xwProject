#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Word模板创建工具
创建一个预定义样式的Word模板，用于Pandoc转换

使用方法:
    python create_template.py
    将生成 template.docx 文件
"""

import subprocess
import sys
from pathlib import Path


def create_default_template(output_file: str = 'template.docx') -> bool:
    """
    创建默认的Word模板

    使用pandoc导出默认模板，然后可以手动修改
    """
    try:
        # 检查pandoc
        result = subprocess.run(
            ['pandoc', '--version'],
            capture_output=True,
            text=True
        )

        if result.returncode != 0:
            print("错误: 未找到pandoc")
            return False

        # 导出默认模板
        cmd = [
            'pandoc',
            '-o', output_file,
            '--print-default-data-file', 'reference.docx'
        ]

        result = subprocess.run(cmd, capture_output=True, text=True)

        if result.returncode == 0:
            print(f"✓ 已创建模板: {output_file}")
            print("\n接下来请手动修改模板样式:")
            print("1. 打开 template.docx")
            print("2. 修改 标题1-6 样式（字体、大小、颜色）")
            print("3. 修改 正文 样式")
            print("4. 修改 表格 样式")
            print("5. 保存文件")
            return True
        else:
            print(f"创建失败: {result.stderr}")
            return False

    except FileNotFoundError:
        print("错误: 未找到pandoc命令")
        return False


def print_manual_guide():
    """打印手动创建指南"""
    guide = """
╔══════════════════════════════════════════════════════════════════╗
║                    Word模板手动创建指南                          ║
╚══════════════════════════════════════════════════════════════════╝

方法一：使用Pandoc导出默认模板（推荐）
─────────────────────────────────────
1. 运行命令:
   pandoc -o template.docx --print-default-data-file reference.docx

2. 打开 template.docx，修改样式:
   - 标题1: 黑体, 16pt, 加粗, 段前0.5行段后0.5行
   - 标题2: 黑体, 14pt, 加粗, 段前0.5行段后0.5行
   - 标题3: 黑体, 12pt, 加粗
   - 正文: 微软雅黑, 10.5pt
   - 表格网格: 边框1pt实线, 浅灰色

3. 保存模板文件


方法二：从现有Word文档创建
─────────────────────────────
1. 打开一个格式良好的Word文档
2. 删除所有内容，保留样式
3. 另存为 template.docx


方法三：使用Python创建模板
─────────────────────────────
安装: pip install python-docx
代码示例:

    from docx import Document
    from docx.shared import Pt, RGBColor, Inches
    from docx.enum.text import WD_PARAGRAPH_ALIGNMENT

    doc = Document()

    # 设置标题1样式
    style = doc.styles['Heading 1']
    style.font.name = '黑体'
    style.font.size = Pt(16)
    style.font.bold = True
    style.font.color.rgb = RGBColor(0, 0, 0)

    # 设置正文样式
    style = doc.styles['Normal']
    style.font.name = '微软雅黑'
    style.font.size = Pt(10.5)

    doc.save('template.docx')


推荐样式设置
─────────────
┌──────────┬────────────┬────────┬────────┐
│ 样式名称 │ 字体       │ 大小   │ 颜色   │
├──────────┼────────────┼────────┼────────┤
│ 标题1    │ 黑体       │ 16pt   │ 黑色   │
│ 标题2    │ 黑体       │ 14pt   │ 黑色   │
│ 标题3    │ 黑体       │ 12pt   │ 黑色   │
│ 正文     │ 微软雅黑   │ 10.5pt │ 黑色   │
│ 代码     │ Consolas   │ 9pt    │ 深灰   │
│ 表格标题│ 黑体       │ 10.5pt │ 黑色   │
└──────────┴────────────┴────────┴────────┘

表格样式
─────────────
- 边框: 1pt实线, 深灰色
- 表头背景: 浅灰色 (#E8E8E8)
- 单元格边距: 上下2pt, 左右4pt
- 对齐: 左对齐


图片样式
─────────────
- 对齐: 居中
- 最大宽度: 15cm (适应A4纸张)
- 标题: 图X-X 居中对齐


代码块样式
─────────────
- 字体: Consolas 或 等宽字体
- 大小: 9pt
- 背景色: 浅灰色 (#F5F5F5)
- 边框: 1pt实线, 灰色
"""
    print(guide)


if __name__ == '__main__':
    if len(sys.argv) > 1 and sys.argv[1] == '--guide':
        print_manual_guide()
    else:
        create_default_template()
        print("\n详细指南:")
        print_manual_guide()