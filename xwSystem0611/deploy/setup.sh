#!/bin/bash
#
# 献微系统 — 一键部署脚本
# 用法: 在新机器上执行 bash deploy/setup.sh
# 前提: deploy/ 目录已包含所有构建产物和迁移数据（见 deploy 目录文件清单）
#
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
XIANWEI_HOME="$(dirname "$SCRIPT_DIR")"

echo "============================================"
echo " 献微系统部署  |  目录: $XIANWEI_HOME"
echo "============================================"

# ── 1. 加载环境变量 ──
if [ ! -f "$SCRIPT_DIR/.env" ]; then
    echo "[错误] 未找到 $SCRIPT_DIR/.env，请先配置"
    exit 1
fi
source "$SCRIPT_DIR/.env"

# ── 2. 检查 deploy/ 中的必备文件 ──
echo ""
echo "[1/8] 检查 deploy/ 目录文件..."
MISSING=0
check_file() { [ ! -e "$1" ] && echo "  [缺失] $1" && MISSING=1; return 0; }
check_file "$SCRIPT_DIR/.env"
check_file "$SCRIPT_DIR/docker-compose.yml"
check_file "$SCRIPT_DIR/nginx.conf.template"
check_file "$SCRIPT_DIR/application-prod.yml"
check_file "$SCRIPT_DIR/dist/index.html"
check_file "$SCRIPT_DIR/uygur-project-0.0.1-SNAPSHOT.jar"
check_file "$SCRIPT_DIR/init.sql"
check_file "$SCRIPT_DIR/算法/算法服务/app.py"
check_file "$SCRIPT_DIR/算法/数据抽取-新/config.json"
check_file "$SCRIPT_DIR/算法/报文融合/fusion_extractor.py"
if [ $MISSING -eq 1 ]; then
    echo ""
    echo "  请补全缺失文件后重新运行。参考 deploy/迁移部署指南.md"
    exit 1
fi
echo "  所有文件就绪"

# ── 3. 创建目录结构 ──
echo "[2/8] 创建目录结构..."
mkdir -p "$XIANWEI_HOME"/{前端,后端,算法,mysql,minio}

# ── 4. 分发文件到运行时位置 ──
echo "[3/8] 分发文件..."
# 配置文件
cp "$SCRIPT_DIR/.env"                "$XIANWEI_HOME/.env"
cp "$SCRIPT_DIR/docker-compose.yml"  "$XIANWEI_HOME/docker-compose.yml"
cp "$SCRIPT_DIR/application-prod.yml" "$XIANWEI_HOME/后端/application-prod.yml"
cp "$SCRIPT_DIR/nginx.conf.template" "$XIANWEI_HOME/nginx.conf.template"
# 构建产物
cp -r "$SCRIPT_DIR/dist"             "$XIANWEI_HOME/前端/dist"
cp "$SCRIPT_DIR/uygur-project-0.0.1-SNAPSHOT.jar" "$XIANWEI_HOME/后端/"
cp -r "$SCRIPT_DIR/算法/"*           "$XIANWEI_HOME/算法/"
# 迁移数据
cp "$SCRIPT_DIR/init.sql"            "$XIANWEI_HOME/mysql/init.sql"
echo "  分发完成"

# ── 5. 检查 Java ──
echo "[4/8] 检查 Java..."
JAVA_BIN=""
if command -v java &>/dev/null && java -version 2>&1 | grep -q "1.8"; then
    JAVA_BIN=java
elif ls "$XIANWEI_HOME"/jdk8*/bin/java &>/dev/null; then
    JAVA_BIN=$(ls "$XIANWEI_HOME"/jdk8*/bin/java | head -1)
else
    echo "  未找到 Java 8，正在下载免安装版..."
    ARCH=$(uname -m | sed 's/x86_64/x64/;s/aarch64/aarch64/')
    curl -L -o /tmp/jdk8.tar.gz \
      "https://api.adoptium.net/v3/binary/latest/8/ga/linux/$ARCH/jdk/hotspot/normal/eclipse"
    tar xzf /tmp/jdk8.tar.gz -C "$XIANWEI_HOME" && rm /tmp/jdk8.tar.gz
    JAVA_BIN=$(ls "$XIANWEI_HOME"/jdk8*/bin/java | head -1)
fi
echo "  Java: $($JAVA_BIN -version 2>&1 | head -1)"

# ── 6. 启动 Docker 容器 ──
echo "[5/8] 启动 Docker 容器..."
cd "$XIANWEI_HOME"
# 加载离线镜像（如果 tar 文件存在且镜像尚未加载）
if [ -f "$SCRIPT_DIR/xianwei-algorithm-offline.tar" ]; then
    if ! docker images --format '{{.Repository}}:{{.Tag}}' | grep -q 'xianwei-algorithm:offline'; then
        echo "  加载算法离线镜像..."
        docker load -i "$SCRIPT_DIR/xianwei-algorithm-offline.tar"
    else
        echo "  算法离线镜像已存在，跳过加载"
    fi
fi
docker compose up -d mysql minio
echo "  等待 MySQL 就绪..."
sleep 10
until docker exec xianwei-mysql mysqladmin ping -h localhost -u root -p${MYSQL_ROOT_PASSWORD} --silent 2>/dev/null; do
    sleep 3
done
echo "  MySQL 就绪"
docker compose up -d algorithm frontend
echo "  等待算法服务就绪..."
until curl -sf http://localhost:${ALGORITHM_PORT}/health > /dev/null 2>&1; do
    sleep 5
done
echo "  算法服务就绪"

# ── 7. 启动后端 ──
echo "[6/8] 启动后端服务..."
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
echo "  等待后端启动..."
sleep 15
if curl -sf http://localhost:$BACKEND_PORT/actuator/health > /dev/null 2>&1; then
    echo "  后端就绪"
else
    echo "  [警告] 后端尚未就绪，请检查: screen -r xianwei-backend"
fi

# ── 8. 健康检查 ──
echo "[7/8] 健康检查..."
echo ""
PASS=0; TOTAL=5
check() { if curl -sf "$1" > /dev/null 2>&1; then echo "  [OK]   $2"; PASS=$((PASS+1)); else echo "  [FAIL] $2"; fi; }
check "http://localhost:$FRONTEND_PORT"             "前端 Nginx (:$FRONTEND_PORT)"
check "http://localhost:$BACKEND_PORT/actuator/health" "后端 Spring Boot (:$BACKEND_PORT)"
check "http://localhost:$ALGORITHM_PORT/health"     "算法 Flask (:$ALGORITHM_PORT)"
check "http://localhost:$MINIO_API_PORT/minio/health/live" "MinIO (:$MINIO_API_PORT)"
docker exec xianwei-mysql mysqladmin ping -h localhost -u root -p${MYSQL_ROOT_PASSWORD} --silent 2>/dev/null && echo "  [OK]   MySQL (:$MYSQL_PORT)" && PASS=$((PASS+1)) || echo "  [FAIL] MySQL (:$MYSQL_PORT)"

echo ""
echo "[8/8] 恢复 MinIO 图片..."
if [ -d "$SCRIPT_DIR/minio_images" ] && [ "$(ls -A "$SCRIPT_DIR/minio_images" 2>/dev/null)" ]; then
    echo "  等待 MinIO 就绪..."
    until curl -sf http://localhost:${MINIO_API_PORT}/minio/health/live > /dev/null 2>&1; do
        sleep 2
    done
    docker exec xianwei-minio mc alias set local http://localhost:9000 $MINIO_ROOT_USER $MINIO_ROOT_PASSWORD 2>/dev/null
    docker exec xianwei-minio mc mb --ignore-existing local/xianwei-images 2>/dev/null
    docker cp "$SCRIPT_DIR/minio_images/." xianwei-minio:/tmp/minio_restore/
    docker exec xianwei-minio mc cp --recursive /tmp/minio_restore/ local/xianwei-images/ 2>/dev/null
    docker exec xianwei-minio mc anonymous set download local/xianwei-images 2>/dev/null
    docker exec xianwei-minio rm -rf /tmp/minio_restore
    IMG_COUNT=$(ls "$SCRIPT_DIR/minio_images" | wc -l)
    echo "  已恢复 $IMG_COUNT 张图片"
else
    echo "  [跳过] deploy/minio_images/ 目录不存在或为空"
    echo "  如需迁移图片，请参考迁移部署指南第 8 步"
fi

echo ""
echo "============================================"
echo " 部署完成  $PASS/$TOTAL 服务正常"
echo " 访问地址: http://$SERVER_IP:$FRONTEND_PORT"
echo " MinIO:   http://$SERVER_IP:$MINIO_CONSOLE_PORT"
echo "============================================"
