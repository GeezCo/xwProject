#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
XIANWEI_HOME="$(dirname "$SCRIPT_DIR")"

source "${XIANWEI_HOME}/.env"

echo "=== 停止献微系统 ==="

echo "1. 停止后端服务..."
screen -S xianwei-backend -X quit 2>/dev/null && echo "   已停止" || echo "   后端未运行"

echo "2. 停止Docker容器..."
cd "${XIANWEI_HOME}"
docker-compose down

echo ""
echo "=== 系统已停止 ==="
