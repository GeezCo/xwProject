import requests
import json

# 将所有 type=5 的报文更新为 type=71（开源获情）
url = "http://localhost:8081/uygur/text/updateByOldType"
data = {
    "oldTypeId": 5,
    "newTypeId": 71
}

response = requests.post(url, json=data, headers={"Content-Type": "application/json"})
print(f"状态码: {response.status_code}")
print(f"响应: {response.json()}")
