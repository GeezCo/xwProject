import requests

# 查询开源获情分类下的报文数量
url = "http://localhost:8081/uygur/getTextList"
data = {
    "pageNum": 1,
    "pageSize": 1,
    "typeId": 71
}

response = requests.post(url, json=data, headers={"Content-Type": "application/json"})
result = response.json()

if result['code'] == 1:
    total = result['data']['total']
    print(f"开源获情 (ID=71) 分类下的报文总数: {total}")
else:
    print(f"查询失败: {result}")

# 查询是否还有 type=5 的报文
url2 = "http://localhost:8081/uygur/getTextList"
data2 = {
    "pageNum": 1,
    "pageSize": 1,
    "typeId": 5
}

response2 = requests.post(url2, json=data2, headers={"Content-Type": "application/json"})
result2 = response2.json()

if result2['code'] == 1:
    total2 = result2['data']['total']
    print(f"type=5 的报文剩余数量: {total2}")
else:
    print(f"查询失败: {result2}")
