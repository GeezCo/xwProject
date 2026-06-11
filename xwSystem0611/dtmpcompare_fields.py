import json

# Q1_data.json 的字段
json_fields = ['id', 'title', 'briefType', 'sendUnit', 'fileName', 'content', 
               'createTime', 'updateTime', 'keywords', 'entities', 'wordCount', 
               'source', 'classification', 'status']

# origin_text 表的字段
db_fields = ['sid', 'title', 'content', 'times', 'type', 'modal_type', 
             'images', 'is_extracted', 'create_time', 'update_time']

print("=== 字段对比 ===\n")
print("JSON 字段:")
for f in json_fields:
    print(f"  - {f}")

print("\n数据库字段:")
for f in db_fields:
    print(f"  - {f}")

print("\n=== 字段映射关系 ===")
mapping = {
    'title': 'title (直接映射)',
    'content': 'content (直接映射)',
    'createTime': 'times (时间字段)',
    'briefType': 'type (需要映射到分类ID)',
    'fileName': '判断 modal_type (PDF/TXT)',
    'keywords': '可选：存入扩展字段',
    'entities': '可选：存入扩展字段',
    'id': '忽略（数据库自动生成sid）'
}

for json_f, desc in mapping.items():
    print(f"  {json_f:15} → {desc}")

print("\n=== 能否导入？===")
print("✓ 可以导入！")
print("  - 核心字段完整（title、content、times）")
print("  - briefType 可映射为分类")
print("  - 需要预处理：briefType → type_id")
print("  - modal_type 从 fileName 后缀判断")
