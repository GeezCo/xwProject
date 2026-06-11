#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
XIANWEI_HOME="$(dirname "$SCRIPT_DIR")"

source "${XIANWEI_HOME}/.env"

echo "=== 启动献微系统 ==="
echo "目录: $XIANWEI_HOME"

# ── 1. 启动 Docker 容器 ──
echo ""
echo "1. 启动Docker容器..."
cd "${XIANWEI_HOME}"
docker-compose up -d

# ── 2. 等待 MySQL ──
echo "2. 等待MySQL就绪..."
sleep 5
until docker exec xianwei-mysql mysqladmin ping -h localhost -u root -p${MYSQL_ROOT_PASSWORD} --silent 2>/dev/null; do
    sleep 3
done
echo "   MySQL已就绪"

# ── 3. 等待 MinIO ──
echo "3. 等待MinIO就绪..."
until curl -sf http://localhost:${MINIO_API_PORT}/minio/health/live > /dev/null 2>&1; do
    sleep 2
done
echo "   MinIO已就绪"

# ── 4. 等待算法服务 ──
echo "4. 等待算法服务就绪..."
until curl -sf http://localhost:${ALGORITHM_PORT}/health > /dev/null 2>&1; do
    sleep 5
done
echo "   算法服务已就绪"

# ── 5. 启动后端（screen） ──
echo "5. 启动后端服务..."
JAVA_BIN=""
if command -v java &>/dev/null && java -version 2>&1 | grep -q "1.8"; then
    JAVA_BIN=java
elif ls "$XIANWEI_HOME"/jdk8*/bin/java &>/dev/null; then
    JAVA_BIN=$(ls "$XIANWEI_HOME"/jdk8*/bin/java | head -1)
else
    echo "   [错误] 未找到 Java 8，请先运行 setup.sh"
    exit 1
fi

screen -S xianwei-backend -X quit 2>/dev/null || true
screen -dmS xianwei-backend bash -c "
  cd $XIANWEI_HOME/后端 && \
  export MYSQL_PORT=$MYSQL_PORT BACKEND_PORT=$BACKEND_PORT ALGORITHM_PORT=$ALGORITHM_PORT \
         MYSQL_USER=$MYSQL_USER MYSQL_ROOT_PASSWORD=$MYSQL_ROOT_PASSWORD \
         SERVER_IP=$SERVER_IP MINIO_API_PORT=$MINIO_API_PORT \
         MINIO_ROOT_USER=$MINIO_ROOT_USER MINIO_ROOT_PASSWORD=$MINIO_ROOT_PASSWORD && \
  $JAVA_BIN -Xms512m -Xmx2g -XX:+UseG1GC \
    -jar uygur-project-0.0.1-SNAPSHOT.jar \
    --spring.config.location=application-prod.yml
"
echo "   等待后端启动..."
sleep 15

# ── 6. 健康检查 ──
echo "6. 健康检查..."
echo ""
PASS=0; TOTAL=5
check() { if curl -sf "$1" > /dev/null 2>&1; then echo "  [OK]   $2"; PASS=$((PASS+1)); else echo "  [FAIL] $2"; fi; }
check "http://localhost:$FRONTEND_PORT"                "前端 Nginx (:$FRONTEND_PORT)"
check "http://localhost:$BACKEND_PORT/actuator/health" "后端 Spring Boot (:$BACKEND_PORT)"
check "http://localhost:$ALGORITHM_PORT/health"        "算法 Flask (:$ALGORITHM_PORT)"
check "http://localhost:$MINIO_API_PORT/minio/health/live" "MinIO (:$MINIO_API_PORT)"
docker exec xianwei-mysql mysqladmin ping -h localhost -u root -p${MYSQL_ROOT_PASSWORD} --silent 2>/dev/null \
    && echo "  [OK]   MySQL (:$MYSQL_PORT)" && PASS=$((PASS+1)) \
    || echo "  [FAIL] MySQL (:$MYSQL_PORT)"

echo ""
echo "=== 启动完成  $PASS/$TOTAL 服务正常 ==="
echo "访问地址: http://${SERVER_IP}:${FRONTEND_PORT}"
echo "MinIO控制台: http://${SERVER_IP}:${MINIO_CONSOLE_PORT}"
