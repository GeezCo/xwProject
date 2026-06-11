#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""验证响应中文不再乱码"""

import requests
import json

# 创建中文分类节点
response = requests.post(
    'http://localhost:8081/api/category/create',
    json={'name': '中文测试节点B', 'parentId': 1, 'description': '验证中文'}
)

# 保存结果
result = {
    'status': response.status_code,
    'content_type': response.headers.get('Content-Type'),
    'encoding': response.encoding,
    'response_text': response.text,
    'response_json': response.json() if response.status_code == 200 else None
}

with open(r'd:\项目开发\献微系统\charset_test_result.json', 'w', encoding='utf-8') as f:
    json.dump(result, f, ensure_ascii=False, indent=2)

print("测试完成，结果保存在 charset_test_result.json")
