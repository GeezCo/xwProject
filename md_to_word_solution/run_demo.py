#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
演示脚本：逐步展示MD转Word的每个步骤
"""

import os
import re
import sys
from pathlib import Path


def step1_read_file():
    """步骤1: 读取原始MD文件"""
    print("\n" + "=" * 70)
    print("【步骤1】读取原始MD文件")
    print("=" * 70)

    input_file = Path("examples/example.md")

    print(f"\n执行动作:")
    print(f"  open('{input_file}', 'r', encoding='utf-8')")

    print(f"\n文件信息:")
    print(f"  ├─ 文件路径: {input_file}")
    print(f"  ├─ 文件存在: {input_file.exists()}")

    if input_file.exists():
        with open(input_file, 'r', encoding='utf-8') as f:
            content = f.read()

        print(f"  ├─ 文件大小: {len(content)} 字符")
        print(f"  ├─ 行数: {len(content.splitlines())} 行")
        print(f"  ├─ 编码: UTF-8 (确保中文正确)")
        print(f"  └─ ✅ 文件读取成功")

        return content

    print(f"  └─ ❌ 文件不存在")
    return None


def step2_extract_mermaid(content):
    """步骤2: 提取Mermaid代码块"""
    print("\n" + "=" * 70)
    print("【步骤2】提取Mermaid代码块")
    print("=" * 70)

    print(f"\n执行动作:")
    print(f"  正则表达式: r'```mermaid\\s*\\n(.*?)```'")

    pattern = r'```mermaid\s*\n(.*?)```'
    matches = re.findall(pattern, content, re.DOTALL)

    print(f"\n搜索结果:")
    print(f"  ├─ 找到 mermaid 代码块: {len(matches)} 个")
    print(f"  └─ ✅ 提取完成")

    # 显示每个代码块的摘要
    for i, match in enumerate(matches, 1):
        # 识别图表类型
        first_line = match.strip().split('\n')[0]
        diagram_type = first_line.split()[0] if first_line else 'unknown'

        lines = len(match.strip().split('\n'))
        chars = len(match)

        print(f"\n  ┌─ 图表 {i} ─────────────────────────────")
        print(f"  │ 类型: {diagram_type}")
        print(f"  │ 行数: {lines}")
        print(f"  │ 字符: {chars}")
        print(f"  │ 预览: {first_line[:40]}...")
        print(f"  └────────────────────────────────────")

    return matches


def step3_render_images(matches):
    """步骤3: 渲染图表为PNG图片"""
    print("\n" + "=" * 70)
    print("【步骤3】渲染图表为PNG图片")
    print("=" * 70)

    print(f"\n执行动作:")
    print(f"  mmdc -i temp.mmd -o diagram_xxx.png -t default -b white -w 1200")

    print(f"\n渲染参数说明:")
    print(f"  ├─ -i: 输入临时.mmd文件")
    print(f"  ├─ -o: 输出PNG图片")
    print(f"  ├─ -t: 主题 (default)")
    print(f"  ├─ -b: 背景色 (white)")
    print(f"  ├─ -w: 宽度 1200像素")
    print(f"  └─ --scale: 2倍缩放 (更清晰)")

    print(f"\n预期输出:")
    os.makedirs("diagrams", exist_ok=True)

    for i, match in enumerate(matches, 1):
        first_line = match.strip().split('\n')[0]
        diagram_type = first_line.split()[0] if first_line else 'unknown'

        # 创建临时mmd文件
        temp_file = Path(f"diagrams/temp_{i}.mmd")
        with open(temp_file, 'w', encoding='utf-8') as f:
            f.write(match)

        print(f"\n  ┌─ 图表 {i} 处理 ────────────────────")
        print(f"  │ 1. 创建临时文件: {temp_file}")
        print(f"  │ 2. 执行渲染命令:")
        print(f"  │    mmdc -i {temp_file} -o diagrams/diagram_{i:03d}.png")
        print(f"  │ 3. 类型: {diagram_type}")

        # 检查mmdc是否存在
        import subprocess
        try:
            subprocess.run(['mmdc', '--version'], capture_output=True, timeout=5)
            print(f"  │ 4. ✅ mmdc已安装，可以渲染")
        except (FileNotFoundError, subprocess.TimeoutExpired):
            print(f"  │ 4. ⚠️ mmdc未安装，需要安装后才能渲染")

        print(f"  └────────────────────────────────────")

    print(f"\n生成的临时文件:")
    temp_files = list(Path("diagrams").glob("temp_*.mmd"))
    for f in temp_files:
        print(f"  ├─ {f}")

    print(f"\n⚠️ 注意: 需要安装 Mermaid CLI 才能实际渲染图片")
    print(f"   安装命令: npm install -g @mermaid-js/mermaid-cli")

    return matches


def step4_replace_content(content, matches):
    """步骤4: 替换代码块为图片引用"""
    print("\n" + "=" * 70)
    print("【步骤4】替换代码块为图片引用")
    print("=" * 70)

    print(f"\n执行动作:")
    print(f"  将 ```mermaid...``` 替换为 ![图表](diagram_xxx.png)")

    # 备份原始内容用于演示
    original = content
    processed = content

    # 查找所有mermaid块的位置
    pattern = r'```mermaid\s*\n.*?```'
    all_matches = list(re.finditer(pattern, content, re.DOTALL))

    print(f"\n替换过程 (从后往前替换，避免位置偏移):")
    print(f"  ├─ 找到 {len(all_matches)} 个代码块")

    # 从后往前替换
    for i, match in enumerate(reversed(all_matches), 1):
        real_index = len(all_matches) - i + 1  # 实际索引（从1开始）

        # 提取前一行内容（标题）
        before_text = content[:match.start()]
        prev_line = before_text.strip().split('\n')[-1] if before_text.strip() else ""

        print(f"\n  ┌─ 替换 {real_index} ─────────────────────────")
        print(f"  │ 原始位置: 行 {content[:match.start()].count(chr(10)) + 1}")
        print(f"  │ 前一标题: {prev_line[:30]}...")

        # 显示替换内容
        original_snippet = match.group(0)[:50].replace('\n', '\\n')
        replacement = f"\n![图表{real_index}](diagram_{real_index:03d}.png)\n"

        print(f"  │ 原始内容: {original_snippet}...")
        print(f"  │ 替换为:   ![图表{real_index}](diagram_{real_index:03d}.png)")

        # 执行替换
        processed = processed[:match.start()] + replacement + processed[match.end():]

        print(f"  │ ✅ 替换完成")
        print(f"  └────────────────────────────────────")

    # 保存中间文件
    output_file = Path("diagrams/example_processed.md")
    with open(output_file, 'w', encoding='utf-8') as f:
        f.write(processed)

    print(f"\n生成中间文件:")
    print(f"  ├─ 文件: {output_file}")
    print(f"  ├─ 大小: {len(processed)} 字符")
    print(f"  └─ ✅ 中间文件已保存")

    # 显示处理前后对比
    print(f"\n处理前后对比:")
    print(f"  ├─ 原始文件: {len(original)} 字符，含 {len(all_matches)} 个代码块")
    print(f"  ├─ 处理后:   {len(processed)} 字符，含 {len(all_matches)} 个图片引用")
    print(f"  └─ 变化:     减少 {len(original) - len(processed)} 字符")

    return processed


def step5_convert_to_word(processed_content):
    """步骤5: 使用Pandoc转换为Word"""
    print("\n" + "=" * 70)
    print("【步骤5】使用Pandoc转换为Word")
    print("=" * 70)

    print(f"\n执行动作:")
    print(f"  pandoc input.md -o output.docx --metadata lang=zh-CN")

    print(f"\nPandoc命令详解:")
    cmd_parts = [
        ("pandoc", "调用Pandoc工具"),
        ("diagrams/example_processed.md", "输入文件"),
        ("-o output.docx", "输出Word文件"),
        ("--from markdown+pipe_tables", "支持管道表格"),
        ("--to docx", "转换为Word格式"),
        ("--standalone", "生成完整文档"),
        ("--metadata lang=zh-CN", "设置中文语言"),
        ("--wrap=none", "不自动换行"),
        ("--resource-path ./diagrams", "图片搜索路径")
    ]

    for part, desc in cmd_parts:
        print(f"  ├─ {part:30s} → {desc}")

    # 检查pandoc
    import subprocess
    try:
        result = subprocess.run(['pandoc', '--version'], capture_output=True, text=True, timeout=5)
        if result.returncode == 0:
            version = result.stdout.split('\n')[0]
            print(f"\n  ✅ Pandoc已安装: {version}")

            # 尝试实际转换
            print(f"\n正在执行转换...")

            input_md = "diagrams/example_processed.md"
            output_docx = "output.docx"

            # 构建完整命令
            cmd = [
                'pandoc',
                input_md,
                '-o', output_docx,
                '--from', 'markdown+pipe_tables+fenced_tables',
                '--to', 'docx',
                '--standalone',
                '--metadata', 'lang=zh-CN',
                '--wrap=none',
                '--resource-path', './diagrams'
            ]

            try:
                result = subprocess.run(cmd, capture_output=True, text=True, timeout=60)

                if result.returncode == 0:
                    print(f"  ✅ 转换成功!")
                    print(f"  ├─ 输出文件: {output_docx}")

                    if Path(output_docx).exists():
                        size = Path(output_docx).stat().st_size
                        print(f"  ├─ 文件大小: {size} 字节")
                    print(f"  └─ ✅ Word文档生成完成")
                else:
                    print(f"  ❌ 转换失败: {result.stderr}")

            except subprocess.TimeoutExpired:
                print(f"  ⚠️ 转换超时")

        else:
            print(f"  ⚠️ Pandoc检查失败")

    except FileNotFoundError:
        print(f"\n  ⚠️ Pandoc未安装")
        print(f"     请从 https://pandoc.org/installing.html 安装")
    except subprocess.TimeoutExpired:
        print(f"\n  ⚠️ Pandoc检查超时")


def main():
    """主流程"""
    print("\n" + "=" * 70)
    print("  MD转Word完整流程演示")
    print("  每一步详细执行过程")
    print("=" * 70)

    # 步骤1
    content = step1_read_file()
    if not content:
        print("\n演示终止：无法读取文件")
        return

    # 步骤2
    matches = step2_extract_mermaid(content)

    # 步骤3
    step3_render_images(matches)

    # 步骤4
    processed = step4_replace_content(content, matches)

    # 步骤5
    step5_convert_to_word(processed)

    # 总结
    print("\n" + "=" * 70)
    print("【流程总结】")
    print("=" * 70)

    print(f"\n文件流转:")
    print(f"  examples/example.md (原始)")
    print(f"       ↓")
    print(f"  diagrams/temp_*.mmd (临时)")
    print(f"       ↓")
    print(f"  diagrams/diagram_*.png (图片)")
    print(f"       ↓")
    print(f"  diagrams/example_processed.md (中间)")
    print(f"       ↓")
    print(f"  output.docx (最终)")

    print(f"\n环境检查:")
    tools = [
        ('Python', 'python --version'),
        ('Pandoc', 'pandoc --version'),
        ('Node.js', 'node --version'),
        ('Mermaid CLI', 'mmdc --version')
    ]

    import subprocess
    for name, cmd in tools:
        try:
            subprocess.run(cmd.split(), capture_output=True, timeout=5)
            print(f"  ├─ {name:15s} ✅ 已安装")
        except FileNotFoundError:
            print(f"  ├─ {name:15s} ❌ 未安装")
        except:
            print(f"  ├─ {name:15s} ⚠️ 检查失败")

    print(f"\n完成！请查看生成的文件。")
    print(f"\n如果工具未安装，请运行 install.bat 或手动安装。")


if __name__ == '__main__':
    main()