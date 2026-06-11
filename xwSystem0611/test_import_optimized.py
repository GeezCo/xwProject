#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""测试优化后的 JSONL 导入接口"""

import requests
import json

url = "http://localhost:8081/uygur/importFromJsonl"

# 测试1：不传任何参数（使用默认分类 ID=2）
print("=" * 50)
print("测试1：不传任何参数（使用默认分类 ID=2）")
print("=" * 50)

with open(r'd:\项目开发\献微系统\test.jsonl', 'rb') as f:
    files = {'file': ('test.jsonl', f, 'application/octet-stream')}
    response = requests.post(url, files=files, timeout=120)

result1 = response.json()
print(f"状态码: {response.status_code}")
print(f"响应: {json.dumps(result1, ensure_ascii=False, indent=2)}\n")

# 测试2：指定自定义默认分类 ID=2
print("=" * 50)
print("测试2：显式传 defaultCategoryId=2")
print("=" * 50)

with open(r'd:\项目开发\献微系统\test.jsonl', 'rb') as f:
    files = {'file': ('test.jsonl', f, 'application/octet-stream')}
    data = {'defaultCategoryId': 2}
    response = requests.post(url, files=files, data=data, timeout=120)

result2 = response.json()
print(f"状态码: {response.status_code}")
print(f"响应: {json.dumps(result2, ensure_ascii=False, indent=2)}\n")

# 保存结果
with open(r'd:\项目开发\献微系统\import_result_optimized.json', 'w', encoding='utf-8') as f:
    json.dump({
        'test1_no_params': result1,
        'test2_with_default': result2
    }, f, ensure_ascii=False, indent=2)

print("✅ 测试完成！结果已保存到 import_result_optimized.json")
