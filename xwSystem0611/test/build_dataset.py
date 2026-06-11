"""
构建测试数据集
从数据库获取不同长度的报文，选取10条构建测试数据集
"""
import requests
import json
import sys
import io
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

BACKEND_URL = "http://localhost:8081"

# 获取前100条报文（足够筛选）
resp = requests.post(f"{BACKEND_URL}/uygur/getTextList", json={
    "pageNum": 1, "pageSize": 100
})
data = resp.json()
reports = data["data"]["list"]

# 按content长度排序
reports_with_len = [(r, len(r.get("content", ""))) for r in reports]
reports_with_len.sort(key=lambda x: x[1])

# 按长度分布选取
total = len(reports_with_len)
print(f"总报文数: {total}")
print(f"最短: {reports_with_len[0][1]} 字, 最长: {reports_with_len[-1][1]} 字")

# 选取策略：均匀采样不同长度
indices = [0, 1]  # 最短2条
indices += [total // 5, total // 5 + 1]  # 较短2条
indices += [total // 2, total // 2 + 1]  # 中等2条
indices += [total * 4 // 5, total * 4 // 5 + 1]  # 较长2条
indices += [total - 2, total - 1]  # 最长2条

# 去重并限制10条
selected = []
seen_sids = set()
for i in indices:
    if i < total:
        r, length = reports_with_len[i]
        sid = r["sid"]
        if sid not in seen_sids:
            seen_sids.add(sid)
            selected.append(r)
            if len(selected) >= 10:
                break

# 补足10条（如果有重复）
if len(selected) < 10:
    for r, length in reports_with_len:
        if r["sid"] not in seen_sids:
            seen_sids.add(r["sid"])
            selected.append(r)
            if len(selected) >= 10:
                break

# 打印选取结果
print(f"\n选取 {len(selected)} 条报文:")
print(f"{'序号':<4} {'SID':<8} {'长度':<8} {'分类':<6} {'标题'}")
print("-" * 70)
for i, r in enumerate(selected):
    content_len = len(r.get("content", ""))
    print(f"{i+1:<4} {r['sid']:<8} {content_len:<8} {r.get('type', '-'):<6} {r['title'][:40]}")

# 对每条报文获取完整详情
dataset = []
for r in selected:
    detail_resp = requests.get(f"{BACKEND_URL}/uygur/detail/{r['sid']}")
    detail = detail_resp.json()
    if detail["code"] == 1:
        dataset.append(detail["data"])
    else:
        print(f"获取详情失败: sid={r['sid']}")

# 保存数据集
output_path = r"D:\项目开发\献微系统\测试\测试数据集.json"
with open(output_path, "w", encoding="utf-8") as f:
    json.dump(dataset, f, ensure_ascii=False, indent=2)

print(f"\n数据集已保存: {output_path}")
print(f"共 {len(dataset)} 条报文")
