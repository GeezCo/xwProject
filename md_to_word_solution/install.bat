@echo off
chcp 65001 >nul
echo ╔══════════════════════════════════════════════════════════════════╗
echo ║           Markdown 转 Word 工具 - 环境安装脚本                   ║
echo ╚══════════════════════════════════════════════════════════════════╝
echo.

:: 检查管理员权限
net session >nul 2>&1
if %errorLevel% neq 0 (
    echo [警告] 建议以管理员身份运行此脚本
    echo.
)

:: 1. 检查Python
echo [1/4] 检查 Python...
python --version >nul 2>&1
if %errorLevel% neq 0 (
    echo [×] Python 未安装
    echo     请从 https://www.python.org/downloads/ 下载安装
    echo     安装时请勾选 "Add Python to PATH"
    goto :error
) else (
    for /f "tokens=2" %%i in ('python --version 2^>^&1') do set PYTHON_VER=%%i
    echo [√] Python 已安装: %PYTHON_VER%
)
echo.

:: 2. 检查Node.js
echo [2/4] 检查 Node.js...
node --version >nul 2>&1
if %errorLevel% neq 0 (
    echo [×] Node.js 未安装
    echo     请从 https://nodejs.org/ 下载安装
    echo     建议安装 LTS 版本
    goto :error
) else (
    for /f %%i in ('node --version') do set NODE_VER=%%i
    echo [√] Node.js 已安装: %NODE_VER%
)
echo.

:: 3. 检查Mermaid CLI
echo [3/4] 检查 Mermaid CLI (mmdc)...
mmdc --version >nul 2>&1
if %errorLevel% neq 0 (
    echo [!] Mermaid CLI 未安装，正在安装...
    echo     这可能需要几分钟，请耐心等待...
    echo.
    npm install -g @mermaid-js/mermaid-cli
    if %errorLevel% neq 0 (
        echo [×] Mermaid CLI 安装失败
        echo     请尝试手动安装: npm install -g @mermaid-js/mermaid-cli
        goto :error
    )
    echo [√] Mermaid CLI 安装成功
) else (
    for /f "tokens=2" %%i in ('mmdc --version 2^>^&1') do set MMDC_VER=%%i
    echo [√] Mermaid CLI 已安装: %MMDC_VER%
)
echo.

:: 4. 检查Pandoc
echo [4/4] 检查 Pandoc...
pandoc --version >nul 2>&1
if %errorLevel% neq 0 (
    echo [×] Pandoc 未安装
    echo     请从 https://pandoc.org/installing.html 下载安装
    echo     或使用 chocolatey: choco install pandoc
    goto :error
) else (
    for /f "tokens=2" %%i in ('pandoc --version 2^>^&1 ^| findstr /r "pandoc"') do set PANDOC_VER=%%i
    echo [√] Pandoc 已安装: %PANDOC_VER%
)
echo.

:: 安装成功
echo ╔══════════════════════════════════════════════════════════════════╗
echo ║                      安装完成！                                   ║
echo ╚══════════════════════════════════════════════════════════════════╝
echo.
echo 环境检查结果:
echo   - Python:      %PYTHON_VER%
echo   - Node.js:     %NODE_VER%
echo   - Mermaid CLI: 已安装
echo   - Pandoc:      %PANDOC_VER%
echo.
echo 使用方法:
echo   python md_to_word.py input.md output.docx
echo.
echo 详细帮助:
echo   python md_to_word.py --help
echo.
goto :end

:error
echo.
echo ╔══════════════════════════════════════════════════════════════════╗
echo ║                      安装失败                                     ║
echo ╚══════════════════════════════════════════════════════════════════╝
echo.
echo 请按照上述提示安装缺失的组件后重新运行此脚本。
echo.

:end
pause