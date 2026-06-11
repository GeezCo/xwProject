#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
数据拆分脚本
功能：
1. 读取 data_20260101_20260419.jsonl
2. 按序号取模拆分为6类（每120条循环：1-20第一类，21-40第二类...）
3. 转换时间格式为 MySQL 可识别格式
4. 输出到对应的 jsonl 文件
"""

import json
from datetime import datetime

# 6类报文类型
CATEGORIES = ['侦察报', '综述报', '监控报', '态势报', '情况通报', '综合情况说明']

def convert_time_format(time_str):
    """
    将中文时间格式转换为 MySQL 格式
    输入: "2026年01月01日"
    输出: "2026-01-01"
    """
    try:
        dt = datetime.strptime(time_str, "%Y年%m月%d日")
        return dt.strftime("%Y-%m-%d")
    except:
        return time_str

def get_category_by_index(index):
    """
    根据序号（从1开始）确定类别
    1-20 -> 侦察报
    21-40 -> 综述报
    41-60 -> 监控报
    61-80 -> 态势报
    81-100 -> 情况通报
    101-120 -> 综合情况说明
    """
    mod = ((index - 1) % 120) + 1  # 转换为1-120循环

    if 1 <= mod <= 20:
        return '侦察报'
    elif 21 <= mod <= 40:
        return '综述报'
    elif 41 <= mod <= 60:
        return '监控报'
    elif 61 <= mod <= 80:
        return '态势报'
    elif 81 <= mod <= 100:
        return '情况通报'
    else:  # 101-120
        return '综合情况说明'

def main():
    input_file = 'd:\\项目开发\\献微系统\\data_20260101_20260419.jsonl'

    # 按类别存储数据
    categorized_data = {cat: [] for cat in CATEGORIES}

    # 读取并分类
    print("开始读取数据...")
    with open(input_file, 'r', encoding='utf-8') as f:
        for line_num, line in enumerate(f, 1):
            if line.strip():
                try:
                    data = json.loads(line)

                    # 转换时间格式
                    original_time = data.get('时间', '')
                    mysql_time = convert_time_format(original_time)

                    # 根据序号确定类别
                    category = get_category_by_index(line_num)

                    # 构造新数据
                    new_data = {
                        '时间': mysql_time,
                        '原始时间': original_time,
                        '标题': data.get('标题', ''),
                        '内容': data.get('内容', ''),
                        '类别': category
                    }

                    categorized_data[category].append(new_data)

                    if line_num % 500 == 0:
                        print(f"已处理 {line_num} 条数据...")

                except json.JSONDecodeError as e:
                    print(f"第 {line_num} 行 JSON 解析错误: {e}")

    # 输出统计信息
    print("\n分类统计:")
    total = 0
    for category in CATEGORIES:
        count = len(categorized_data[category])
        total += count
        print(f"  {category}: {count} 条")
    print(f"  总计: {total} 条")

    # 写入分类文件
    print("\n开始写入分类文件...")
    for category in CATEGORIES:
        items = categorized_data[category]
        output_file = f'd:\\项目开发\\献微系统\\{category}.jsonl'
        with open(output_file, 'w', encoding='utf-8') as f:
            for item in items:
                f.write(json.dumps(item, ensure_ascii=False) + '\n')
        print(f"  {category}.jsonl 已生成 ({len(items)} 条)")

    print("\n处理完成！")

if __name__ == '__main__':
    main()
