"""
BGE Embedding 服务
独立 Flask 应用，端口 5002
加载 BAAI/bge-large-zh-v1.5 模型，提供文本向量化 API
"""
import os
import time
import logging
from flask import Flask, request, jsonify

logging.basicConfig(level=logging.INFO, format='%(asctime)s [%(levelname)s] %(message)s')
logger = logging.getLogger(__name__)

app = Flask(__name__)

# ---------- 模型加载 ----------
MODEL_PATH = os.environ.get("BGE_MODEL_PATH", os.path.join(os.path.dirname(__file__), "models/bge-large-zh-v1.5"))
MODEL_NAME = "bge-large-zh-v1.5"
DIMENSION = 1024
MAX_BATCH_SIZE = 64
MAX_SEQ_LENGTH = 512
START_TIME = time.time()

model = None

def load_model():
    global model
    try:
        from sentence_transformers import SentenceTransformer
        logger.info(f"正在加载模型: {MODEL_PATH}")
        if os.path.exists(MODEL_PATH):
            model = SentenceTransformer(MODEL_PATH)
        else:
            model = SentenceTransformer(MODEL_NAME)
        logger.info(f"模型加载完成，维度: {model.get_sentence_embedding_dimension()}")
    except Exception as e:
        logger.error(f"模型加载失败: {e}")
        raise

load_model()


# ---------- API ----------
@app.route("/embed", methods=["POST"])
def embed():
    """文本向量化"""
    data = request.get_json(force=True)
    texts = data.get("texts", [])
    normalize = data.get("normalize", True)

    if not texts:
        return jsonify({"code": 0, "msg": "texts 不能为空", "data": None}), 400

    if isinstance(texts, str):
        texts = [texts]

    if len(texts) > MAX_BATCH_SIZE:
        texts = texts[:MAX_BATCH_SIZE]

    texts = [t[:MAX_SEQ_LENGTH * 4] if len(t) > MAX_SEQ_LENGTH * 4 else t for t in texts]

    t0 = time.time()
    try:
        embeddings = model.encode(texts, normalize_embeddings=normalize, show_progress_bar=False)
        embeddings_list = [emb.tolist() for emb in embeddings]
        took_ms = int((time.time() - t0) * 1000)

        return jsonify({
            "code": 1,
            "data": {
                "embeddings": embeddings_list,
                "dimension": DIMENSION,
                "count": len(embeddings_list),
                "took_ms": took_ms
            }
        })
    except Exception as e:
        logger.error(f"向量化失败: {e}")
        return jsonify({"code": 0, "msg": str(e), "data": None}), 500


@app.route("/health", methods=["GET"])
def health():
    """健康检查"""
    return jsonify({
        "status": "ok",
        "model": MODEL_NAME,
        "dimension": DIMENSION,
        "uptime_seconds": int(time.time() - START_TIME)
    })


@app.route("/model/info", methods=["GET"])
def model_info():
    """模型信息"""
    return jsonify({
        "model": MODEL_NAME,
        "dimension": DIMENSION,
        "max_batch_size": MAX_BATCH_SIZE,
        "max_seq_length": MAX_SEQ_LENGTH
    })


if __name__ == "__main__":
    port = int(os.environ.get("PORT", 5002))
    app.run(host="0.0.0.0", port=port, threaded=True)
