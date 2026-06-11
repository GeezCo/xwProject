"""
批量抽取脚本：每个分类前20条数据
"""
import requests
import json
import time
from datetime import datetime

BASE_URL = "http://localhost:8081"

def get_categories():
    """获取所有分类"""
    resp = requests.get(f"{BASE_URL}/uygur/category")
    data = resp.json()
    if data['code'] == 1:
        return data['data']
    return []

def get_texts_by_category(type_id, page_size=20):
    """获取分类下的文本列表"""
    resp = requests.post(
        f"{BASE_URL}/uygur/getTextList",
        json={"pageNum": 1, "pageSize": page_size, "typeId": type_id}
    )
    data = resp.json()
    if data['code'] == 1:
        return data['data']['list']
    return []

def extract_text(origin_text_id, force=True):
    """执行抽取"""
    resp = requests.post(
        f"{BASE_URL}/extraction/extract",
        params={"originTextId": origin_text_id, "force": force},
        timeout=1200  # 20分钟超时
    )
    return resp.json()

def main():
    print("=" * 60)
    print(f"批量抽取开始: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    print("=" * 60)

    # 获取所有分类
    categories = get_categories()
    print(f"共 {len(categories)} 个分类")

    # 统计
    total_texts = 0
    total_success = 0
    total_failed = 0
    total_events = 0
    results = []

    # 遍历每个分类
    for cat in categories:
        cat_id = cat['id']
        cat_name = cat['typeName']
        print(f"\n{'='*60}")
        print(f"分类 {cat_id}: {cat_name}")
        print("=" * 60)

        # 获取前20条文本
        texts = get_texts_by_category(cat_id, page_size=20)
        print(f"获取到 {len(texts)} 条文本")

        for i, text in enumerate(texts):
            sid = text['sid']
            title = text['title'][:30] if text['title'] else '无标题'
            total_texts += 1

            print(f"\n[{i+1}/{len(texts)}] sid={sid}: {title}")

            try:
                start_time = time.time()
                result = extract_text(sid, force=False)  # force=False 避免重复抽取
                duration = time.time() - start_time

                if result['code'] == 1 and result['data']:
                    events_count = result['data'].get('totalEvents', 0)
                    total_success += 1
                    total_events += events_count
                    print(f"  [OK] 成功: {events_count} 个事件, 耗时 {duration:.1f}秒")
                    results.append({
                        'sid': sid,
                        'category': cat_name,
                        'title': title,
                        'status': 'success',
                        'events': events_count,
                        'duration': round(duration, 1)
                    })
                else:
                    total_failed += 1
                    msg = result.get('msg', '未知错误')
                    print(f"  [FAIL] 失败: {msg}")
                    results.append({
                        'sid': sid,
                        'category': cat_name,
                        'title': title,
                        'status': 'failed',
                        'msg': msg
                    })
            except Exception as e:
                total_failed += 1
                print(f"  [ERR] 异常: {str(e)}")
                results.append({
                    'sid': sid,
                    'category': cat_name,
                    'title': title,
                    'status': 'error',
                    'error': str(e)
                })

    # 保存结果
    output = {
        'start_time': datetime.now().strftime('%Y-%m-%d %H:%M:%S'),
        'summary': {
            'total_texts': total_texts,
            'total_success': total_success,
            'total_failed': total_failed,
            'total_events': total_events,
            'success_rate': f"{total_success/total_texts*100:.1f}%" if total_texts > 0 else "0%"
        },
        'results': results
    }

    with open('批量抽取结果.json', 'w', encoding='utf-8') as f:
        json.dump(output, f, ensure_ascii=False, indent=2)

    # 打印汇总
    print("\n" + "=" * 60)
    print("批量抽取完成")
    print("=" * 60)
    print(f"总文本数: {total_texts}")
    print(f"成功: {total_success}")
    print(f"失败: {total_failed}")
    print(f"总事件数: {total_events}")
    print(f"成功率: {total_success/total_texts*100:.1f}%" if total_texts > 0 else "0%")
    print(f"结果已保存: 批量抽取结果.json")

if __name__ == '__main__':
    main()