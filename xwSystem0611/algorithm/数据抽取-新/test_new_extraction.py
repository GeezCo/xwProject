"""测试新版抽取逻辑：从数据库取两条数据进行事件抽取"""
import sys
import os
import json

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from llm_event_extractor import LLMEventExtractor

test_texts = [
    {
        "sid": 50502,
        "title": "2026年1月22日外军动态监测报告",
        "content": "2026 年 1 月 22 日掌握部分美军组织开展情报收集获得，具体情况如下：\n一是在南海举行联合演习；\n二是拜登发表声明；\n三是布林肯与岸田文雄在华盛顿会面；\n四是奥斯汀去横须贺海军基地进行港口访问；\n五是 F-35B 战斗机在东海空域进行巡逻。"
    },
    {
        "sid": 55544,
        "title": "美军飞机舰船在西太平洋区域活动通报",
        "content": "2026 年 1 月 21 日（1 月 20 日 18 时至 21 日 12 时）情况：\n美国空军 5 架次飞机、3 艘船在我国外海活动。\n其中 F-15 战斗机在西太平洋空域活动、\nP-8A 巡逻机在台湾周边空域活动、\nRC-135 侦察机在菲律宾海空域活动、\nF-35 战斗机在日本海空域活动、\nC-130 运输机在相关空域活动;\n里根号航母在南海活动、\n阿利·伯克级驱逐舰在东海活动、\n补给舰在菲律宾海活动。\n具体情况为：\n一、飞机\n1. F-15 战斗机从横须贺海军基地起飞赴西太平洋空域活动；\n2. P-8A 巡逻机从三泽空军基地起飞赴台湾周边空域活动\n3. C-130 运输机从关岛起飞赴菲律宾海空域活动\n二、舰船\n1. 里根号航母，20 日晚及 21 日前往南海应对自由航行任务；\n2. 阿利·伯克级驱逐舰，21 日凌晨位于东海。\n三、导弹\n1. 战斧巡航导弹，21 日 9 时 30 分在西太平洋空域进行实战化训练。"
    }
]

extractor = LLMEventExtractor()

print("=" * 70)
print(f"主体分类字段: {extractor.CATEGORY_NAMES}")
print("=" * 70)

for item in test_texts:
    print(f"\n{'=' * 70}")
    print(f"测试 sid={item['sid']}: {item['title']}")
    print(f"{'=' * 70}")

    result = extractor.extract_all(item['content'])

    print(f"\n抽取到 {result['total_events']} 个事件：")
    for event in result['events']:
        print(f"\n  事件 {event['event_id']}:")
        if event.get('time'):
            print(f"    时间: {event['time']}")
        if event.get('location'):
            print(f"    地点: {event['location']}")
        for cat in extractor.CATEGORY_NAMES:
            if event.get(cat):
                print(f"    {cat}: {event[cat]}")
        if event.get('action'):
            print(f"    行为: {event['action']}")
        if event.get('original_text'):
            print(f"    原文: {event['original_text'][:100]}...")

    # 保存结果到文件
    output_file = f"test_result_sid{item['sid']}.json"
    extractor.save_to_json(result, output_file)
