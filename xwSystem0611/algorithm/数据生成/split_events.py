"""
事件拆分脚本

功能说明：
将包含多个事件的txt文件拆分为单独的事件文件。

使用方法：
    python split_events.py

输入目录：输出/1
输出目录：输出/单一事件
"""

import os
import re
from pathlib import Path


def split_events(text: str) -> list:
    """
    将文本按空行分割成多个事件段落

    Args:
        text: 原始文本

    Returns:
        list: 事件段落列表
    """
    # 使用正则表达式按连续空行分割
    # \n\s*\n 匹配：换行 + 任意空白 + 换行
    events = re.split(r'\n\s*\n', text.strip())

    # 过滤空事件，并去除首尾空白
    events = [e.strip() for e in events if e.strip()]

    return events


def process_file(input_path: str, output_dir: str) -> int:
    """
    处理单个文件，拆分并保存事件

    Args:
        input_path: 输入文件路径
        output_dir: 输出目录

    Returns:
        int: 拆分出的事件数量
    """
    # 尝试多种编码读取
    encodings = ['utf-8', 'gbk', 'gb2312', 'utf-16']
    content = None

    for encoding in encodings:
        try:
            with open(input_path, 'r', encoding=encoding) as f:
                content = f.read()
            break
        except UnicodeDecodeError:
            continue

    if content is None:
        print(f"  [跳过] 无法读取文件: {input_path}")
        return 0

    # 拆分事件
    events = split_events(content)

    if not events:
        print(f"  [跳过] 文件无事件内容: {input_path}")
        return 0

    # 获取原文件名（不含扩展名）
    base_name = Path(input_path).stem

    # 保存每个事件
    for i, event in enumerate(events, 1):
        # 构建输出文件名：原文件名_序号.txt
        output_name = f"{base_name}_{i:02d}.txt"
        output_path = os.path.join(output_dir, output_name)

        # 写入文件
        with open(output_path, 'w', encoding='utf-8') as f:
            f.write(event)

    return len(events)


def main():
    """主函数"""
    # 定义目录路径
    base_dir = Path(__file__).parent
    input_dir = base_dir / "输出" / "1"
    output_dir = base_dir / "输出" / "单一事件"

    # 检查输入目录
    if not input_dir.exists():
        print(f"输入目录不存在: {input_dir}")
        return

    # 创建输出目录
    output_dir.mkdir(parents=True, exist_ok=True)
    print(f"输出目录: {output_dir}")

    # 获取所有txt文件
    txt_files = list(input_dir.glob("*.txt"))

    if not txt_files:
        print("未找到txt文件")
        return

    print(f"找到 {len(txt_files)} 个文件待处理")
    print("-" * 50)

    # 统计信息
    total_files = 0
    total_events = 0

    # 处理每个文件
    for txt_file in sorted(txt_files):
        event_count = process_file(str(txt_file), str(output_dir))
        if event_count > 0:
            total_files += 1
            total_events += event_count
            print(f"  [完成] {txt_file.name} -> {event_count} 个事件")

    # 打印统计
    print("-" * 50)
    print(f"处理完成！")
    print(f"  处理文件: {total_files} 个")
    print(f"  生成事件: {total_events} 个")
    print(f"  输出目录: {output_dir}")


if __name__ == '__main__':
    main()