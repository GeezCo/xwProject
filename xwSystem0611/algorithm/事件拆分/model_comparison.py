"""
模型对比测试脚本

功能说明：
测试不同模型在事件拆分任务上的表现差异，包括：
- 拆分精度（事件数量、要素补全）
- 拆分耗时

使用方法：
    python model_comparison.py [input.txt]
"""

import json
import os
import time
from datetime import datetime
from typing import Dict, List, Any
from event_splitter import EventSplitter


# 待测试的模型列表
MODELS = [
    "GLM-5-Turbo",
    "GLM-5",
    "GLM-4.7",
    "Qwen3.5-35B-A3B",
    "Qwen3.5-27B",
    "Qwen3.5-122B-A10B",
    "Qwen3-32B"
]


def test_single_model(model: str, text: str) -> Dict[str, Any]:
    """
    测试单个模型

    Args:
        model: 模型名称
        text: 测试文本

    Returns:
        Dict: 测试结果
    """
    print(f"\n{'='*50}")
    print(f"测试模型: {model}")
    print(f"{'='*50}")

    # 创建拆分器
    splitter = EventSplitter(model=model)

    # 记录开始时间
    start_time = time.time()

    try:
        # 执行拆分
        result = splitter.split(text)

        # 计算耗时
        elapsed_time = time.time() - start_time

        # 统计信息
        event_count = result.get('total_events', 0)
        global_time = result.get('global_time')
        global_location = result.get('global_location')

        print(f"拆分完成: {event_count} 个事件, 耗时 {elapsed_time:.2f} 秒")

        return {
            "model": model,
            "success": True,
            "event_count": event_count,
            "elapsed_time": round(elapsed_time, 2),
            "global_time": global_time,
            "global_location": global_location,
            "events": result.get('events', []),
            "error": None
        }

    except Exception as e:
        elapsed_time = time.time() - start_time
        print(f"测试失败: {e}")

        return {
            "model": model,
            "success": False,
            "event_count": 0,
            "elapsed_time": round(elapsed_time, 2),
            "global_time": None,
            "global_location": None,
            "events": [],
            "error": str(e)
        }


def evaluate_quality(result: Dict, expected_count: int = None) -> Dict:
    """
    评估拆分质量

    Args:
        result: 测试结果
        expected_count: 预期事件数量（可选）

    Returns:
        Dict: 质量评估结果
    """
    events = result.get('events', [])
    event_count = len(events)

    # 统计要素完整度
    time_complete = 0  # 有时间的数量
    location_complete = 0  # 有地点的数量
    content_length_sum = 0  # 内容长度总和

    for event in events:
        content = event.get('content', '')
        content_length_sum += len(content)

        # 简单检测是否包含时间信息
        if any(kw in content for kw in ['月', '日', '时', '年', '今天', '昨日', '近日']):
            time_complete += 1

        # 检测是否包含地点信息（简单规则）
        if any(kw in content for kw in ['在', '于', '抵达', '进入', '穿越', '从', '前往']):
            location_complete += 1

    avg_length = content_length_sum / event_count if event_count > 0 else 0

    evaluation = {
        "event_count": event_count,
        "avg_content_length": round(avg_length, 1),
        "time_complete_ratio": round(time_complete / event_count, 2) if event_count > 0 else 0,
        "location_complete_ratio": round(location_complete / event_count, 2) if event_count > 0 else 0
    }

    # 如果有预期数量，计算准确率
    if expected_count:
        evaluation["expected_count"] = expected_count
        evaluation["count_accuracy"] = round(min(event_count, expected_count) / expected_count, 2)

    return evaluation


def run_comparison(input_path: str, output_dir: str = None):
    """
    运行所有模型对比测试

    Args:
        input_path: 输入文件路径
        output_dir: 输出目录
    """
    # 设置输出目录
    if output_dir is None:
        output_dir = os.path.join(os.path.dirname(__file__), '输出')

    os.makedirs(output_dir, exist_ok=True)

    # 读取测试文本
    print(f"读取测试文件: {input_path}")
    splitter = EventSplitter()
    text = splitter.read_file(input_path)

    print(f"文本长度: {len(text)} 字符")
    print(f"待测试模型: {len(MODELS)} 个")

    # 测试所有模型
    results = []
    for model in MODELS:
        result = test_single_model(model, text)
        results.append(result)

        # 保存单个模型详细结果
        detail_path = os.path.join(output_dir, f"{model.replace('.', '_')}_detail.json")
        with open(detail_path, 'w', encoding='utf-8') as f:
            json.dump(result, f, ensure_ascii=False, indent=2)

    # 生成对比报告
    print("\n" + "=" * 60)
    print("对比报告")
    print("=" * 60)

    report = {
        "test_time": datetime.now().strftime('%Y-%m-%d %H:%M:%S'),
        "source_file": os.path.basename(input_path),
        "text_length": len(text),
        "models_tested": len(MODELS),
        "results": []
    }

    print(f"\n{'模型':<20} {'事件数':<8} {'耗时(秒)':<10} {'状态':<8} {'平均长度':<10} {'时间完整度':<10}")
    print("-" * 70)

    for result in results:
        evaluation = evaluate_quality(result)

        status = "成功" if result['success'] else "失败"
        print(f"{result['model']:<20} {evaluation['event_count']:<8} {result['elapsed_time']:<10.2f} {status:<8} {evaluation['avg_content_length']:<10.1f} {evaluation['time_complete_ratio']:<10.2f}")

        report['results'].append({
            "model": result['model'],
            "success": result['success'],
            "event_count": evaluation['event_count'],
            "elapsed_time": result['elapsed_time'],
            "avg_content_length": evaluation['avg_content_length'],
            "time_complete_ratio": evaluation['time_complete_ratio'],
            "location_complete_ratio": evaluation['location_complete_ratio'],
            "global_time": result['global_time'],
            "global_location": result['global_location'],
            "error": result['error']
        })

    # 保存报告
    report_path = os.path.join(output_dir, f"comparison_report_{datetime.now().strftime('%Y%m%d_%H%M%S')}.json")
    with open(report_path, 'w', encoding='utf-8') as f:
        json.dump(report, f, ensure_ascii=False, indent=2)

    print(f"\n报告已保存: {report_path}")

    return report


if __name__ == '__main__':
    import sys

    # 检查参数
    if len(sys.argv) < 2:
        print("使用方法: python model_comparison.py <input.txt>")
        print("示例: python model_comparison.py test.txt")
        sys.exit(1)

    # 运行测试
    input_file = sys.argv[1]
    run_comparison(input_file)