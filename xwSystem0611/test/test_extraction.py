"""
抽取接口全面测试
对测试数据集中的10条报文逐一调用抽取接口，检查结果完整性
"""
import json
import time
import requests
from datetime import datetime

BACKEND_URL = "http://localhost:8081"
ENTITY_LABELS = ["人物", "组织", "部队", "军舰", "火炮", "战机", "武器", "装备"]
EVENT_FIELDS = ["event_id", "time", "location", "subject", "action"]
LOG_PATH = r"D:\项目开发\献微系统\测试\测试结果\extraction_log.txt"
RESULT_PATH = r"D:\项目开发\献微系统\测试\测试结果\extraction_test_result.json"

log_lines = []

def log(msg):
    log_lines.append(msg)
    with open(LOG_PATH, "w", encoding="utf-8") as f:
        f.write("\n".join(log_lines))

with open(r"D:\项目开发\献微系统\测试\测试数据集.json", "r", encoding="utf-8") as f:
    dataset = json.load(f)

results = []
anomalies = []

log(f"开始抽取测试，共 {len(dataset)} 条报文")
log("=" * 80)

for i, report in enumerate(dataset):
    sid = report["sid"]
    title = report["title"]
    content_len = len(report.get("content", ""))
    log(f"\n[{i+1}/10] SID={sid} 长度={content_len}字 标题={title}")

    start_time = time.time()
    try:
        resp = requests.post(
            f"{BACKEND_URL}/extraction/extract",
            params={"originTextId": sid, "force": True},
            timeout=1200
        )
        elapsed = round(time.time() - start_time, 1)
        resp_data = resp.json()
    except requests.Timeout:
        elapsed = round(time.time() - start_time, 1)
        anomalies.append({"sid": sid, "type": "超时", "detail": f"超过1200秒"})
        results.append({"sid": sid, "title": title, "status": "TIMEOUT", "elapsed": elapsed})
        log(f"  [FAIL] 超时 ({elapsed}s)")
        continue
    except Exception as e:
        elapsed = round(time.time() - start_time, 1)
        anomalies.append({"sid": sid, "type": "请求异常", "detail": str(e)})
        results.append({"sid": sid, "title": title, "status": "ERROR", "elapsed": elapsed, "error": str(e)})
        log(f"  [FAIL] 异常: {e}")
        continue

    result_entry = {
        "sid": sid, "title": title, "content_len": content_len,
        "elapsed": elapsed, "http_status": resp.status_code
    }

    if resp_data.get("code") != 1:
        anomalies.append({"sid": sid, "type": "code!=1", "detail": resp_data.get("msg", "")})
        result_entry["status"] = "FAIL"
        result_entry["msg"] = resp_data.get("msg", "")
        results.append(result_entry)
        log(f"  [FAIL] code={resp_data.get('code')} msg={resp_data.get('msg')} ({elapsed}s)")
        continue

    data = resp_data.get("data", {})
    result_entry["status"] = "OK"

    # 检查events
    events = data.get("events", [])
    if isinstance(events, str):
        try:
            events = json.loads(events)
        except:
            events = []
    if not events and data.get("eventsJson"):
        try:
            ej = json.loads(data["eventsJson"])
            events = ej.get("events", []) if isinstance(ej, dict) else ej
        except:
            pass

    total_events = data.get("totalEvents", 0)
    result_entry["total_events"] = total_events
    result_entry["events_count"] = len(events) if events else 0

    if not events or len(events) == 0:
        anomalies.append({"sid": sid, "type": "无事件", "detail": f"totalEvents={total_events}"})
        log(f"  [WARN] 无事件 totalEvents={total_events} ({elapsed}s)")
    else:
        for j, event in enumerate(events):
            missing = [f for f in EVENT_FIELDS if f not in event]
            if missing:
                anomalies.append({"sid": sid, "type": "事件字段缺失", "detail": f"event[{j}]缺少{missing}"})

        if total_events != len(events):
            anomalies.append({"sid": sid, "type": "事件数不一致", "detail": f"totalEvents={total_events} 实际={len(events)}"})

    # 检查labels
    labels = data.get("labels", [])
    if isinstance(labels, str):
        try:
            labels = json.loads(labels)
        except:
            labels = []
    result_entry["labels"] = labels
    result_entry["labels_count"] = len(labels) if labels else 0

    if not labels:
        anomalies.append({"sid": sid, "type": "无标签", "detail": "labels为空"})
    if labels and "其他" in labels:
        anomalies.append({"sid": sid, "type": "标签含其他", "detail": f"labels={labels}"})

    # 检查entities
    entities = data.get("entities", {})
    if isinstance(entities, str):
        try:
            entities = json.loads(entities)
        except:
            entities = {}
    result_entry["entities"] = entities

    if not entities:
        anomalies.append({"sid": sid, "type": "无实体", "detail": "entities为空"})
    else:
        missing_labels = [l for l in ENTITY_LABELS if l not in entities]
        if missing_labels:
            anomalies.append({"sid": sid, "type": "实体类别缺失", "detail": f"缺少{missing_labels}"})

    entity_count = sum(len(v) for v in entities.values() if isinstance(v, list)) if entities else 0
    result_entry["entity_count"] = entity_count

    results.append(result_entry)
    log(f"  [OK] 事件={len(events)} 标签={labels} 实体={entity_count}个 ({elapsed}s)")

    # 验证持久化
    try:
        query_resp = requests.get(f"{BACKEND_URL}/extraction/result/{sid}", timeout=10)
        query_data = query_resp.json()
        if query_data.get("code") != 1:
            anomalies.append({"sid": sid, "type": "查询失败", "detail": f"code={query_data.get('code')}"})
            log(f"  [WARN] 查询验证失败")
        elif not query_data.get("data"):
            anomalies.append({"sid": sid, "type": "查询无数据", "detail": "data为空"})
            log(f"  [WARN] 查询返回空数据")
        else:
            log(f"  [OK] 持久化验证通过")
    except Exception as e:
        anomalies.append({"sid": sid, "type": "查询异常", "detail": str(e)})

    # 每条完成后即时保存结果（防止中途崩溃丢失）
    interim = {
        "test_time": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
        "progress": f"{i+1}/{len(dataset)}",
        "results": results,
        "anomalies": anomalies,
    }
    with open(RESULT_PATH, "w", encoding="utf-8") as f:
        json.dump(interim, f, ensure_ascii=False, indent=2)

# 最终保存
output = {
    "test_time": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
    "total_reports": len(dataset),
    "results": results,
    "anomalies": anomalies,
    "summary": {
        "success": len([r for r in results if r.get("status") == "OK"]),
        "fail": len([r for r in results if r.get("status") != "OK"]),
        "total_anomalies": len(anomalies),
        "avg_elapsed": round(sum(r.get("elapsed", 0) for r in results) / max(len(results), 1), 1)
    }
}
with open(RESULT_PATH, "w", encoding="utf-8") as f:
    json.dump(output, f, ensure_ascii=False, indent=2)

log("\n" + "=" * 80)
log(f"测试完成: 成功={output['summary']['success']} 失败={output['summary']['fail']} 异常={output['summary']['total_anomalies']}")
log(f"平均耗时: {output['summary']['avg_elapsed']}s")
if anomalies:
    log(f"\n异常列表:")
    for a in anomalies:
        log(f"  - SID={a['sid']} [{a['type']}] {a['detail']}")
