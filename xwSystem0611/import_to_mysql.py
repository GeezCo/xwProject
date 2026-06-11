#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
数据导入MySQL脚本
功能：
1. 读取6个分类的jsonl文件
2. 在text_type表中创建一级分类（如"侦察报"）和二级分类"仿真报"
3. 将数据导入origin_text表
"""

import json
import pymysql
from datetime import datetime

# 数据库配置
DB_CONFIG = {
    'host': '36.103.234.242',
    'port': 8010,
    'user': 'root',
    'password': 'jixianyuan1314',
    'database': 'uygur_project',
    'charset': 'utf8mb4'
}

# 6类报文及其对应的ID（手动创建后固定）
CATEGORY_MAPPING = {
    '侦察报': {'level1_id': None, 'level2_id': None},
    '综述报': {'level1_id': None, 'level2_id': None},
    '监控报': {'level1_id': None, 'level2_id': None},
    '态势报': {'level1_id': None, 'level2_id': None},
    '情况通报': {'level1_id': None, 'level2_id': None},
    '综合情况说明': {'level1_id': None, 'level2_id': None}
}

def ensure_categories(cursor, conn):
    """
    确保所有分类存在，并返回ID映射
    """
    print("初始化分类...")

    for category_name in CATEGORY_MAPPING.keys():
        # 1. 创建或获取一级分类
        cursor.execute(
            "INSERT INTO text_type (type_name, parent_id) VALUES (%s, NULL)",
            (category_name,)
        )
        level1_id = cursor.lastrowid

        # 2. 创建二级分类"仿真报"
        cursor.execute(
            "INSERT INTO text_type (type_name, parent_id) VALUES (%s, %s)",
            ("仿真报", level1_id)
        )
        level2_id = cursor.lastrowid

        CATEGORY_MAPPING[category_name]['level1_id'] = level1_id
        CATEGORY_MAPPING[category_name]['level2_id'] = level2_id

        print(f"  {category_name}: 一级ID={level1_id}, 二级ID={level2_id}")

    conn.commit()

def import_category_data(cursor, conn, category_name, file_path):
    """
    导入单个分类的数据
    """
    print(f"\n处理 {category_name}...")

    level2_id = CATEGORY_MAPPING[category_name]['level2_id']
    print(f"  使用二级分类ID: {level2_id}")

    # 3. 读取并导入数据
    count = 0
    with open(file_path, 'r', encoding='utf-8') as f:
        for line in f:
            if line.strip():
                data = json.loads(line)

                # 插入origin_text表
                cursor.execute("""
                    INSERT INTO origin_text (title, content, times, type, modal_type, is_extracted)
                    VALUES (%s, %s, %s, %s, %s, 0)
                """, (
                    data['标题'],
                    data['内容'],
                    data['时间'],
                    level2_id,  # 使用二级分类ID
                    '文字报'     # 默认模态类型
                ))
                count += 1

                if count % 100 == 0:
                    print(f"    已导入 {count} 条...")

    print(f"  {category_name} 导入完成，共 {count} 条数据")
    return count

def main():
    print("开始导入数据到MySQL...")

    conn = None
    try:
        # 连接数据库
        print("\n连接数据库...")
        conn = pymysql.connect(**DB_CONFIG)
        cursor = conn.cursor()
        print("数据库连接成功")

        # 初始化分类
        ensure_categories(cursor, conn)

        # 导入每个分类
        total_count = 0
        for category in CATEGORY_MAPPING.keys():
            file_path = f'd:\\项目开发\\献微系统\\待导入数据\\{category}.jsonl'
            count = import_category_data(cursor, conn, category, file_path)
            total_count += count
            conn.commit()  # 每个分类提交一次

        print(f"\n全部导入完成！总计 {total_count} 条数据")

        # 查询统计信息
        cursor.execute("SELECT COUNT(*) FROM origin_text")
        total = cursor.fetchone()[0]
        print(f"数据库中现有报文总数: {total}")

    except Exception as e:
        print(f"\n错误: {e}")
        if conn:
            conn.rollback()
    finally:
        if conn:
            conn.close()
            print("\n数据库连接已关闭")

if __name__ == '__main__':
    main()
