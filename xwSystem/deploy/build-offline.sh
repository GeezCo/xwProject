#!/bin/bash
#
# 献微系统 — 离线镜像打包脚本（构建机执行）
#
# 用法：在联网机器上（架构与生产机一致）执行：
#   bash deploy/build-offline.sh
#
# 输出：
#   deploy/offline-images.tar     — 全部 Docker 镜像（一次 docker load）
#   deploy/models/bge-large-zh-v1.5/  — Embedding 模型权重（不进镜像，单独挂载）
#
# 前提：
#   - 已安装 Docker、且能访问 Docker Hub（或配置好镜像加速）
#   - 已安装 huggingface-cli（pip install huggingface_hub）或可手动下载模型
#

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
EMBEDDING_DIR="$PROJECT_ROOT/xwEmbedding"

# ── 目标平台（生产机为 ARM64 鲲鹏服务器）──
PLATFORM="${TARGET_PLATFORM:-linux/arm64}"

# ── 镜像清单（与 docker-compose.yml 同步）──
IMAGES=(
    "mysql:8.0.35"
    "minio/minio:RELEASE.2024-08-17T01-24-54Z"
    "nginx:1.25-alpine"
    "elasticsearch:7.17.15"
)

echo "============================================"
echo " 献微系统 — 离线镜像打包"
echo " 目标架构: $PLATFORM"
echo "============================================"

# ── 1. 检查 Docker ──
if ! command -v docker >/dev/null 2>&1; then
    echo "[错误] 未找到 docker 命令，请先安装 Docker Desktop / Engine"
    exit 1
fi

# ── 2. 拉取公共镜像 ──
echo ""
echo "[1/4] 拉取公共镜像（约 1-2 GB）..."
for img in "${IMAGES[@]}"; do
    echo "  → $img"
    docker pull --platform "$PLATFORM" "$img"
done

# ── 3. 构建自有镜像 ──
echo ""
echo "[2/4] 构建 xianwei-embedding:offline ..."
if [ ! -d "$EMBEDDING_DIR" ]; then
    echo "[错误] 找不到 xwEmbedding 目录: $EMBEDDING_DIR"
    exit 1
fi
docker build --platform "$PLATFORM" \
    -t xianwei-embedding:offline \
    "$EMBEDDING_DIR"

# 算法镜像（如已存在跳过；否则用项目内 Dockerfile 重新 build）
if docker images --format '{{.Repository}}:{{.Tag}}' | grep -q '^xianwei-algorithm:offline$'; then
    echo "  → xianwei-algorithm:offline 已存在，跳过构建"
else
    echo "[警告] 缺少 xianwei-algorithm:offline 镜像，请先在 算法/ 目录执行 docker build"
fi

# ── 4. 下载模型权重 ──
echo ""
echo "[3/4] 检查 / 下载 Embedding 模型..."
MODEL_DIR="$SCRIPT_DIR/models/bge-large-zh-v1.5"
if [ -f "$MODEL_DIR/config.json" ] && [ -f "$MODEL_DIR/pytorch_model.bin" -o -f "$MODEL_DIR/model.safetensors" ]; then
    echo "  ✅ 模型已存在: $MODEL_DIR"
else
    mkdir -p "$MODEL_DIR"
    if command -v huggingface-cli >/dev/null 2>&1; then
        echo "  → 使用 huggingface-cli 下载（约 1.3 GB）..."
        # 国内可设 HF_ENDPOINT=https://hf-mirror.com
        HF_ENDPOINT="${HF_ENDPOINT:-https://hf-mirror.com}" \
        huggingface-cli download BAAI/bge-large-zh-v1.5 \
            --local-dir "$MODEL_DIR" \
            --local-dir-use-symlinks False
    else
        echo "  [警告] 未安装 huggingface-cli，请手动下载："
        echo "         pip install huggingface_hub"
        echo "         HF_ENDPOINT=https://hf-mirror.com huggingface-cli download \\"
        echo "             BAAI/bge-large-zh-v1.5 --local-dir $MODEL_DIR"
        echo ""
        echo "         或从其他渠道拷贝模型文件到 $MODEL_DIR"
    fi
fi

# ── 5. save 全部镜像到一个 tar ──
echo ""
echo "[4/4] 保存镜像到 offline-images.tar ..."
TAR_FILE="$SCRIPT_DIR/offline-images.tar"

ALL_IMAGES=("${IMAGES[@]}" "xianwei-embedding:offline")
if docker images --format '{{.Repository}}:{{.Tag}}' | grep -q '^xianwei-algorithm:offline$'; then
    ALL_IMAGES+=("xianwei-algorithm:offline")
fi

docker save -o "$TAR_FILE" "${ALL_IMAGES[@]}"

echo ""
echo "============================================"
echo " ✅ 打包完成"
echo "    镜像 tar: $TAR_FILE  ($(du -h "$TAR_FILE" | cut -f1))"
echo "    模型目录: $MODEL_DIR  ($(du -sh "$MODEL_DIR" 2>/dev/null | cut -f1))"
echo ""
echo " 下一步：把整个 deploy/ 目录上传到生产机，运行："
echo "    bash deploy/setup.sh"
echo "============================================"
