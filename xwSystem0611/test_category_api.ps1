# 献微系统 - 分类管理功能测试脚本 (PowerShell)

$BASE_URL = "http://localhost:8081"

Write-Host "==========================================" -ForegroundColor Green
Write-Host "献微系统 - 分类管理功能测试" -ForegroundColor Green
Write-Host "==========================================" -ForegroundColor Green
Write-Host ""

# 1. 测试获取分类树
Write-Host "1. 测试获取分类树" -ForegroundColor Yellow
try {
    $response = Invoke-RestMethod -Uri "$BASE_URL/api/category/tree" -Method Get
    $response | ConvertTo-Json -Depth 10
} catch {
    Write-Host "Error: $_" -ForegroundColor Red
}
Write-Host ""

# 2. 测试获取叶子节点
Write-Host "2. 测试获取叶子节点" -ForegroundColor Yellow
try {
    $response = Invoke-RestMethod -Uri "$BASE_URL/api/category/leafs" -Method Get
    $response | ConvertTo-Json -Depth 5
} catch {
    Write-Host "Error: $_" -ForegroundColor Red
}
Write-Host ""

# 3. 测试新增分类
Write-Host "3. 测试新增分类（在根分类下新增'测试单位'）" -ForegroundColor Yellow
try {
    $body = @{
        name = "测试单位"
        parentId = 1
        description = "自动化测试创建"
    } | ConvertTo-Json

    $response = Invoke-RestMethod -Uri "$BASE_URL/api/category/create" `
        -Method Post `
        -ContentType "application/json" `
        -Body $body
    $response | ConvertTo-Json
} catch {
    Write-Host "Error: $_" -ForegroundColor Red
}
Write-Host ""

# 4. 测试导入说明
Write-Host "4. 测试导入 test.jsonl" -ForegroundColor Yellow
Write-Host "   请使用 Postman 或前端页面手动导入 test.jsonl 文件" -ForegroundColor Cyan
Write-Host "   接口: POST $BASE_URL/uygur/importFromJsonl" -ForegroundColor Cyan
Write-Host "   参数: file=test.jsonl, parentCategoryName=根分类, categoryName=未分类" -ForegroundColor Cyan
Write-Host ""

# 5. 查询统计
Write-Host "5. 查询数据库统计" -ForegroundColor Yellow
Write-Host "   执行 SQL 查看分类节点数和报文分布..." -ForegroundColor Cyan
Write-Host ""

Write-Host "==========================================" -ForegroundColor Green
Write-Host "测试完成" -ForegroundColor Green
Write-Host "==========================================" -ForegroundColor Green
Write-Host ""
Write-Host "提示：" -ForegroundColor Cyan
Write-Host "  1. 确保后端服务已启动（端口 8081）" -ForegroundColor White
Write-Host "  2. 使用前端或 Postman 导入 test.jsonl" -ForegroundColor White
Write-Host "  3. 导入后应自动创建 4 个 sendUnitName 分类节点" -ForegroundColor White
Write-Host "  4. 访问 http://localhost:8080/Home/categoryManagement 查看分类管理页面" -ForegroundColor White
