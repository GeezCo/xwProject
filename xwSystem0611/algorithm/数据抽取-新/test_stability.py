"""稳定性测试：从侦察报.jsonl抽100条测试新抽取算法的稳定性、速度和准确性"""
import sys
import os
import json
import time
import random
import traceback

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from llm_event_extractor import LLMEventExtractor

JSONL_PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', '侦察报.jsonl')
SAMPLE_SIZE = 100
OUTPUT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'stability_test_results')

def load_jsonl(path):
    records = []
    with open(path, 'r', encoding='utf-8') as f:
        for line in f:
            line = line.strip()
            if line:
                records.append(json.loads(line))
    return records

def run_test():
    print(f"加载数据: {JSONL_PATH}")
    all_records = load_jsonl(JSONL_PATH)
    print(f"总记录数: {len(all_records)}")

    random.seed(42)
    samples = random.sample(all_records, min(SAMPLE_SIZE, len(all_records)))
    print(f"抽样数量: {len(samples)}")

    os.makedirs(OUTPUT_DIR, exist_ok=True)

    extractor = LLMEventExtractor()
    print(f"主体分类: {extractor.CATEGORY_NAMES}")
    print(f"LLM模型: {extractor.MODEL}")
    print(f"API地址: {extractor.BASE_URL}")
    print("=" * 70)

    results = []
    success_count = 0
    fail_count = 0
    empty_count = 0
    total_events = 0
    total_time = 0.0
    category_stats = {cat: 0 for cat in extractor.CATEGORY_NAMES}
    errors = []

    for i, record in enumerate(samples):
        title = record.get('标题', '无标题')
        content = record.get('内容', '')
        record_time = record.get('时间', '')

        print(f"\n[{i+1}/{len(samples)}] {title}")
        print(f"  内容长度: {len(content)} 字符")

        start = time.time()
        try:
            result = extractor.extract_all(content)
            elapsed = time.time() - start
            total_time += elapsed

            event_count = result.get('total_events', 0)
            total_events += event_count

            if event_count > 0:
                success_count += 1
            else:
                empty_count += 1

            for event in result.get('events', []):
                for cat in extractor.CATEGORY_NAMES:
                    if event.get(cat):
                        category_stats[cat] += len(event[cat])

            results.append({
                'index': i,
                'title': title,
                'time': record_time,
                'content_length': len(content),
                'status': 'success',
                'event_count': event_count,
                'elapsed_seconds': round(elapsed, 2),
                'events': result.get('events', [])
            })

            print(f"  结果: {event_count} 个事件, 耗时 {elapsed:.2f}s")

        except Exception as e:
            elapsed = time.time() - start
            total_time += elapsed
            fail_count += 1
            error_msg = f"{type(e).__name__}: {str(e)}"
            errors.append({'index': i, 'title': title, 'error': error_msg})

            results.append({
                'index': i,
                'title': title,
                'time': record_time,
                'content_length': len(content),
                'status': 'error',
                'error': error_msg,
                'elapsed_seconds': round(elapsed, 2),
                'events': []
            })

            print(f"  错误: {error_msg}")
            traceback.print_exc()

    processed = success_count + empty_count + fail_count
    avg_time = total_time / processed if processed > 0 else 0
    avg_events = total_events / (success_count + empty_count) if (success_count + empty_count) > 0 else 0

    summary = {
        'test_config': {
            'sample_size': len(samples),
            'model': extractor.MODEL,
            'base_url': extractor.BASE_URL,
            'categories': extractor.CATEGORY_NAMES
        },
        'stability': {
            'total_processed': processed,
            'success_with_events': success_count,
            'success_empty': empty_count,
            'failures': fail_count,
            'success_rate': round((success_count + empty_count) / processed * 100, 2) if processed > 0 else 0,
            'event_extraction_rate': round(success_count / processed * 100, 2) if processed > 0 else 0
        },
        'speed': {
            'total_time_seconds': round(total_time, 2),
            'avg_time_per_record': round(avg_time, 2),
            'min_time': round(min(r['elapsed_seconds'] for r in results), 2) if results else 0,
            'max_time': round(max(r['elapsed_seconds'] for r in results), 2) if results else 0
        },
        'accuracy': {
            'total_events_extracted': total_events,
            'avg_events_per_record': round(avg_events, 2),
            'category_entity_counts': category_stats
        },
        'errors': errors
    }

    summary_path = os.path.join(OUTPUT_DIR, 'summary.json')
    with open(summary_path, 'w', encoding='utf-8') as f:
        json.dump(summary, f, ensure_ascii=False, indent=2)

    detail_path = os.path.join(OUTPUT_DIR, 'detail_results.json')
    with open(detail_path, 'w', encoding='utf-8') as f:
        json.dump(results, f, ensure_ascii=False, indent=2)

    print("\n" + "=" * 70)
    print("稳定性测试报告")
    print("=" * 70)
    print(f"样本数量:    {len(samples)}")
    print(f"成功(有事件): {success_count}")
    print(f"成功(无事件): {empty_count}")
    print(f"失败:        {fail_count}")
    print(f"成功率:      {summary['stability']['success_rate']}%")
    print(f"有事件率:    {summary['stability']['event_extraction_rate']}%")
    print("-" * 70)
    print(f"总耗时:      {summary['speed']['total_time_seconds']}s")
    print(f"平均耗时:    {summary['speed']['avg_time_per_record']}s/条")
    print(f"最快:        {summary['speed']['min_time']}s")
    print(f"最慢:        {summary['speed']['max_time']}s")
    print("-" * 70)
    print(f"总事件数:    {total_events}")
    print(f"平均事件数:  {summary['accuracy']['avg_events_per_record']}/条")
    print("-" * 70)
    print("各分类实体统计:")
    for cat, count in sorted(category_stats.items(), key=lambda x: -x[1]):
        print(f"  {cat}: {count}")
    print("-" * 70)
    if errors:
        print(f"错误详情 ({len(errors)} 条):")
        for err in errors:
            print(f"  [{err['index']}] {err['title']}: {err['error']}")
    print("=" * 70)
    print(f"详细结果: {detail_path}")
    print(f"汇总报告: {summary_path}")

if __name__ == '__main__':
    run_test()
