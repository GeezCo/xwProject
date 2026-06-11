"""
单独抽取sid=191的数据
"""

import json
import os
import sys
from datetime import datetime

# 添加算法模块路径
sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..', '算法', '数据抽取'))

from hierarchical_extractor import HierarchicalEventExtractor

# API密钥
API_KEY = "sk-K0_Bo4nf8yhgEk9RQsXsWg"

# 读取原始数据
with open('原始数据.json', 'r', encoding='utf-8') as f:
    data_list = json.load(f)

# 找到sid=191的数据
data_191 = None
for item in data_list:
    if item['sid'] == 191:
        data_191 = item
        break

if not data_191:
    print("未找到sid=191的数据")
    sys.exit(1)

print("=" * 60)
print(f"重新抽取 sid=191 的数据")
print(f"标题: {data_191['title']}")
print(f"内容长度: {len(data_191['content'])} 字符")
print("=" * 60)

# 创建抽取器
extractor = HierarchicalEventExtractor(API_KEY)

# 记录开始时间
start_time = datetime.now()

# 执行抽取
result = extractor.extract_all(data_191['content'])

# 记录结束时间
end_time = datetime.now()
duration = (end_time - start_time).total_seconds()

# 构建完整结果
test_result = {
    'test_id': 1,
    'test_time': start_time.strftime('%Y-%m-%d %H:%M:%S'),
    'duration_seconds': round(duration, 2),
    'input': {
        'sid': data_191['sid'],
        'title': data_191['title'],
        'content': data_191['content'],
        'content_length': len(data_191['content'])
    },
    'output': result
}

# 保存结果
output_file = '抽取结果_191_重新抽取.json'
with open(output_file, 'w', encoding='utf-8') as f:
    json.dump(test_result, f, ensure_ascii=False, indent=2)

print("\n" + "=" * 60)
print("抽取完成!")
print("=" * 60)
print(f"耗时: {duration:.2f} 秒")
print(f"段落数: {result['paragraph_count']}")
print(f"LLM调用: {result['llm_calls']} 次")
print(f"节省调用: {result['llm_calls_saved']} 次")
print(f"事件数: {result['total_events']} 个")
print(f"结果已保存: {output_file}")
print("=" * 60)