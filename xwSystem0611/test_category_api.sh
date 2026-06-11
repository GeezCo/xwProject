#!/bin/bash
# 献微系统 - 分类管理功能测试脚本

BASE_URL="http://localhost:8081"

echo "=========================================="
echo "献微系统 - 分类管理功能测试"
echo "=========================================="
echo ""

# 1. 测试获取分类树
echo "1. 测试获取分类树"
curl -s "${BASE_URL}/api/category/tree" | python -m json.tool
echo ""
echo ""

# 2. 测试获取叶子节点
echo "2. 测试获取叶子节点"
curl -s "${BASE_URL}/api/category/leafs" | python -m json.tool
echo ""
echo ""

# 3. 测试新增分类
echo "3. 测试新增分类（在根分类下新增'测试单位'）"
curl -s -X POST "${BASE_URL}/api/category/create" \
  -H "Content-Type: application/json" \
  -d '{"name":"测试单位","parentId":1,"description":"自动化测试创建"}' \
  | python -m json.tool
echo ""
echo ""

# 4. 测试导入 JSONL（需要手动执行）
echo "4. 测试导入 test.jsonl"
echo "   请使用 Postman 或前端页面手动导入 test.jsonl 文件"
echo "   接口: POST ${BASE_URL}/uygur/importFromJsonl"
echo "   参数: file=test.jsonl, parentCategoryName=根分类, categoryName=未分类"
echo ""
echo ""

# 5. 再次查看分类树（查看是否自动创建了 sendUnitName 节点）
echo "5. 导入后再次查看分类树"
echo "   预期：应该能看到'战略研究所'、'情报分析中心'、'研究院'、'外交部'等节点"
echo ""
sleep 2
curl -s "${BASE_URL}/api/category/tree" | python -m json.tool
echo ""
echo ""

echo "=========================================="
echo "测试完成"
echo "=========================================="
