#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Markdown 转 Word 完整解决方案
支持Mermaid图表、表格、无乱码转换

使用方法:
    python md_to_word.py input.md output.docx
    python md_to_word.py input.md output.docx --template template.docx
    python md_to_word.py input.md output.docx --keep-temp
"""

import os
import sys
import re
import shutil
import tempfile
import subprocess
import hashlib
from pathlib import Path
from typing import List, Tuple, Optional
import argparse
import json


class MermaidExtractor:
    """提取和渲染Mermaid图表"""

    # 支持的图表类型
    DIAGRAM_TYPES = [
        'graph', 'flowchart',
        'sequenceDiagram',
        'classDiagram',
        'stateDiagram',
        'erDiagram',
        'gantt',
        'pie',
        'gitgraph',
        'journey',
        'mindmap',
        'quadrantChart',
        'requirementDiagram',
        'C4Context'
    ]

    def __init__(self, output_dir: str = './diagrams'):
        self.output_dir = Path(output_dir)
        self.output_dir.mkdir(parents=True, exist_ok=True)
        self.diagram_index = 0

    def extract_mermaid_blocks(self, content: str) -> List[Tuple[int, int, str]]:
        """
        提取所有Mermaid代码块
        返回: [(开始位置, 结束位置, 代码内容), ...]
        """
        blocks = []
        # 匹配 ```mermaid ... ```
        pattern = r'```mermaid\s*\n(.*?)```'

        for match in re.finditer(pattern, content, re.DOTALL):
            blocks.append((match.start(), match.end(), match.group(1)))

        return blocks

    def render_to_image(self, mermaid_code: str, output_path: str,
                        theme: str = 'default',
                        background: str = 'white',
                        width: int = 1200) -> bool:
        """
        使用mmdc将Mermaid代码渲染为图片

        Args:
            mermaid_code: Mermaid代码
            output_path: 输出图片路径
            theme: 主题 (default, dark, forest, neutral)
            background: 背景色
            width: 图片宽度

        Returns:
            是否成功
        """
        # 创建临时.mmd文件
        temp_mmd = self.output_dir / f"temp_{hashlib.md5(mermaid_code.encode()).hexdigest()}.mmd"

        try:
            # 写入临时文件（确保UTF-8编码）
            with open(temp_mmd, 'w', encoding='utf-8') as f:
                f.write(mermaid_code)

            # 构建mmdc命令
            cmd = [
                'mmdc',
                '-i', str(temp_mmd),
                '-o', output_path,
                '-t', theme,
                '-b', background,
                '-w', str(width),
                '--scale', '2'  # 2倍缩放，更清晰
            ]

            # 执行命令
            result = subprocess.run(
                cmd,
                capture_output=True,
                text=True,
                encoding='utf-8',
                timeout=60  # 60秒超时
            )

            if result.returncode == 0:
                return True
            else:
                print(f"Mermaid渲染错误: {result.stderr}")
                return False

        except subprocess.TimeoutExpired:
            print("Mermaid渲染超时")
            return False
        except FileNotFoundError:
            print("未找到mmdc命令，请先安装: npm install -g @mermaid-js/mermaid-cli")
            return False
        except Exception as e:
            print(f"渲染异常: {e}")
            return False
        finally:
            # 清理临时文件
            if temp_mmd.exists():
                temp_mmd.unlink()

    def process_content(self, content: str) -> Tuple[str, List[str]]:
        """
        处理MD内容，提取图表并替换为图片引用

        Args:
            content: 原始MD内容

        Returns:
            (处理后的MD内容, 生成的图片列表)
        """
        blocks = self.extract_mermaid_blocks(content)

        if not blocks:
            return content, []

        generated_images = []
        result_content = content

        # 从后往前替换，避免位置偏移
        for start, end, code in reversed(blocks):
            self.diagram_index += 1
            image_filename = f"diagram_{self.diagram_index:03d}.png"
            image_path = self.output_dir / image_filename

            print(f"正在渲染图表 {self.diagram_index}...")

            if self.render_to_image(code, str(image_path)):
                generated_images.append(str(image_path))
                # 生成图片引用（使用相对路径）
                image_ref = f"\n![图表{self.diagram_index}]({image_filename})\n"
                result_content = result_content[:start] + image_ref + result_content[end:]
                print(f"  ✓ 图表 {self.diagram_index} 已保存: {image_path}")
            else:
                # 渲染失败，保留原始代码块但标记
                error_msg = f"\n> ⚠️ 图表渲染失败，原始代码如下：\n```mermaid\n{code}```\n"
                result_content = result_content[:start] + error_msg + result_content[end:]
                print(f"  ✗ 图表 {self.diagram_index} 渲染失败")

        return result_content, generated_images


class MarkdownToWordConverter:
    """Markdown转Word转换器"""

    def __init__(self, template_path: Optional[str] = None):
        self.template_path = template_path

    def check_pandoc(self) -> bool:
        """检查pandoc是否安装"""
        try:
            result = subprocess.run(
                ['pandoc', '--version'],
                capture_output=True,
                text=True
            )
            return result.returncode == 0
        except FileNotFoundError:
            return False

    def convert(self, input_md: str, output_docx: str,
                resource_path: Optional[str] = None) -> bool:
        """
        将MD文件转换为Word文档

        Args:
            input_md: 输入MD文件路径
            output_docx: 输出Word文件路径
            resource_path: 资源文件路径（图片目录）

        Returns:
            是否成功
        """
        if not self.check_pandoc():
            print("错误: 未找到pandoc，请先安装")
            print("下载地址: https://pandoc.org/installing.html")
            return False

        # 构建pandoc命令
        cmd = [
            'pandoc',
            input_md,
            '-o', output_docx,
            '--from', 'markdown+pipe_tables+fenced_tables',
            '--to', 'docx',
            '--standalone',
            '--metadata', 'lang=zh-CN',
            '--wrap=none',  # 不自动换行，避免格式问题
        ]

        # 添加模板
        if self.template_path and os.path.exists(self.template_path):
            cmd.extend(['--reference-doc', self.template_path])

        # 添加资源路径
        if resource_path:
            cmd.extend(['--resource-path', resource_path])

        try:
            print(f"正在转换: {input_md} -> {output_docx}")
            result = subprocess.run(
                cmd,
                capture_output=True,
                text=True,
                encoding='utf-8',
                timeout=120
            )

            if result.returncode == 0:
                print(f"✓ 转换成功: {output_docx}")
                return True
            else:
                print(f"Pandoc错误: {result.stderr}")
                return False

        except subprocess.TimeoutExpired:
            print("转换超时")
            return False
        except Exception as e:
            print(f"转换异常: {e}")
            return False


def convert_md_to_word(input_file: str,
                       output_file: str,
                       template: Optional[str] = None,
                       output_dir: Optional[str] = None,
                       keep_temp: bool = False) -> bool:
    """
    主转换函数

    Args:
        input_file: 输入MD文件
        output_file: 输出Word文件
        template: Word模板文件
        output_dir: 中间文件输出目录
        keep_temp: 是否保留临时文件

    Returns:
        是否成功
    """
    input_path = Path(input_file)

    if not input_path.exists():
        print(f"错误: 文件不存在 - {input_file}")
        return False

    # 设置输出目录
    if output_dir is None:
        output_dir = input_path.parent / 'diagrams'

    output_dir = Path(output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)

    # 读取MD文件（确保UTF-8）
    print(f"读取文件: {input_file}")
    with open(input_path, 'r', encoding='utf-8') as f:
        content = f.read()

    # 步骤1: 提取并渲染图表
    print("\n[步骤1] 提取并渲染图表...")
    extractor = MermaidExtractor(output_dir=str(output_dir))
    processed_content, images = extractor.process_content(content)

    if images:
        print(f"共生成 {len(images)} 张图片")

    # 步骤2: 生成中间MD文件
    temp_md = output_dir / f"{input_path.stem}_processed.md"
    print(f"\n[步骤2] 生成中间文件: {temp_md}")

    with open(temp_md, 'w', encoding='utf-8') as f:
        f.write(processed_content)

    # 步骤3: 转换为Word
    print("\n[步骤3] 转换为Word...")
    converter = MarkdownToWordConverter(template_path=template)
    success = converter.convert(
        input_md=str(temp_md),
        output_docx=output_file,
        resource_path=str(output_dir)
    )

    # 清理临时文件
    if not keep_temp and success:
        temp_md.unlink()
        print("已清理临时文件")

    return success


def main():
    """命令行入口"""
    parser = argparse.ArgumentParser(
        description='Markdown转Word工具 - 支持Mermaid图表、表格、无乱码',
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
示例:
  %(prog)s input.md output.docx
  %(prog)s input.md output.docx --template custom.docx
  %(prog)s input.md output.docx --output-dir ./images --keep-temp
        """
    )

    parser.add_argument('input', help='输入的Markdown文件')
    parser.add_argument('output', help='输出的Word文件')
    parser.add_argument('--template', '-t', help='Word模板文件路径')
    parser.add_argument('--output-dir', '-o', help='中间文件输出目录（图片等）')
    parser.add_argument('--keep-temp', '-k', action='store_true',
                       help='保留临时文件（用于调试）')
    parser.add_argument('--version', '-v', action='version', version='%(prog)s 1.0.0')

    args = parser.parse_args()

    success = convert_md_to_word(
        input_file=args.input,
        output_file=args.output,
        template=args.template,
        output_dir=args.output_dir,
        keep_temp=args.keep_temp
    )

    sys.exit(0 if success else 1)


if __name__ == '__main__':
    main()