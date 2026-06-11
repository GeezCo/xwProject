"""
分类服务客户端

调用事件分类服务对subject进行多标签分类
"""

import requests
import json
import os
from typing import List, Dict, Any, Optional

# 分类服务地址
CLASSIFY_SERVICE_URL = "http://36.103.234.242:8514"

# 从config.json加载实体分类标签
_config_path = os.path.join(os.path.dirname(__file__), 'config.json')
if os.path.exists(_config_path):
    with open(_config_path, 'r', encoding='utf-8') as _f:
        _config = json.load(_f)
        CLASSIFY_LABELS = _config.get('entity_labels', ["人物", "组织", "部队", "军舰", "火炮", "战机", "武器", "装备"])
else:
    CLASSIFY_LABELS = ["人物", "组织", "部队", "军舰", "火炮", "战机", "武器", "装备"]


def classify_subject(subject: List[str]) -> List[str]:
    """
    对subject列表进行多标签分类

    Args:
        subject: 主体列表，如 ["拜登", "白宫", "B-52轰炸机"]

    Returns:
        List[str]: 分类标签列表（去重后），如 ["人物", "组织", "战机"]
    """
    if not subject:
        return []

    all_labels = []

    try:
        # 对每个subject项进行分类
        for item in subject:
            if not item or len(item.strip()) == 0:
                continue

            # 调用分类接口（拼接"其他"作为兜底类别）
            response = requests.post(
                f"{CLASSIFY_SERVICE_URL}/api/v1/classify",
                json={
                    "text": item,
                    "labels": CLASSIFY_LABELS + ["其他"]
                },
                timeout=5  # 5秒超时
            )

            if response.status_code == 200:
                result = response.json()
                label = result.get('label')
                if label and label != "其他":
                    all_labels.append(label)
            else:
                print(f"[分类失败] {item}: HTTP {response.status_code}")

    except Exception as e:
        print(f"[分类异常] {e}")

    # 去重并返回
    unique_labels = list(set(all_labels))
    return unique_labels


def classify_events(events: List[Dict]) -> List[Dict]:
    """
    为事件列表添加分类标签

    Args:
        events: 事件列表

    Returns:
        List[Dict]: 添加了labels字段的事件列表
    """
    for event in events:
        subject = event.get('subject', [])
        if subject:
            labels = classify_subject(subject)
            event['labels'] = labels
            print(f"[分类] subject={subject} -> labels={labels}")
        else:
            event['labels'] = []

    return events


def classify_single_item(item: str) -> Optional[str]:
    """
    对单个subject项进行分类

    Args:
        item: 单个主体项，如 "拜登"、"北约"

    Returns:
        Optional[str]: 分类标签，如 "人物"、"组织"，失败返回None
    """
    if not item or not item.strip():
        return None

    try:
        response = requests.post(
            f"{CLASSIFY_SERVICE_URL}/api/v1/classify",
            json={"text": item, "labels": CLASSIFY_LABELS + ["其他"]},
            timeout=5
        )
        if response.status_code == 200:
            label = response.json().get('label')
            if label and label != "其他":
                return label
    except Exception as e:
        print(f"[分类异常] {item}: {e}")

    return None


def extract_entities_from_events(events: List[Dict]) -> Dict:
    """
    从事件列表中提取并分类实体

    实体分类与预定义标签完全对齐：
    - 人物、组织、部队、军舰、火炮、战机、武器、装备

    Args:
        events: 事件列表（已包含labels字段）

    Returns:
        Dict: {"人物": [...], "组织": [...], "部队": [...], ...}
    """
    # 初始化各类实体集合
    entities = {label: set() for label in CLASSIFY_LABELS}

    for event in events:
        subjects = event.get('subject', [])

        for item in subjects:
            if not item:
                continue
            # 调用分类服务获取单个subject项的label
            item_label = classify_single_item(item)
            if item_label and item_label in entities:
                entities[item_label].add(item)

    # 转换为列表
    return {label: list(items) for label, items in entities.items()}


if __name__ == '__main__':
    # 测试
    test_subject = ["拜登", "白宫", "B-52轰炸机", "美国空军"]
    labels = classify_subject(test_subject)
    print(f"分类结果: {labels}")