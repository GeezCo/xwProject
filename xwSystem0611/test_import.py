#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""测试 JSONL 导入功能"""

import requests
import json

url = "http://localhost:8081/uygur/importFromJsonl"

# 正常发送，后端已配置 UTF-8
with open(r'd:\项目开发\献微系统\test.jsonl', 'rb') as f:
    files = {
        'file': ('test.jsonl', f, 'application/octet-stream')
    }
    data = {
        'parentCategoryName': '根分类',
        'categoryName': '未分类'
    }

    print("开始导入 test.jsonl...")
    response = requests.post(url, files=files, data=data, timeout=120)

print(f"状态码: {response.status_code}")

# 保存结果到文件
result = {
    'status_code': response.status_code,
    'response': response.json()
}

with open(r'd:\项目开发\献微系统\import_result.json', 'w', encoding='utf-8') as f:
    json.dump(result, f, ensure_ascii=False, indent=2)

print("结果已保存到 import_result.json")
