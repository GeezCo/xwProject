#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

echo "=== 重启献微系统 ==="
echo ""
bash "$SCRIPT_DIR/stop.sh"
echo ""
bash "$SCRIPT_DIR/start.sh"
