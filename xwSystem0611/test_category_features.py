#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""验证分类管理的三大核心功能（结果保存到文件）"""

import requests
import json
import io
import sys

# 强制 stdout 使用 UTF-8（Windows）
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

BASE_URL = "http://localhost:8081/api/category"
results = []

def log(msg):
    print(msg)
    results.append(msg)

def call_api(method, url, json_data=None, success_msg="操作成功"):
    try:
        if method == "GET":
            response = requests.get(url)
        elif method == "POST":
            response = requests.post(url, json=json_data)
        elif method == "PUT":
            response = requests.put(url, json=json_data)
        elif method == "DELETE":
            response = requests.delete(url)

        data = response.json()
        log(f"  HTTP {response.status_code} | code={data.get('code')} | {data.get('message', data.get('msg', ''))}")
        return data
    except Exception as e:
        log(f"  错误: {e}")
        return None

# ==================== 测试1：5层层级限制 ====================
log("\n========== 测试1：5层层级限制 ==========")

created_ids = {}

# 5层节点
test_nodes = [
    ("测试根", 1, "level1"),
    ("测试国家", None, "level2"),
    ("测试部门", None, "level3"),
    ("测试科室", None, "level4"),
    ("测试小组", None, "level5"),
]

for i, (name, parent_id, key) in enumerate(test_nodes, 1):
    if parent_id is None:
        prev_key = test_nodes[i-2][2]
        parent_id = created_ids.get(prev_key)

    log(f"\n[{i}/5] 创建第{i}层节点：{name}")
    result = call_api("POST", f"{BASE_URL}/create", {
        "name": name,
        "parentId": parent_id,
        "description": f"第{i}层测试节点"
    })

    if result and result.get('code') == 200 and result.get('data'):
        created_ids[key] = result['data']['id']
        log(f"  -> ID={result['data']['id']}, level={result['data']['level']}, fullPath={result['data']['fullPath']}")

# 尝试创建第6层（应该失败）
log(f"\n[6/5] 尝试创建第6层（预期失败）")
result = call_api("POST", f"{BASE_URL}/create", {
    "name": "测试个人",
    "parentId": created_ids.get('level5')
})
if result and result.get('code') != 200:
    log(f"  [PASS] 正确拒绝：{result.get('message', result.get('msg'))}")
else:
    log(f"  [FAIL] 应该拒绝但成功了！")

# ==================== 测试2：节点重命名同步更新 ====================
log("\n========== 测试2：节点重命名同步更新 ==========")

if 'level2' in created_ids and 'level5' in created_ids:
    # 查询子孙节点（重命名前）
    log("\n[步骤1] 查询第5层节点（重命名前）")
    result = call_api("GET", f"{BASE_URL}/detail/{created_ids['level5']}")
    if result and result.get('data'):
        path_before = result['data']['fullPath']
        log(f"  -> 重命名前路径: {path_before}")

    # 重命名第2层
    log("\n[步骤2] 重命名第2层：测试国家 -> TEST_COUNTRY")
    result = call_api("PUT", f"{BASE_URL}/update", {
        "categoryId": created_ids['level2'],
        "newName": "TEST_COUNTRY"
    })
    if result and result.get('data'):
        log(f"  -> 第2层新路径: {result['data']['fullPath']}")

    # 再次查询第5层
    log("\n[步骤3] 再次查询第5层节点（重命名后）")
    result = call_api("GET", f"{BASE_URL}/detail/{created_ids['level5']}")
    if result and result.get('data'):
        path_after = result['data']['fullPath']
        log(f"  -> 重命名后路径: {path_after}")

        if "TEST_COUNTRY" in path_after and "测试国家" not in path_after:
            log("  [PASS] 子孙节点路径已同步更新")
        else:
            log(f"  [FAIL] 路径未同步！before={path_before}, after={path_after}")

# ==================== 测试3：级联删除 ====================
log("\n========== 测试3：级联删除 ==========")

if 'level1' in created_ids:
    # 查询删除前的所有节点ID
    log("\n[步骤1] 查询所有创建的测试节点（删除前）")
    for key, node_id in created_ids.items():
        result = call_api("GET", f"{BASE_URL}/detail/{node_id}")
        if result and result.get('data'):
            log(f"  {key}: ID={node_id}, name={result['data']['name']}")

    # 删除第1层（应级联删除所有子孙）
    log(f"\n[步骤2] 删除第1层节点 ID={created_ids['level1']}")
    result = call_api("DELETE", f"{BASE_URL}/delete/{created_ids['level1']}")
    if result and result.get('code') == 200:
        log("  -> 删除请求成功")

    # 验证所有子孙节点都已删除
    log("\n[步骤3] 验证所有子孙节点是否已删除")
    deleted_count = 0
    for key, node_id in created_ids.items():
        result = call_api("GET", f"{BASE_URL}/detail/{node_id}")
        if result and (result.get('code') == 404 or result.get('data') is None):
            log(f"  {key}: 已删除")
            deleted_count += 1
        else:
            log(f"  {key}: 仍然存在！")

    if deleted_count == len(created_ids):
        log(f"  [PASS] 所有 {deleted_count} 个节点已级联删除")
    else:
        log(f"  [FAIL] 只删除了 {deleted_count}/{len(created_ids)} 个节点")

# ==================== 总结 ====================
log("\n========== 测试总结 ==========")
log("已完成3项核心功能验证，详细结果见上方日志")

# 保存结果到文件
with open(r'd:\项目开发\献微系统\test_features_result.txt', 'w', encoding='utf-8') as f:
    f.write('\n'.join(results))

print("\n测试结果已保存到: test_features_result.txt")
