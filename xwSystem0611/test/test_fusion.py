"""
融合接口全面测试
使用抽取完成的报文数据，测试多种组合的融合效果
"""
import json
import time
import sys
import io
import requests
from datetime import datetime

BACKEND_URL = "http://localhost:8081"
LOG_PATH = r"D:\项目开发\献微系统\测试\测试结果\fusion_log.txt"
RESULT_PATH = r"D:\项目开发\献微系统\测试\测试结果\fusion_test_result.json"

log_lines = []
def log(msg):
    log_lines.append(msg)
    with open(LOG_PATH, "w", encoding="utf-8") as _f:
        _f.write("\n".join(log_lines))
ENTITY_LABELS = ["人物", "组织", "部队", "军舰", "火炮", "战机", "武器", "装备"]

with open(r"D:\项目开发\献微系统\测试\测试数据集.json", "r", encoding="utf-8") as f:
    dataset = json.load(f)

# 定义融合测试用例
test_cases = [
    {"name": "2篇短文融合", "indices": [0, 1]},
    {"name": "2篇长文融合", "indices": [8, 9]},
    {"name": "3篇混合融合", "indices": [0, 4, 9]},
    {"name": "5篇批量融合", "indices": [0, 2, 4, 6, 8]},
]

results = []
anomalies = []

log(f"开始融合测试，共 {len(test_cases)} 组用例")
log("=" * 80)


def build_report_data(report):
    """构建融合请求中的单个report对象"""
    sid = report["sid"]

    # 获取抽取结果
    try:
        resp = requests.get(f"{BACKEND_URL}/extraction/result/{sid}", timeout=10)
        ext_data = resp.json().get("data")
    except:
        ext_data = None

    rd = {
        "id": sid,
        "title": report.get("title", ""),
        "content": report.get("content", ""),
        "times": report.get("times", ""),
        "type": report.get("type"),
    }

    if ext_data:
        extraction_result = {
            "events": ext_data.get("events", []),
            "entities": ext_data.get("entities", {}),
            "labels": ext_data.get("labels", []),
        }
        rd["extractionResult"] = extraction_result
    else:
        rd["extractionResult"] = None

    return rd


def validate_json_field(value, field_name, expected_type):
    """验证JSON字段"""
    if value is None:
        return False, f"{field_name}为null"
    if isinstance(value, str):
        try:
            parsed = json.loads(value)
            if not isinstance(parsed, expected_type):
                return False, f"{field_name}类型错误: 期望{expected_type.__name__}, 实际{type(parsed).__name__}"
            return True, parsed
        except json.JSONDecodeError:
            return False, f"{field_name}不是有效JSON: {value[:50]}"
    if isinstance(value, expected_type):
        return True, value
    return False, f"{field_name}类型错误: {type(value).__name__}"


for case_idx, case in enumerate(test_cases):
    name = case["name"]
    indices = case["indices"]
    reports = [dataset[i] for i in indices]
    sids = [r["sid"] for r in reports]

    log(f"\n[用例{case_idx+1}] {name} (SID={sids})")

    # 构建请求
    report_data_list = [build_report_data(r) for r in reports]
    request_body = {
        "reports": report_data_list,
        "fusionType": "standard",
        "customTitle": None,
    }

    # 调用融合接口
    start_time = time.time()
    try:
        resp = requests.post(
            f"{BACKEND_URL}/api/fusion/create",
            json=request_body,
            timeout=600
        )
        elapsed = round(time.time() - start_time, 1)
        resp_data = resp.json()
    except requests.Timeout:
        elapsed = round(time.time() - start_time, 1)
        anomalies.append({"case": name, "type": "超时", "detail": f"超过600秒"})
        results.append({"case": name, "sids": sids, "status": "TIMEOUT", "elapsed": elapsed})
        log(f"  [FAIL] 超时 ({elapsed}s)")
        continue
    except Exception as e:
        elapsed = round(time.time() - start_time, 1)
        anomalies.append({"case": name, "type": "请求异常", "detail": str(e)})
        results.append({"case": name, "sids": sids, "status": "ERROR", "elapsed": elapsed, "error": str(e)})
        log(f"  [FAIL] 异常: {e}")
        continue

    result_entry = {
        "case": name, "sids": sids, "elapsed": elapsed,
        "http_status": resp.status_code,
    }

    if resp_data.get("code") != 1:
        anomalies.append({"case": name, "type": "code!=1", "detail": resp_data.get("msg", "")})
        result_entry["status"] = "FAIL"
        result_entry["msg"] = resp_data.get("msg", "")
        results.append(result_entry)
        log(f"  [FAIL] code={resp_data.get('code')} msg={resp_data.get('msg')} ({elapsed}s)")
        continue

    data = resp_data.get("data", {})
    if not data:
        anomalies.append({"case": name, "type": "data为空", "detail": "响应data字段为空"})
        result_entry["status"] = "FAIL"
        results.append(result_entry)
        log(f"  [FAIL] data为空 ({elapsed}s)")
        continue

    result_entry["status"] = "OK"

    # 检查title
    title = data.get("title", "")
    result_entry["title"] = title
    if not title:
        anomalies.append({"case": name, "type": "标题为空", "detail": ""})

    # 检查summary
    summary = data.get("summary", "")
    result_entry["summary_len"] = len(summary)
    if not summary:
        anomalies.append({"case": name, "type": "摘要为空", "detail": ""})

    # 检查content
    content = data.get("content", "")
    result_entry["content_len"] = len(content)
    if not content:
        anomalies.append({"case": name, "type": "内容为空", "detail": ""})
    elif len(content) < 100:
        anomalies.append({"case": name, "type": "内容过短", "detail": f"仅{len(content)}字"})
    # 检查是否有Markdown结构
    if content and "##" not in content:
        anomalies.append({"case": name, "type": "缺少Markdown结构", "detail": "content中无##标题"})

    # 检查timeline
    ok, timeline = validate_json_field(data.get("timeline"), "timeline", list)
    if not ok:
        anomalies.append({"case": name, "type": "timeline异常", "detail": timeline})
        result_entry["timeline_count"] = 0
    else:
        result_entry["timeline_count"] = len(timeline)
        if len(timeline) == 0:
            anomalies.append({"case": name, "type": "时间线为空", "detail": "timeline数组长度=0"})

    # 检查entities
    ok, entities = validate_json_field(data.get("entities"), "entities", dict)
    if not ok:
        anomalies.append({"case": name, "type": "entities异常", "detail": entities})
        result_entry["entity_count"] = 0
    else:
        missing = [l for l in ENTITY_LABELS if l not in entities]
        if missing:
            anomalies.append({"case": name, "type": "实体类别缺失", "detail": f"缺少{missing}"})
        entity_count = sum(len(v) for v in entities.values() if isinstance(v, list))
        result_entry["entity_count"] = entity_count

    # 检查labels
    ok, labels = validate_json_field(data.get("labels"), "labels", list)
    if not ok:
        anomalies.append({"case": name, "type": "labels异常", "detail": labels})
        result_entry["labels"] = []
    else:
        result_entry["labels"] = labels
        if "其他" in labels:
            anomalies.append({"case": name, "type": "标签含其他", "detail": f"labels={labels}"})

    # 检查sourceIds
    ok, source_ids = validate_json_field(data.get("sourceIds"), "sourceIds", list)
    if ok:
        if set(source_ids) != set(sids):
            anomalies.append({"case": name, "type": "sourceIds不匹配", "detail": f"期望{sids} 实际{source_ids}"})

    log(f"  [OK] title={title[:30]} content={len(content)}字 timeline={result_entry.get('timeline_count',0)}条 entities={result_entry.get('entity_count',0)}个 ({elapsed}s)")

    # 保存融合报告并验证
    try:
        save_resp = requests.post(f"{BACKEND_URL}/api/fusion/save", json=data, timeout=10)
        save_data = save_resp.json()
        if save_data.get("code") == 1:
            saved = save_data.get("data", {})
            fusion_id = saved.get("fusionId")
            result_entry["saved_fusion_id"] = fusion_id
            log(f"  [OK] 保存成功 fusionId={fusion_id}")

            # 查询验证
            if fusion_id:
                detail_resp = requests.get(f"{BACKEND_URL}/api/fusion/detail/{fusion_id}", timeout=10)
                detail_data = detail_resp.json()
                if detail_data.get("code") == 1 and detail_data.get("data"):
                    log(f"  [OK] 查询验证通过")
                else:
                    anomalies.append({"case": name, "type": "保存后查询失败", "detail": f"fusionId={fusion_id}"})
                    log(f"  [WARN] 保存后查询失败")
        else:
            anomalies.append({"case": name, "type": "保存失败", "detail": save_data.get("msg", "")})
            log(f"  [WARN] 保存失败: {save_data.get('msg','')}")
    except Exception as e:
        anomalies.append({"case": name, "type": "保存异常", "detail": str(e)})
        log(f"  [WARN] 保存异常: {e}")

    results.append(result_entry)

    # 即时保存中间结果
    interim = {
        "test_time": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
        "progress": f"{case_idx+1}/{len(test_cases)}",
        "results": results,
        "anomalies": anomalies,
    }
    with open(RESULT_PATH, "w", encoding="utf-8") as _f:
        json.dump(interim, _f, ensure_ascii=False, indent=2)

# 保存结果
output = {
    "test_time": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
    "total_cases": len(test_cases),
    "results": results,
    "anomalies": anomalies,
    "summary": {
        "success": len([r for r in results if r.get("status") == "OK"]),
        "fail": len([r for r in results if r.get("status") != "OK"]),
        "total_anomalies": len(anomalies),
        "avg_elapsed": round(sum(r.get("elapsed", 0) for r in results) / max(len(results), 1), 1),
    },
}

output_path = RESULT_PATH
with open(output_path, "w", encoding="utf-8") as f:
    json.dump(output, f, ensure_ascii=False, indent=2)

log("\n" + "=" * 80)
log(f"测试完成: 成功={output['summary']['success']} 失败={output['summary']['fail']} 异常={output['summary']['total_anomalies']}")
log(f"平均耗时: {output['summary']['avg_elapsed']}s")
log(f"结果已保存: {output_path}")
if anomalies:
    log(f"\n异常列表:")
    for a in anomalies:
        log(f"  - [{a.get('case','')}] [{a['type']}] {a['detail']}")
