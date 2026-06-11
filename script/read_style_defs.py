# -*- coding: utf-8 -*-
"""
读取Word文档样式库定义工具
从文档的样式库中获取样式定义信息
"""

from docx import Document
from docx.shared import Pt
import sys

def read_style_definitions(doc_path):
    """
    读取文档样式库定义

    Args:
        doc_path: 文档路径
    """
    print("="*60)
    print("Word文档样式库分析工具")
    print("="*60)

    print(f"\n正在读取文档: {doc_path}")

    try:
        doc = Document(doc_path)

        # -----------------------------------------------------------
        # 1. 获取文档中所有样式定义
        # -----------------------------------------------------------
        print("\n[样式库定义]")
        print("-"*60)

        # 获取所有样式
        styles = doc.styles

        print(f"\n样式总数: {len(styles)}")

        # 分析关键样式
        key_styles = ['Normal', 'Heading 1', 'Heading 2', 'Heading 3', 'Heading 4',
                      'Body Text', 'TOC 1', 'TOC 2', 'TOC 3',
                      'Table Normal', '封面_文档封格式版本', '封面_文件名',
                      '封面_项目文档统一标识', '目录_正文']

        for style_name in key_styles:
            try:
                style = styles[style_name]
                print(f"\n样式: {style_name}")

                # 字体信息
                font = style.font
                print(f"  字体名称: {font.name}")
                print(f"  字号: {font.size.pt if font.size else '继承'}磅")
                print(f"  加粗: {font.bold}")

                # 段落格式
                para_format = style.paragraph_format
                if para_format.line_spacing:
                    print(f"  行距: {para_format.line_spacing.pt}磅")
                if para_format.first_line_indent:
                    indent_chars = para_format.first_line_indent.pt / 12 if para_format.first_line_indent.pt else 0
                    print(f"  首行缩进: {indent_chars:.1f}字符")

            except KeyError:
                # 样式不存在，跳过
                pass
            except Exception as e:
                print(f"  [读取失败: {e}]")

        # -----------------------------------------------------------
        # 2. 遍历所有样式，找出可能相关的样式
        # -----------------------------------------------------------
        print("\n" + "="*60)
        print("[所有样式列表]")
        print("-"*60)

        for style in styles:
            style_type = style.type
            style_name = style.name

            # 只显示段落样式和字符样式
            if style_type == 1 or style_type == 2:  # PARAGRAPH或CHARACTER
                print(f"\n样式: {style_name} (类型: {style_type})")

                try:
                    font = style.font
                    if font.name:
                        print(f"  字体: {font.name}")
                    if font.size:
                        print(f"  字号: {font.size.pt}磅")
                    if font.bold:
                        print(f"  加粗: {font.bold}")

                    # 尝试获取东亚字体设置
                    try:
                        if style.element:
                            rPr = style.element.find('.//{http://schemas.openxmlformats.org/wordprocessingml/2006/main}rPr')
                            if rPr:
                                rFonts = rPr.find('.//{http://schemas.openxmlformats.org/wordprocessingml/2006/main}rFonts')
                                if rFonts:
                                    east_asia = rFonts.get('{http://schemas.openxmlformats.org/wordprocessingml/2006/main}eastAsia')
                                    if east_asia:
                                        print(f"  中文字体: {east_asia}")
                    except:
                        pass

                    para_format = style.paragraph_format
                    if para_format.line_spacing:
                        print(f"  行距: {para_format.line_spacing.pt}磅")
                    if para_format.first_line_indent:
                        indent_chars = para_format.first_line_indent.pt / 12 if para_format.first_line_indent.pt else 0
                        print(f"  首行缩进: {indent_chars:.1f}字符")

                except Exception as e:
                    print(f"  [读取失败: {e}]")

        # -----------------------------------------------------------
        # 3. 分析正文段落实际样式（从内容推断）
        # -----------------------------------------------------------
        print("\n" + "="*60)
        print("[正文内容样式推断]")
        print("-"*60)

        # 找到正文部分（跳过封面、目录等）
        content_start = False
        content_samples = []

        for i, para in enumerate(doc.paragraphs):
            text = para.text.strip()

            # 检测正文开始（通常是"1 范围"等）
            if text.startswith('1 ') or text.startswith('1\t'):
                content_start = True

            if content_start and text:
                content_samples.append((i, text, para))
                if len(content_samples) >= 20:
                    break

        print(f"\n正文样本数量: {len(content_samples)}")

        for i, text, para in content_samples[:10]:
            print(f"\n段落{i}: '{text[:30]}...'")

            # 获取实际字体信息
            if para.runs:
                run = para.runs[0]
                font_name = run.font.name
                font_size = run.font.size.pt if run.font.size else '未知'
                bold = run.font.bold

                print(f"  实际字体: {font_name}")
                print(f"  实际字号: {font_size}磅")
                print(f"  实际加粗: {bold}")

                # 获取东亚字体
                try:
                    rPr = run._element.rPr
                    if rPr is not None:
                        rFonts = rPr.find('.//{http://schemas.openxmlformats.org/wordprocessingml/2006/main}rFonts')
                        if rFonts is not None:
                            east_asia = rFonts.get('{http://schemas.openxmlformats.org/wordprocessingml/2006/main}eastAsia')
                            if east_asia:
                                print(f"  实际中文字体: {east_asia}")
                except:
                    pass

            # 获取段落格式
            pf = para.paragraph_format
            if pf.line_spacing:
                print(f"  实际行距: {pf.line_spacing.pt}磅")
            if pf.first_line_indent:
                indent_chars = pf.first_line_indent.pt / 12 if pf.first_line_indent.pt else 0
                print(f"  实际首行缩进: {indent_chars:.1f}字符")

        print("\n" + "="*60)
        print("分析完成！")
        print("="*60)

        return True

    except Exception as e:
        print(f"\n错误: {e}")
        import traceback
        traceback.print_exc()
        return False

if __name__ == '__main__':
    if len(sys.argv) < 2:
        print("使用方法: python read_style_defs.py <文档路径>")
        sys.exit(1)

    read_style_definitions(sys.argv[1])