# PowerShell 测试导入 test.jsonl
$url = "http://localhost:8081/uygur/importFromJsonl"
$filePath = "d:\项目开发\献微系统\test.jsonl"

$boundary = [System.Guid]::NewGuid().ToString()
$bodyLines = @(
    "--$boundary",
    "Content-Disposition: form-data; name=`"file`"; filename=`"test.jsonl`"",
    "Content-Type: application/octet-stream",
    "",
    [System.IO.File]::ReadAllText($filePath, [System.Text.Encoding]::UTF8),
    "--$boundary",
    "Content-Disposition: form-data; name=`"parentCategoryName`"",
    "",
    "根分类",
    "--$boundary",
    "Content-Disposition: form-data; name=`"categoryName`"",
    "",
    "未分类",
    "--$boundary--"
)

$body = $bodyLines -join "`r`n"

Write-Host "开始导入 test.jsonl..."

try {
    $response = Invoke-RestMethod -Uri $url -Method Post `
        -ContentType "multipart/form-data; boundary=$boundary" `
        -Body ([System.Text.Encoding]::UTF8.GetBytes($body))

    Write-Host "导入成功!" -ForegroundColor Green
    $response | ConvertTo-Json
} catch {
    Write-Host "导入失败: $_" -ForegroundColor Red
}
