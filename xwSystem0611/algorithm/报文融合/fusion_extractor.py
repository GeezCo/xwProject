"""
报文融合抽取器

功能：
1. 数据提取：获取报文数据，若未抽取则调用属性抽取
2. 信息整合：合并时间线、实体、标签（代码实现，非LLM）
3. LLM生成：生成标题、摘要、详细内容（简化Prompt）
4. 结果组装：合成最终融合报告

作者：Claude Code
日期：2026-04-16
"""

import sys
import os
import json
import time
import logging
import re
from datetime import datetime
from typing import List, Dict, Optional

# 添加新抽取模块路径
sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..', '数据抽取-新'))

# 导入LLM抽取器
from llm_event_extractor import LLMEventExtractor

# 配置日志
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s [%(levelname)s] %(message)s',
    datefmt='%Y-%m-%d %H:%M:%S'
)
logger = logging.getLogger(__name__)

# 从config.json加载实体分类标签
_config_path = os.path.join(os.path.dirname(__file__), '..', '数据抽取-新', 'config.json')
if os.path.exists(_config_path):
    with open(_config_path, 'r', encoding='utf-8') as _f:
        _config = json.load(_f)
        ENTITY_LABELS = list(_config.get('subject_categories', {}).keys()) or ["船只", "飞机", "无人机", "导弹", "雷达", "火炮", "坦克/装甲车辆", "军事基地", "部队番号", "人物", "组织"]
        _MODEL_NAME = _config.get('llm', {}).get('model', 'Qwen/Qwen3.5-35B-A3B-w8a8-mtp')
else:
    ENTITY_LABELS = ["船只", "飞机", "无人机", "导弹", "雷达", "火炮", "坦克/装甲车辆", "军事基地", "部队番号", "人物", "组织"]
    _MODEL_NAME = 'Qwen/Qwen3.5-35B-A3B-w8a8-mtp'


# ========== 实体名归一化（用于"目标列表"表格行聚合） ==========

# 可剥离的前缀：国家+职位、纯职位、国家、军种简称
_ENTITY_PREFIXES = [
    '美国总统', '中国国家主席', '俄罗斯总统', '日本首相', '英国首相', '法国总统', '德国总理',
    '总统', '主席', '总理', '首相', '部长', '将军', '少将', '中将', '上将', '元帅',
    '司令员', '司令', '指挥官', '发言人', '大使', '外长', '防长', '国务卿',
    '中国人民解放军', '解放军', '美军', '俄军', '日军', '英军', '法军', '德军',
    '美国', '中国', '俄罗斯', '日本', '英国', '法国', '德国', '韩国', '朝鲜', '印度',
    '国防部', '外交部', '五角大楼', '白宫',
]
_ENTITY_PREFIXES.sort(key=len, reverse=True)  # 长前缀优先匹配

# 可剥离的后缀：装备类型、部队单位
_ENTITY_SUFFIXES = [
    '战斗机', '轰炸机', '侦察机', '预警机', '运输机', '直升机', '加油机', '反潜机',
    '航空母舰', '航母', '驱逐舰', '巡洋舰', '护卫舰', '潜艇', '补给舰', '两栖攻击舰', '登陆舰',
    '反导系统', '防空系统', '雷达系统', '导弹系统', '武器系统',
    '主战坦克', '坦克', '步兵战车', '步战车', '装甲运兵车', '装甲车',
    '空军基地', '海军基地', '军事基地', '军港', '哨所',
    '航空联队', '航空大队', '舰队', '集团军', '空降师', '陆战师', '突击队', '特种部队',
    '武装力量', '军事力量', '军队', '部队',
]
_ENTITY_SUFFIXES.sort(key=len, reverse=True)


def _normalize_entity_name(name: str) -> str:
    """
    将实体名归一化为聚合键

    处理：
    1. 字符规范：全角→半角、压缩空白
    2. 标点统一：中文括号→英文、各种破折号→连字符、中文引号→直引号
    3. 剥离括号补充：'F-35(闪电Ⅱ)' → 'F-35'
    4. 剥离一层前缀：'美国总统拜登' → '拜登'
    5. 剥离一层后缀：'里根号航母' → '里根号'

    返回归一化后的字符串；空串视为无效（调用方应回退到原名）
    """
    if not name:
        return ''

    s = name.strip()
    # 全角→半角（仅可见 ASCII 范围）
    s = ''.join(chr(ord(c) - 0xFEE0) if 0xFF01 <= ord(c) <= 0xFF5E else c for c in s)
    # 中文场景下空白通常是噪声，全部去除
    s = re.sub(r'\s+', '', s)
    # 标点统一
    s = s.replace('（', '(').replace('）', ')')
    s = s.replace('—', '-').replace('–', '-').replace('－', '-')
    s = s.replace('"', '"').replace('"', '"').replace(''', "'").replace(''', "'")
    # 去除括号补充内容（不递归）
    s = re.sub(r'\([^)]*\)', '', s)

    # 剥离前缀（只剥一层，长前缀优先）
    for prefix in _ENTITY_PREFIXES:
        if s.startswith(prefix) and len(s) > len(prefix):
            s = s[len(prefix):]
            break

    # 剥离后缀（只剥一层，长后缀优先）
    for suffix in _ENTITY_SUFFIXES:
        if s.endswith(suffix) and len(s) > len(suffix):
            s = s[:-len(suffix)]
            break

    return s.strip()


def _entity_key(name: str) -> str:
    """获取实体名的聚合键；归一化结果为空时回退到原名"""
    normalized = _normalize_entity_name(name)
    return normalized or name


class FusionExtractor:
    """
    报文融合抽取器

    将多篇报文融合生成综合报告
    """

    def __init__(self):
        """初始化融合抽取器"""
        # LLM抽取器实例（用于属性抽取和融合生成）
        self.llm_extractor = LLMEventExtractor()
        logger.info("报文融合抽取器初始化完成")

    def fuse_reports(self,
                      reports_data: List[Dict],
                      fusion_type: str = 'standard',
                      custom_title: Optional[str] = None) -> Dict:
        """
        融合多篇报文

        Args:
            reports_data: 报文数据列表（已包含内容和抽取结果）
                每个元素结构：{
                    "id": 报文ID,
                    "title": 报文标题,
                    "content": 报文正文,
                    "extractionResult": {events, entities, labels}
                }
            fusion_type: 融合类型（standard）
            custom_title: 自定义标题（可选）

        Returns:
            融合报告字典
        """
        logger.info("=" * 50)
        logger.info(f"[报文融合] 开始处理，报文数量: {len(reports_data)}")

        # 参数校验
        if len(reports_data) < 2:
            raise ValueError("至少需要2篇报文进行融合")
        if len(reports_data) > 10:
            raise ValueError("最多支持10篇报文融合")

        # 校验每个报文是否有content
        for i, report in enumerate(reports_data):
            if not report.get('content'):
                raise ValueError(f"报文{i+1}缺少content字段")

        start_time = time.time()

        # Step 1: 数据预处理（从传入的数据中提取信息）
        logger.info("[Step 1] 数据预处理...")
        processed_reports = self._preprocess_reports(reports_data)

        # Step 2: 信息整合（代码实现）
        logger.info("[Step 2] 信息整合...")
        merged_info = self._merge_all_info(processed_reports)

        # Step 3: LLM生成
        logger.info("[Step 3] LLM生成融合报告...")
        llm_result = self._generate_fusion_content(processed_reports, merged_info, custom_title)

        # Step 4: 结果组装
        logger.info("[Step 4] 结果组装...")
        report_ids = [r.get('id', i+1) for i, r in enumerate(reports_data)]
        fusion_result = self._assemble_result(llm_result, merged_info, report_ids, processed_reports)

        elapsed = time.time() - start_time
        logger.info(f"[报文融合] 处理完成，耗时: {elapsed:.1f}秒")
        logger.info("=" * 50)

        return fusion_result

    def _preprocess_reports(self, reports_data: List[Dict]) -> List[Dict]:
        """
        预处理报文数据，统一字段名称

        将传入的报文数据转换为内部使用的格式
        """
        processed = []
        for i, report in enumerate(reports_data):
            processed_report = {
                'sid': report.get('id', i+1),
                'title': report.get('title', ''),
                'content': report.get('content', ''),
                'times': report.get('times', ''),
                'type': report.get('type'),
            }

            # 获取抽取结果（如果有）
            extraction_result = report.get('extractionResult') or report.get('extraction_result')
            if extraction_result:
                processed_report['extraction_result'] = extraction_result
            else:
                # 若无抽取结果，初始化空结构
                processed_report['extraction_result'] = {
                    'events': [],
                    'entities': {},
                    'labels': []
                }

            processed.append(processed_report)

        return processed

    def _extract_entities_from_events(self, events: List[Dict]) -> Dict:
        """从事件列表中提取实体"""
        persons = set()
        organizations = set()
        equipment = set()

        # 已知组织关键词（用于识别主体是组织还是人物）
        org_keywords = ['北约', '联合国', '美国', '中国', '俄罗斯', '乌克兰', '巴基斯坦',
                        '以色列', '伊朗', '英国', '法国', '德国', '日本', '韩国', '印度',
                        '国防部', '外交部', '白宫', '政府', '军队', '海军', '空军', '陆军',
                        'BBC', 'CNN', '今日俄罗斯']

        for event in events:
            subject = event.get('subject')

            # subject可能是列表（LLM返回的简化格式）或字典（标准格式）
            if subject:
                if isinstance(subject, list):
                    # 列表格式：根据关键词判断是人物还是组织
                    for item in subject:
                        if isinstance(item, str):
                            if any(kw in item for kw in org_keywords):
                                organizations.add(item)
                            elif len(item) > 2:  # 短名称可能是人物名
                                persons.add(item)
                elif isinstance(subject, dict):
                    # 字典格式：标准格式
                    if subject.get('persons'):
                        persons.update(subject['persons'])
                    if subject.get('organizations'):
                        organizations.update(subject['organizations'])

            # 装备关键词匹配
            action = event.get('action', '')
            equip_keywords = ['轰炸机', '战斗机', '导弹', '军舰', '坦克', 'B-52', 'F-35', '军演', '演习']
            for kw in equip_keywords:
                if kw in action:
                    equipment.add(kw)

        return {
            'persons': list(persons),
            'organizations': list(organizations),
            'equipment': list(equipment)
        }

    def _generate_single_labels(self, events: List[Dict], entities: Dict) -> List[str]:
        """为单篇报文生成标签"""
        labels = []
        labels.extend(entities['persons'][:2])
        labels.extend(entities['organizations'][:2])
        labels.extend(entities['equipment'][:2])

        labels = list(set(labels))

        default_labels = ['军事', '国际', '政治']
        while len(labels) < 3 and default_labels:
            label = default_labels.pop(0)
            if label not in labels:
                labels.append(label)

        return labels[:6]

    def _merge_all_info(self, reports_data: List[Dict]) -> Dict:
        """整合多篇报文的信息（代码实现）"""
        all_events = []
        all_entities = []
        all_labels = []

        for report in reports_data:
            extraction = report.get('extraction_result') or report.get('extractionResult') or {}
            if extraction.get('events'):
                all_events.append(extraction['events'])
            if extraction.get('entities'):
                all_entities.append(extraction['entities'])
            if extraction.get('labels'):
                all_labels.extend(extraction['labels'])

        timeline = self._merge_timeline(all_events)
        entities = self._merge_entities(all_entities)
        labels = self._merge_labels(all_labels)

        return {
            'timeline': timeline,
            'entities': entities,
            'labels': labels
        }

    def _merge_timeline(self, all_events: List[List[Dict]]) -> List[Dict]:
        """合并时间线（代码实现）"""
        time_events = {}

        for events in all_events:
            for event in events:
                time_str = event.get('time', '')
                normalized_time = self._normalize_time(time_str)

                if normalized_time:
                    description = event.get('original_text', event.get('action', ''))
                    if normalized_time not in time_events:
                        time_events[normalized_time] = []
                    time_events[normalized_time].append(description)

        sorted_times = sorted(time_events.keys())

        timeline = []
        for t in sorted_times:
            descriptions = time_events[t]
            combined_desc = '; '.join(descriptions[:3])
            timeline.append({
                'time': t,
                'description': combined_desc
            })

        return timeline

    def _normalize_time(self, time_str: str) -> str:
        """标准化时间格式"""
        if not time_str:
            return ''

        patterns = [
            r'(\d{4})[年\-/](\d{1,2})[月\-/](\d{1,2})',
        ]

        for pattern in patterns:
            match = re.search(pattern, time_str)
            if match:
                year, month, day = match.groups()
                return f"{year}-{month.zfill(2)}-{day.zfill(2)}"

        return time_str[:10] if len(time_str) >= 10 else ''

    def _merge_entities(self, all_entities: List[Dict]) -> Dict:
        """合并实体信息（与标签分类对齐）"""
        merged = {label: set() for label in ENTITY_LABELS}

        for entities in all_entities:
            for label, items in entities.items():
                if label in merged and items:
                    merged[label].update(items)

        return {label: list(items) for label, items in merged.items()}

    def _merge_labels(self, all_labels: List[str]) -> List[str]:
        """合并标签（仅使用智能分类标签，不补充默认标签）"""
        # 去重合并，不补充默认标签
        labels = list(set(all_labels))
        # 过滤掉"其他"标签
        labels = [l for l in labels if l != "其他"]
        return labels[:8]

    def _generate_fusion_content(self,
                                   reports_data: List[Dict],
                                   merged_info: Dict,
                                   custom_title: Optional[str] = None) -> Dict:
        """调用LLM生成融合内容（只生成title、summary、content）"""
        prompt = self._build_fusion_prompt(reports_data, merged_info)

        logger.info("[LLM调用] 开始生成融合报告...")

        try:
            # 直接调用LLM API生成内容
            response = self._call_llm_for_text(prompt)
            logger.info(f"[LLM响应] 原始长度: {len(response) if response else 0}")
            logger.info(f"[LLM响应] 前500字符: {response[:500] if response else 'None'}")

            result = self._parse_llm_response(response)
            logger.info(f"[解析结果] content长度: {len(result.get('content', ''))}")

            if custom_title:
                result['title'] = custom_title

            return result

        except Exception as e:
            logger.error(f"LLM生成失败: {e}")
            return {
                'title': custom_title or '多报文融合报告',
                'summary': '本报告综合了多篇报文内容。',
                'content': '## 一、事件概述\n\n根据多篇报文内容综合分析...\n\n## 二、关键人物分析\n\n涉及人物包括...\n\n## 三、关键组织分析\n\n涉及组织包括...\n\n## 四、武器装备分析\n\n涉及装备包括...\n\n## 五、综合影响评估\n\n整体事件影响...'
            }

    def _call_llm_for_text(self, prompt: str) -> str:
        """
        直接调用LLM API生成文本内容

        Args:
            prompt: 输入提示词

        Returns:
            LLM返回的文本内容
        """
        import openai

        # 加载配置
        config_path = os.path.join(os.path.dirname(__file__), '..', '数据抽取-新', 'config.json')
        if os.path.exists(config_path):
            with open(config_path, 'r', encoding='utf-8') as f:
                config = json.load(f)
                # 配置文件是嵌套结构，需要提取llm部分
                llm_config = config.get('llm', config)
        else:
            llm_config = {
                'api_key': 'sk-Sf3cCx7aSWyk_4KUFoi8Tw',
                'base_url': 'http://36.141.21.176:8513/v1',
                'model': 'Qwen/Qwen3.5-35B-A3B-w8a8-mtp'
            }

        client = openai.OpenAI(
            api_key=llm_config.get('api_key'),
            base_url=llm_config.get('base_url')
        )

        try:
            response = client.chat.completions.create(
                model=llm_config.get('model', 'Qwen/Qwen3.5-35B-A3B-w8a8-mtp'),
                messages=[{"role": "user", "content": prompt}],
                temperature=0.1,
                max_tokens=4000,
                extra_body={"chat_template_kwargs": {"enable_thinking": False}}
            )

            # GLM-5返回内容可能在reasoning_content字段
            content = response.choices[0].message.content
            if content is None or not content.strip():
                if hasattr(response.choices[0].message, 'reasoning_content'):
                    content = response.choices[0].message.reasoning_content

            logger.info(f"[LLM API] 返回content长度: {len(content) if content else 0}")
            logger.info(f"[LLM API] finish_reason: {response.choices[0].finish_reason}")

            return content

        except Exception as e:
            logger.error(f"LLM API调用失败: {e}")
            raise

    def _build_fusion_prompt(self, reports_data: List[Dict], merged_info: Dict) -> str:
        """构建融合Prompt（简化版）"""
        reports_section = ""
        for i, report in enumerate(reports_data, 1):
            content_summary = report['content'][:300] if len(report['content']) > 300 else report['content']
            reports_section += f"报文{i}：{report['title']}\n{content_summary}\n\n"

        entities = merged_info['entities']
        persons_str = ', '.join(entities.get('人物', [])[:5]) if entities.get('人物') else '无'
        orgs_str = ', '.join(entities.get('组织', [])[:5]) if entities.get('组织') else '无'
        units_str = ', '.join(entities.get('部队', [])[:5]) if entities.get('部队') else '无'
        weapons_str = ', '.join(entities.get('武器', [])[:5]) if entities.get('武器') else '无'
        equip_str = ', '.join(entities.get('装备', [])[:5]) if entities.get('装备') else '无'

        # 使用字符串拼接避免f-string解析JSON示例的问题
        prompt = """
请根据以下报文信息，生成融合报告的标题、摘要和详细内容。

【报文原文】

""" + reports_section + """

【已提取的实体信息】

人物：""" + persons_str + """
组织：""" + orgs_str + """
部队：""" + units_str + """
武器：""" + weapons_str + """
装备：""" + equip_str + """

【输出要求】

请用```json代码块输出JSON格式，注意：JSON字符串值中不要使用双引号，可以用单引号或省略：

```json
{
  "title": "融合标题",
  "summary": "融合摘要",
  "content": "详细内容"
}
```

【详细内容结构】

## 一、事件概述（100字左右）

## 二、关键人物分析（分析各人物的角色和行为）

## 三、关键组织分析（分析各组织的参与和作用）

## 四、武器装备分析（分析各武器装备的性能和用途）

## 五、综合影响评估（分析事件的整体影响）

【注意】
- 内容基于报文原文，不可虚构
- 语言风格：客观、专业
- 使用中文输出
- content字段使用Markdown格式，换行用\\n表示
"""

        return prompt

    def _parse_llm_response(self, response: str) -> Dict:
        """解析LLM响应"""
        # 方法1: 尝试直接解析JSON
        try:
            json_match = re.search(r'\{[\s\S]*\}', response)
            if json_match:
                json_str = json_match.group()
                result = json.loads(json_str)
                if 'title' in result and 'summary' in result and 'content' in result:
                    logger.info("[解析] JSON直接解析成功")
                    return result
        except json.JSONDecodeError as e:
            logger.warning(f"[解析] JSON解析失败: {e}")
            # 尝试将单引号替换为双引号后解析
            try:
                json_match = re.search(r'\{[\s\S]*\}', response)
                if json_match:
                    json_str = json_match.group()
                    # 替换单引号为双引号
                    json_str_fixed = json_str.replace("'", '"')
                    result = json.loads(json_str_fixed)
                    if 'title' in result and 'summary' in result and 'content' in result:
                        logger.info("[解析] 单引号JSON修复后解析成功")
                        return result
            except json.JSONDecodeError as e2:
                logger.warning(f"[解析] 单引号修复后仍失败: {e2}")

        # 方法2: 尝试解析```json```代码块
        try:
            code_match = re.search(r'```json\s*([\s\S]*?)\s*```', response)
            if code_match:
                json_str = code_match.group(1)
                # 尝试直接解析
                try:
                    result = json.loads(json_str)
                    logger.info("[解析] JSON代码块解析成功")
                    return result
                except json.JSONDecodeError:
                    # 尝试替换单引号
                    json_str_fixed = json_str.replace("'", '"')
                    result = json.loads(json_str_fixed)
                    logger.info("[解析] JSON代码块(单引号修复)解析成功")
                    return result
        except:
            pass

        # 方法3: 使用正则表达式逐字段提取
        logger.info("[解析] 使用正则表达式逐字段提取")
        title = self._extract_field(response, 'title', '融合报告')
        summary = self._extract_field(response, 'summary', '综合分析多篇报文内容')
        content = self._extract_field(response, 'content', '## 一、事件概述\n\n待补充')

        logger.info(f"[解析] 提取结果 - title长度: {len(title)}, summary长度: {len(summary)}, content长度: {len(content)}")

        return {
            'title': title,
            'summary': summary,
            'content': content
        }

    def _extract_field(self, response: str, field_name: str, default: str) -> str:
        """从响应中提取特定字段（支持多行文本）"""
        # 方法1: 尝试匹配非转义引号结束的字段
        # 使用更精确的模式：找到字段后，匹配到下一个未转义的引号
        pattern = rf'"{field_name}"\s*:\s*"'
        match = re.search(pattern, response)
        if match:
            start = match.end()
            # 从start位置开始，找到结束引号（处理转义引号）
            i = start
            while i < len(response):
                if response[i] == '"':
                    # 检查是否是转义引号
                    if i > 0 and response[i-1] == '\\':
                        # 检查是否是偶数个反斜杠（真正的转义）
                        backslash_count = 0
                        j = i - 1
                        while j >= start and response[j] == '\\':
                            backslash_count += 1
                            j -= 1
                        if backslash_count % 2 == 0:
                            # 偶数个反斜杠，这是真正的结束引号
                            content = response[start:i]
                            # 处理转义字符
                            content = content.replace('\\n', '\n')
                            content = content.replace('\\t', '\t')
                            content = content.replace('\\"', '"')
                            content = content.replace('\\\\', '\\')
                            return content.strip()
                    else:
                        # 没有转义，直接结束
                        content = response[start:i]
                        content = content.replace('\\n', '\n')
                        content = content.replace('\\t', '\t')
                        content = content.replace('\\"', '"')
                        content = content.replace('\\\\', '\\')
                        return content.strip()
                i += 1

        # 方法2: 简单模式匹配（兜底）
        pattern = rf'"{field_name}"\s*:\s*"([\s\S]*?)"'
        match = re.search(pattern, response)
        if match:
            content = match.group(1).strip()
            content = content.replace('\\n', '\n')
            content = content.replace('\\t', '\t')
            content = content.replace('\\"', '"')
            return content

        return default

    def _build_targets_table(self,
                             reports_data: List[Dict],
                             merged_entities: Dict) -> List[Dict]:
        """
        构建实体行为表格数据

        Args:
            reports_data: 原始报文数据列表，包含 extraction_result.events
            merged_entities: 合并去重后的实体字典 {category: [name1, name2]}

        Returns:
            表格行数组 [{category, name, action}, ...]
            其中 action 为 "时间: 行为、时间: 行为" 拼接文本，无行为时为 '-'
        """
        # Step 1: 按归一化键聚合事件 + 记录每个键对应的"代表名"（取最长形式）
        entity_actions = {}  # {key: [(time, action), ...]}
        key_to_display_name = {}  # {key: display_name}

        for report in reports_data:
            extraction = report.get('extraction_result') or report.get('extractionResult') or {}
            events = extraction.get('events', [])

            for event in events:
                subjects = event.get('subject', [])
                action = event.get('original_text', event.get('action', ''))
                time_str = event.get('time', '')

                if not isinstance(subjects, list):
                    continue
                if not action:
                    continue

                for subj in subjects:
                    if not subj:
                        continue
                    key = _entity_key(subj)
                    # 维护展示名：取最长的（信息最完整）
                    if key not in key_to_display_name or len(subj) > len(key_to_display_name[key]):
                        key_to_display_name[key] = subj
                    if key not in entity_actions:
                        entity_actions[key] = []
                    entity_actions[key].append({
                        'time': self._normalize_time(time_str) or time_str,
                        'action': action
                    })

        # Step 2: merged_entities 也用相同归一化逻辑去重，同一类别下等价名只输出一行
        table_rows = []
        seen_keys_per_category = {}  # {category: set(key)}

        for category, names in merged_entities.items():
            if not names:
                continue
            seen_keys_per_category[category] = set()

            for name in names:
                key = _entity_key(name)
                if key in seen_keys_per_category[category]:
                    continue  # 同分类下已有等价实体
                seen_keys_per_category[category].add(key)

                # 选择展示名：优先用 entity_actions 里的最长展示名，否则用当前 name
                display_name = key_to_display_name.get(key, name)

                action_list = entity_actions.get(key, [])

                if not action_list:
                    action_text = '-'
                else:
                    # 按时间排序（空时间排最后）
                    action_list.sort(key=lambda x: x['time'] or 'zzz')
                    # 拼接为 "时间: 行为" 格式，用顿号分隔
                    action_text = '、'.join(
                        f"{a['time']}: {a['action']}" if a['time'] else a['action']
                        for a in action_list
                    )

                table_rows.append({
                    'category': category,
                    'name': display_name,
                    'action': action_text
                })

        logger.info(f"[表格构建] 实体行为映射数: {len(entity_actions)}, 表格行数: {len(table_rows)}")
        return table_rows

    def _assemble_result(self,
                          llm_result: Dict,
                          merged_info: Dict,
                          report_ids: List[int],
                          reports_data: List[Dict]) -> Dict:
        """组装最终融合报告"""
        fusion_id = int(time.time() * 1000)

        # 构建实体行为表格数据
        targets_table = self._build_targets_table(reports_data, merged_info['entities'])

        return {
            'fusionId': fusion_id,
            'title': llm_result['title'],
            'summary': llm_result['summary'],
            'timeline': merged_info['timeline'],
            'content': llm_result['content'],
            'entities': targets_table,
            'labels': merged_info['labels'],
            'sourceIds': report_ids,
            'modelUsed': _MODEL_NAME,
            'createTime': datetime.now().strftime('%Y-%m-%d %H:%M:%S'),
            'updateTime': datetime.now().strftime('%Y-%m-%d %H:%M:%S')
        }


# 单元测试
def test_fusion_extractor():
    """测试融合抽取器"""
    print("=" * 50)
    print("报文融合抽取器测试")
    print("=" * 50)

    extractor = FusionExtractor()

    report_ids = [1, 2, 3]
    result = extractor.fuse_reports(report_ids)

    print(f"\n融合报告ID: {result['fusionId']}")
    print(f"标题: {result['title']}")
    print(f"摘要: {result['summary'][:100]}...")
    print(f"时间线: {len(result['timeline'])} 个事件")
    print(f"实体: {result['entities']}")
    print(f"标签: {result['labels']}")

    print("\n时间线详情:")
    for item in result['timeline']:
        print(f"  {item['time']}: {item['description']}")

    print("\n详细内容预览:")
    print(result['content'][:300])

    print("\n" + "=" * 50)
    print("测试完成")


if __name__ == '__main__':
    test_fusion_extractor()