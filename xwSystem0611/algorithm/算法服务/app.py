"""
算法服务 - Flask REST API

提供事件抽取算法的HTTP接口

接口：
- POST /extract: 执行事件抽取
- GET /health: 健康检查
"""

# Flask框架核心模块
from flask import Flask, request, jsonify
# 跨域支持模块
from flask_cors import CORS
# 系统模块
import sys
import os
import logging
from logging.handlers import RotatingFileHandler
# 异常追踪模块
import traceback
# 日期时间模块
from datetime import datetime

# 添加新抽取模块路径
data_extraction_path = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '数据抽取-新'))
sys.path.insert(0, data_extraction_path)

# 添加报文融合目录到路径
sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..', '报文融合'))

# 添加事件拆分模块路径
sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..', 'event_split'))

# 导入V3事件抽取器（含状态机修复+输入预处理）
from llm_event_extractor_v3 import LLMEventExtractorV3
from llm_event_extractor import load_config

# 导入报文融合抽取器
from fusion_extractor import FusionExtractor

# 导入事件拆分器
from event_splitter import EventSplitter

# 加载配置
CONFIG = load_config()

# 创建Flask应用实例
app = Flask(__name__)
# 设置JSON编码为UTF-8，避免中文乱码
app.config['JSON_AS_ASCII'] = False
# 启用跨域支持，允许前端跨域访问
CORS(app)

# ========== 日志配置 ==========
def setup_logging():
    """配置日志系统"""
    # 创建日志目录
    log_dir = os.path.join(os.path.dirname(__file__), 'logs')
    os.makedirs(log_dir, exist_ok=True)

    # 设置日志格式
    formatter = logging.Formatter(
        '%(asctime)s [%(levelname)s] %(message)s',
        datefmt='%Y-%m-%d %H:%M:%S'
    )

    # 文件处理器（自动轮转，最大10MB，保留5个备份）
    file_handler = RotatingFileHandler(
        os.path.join(log_dir, 'algorithm.log'),
        maxBytes=10*1024*1024,
        backupCount=5,
        encoding='utf-8'
    )
    file_handler.setFormatter(formatter)
    file_handler.setLevel(logging.INFO)

    # 控制台处理器
    console_handler = logging.StreamHandler()
    console_handler.setFormatter(formatter)
    console_handler.setLevel(logging.INFO)

    # 配置应用日志
    app.logger.addHandler(file_handler)
    app.logger.addHandler(console_handler)
    app.logger.setLevel(logging.INFO)

# 初始化日志
setup_logging()

# 抽取器实例（全局变量，延迟初始化以节省资源）
extractor = None

# 融合抽取器实例（全局变量，延迟初始化）
fusion_extractor = None

# 事件拆分器实例（全局变量，延迟初始化）
event_splitter_instance = None


def adapt_new_result(result):
    """
    将新抽取器输出转换为兼容旧API的格式

    新抽取器每个事件直接包含分类字段（船只、飞机等），
    此函数生成旧API所需的 subject、labels、entities 字段，
    确保后端和前端无需修改。
    """
    events = result.get('events', [])
    category_names = result.get('subject_categories', [])

    all_labels = set()
    entities = {}

    for event in events:
        event_subjects = []
        event_labels = []
        for cat in category_names:
            cat_entities = event.get(cat, [])
            if cat_entities:
                event_labels.append(cat)
                event_subjects.extend(cat_entities)
                if cat not in entities:
                    entities[cat] = set()
                entities[cat].update(cat_entities)
        event['subject'] = event_subjects
        event['labels'] = event_labels
        all_labels.update(event_labels)

    for cat in entities:
        entities[cat] = list(entities[cat])

    result['labels'] = list(all_labels)
    result['entities'] = entities
    result['llm_calls'] = result.get('paragraph_count', 0)
    result['llm_calls_saved'] = 0

    return result


def get_extractor():
    """
    获取抽取器实例（懒加载模式）

    使用V3抽取器：LLM直接抽取+分类，含输入预处理防御引号问题。

    Returns:
        LLMEventExtractorV3: 抽取器实例
    """
    global extractor
    if extractor is None:
        extractor = LLMEventExtractorV3()
    return extractor


def get_fusion_extractor():
    """
    获取融合抽取器实例（懒加载模式）

    Returns:
        FusionExtractor: 融合抽取器实例
    """
    global fusion_extractor
    if fusion_extractor is None:
        fusion_extractor = FusionExtractor()
    return fusion_extractor


def get_event_splitter():
    """
    获取事件拆分器实例（懒加载模式）

    Returns:
        EventSplitter: 事件拆分器实例
    """
    global event_splitter_instance
    if event_splitter_instance is None:
        event_splitter_instance = EventSplitter()
    return event_splitter_instance


@app.route('/health', methods=['GET'])
def health_check():
    """
    健康检查接口

    用途：
    - 服务启动后验证服务是否正常运行
    - 监控系统定期检测服务健康状态
    - 负载均衡器判断服务是否可用

    Returns:
        JSON响应，包含服务状态和当前时间
    """
    # 返回健康状态JSON
    return jsonify({
        'status': 'ok',  # 状态标识
        'service': 'algorithm-service',  # 服务名称
        'time': datetime.now().strftime('%Y-%m-%d %H:%M:%S')  # 当前时间
    })


@app.route('/extract', methods=['POST'])
def extract():
    """
    事件抽取接口（完整版）

    请求体：
    {
        "text": "要抽取的文本内容",
        "origin_text_id": 123  // 可选，用于日志记录
    }

    返回：
    {
        "code": 1,
        "data": {
            "extraction_time": "2024-01-01 12:00:00",
            "text_length": 1000,
            "paragraph_count": 5,
            "llm_calls": 3,
            "llm_calls_saved": 2,
            "total_events": 5,
            "events": [...]
        },
        "msg": "success"
    }
    """
    try:
        # 获取JSON格式的请求数据
        data = request.get_json(force=True, silent=True)
        # 检查请求体是否存在
        if not data:
            app.logger.error("[输入错误] 请求体为空")
            return jsonify({
                'code': 0,  # 0表示失败
                'msg': '请求体不能为空',
                'data': None
            }), 400  # HTTP 400错误码

        # 获取文本内容
        text = data.get('text')
        # 获取文本ID（可选，用于日志记录）
        origin_text_id = data.get('origin_text_id', 'unknown')

        # 检查文本内容是否存在
        if not text:
            app.logger.error("[输入错误] text字段为空")
            return jsonify({
                'code': 0,
                'msg': 'text字段不能为空',
                'data': None
            }), 400

        # 记录输入日志
        app.logger.info("=" * 50)
        app.logger.info("[事件抽取] 开始处理")
        app.logger.info(f"[输入] 文本ID: {origin_text_id}")
        app.logger.info(f"[输入] 文本长度: {len(text)} 字符")
        app.logger.info(f"[输入] 文本类型: {type(text)}")
        app.logger.info(f"[输入] 文本内容: {repr(text[:200])}")

        # 获取抽取器实例
        ext = get_extractor()
        # 执行事件抽取
        result = ext.extract_all(text)
        # 适配输出格式（生成subject/labels/entities兼容字段）
        result = adapt_new_result(result)

        # 记录输出日志
        app.logger.info(f"[输出] 事件数量: {result['total_events']}")
        app.logger.info(f"[输出] 分类标签: {result.get('labels', [])}")
        app.logger.info(f"[输出] LLM调用: {result['llm_calls']}, 节省: {result['llm_calls_saved']}")
        app.logger.info(f"[输出] 处理耗时: {result.get('extraction_time', 'N/A')}")
        app.logger.info("[事件抽取] 处理完成")
        app.logger.info("=" * 50)

        # 返回成功结果
        return jsonify({
            'code': 1,  # 1表示成功
            'msg': 'success',
            'data': result  # 抽取结果数据
        })

    except Exception as e:
        # 捕获异常，记录错误日志
        app.logger.error("=" * 50)
        app.logger.error(f"[事件抽取] 处理失败")
        app.logger.error(f"[错误] {str(e)}")
        app.logger.error(f"[堆栈] {traceback.format_exc()}")
        app.logger.error("=" * 50)
        # 返回错误信息
        return jsonify({
            'code': 0,
            'msg': f'抽取失败: {str(e)}',
            'data': None
        }), 500  # HTTP 500服务器错误码


@app.route('/extract/simple', methods=['POST'])
def extract_simple():
    """
    简单抽取接口（仅返回事件列表）

    与完整版的区别：
    - 返回格式更简洁
    - 只包含事件列表和总数
    - 适用于不需要详细统计信息的场景

    请求体：
    {
        "text": "要抽取的文本内容"
    }

    返回：
    {
        "events": [...],
        "total": 5
    }
    """
    try:
        # 获取JSON格式的请求数据
        data = request.get_json(force=True, silent=True)
        # 检查请求体和文本字段是否存在
        if not data or not data.get('text'):
            # 返回错误
            return jsonify({
                'events': [],
                'total': 0,
                'error': 'text字段不能为空'
            }), 400

        # 获取文本内容
        text = data.get('text')
        # 获取抽取器实例
        ext = get_extractor()
        # 执行事件抽取
        result = ext.extract_all(text)
        # 适配输出格式
        result = adapt_new_result(result)

        # 返回简化结果
        return jsonify({
            'events': result['events'],  # 事件列表
            'total': result['total_events']  # 事件总数
        })

    except Exception as e:
        # 捕获异常，返回错误
        return jsonify({
            'events': [],
            'total': 0,
            'error': str(e)
        }), 500


# ========== 报文融合接口 ==========

@app.route('/fusion/create', methods=['POST'])
def fusion_create():
    """
    报文融合接口

    请求体（新格式）：
    {
        "reports": [
            {
                "id": 9741,
                "title": "报文标题",
                "content": "报文正文内容",
                "extractionResult": {
                    "events": [...],
                    "entities": {...},
                    "labels": [...]
                }
            }
        ],
        "fusionType": "standard",
        "customTitle": null
    }

    返回：
    {
        "code": 1,
        "data": {
            "fusionId": 1234567890,
            "title": "融合报告标题",
            "summary": "报告摘要",
            "timeline": [{"time": "2024-03-15", "description": "事件描述"}],
            "content": "详细内容（Markdown）",
            "entities": {...},
            "labels": ["标签1", "标签2"],
            "sourceIds": [9741, 9742],
            "modelUsed": "Qwen3.5-122B-A10B",
            "createTime": "2026-04-18 12:00:00"
        },
        "msg": "success"
    }
    """
    try:
        # 获取JSON格式的请求数据
        data = request.get_json(force=True, silent=True)
        if not data:
            app.logger.error("[融合输入错误] 请求体为空")
            return jsonify({
                'code': 0,
                'msg': '请求体不能为空',
                'data': None
            }), 400

        # 获取报文数据列表（新参数格式）
        reports = data.get('reports')
        if not reports:
            app.logger.error("[融合输入错误] reports字段为空")
            return jsonify({
                'code': 0,
                'msg': 'reports字段不能为空',
                'data': None
            }), 400

        # 校验报文数量
        if len(reports) < 2:
            app.logger.error("[融合输入错误] 报文数量不足")
            return jsonify({
                'code': 0,
                'msg': '至少需要2篇报文进行融合',
                'data': None
            }), 400

        if len(reports) > 10:
            app.logger.error("[融合输入错误] 报文数量过多")
            return jsonify({
                'code': 0,
                'msg': '最多支持10篇报文融合',
                'data': None
            }), 400

        # 校验每个报文是否有content
        for i, report in enumerate(reports):
            if not report.get('content'):
                app.logger.error(f"[融合输入错误] 报文{i}缺少content字段")
                return jsonify({
                    'code': 0,
                    'msg': f'报文{i+1}缺少content字段',
                    'data': None
                }), 400

        # 获取可选参数
        fusion_type = data.get('fusionType', 'standard')
        custom_title = data.get('customTitle')

        # 记录输入日志
        app.logger.info("=" * 50)
        app.logger.info("[报文融合] 开始处理")
        app.logger.info(f"[输入] 报文数量: {len(reports)}")
        app.logger.info(f"[输入] 报文ID列表: {[r.get('id') for r in reports]}")
        app.logger.info(f"[输入] 融合类型: {fusion_type}")

        # 获取融合抽取器实例
        fe = get_fusion_extractor()
        # 执行融合（传入完整的报文数据）
        result = fe.fuse_reports(
            reports_data=reports,
            fusion_type=fusion_type,
            custom_title=custom_title
        )

        # 记录输出日志
        app.logger.info(f"[输出] 融合ID: {result['fusionId']}")
        app.logger.info(f"[输出] 标题: {result['title']}")
        app.logger.info(f"[输出] 时间线事件数: {len(result['timeline'])}")
        app.logger.info("[报文融合] 处理完成")
        app.logger.info("=" * 50)

        # 返回成功结果
        return jsonify({
            'code': 1,
            'msg': 'success',
            'data': result
        })

    except Exception as e:
        # 捕获异常，记录错误日志
        app.logger.error("=" * 50)
        app.logger.error("[报文融合] 处理失败")
        app.logger.error(f"[错误] {str(e)}")
        app.logger.error(f"[堆栈] {traceback.format_exc()}")
        app.logger.error("=" * 50)
        # 返回错误信息
        return jsonify({
            'code': 0,
            'msg': f'融合失败: {str(e)}',
            'data': None
        }), 500


@app.route('/eventSplit', methods=['POST'])
def event_split():
    """
    事件拆分接口

    输入：{"text": "报文原文"}
    输出：{"events": [...], "overall_analysis": "..."}
    """
    try:
        data = request.get_json()
        if not data:
            return jsonify({'code': 0, 'msg': '请求体不能为空'}), 400

        text = data.get('text', '').strip()
        if not text:
            return jsonify({'code': 0, 'msg': '参数错误：text字段不能为空'}), 400

        if len(text) > 10000:
            return jsonify({'code': 0, 'msg': '文本长度超过限制（最大10000字）'}), 413

        app.logger.info(f"[eventSplit] 开始处理，文本长度: {len(text)}")
        splitter = get_event_splitter()
        result = splitter.split_events(text)

        app.logger.info(f"[eventSplit] 处理完成，事件数: {len(result.get('events', []))}")
        return jsonify(result)

    except Exception as e:
        app.logger.error(f"[eventSplit] 处理失败: {str(e)}")
        app.logger.error(traceback.format_exc())
        return jsonify({
            'code': 0,
            'msg': f'事件拆分失败: {str(e)}'
        }), 500


if __name__ == '__main__':
    # 创建日志目录（如果不存在）
    os.makedirs('logs', exist_ok=True)

    # 打印服务启动信息
    print("=" * 50)
    print("算法服务启动中...")
    print("=" * 50)
    # 打印可用接口
    print("API接口:")
    print("  - GET  /health           健康检查")
    print("  - POST /extract          事件抽取(完整)")
    print("  - POST /extract/simple   事件抽取(简化)")
    print("  - POST /fusion/create    报文融合")
    print("  - POST /eventSplit       事件拆分与分析")
    print("=" * 50)
    # 打印端口信息
    print("端口: 5001")
    print("=" * 50)

    # 启动Flask服务
    # host='0.0.0.0' 允许外部访问
    # port=5001 指定端口
    # debug=False 关闭调试模式（生产环境）
    app.run(host='0.0.0.0', port=5001, debug=False)