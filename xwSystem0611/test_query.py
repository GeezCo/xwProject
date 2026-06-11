#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""测试查询分类接口"""

import requests

url = "http://localhost:8081/test/queryCategory"
params = {'name': '根分类'}

print(f"发送参数: {params}")
response = requests.get(url, params=params)

print(f"状态码: {response.status_code}")
print(f"响应: {response.text}")

# 再测试一下直接用 ID=1 查询
print("\n现在我们先保存重构 plan...")
