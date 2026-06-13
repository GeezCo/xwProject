# 献微 Embedding 服务（BGE-large-zh-v1.5）
# 离线部署：CPU 推理，模型权重通过 volume 挂载（不进镜像）

FROM python:3.10-slim

WORKDIR /app

# 系统依赖（torch 需要 libgomp）
RUN apt-get update && apt-get install -y --no-install-recommends \
        libgomp1 \
        curl \
    && rm -rf /var/lib/apt/lists/*

# Python 依赖：先装 torch CPU 版本（小，约 200MB；GPU 版要 2GB+）
RUN pip install --no-cache-dir \
        --index-url https://download.pytorch.org/whl/cpu \
        torch==2.2.2

# 业务依赖（用清华源加速国内构建）
COPY requirements.txt .
RUN pip install --no-cache-dir \
        -i https://pypi.tuna.tsinghua.edu.cn/simple \
        flask>=2.0 \
        flask-cors \
        sentence-transformers>=2.2.0 \
        numpy

# 应用代码
COPY app.py .

# 模型路径（通过 volume 挂载到此路径）
ENV BGE_MODEL_PATH=/app/models/bge-large-zh-v1.5

EXPOSE 5002

# 健康检查
HEALTHCHECK --interval=30s --timeout=10s --start-period=120s --retries=3 \
    CMD curl -f http://localhost:5002/health || exit 1

CMD ["python", "-u", "app.py"]
