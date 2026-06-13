#!/bin/bash

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
XIANWEI_HOME="$(dirname "$SCRIPT_DIR")"

echo "=== 献微系统日志查看 ==="
echo ""
echo "选择要查看的日志:"
echo "1) 前端 Nginx"
echo "2) 算法服务"
echo "3) MySQL"
echo "4) MinIO"
echo "5) 后端服务 (screen)"
echo "6) 全部容器"
echo ""
read -p "请输入选项 (1-6): " choice

cd "${XIANWEI_HOME}"

case $choice in
    1) docker-compose logs -f frontend ;;
    2) docker-compose logs -f algorithm ;;
    3) docker-compose logs -f mysql ;;
    4) docker-compose logs -f minio ;;
    5) screen -r xianwei-backend ;;
    6) docker-compose logs -f ;;
    *) echo "无效选项"; exit 1 ;;
esac
