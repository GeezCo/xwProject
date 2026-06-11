"""
基于LLM的事件抽取算法

功能说明：
1. 读取txt文件的文本内容
2. 按段落分割文本
3. 调用大模型进行事件抽取
4. 抽取原子化事件，每个事件包含：
   - 时间（time）
   - 地点（location）
   - 多个主体分类字段（从config.json读取，如船只、飞机、导弹等）
   - 行为（action）
   - 原文句子（original_text）
5. 合并去重后输出结构化JSON
"""

import json
import re
import os
from datetime import datetime
from concurrent.futures import ThreadPoolExecutor, as_completed
from typing import List, Dict, Any, Optional
from openai import OpenAI


def load_config() -> Dict[str, Any]:
    current_dir = os.path.dirname(os.path.abspath(__file__))
    config_path = os.path.join(current_dir, 'config.json')

    default_config = {
        "llm": {
            "base_url": "https://llmapi.paratera.com",
            "model": "GLM-5",
            "api_key": "sk-Sf3cCx7aSWyk_4KUFoi8Tw",
            "temperature": 0.1,
            "max_tokens": 16000
        },
        "extraction": {
            "min_paragraph_length": 20,
            "retry_count": 3,
            "retry_delay": 2
        },
        "subject_categories": {
            "船只": {
                "description": "舰船类：航母、驱逐舰、护卫舰、巡洋舰、潜艇、补给舰等",
                "examples": ["里根号航母", "提康德罗加级巡洋舰"]
            },
            "飞机": {
                "description": "军用/民用飞机：战斗机、轰炸机、预警机、无人机等",
                "examples": ["F-35", "B-52轰炸机"]
            },
            "导弹": {
                "description": "各类导弹及反导系统",
                "examples": ["萨德反导系统", "东风-41"]
            },
            "雷达": {
                "description": "雷达及电子设备",
                "examples": ["AN/TPY-2雷达", "宙斯盾系统"]
            },
            "军事基地": {
                "description": "军事设施：空军基地、海军基地、军港等",
                "examples": ["嘉手纳空军基地", "横须贺海军基地"]
            },
            "部队番号": {
                "description": "部队编制单位：舰队、集团军、师旅团营等",
                "examples": ["第七舰队", "第101空降师"]
            },
            "人物": {
                "description": "具体人物：领导人、军事指挥官、政治人物等",
                "examples": ["拜登", "普京"]
            },
            "组织": {
                "description": "组织机构：国际组织、政府机构、军事联盟等",
                "examples": ["北约", "五角大楼"]
            }
        }
    }

    if os.path.exists(config_path):
        try:
            with open(config_path, 'r', encoding='utf-8') as f:
                user_config = json.load(f)
            for key in user_config:
                if key == 'subject_categories':
                    # subject_categories 完全替换，不合并
                    default_config[key] = user_config[key]
                elif key in default_config and isinstance(default_config[key], dict):
                    default_config[key].update(user_config[key])
                else:
                    default_config[key] = user_config[key]
        except Exception as e:
            print(f"加载配置文件失败: {e}")

    return default_config


CONFIG = load_config()


def build_system_prompt(subject_categories: Dict[str, Dict]) -> str:
    """根据配置中的主体分类动态生成系统提示词"""
    category_names = list(subject_categories.keys())

    # 构建主体分类说明
    category_lines = []
    for name, info in subject_categories.items():
        desc = info.get('description', '')
        examples = info.get('examples', [])
        category_lines.append(f'- {name}: {desc}\n  例如：{json.dumps(examples, ensure_ascii=False)}')

    category_block = '\n'.join(category_lines)

    # 构建示例输出中的分类字段
    example_fields = []
    example_fields.append('      "time": "时间"')
    example_fields.append('      "location": ["地点1", "地点2"]')
    for name in category_names:
        example_fields.append(f'      "{name}": ["实体1", "实体2"]')
    example_fields.append('      "action": "行为描述"')
    example_fields.append('      "original_text": "原文句子"')
    format_block = ',\n'.join(example_fields)

    prompt = f"""你是一个事件抽取专家。请从给定文本中抽取所有原子化事件。

每个事件包含以下要素：
- time: 事件发生的具体时间
- location: 事件发生的地点列表，包括城市、国家、地区、海域、空域等
  例如：["横须贺", "关岛", "南海", "波斯湾"]
  注意：军事基地、机场等设施不放入location，放入对应的主体分类字段

以下是主体分类字段，每个字段为列表类型，将文本中出现的实体归入对应分类：
{category_block}

- action: 主体执行的核心行为
- original_text: 原文句子

要求：
1. 抽取所有事件，不要遗漏
2. 同一事件只抽取一次
3. 未提及的字段直接省略，不要输出该key
4. 每个主体分类字段必须列出该类别下的所有实体
5. location只放纯地理位置（城市、国家、海域等），不放军事设施
6. original_text是必填字段

示例：
输入："美横须贺海军基地的第七舰队派遣里根号航母，搭载F-18战斗机前往南海巡逻。"
输出：
{{
  "location": ["横须贺", "南海"],
  "军事基地": ["横须贺海军基地"],
  "部队番号": ["第七舰队"],
  "船只": ["里根号航母"],
  "飞机": ["F-18战斗机"],
  "action": "派遣航母搭载战斗机前往南海巡逻",
  "original_text": "美横须贺海军基地的第七舰队派遣里根号航母，搭载F-18战斗机前往南海巡逻。"
}}

严格按照JSON格式输出：
{{
  "events": [
    {{
{format_block}
    }}
  ]
}}"""

    return prompt


class LLMEventExtractor:
    """
    基于LLM的事件抽取器

    主体字段从config.json的subject_categories动态加载，
    不再使用单一的subject字段。
    """

    BASE_URL = CONFIG['llm']['base_url']
    MODEL = CONFIG['llm']['model']

    SUBJECT_CATEGORIES = CONFIG.get('subject_categories', {})
    CATEGORY_NAMES = list(SUBJECT_CATEGORIES.keys())

    SYSTEM_PROMPT = build_system_prompt(SUBJECT_CATEGORIES)

    USER_PROMPT_TEMPLATE = """请从以下文本中抽取所有事件：

{text}"""

    def __init__(self, api_key: str = None):
        if api_key is None:
            api_key = CONFIG['llm']['api_key']
        self.client = OpenAI(
            api_key=api_key,
            base_url=self.BASE_URL
        )

    def read_txt_file(self, file_path: str) -> str:
        encodings = ['utf-8', 'gbk', 'gb2312', 'utf-16']
        for encoding in encodings:
            try:
                with open(file_path, 'r', encoding=encoding) as f:
                    content = f.read()
                print(f"成功读取文件，编码: {encoding}")
                return content
            except UnicodeDecodeError:
                continue
            except FileNotFoundError:
                raise FileNotFoundError(f"文件不存在: {file_path}")
        raise ValueError(f"无法识别文件编码，尝试过: {encodings}")

    def split_by_paragraph(self, text: str) -> List[str]:
        paragraphs = re.split(r'\n\s*\n', text.strip())
        paragraphs = [p.strip() for p in paragraphs if len(p.strip()) > 20]
        print(f"文本已分割为 {len(paragraphs)} 个段落")
        return paragraphs

    def call_llm(self, paragraph: str, retry_count: int = 3) -> Optional[Dict]:
        print(f"[LLM调用] 模型: {self.MODEL}")
        print(f"[LLM调用] 输入长度: {len(paragraph)} 字符")
        print(f"[LLM调用] 输入预览: {paragraph[:200]}..." if len(paragraph) > 200 else f"[LLM调用] 输入内容: {paragraph}")

        for attempt in range(retry_count):
            try:
                response = self.client.chat.completions.create(
                    model=self.MODEL,
                    messages=[
                        {"role": "system", "content": self.SYSTEM_PROMPT},
                        {"role": "user", "content": self.USER_PROMPT_TEMPLATE.format(text=paragraph)}
                    ],
                    temperature=CONFIG['llm'].get('temperature', 0.1),
                    max_tokens=CONFIG['llm'].get('max_tokens', 16000),
                    extra_body={
                        "chat_template_kwargs": {"enable_thinking": False}
                    }
                )

                if response and response.choices and len(response.choices) > 0:
                    msg = response.choices[0].message
                    content = msg.content

                    if content is None or not content.strip():
                        if hasattr(msg, 'reasoning_content') and msg.reasoning_content:
                            content = msg.reasoning_content
                        elif hasattr(msg, 'reasoning') and msg.reasoning:
                            content = msg.reasoning

                    if content and content.strip():
                        return {
                            'choices': [
                                {'message': {'content': content}}
                            ]
                        }
                    else:
                        print(f"API返回空内容，尝试重试 ({attempt + 1}/{retry_count})")
                else:
                    print(f"API返回无效响应，尝试重试 ({attempt + 1}/{retry_count})")

            except Exception as e:
                print(f"API调用失败 (尝试 {attempt + 1}/{retry_count}): {e}")

            if attempt < retry_count - 1:
                import time
                time.sleep(2)

        return None

    def parse_llm_response(self, response: Dict, paragraph: str) -> List[Dict]:
        try:
            content = response['choices'][0]['message']['content']

            print(f"[LLM响应] 长度: {len(content) if content else 0} 字符")
            print(f"[LLM响应] 内容预览: {content[:500] if content and len(content) > 500 else content}...")

            if content is None or not content.strip():
                print(f"LLM返回空内容")
                return []

            content = content.strip()
            content = self._fix_json_format(content)

            # 方法1: 直接解析JSON
            try:
                result = json.loads(content)
                events = result.get('events', [])
                events = self._normalize_events(events, paragraph)
                return events
            except json.JSONDecodeError:
                pass

            # 方法2: 移除markdown代码块标记后解析
            if '```' in content:
                match = re.search(r'```(?:json)?\s*([\s\S]*?)\s*```', content)
                if match:
                    json_str = match.group(1).strip()
                    json_str = self._fix_json_format(json_str)
                    try:
                        result = json.loads(json_str)
                        events = result.get('events', [])
                        events = self._normalize_events(events, paragraph)
                        return events
                    except json.JSONDecodeError:
                        pass

            # 方法3: 查找JSON对象
            events_pos = content.find('"events"')
            if events_pos != -1:
                start_idx = content.rfind('{', 0, events_pos)
                if start_idx != -1:
                    brace_count = 0
                    end_idx = -1
                    for i in range(start_idx, len(content)):
                        if content[i] == '{':
                            brace_count += 1
                        elif content[i] == '}':
                            brace_count -= 1
                            if brace_count == 0:
                                end_idx = i
                                break
                    if end_idx != -1:
                        json_str = content[start_idx:end_idx + 1]
                        json_str = self._fix_json_format(json_str)
                        try:
                            result = json.loads(json_str)
                            events = result.get('events', [])
                            events = self._normalize_events(events, paragraph)
                            return events
                        except json.JSONDecodeError:
                            pass

            # 方法4: 提取events数组部分
            events_match = re.search(r'"events"\s*:\s*\[([\s\S]*?)\]', content)
            if events_match:
                events_str = events_match.group(1)
                events = self._extract_events_from_string(events_str, paragraph)
                if events:
                    return events

            # 方法5: 强制手动提取
            events = self._force_extract_events(content, paragraph)
            if events:
                return events

            print(f"[解析失败] 无法解析JSON响应")
            print(f"[解析失败] 原始内容: {content[:1000] if len(content) > 1000 else content}")
            return []

        except (KeyError, IndexError) as e:
            print(f"响应格式错误: {e}")
            return []

    def _fix_json_format(self, content: str) -> str:
        """Fix JSON: state machine to replace only structural Chinese quotes."""
        CL = "\u201c"  # Chinese left quote
        CR = "\u201d"  # Chinese right quote
        FC = "\uff1a"  # Fullwidth colon

        result = []
        in_string = False
        opened_by_chinese = False
        i = 0
        while i < len(content):
            ch = content[i]
            if in_string:
                if ch == "\\" and i + 1 < len(content):
                    result.append(ch)
                    result.append(content[i + 1])
                    i += 2
                    continue
                elif ch == '"':
                    in_string = False
                    opened_by_chinese = False
                    result.append(ch)
                elif ch == CR and opened_by_chinese:
                    j = i + 1
                    while j < len(content) and content[j] in " \t\n\r":
                        j += 1
                    if j >= len(content) or content[j] in ":,}]":
                        in_string = False
                        opened_by_chinese = False
                        result.append('"')
                    else:
                        result.append(ch)
                else:
                    result.append(ch)
            else:
                if ch == '"':
                    in_string = True
                    opened_by_chinese = False
                    result.append(ch)
                elif ch in (CL, CR):
                    in_string = True
                    opened_by_chinese = True
                    result.append('"')
                elif ch == FC:
                    result.append(":")
                else:
                    result.append(ch)
            i += 1

        content = "".join(result)
        content = re.sub(r",\s*]", "]", content)
        content = re.sub(r",\s*}", "}", content)
        return content

    def _normalize_events(self, events: List[Dict], paragraph: str) -> List[Dict]:
        """规范化事件列表：补充缺失时间、确保所有分类字段存在"""
        time_patterns = [
            r'(\d{4})[年\s\-/]*(\d{1,2})[月\s\-/]*(\d{1,2})[日]?',
            r'(\d{4})[年\s\-/]*(\d{1,2})[月]',
            r'(\d{4})[\-/](\d{1,2})[\-/](\d{1,2})',
        ]

        for event in events:
            # 处理time字段
            if 'time' not in event:
                event['time'] = '未抽取'

            if event.get('time') and event['time'] not in ['未抽取', '', None]:
                normalized_time = event['time']
                normalized_time = re.sub(r'(\d+)\s*年\s*', r'\1年', normalized_time)
                normalized_time = re.sub(r'(\d+)\s*月\s*', r'\1月', normalized_time)
                normalized_time = re.sub(r'(\d+)\s*日', r'\1日', normalized_time)
                normalized_time = normalized_time.replace(' ', '').strip()
                if normalized_time:
                    event['time'] = normalized_time

            if event.get('time') in ['', '未抽取', None]:
                sources = [
                    event.get('original_text', ''),
                    paragraph,
                    event.get('action', '')
                ]
                extracted_time = None
                for source in sources:
                    if source:
                        for pattern in time_patterns:
                            match = re.search(pattern, source)
                            if match:
                                groups = match.groups()
                                if len(groups) >= 3 and groups[0]:
                                    extracted_time = f"{groups[0]}年{groups[1]}月{groups[2]}日"
                                elif len(groups) == 2 and groups[0]:
                                    extracted_time = f"{groups[0]}年{groups[1]}月"
                                if extracted_time:
                                    break
                        if extracted_time:
                            break
                event['time'] = extracted_time if extracted_time else '未抽取'

            # 确保基础字段有默认值
            if 'location' not in event:
                event['location'] = []
            if 'action' not in event:
                event['action'] = '未抽取'
            if 'original_text' not in event:
                event['original_text'] = ''

            # 兼容旧格式：如果LLM仍返回subject字段，自动保留
            # 各主体分类字段不强制设默认空列表，只保留LLM实际返回的

        return events

    def _extract_events_from_string(self, events_str: str, paragraph: str) -> List[Dict]:
        events = []
        events_str = self._fix_json_format(events_str)

        brace_count = 0
        in_event = False
        start_idx = -1

        for i, char in enumerate(events_str):
            if char == '{':
                if brace_count == 0:
                    start_idx = i
                    in_event = True
                brace_count += 1
            elif char == '}':
                brace_count -= 1
                if brace_count == 0 and in_event:
                    event_str = events_str[start_idx:i+1]
                    try:
                        event = json.loads(event_str)
                        events.append(event)
                    except json.JSONDecodeError:
                        event = self._parse_event_manually(event_str)
                        if event:
                            events.append(event)
                    in_event = False
                    start_idx = -1

        events = self._normalize_events(events, paragraph)
        return events

    def _parse_event_manually(self, event_str: str) -> Optional[Dict]:
        event = {}

        # 提取所有数组类型字段（location + 各主体分类）
        all_array_fields = ['location'] + self.CATEGORY_NAMES
        for field in all_array_fields:
            match = re.search(rf'"{re.escape(field)}"\s*:\s*(\[.*?\])', event_str)
            if match:
                try:
                    val = self._fix_json_format(match.group(1))
                    parsed = json.loads(val)
                    if parsed:
                        event[field] = parsed
                except:
                    items = re.findall(r'"([^"]*)"', match.group(1))
                    if items:
                        event[field] = items

        # 提取action
        action_match = re.search(r'"action"\s*:\s*"([^"]*(?:["""][^"]*)*)"', event_str)
        if action_match:
            action = action_match.group(1).replace('“', '').replace('”', '').replace('\\"', '').strip()
            if action:
                event['action'] = action

        # 提取original_text
        original_match = re.search(r'"original_text"\s*:\s*"([^"]*(?:["""][^"]*)*)"', event_str)
        if original_match:
            original = original_match.group(1).replace('“', '').replace('”', '').replace('\\"', '').strip()
            if original:
                event['original_text'] = original

        # 提取time
        time_match = re.search(r'"time"\s*:\s*"([^"]*)"', event_str)
        if time_match:
            time_val = time_match.group(1).strip()
            if time_val:
                event['time'] = time_val

        # 至少要有一个主体分类字段或action才算有效事件
        has_any_subject = any(event.get(cat) for cat in self.CATEGORY_NAMES)
        if not has_any_subject and not event.get('action'):
            return None

        return event

    def _force_extract_events(self, content: str, paragraph: str) -> List[Dict]:
        events = []

        brace_blocks = []
        brace_count = 0
        start_idx = -1

        for i, char in enumerate(content):
            if char == '{':
                if brace_count == 0:
                    start_idx = i
                brace_count += 1
            elif char == '}':
                brace_count -= 1
                if brace_count == 0 and start_idx != -1:
                    brace_blocks.append(content[start_idx:i+1])
                    start_idx = -1

        for block in brace_blocks:
            event = self._parse_event_manually(block)
            if event:
                events.append(event)

        events = self._normalize_events(events, paragraph)
        return events

    def extract_events_from_paragraph(self, paragraph: str, paragraph_id: int) -> List[Dict]:
        print(f"正在处理段落 {paragraph_id + 1}...")

        response = self.call_llm(paragraph)
        if response is None:
            return []

        events = self.parse_llm_response(response, paragraph)

        for event in events:
            event['paragraph_id'] = paragraph_id

        print(f"段落 {paragraph_id + 1} 抽取到 {len(events)} 个事件")
        return events

    def merge_and_deduplicate(self, all_events: List[Dict]) -> List[Dict]:
        seen_texts = set()
        unique_events = []

        for event in all_events:
            original_text = event.get('original_text', '')
            normalized_text = re.sub(r'\s+', '', original_text)

            if normalized_text not in seen_texts:
                seen_texts.add(normalized_text)
                unique_events.append(event)

        for i, event in enumerate(unique_events, 1):
            event['event_id'] = i

        print(f"去重后剩余 {len(unique_events)} 个事件")
        return unique_events

    def extract_all(self, text: str, parallel: bool = False) -> Dict[str, Any]:
        paragraphs = self.split_by_paragraph(text)

        all_events = []

        if parallel and len(paragraphs) > 1:
            with ThreadPoolExecutor(max_workers=min(len(paragraphs), 3)) as executor:
                futures = {
                    executor.submit(self.extract_events_from_paragraph, p, i): i
                    for i, p in enumerate(paragraphs)
                }
                for future in as_completed(futures):
                    events = future.result()
                    all_events.extend(events)
        else:
            for i, paragraph in enumerate(paragraphs):
                events = self.extract_events_from_paragraph(paragraph, i)
                all_events.extend(events)

        unique_events = self.merge_and_deduplicate(all_events)

        result = {
            'extraction_time': datetime.now().strftime('%Y-%m-%d %H:%M:%S'),
            'text_length': len(text),
            'paragraph_count': len(paragraphs),
            'total_events': len(unique_events),
            'subject_categories': self.CATEGORY_NAMES,
            'events': unique_events
        }

        return result

    def save_to_json(self, result: Dict, output_path: str):
        with open(output_path, 'w', encoding='utf-8') as f:
            json.dump(result, f, ensure_ascii=False, indent=2)
        print(f"结果已保存到: {output_path}")


def process_file(input_path: str, output_path: Optional[str] = None, api_key: str = None) -> Dict:
    extractor = LLMEventExtractor(api_key)

    print(f"正在读取文件: {input_path}")
    text = extractor.read_txt_file(input_path)

    print("正在抽取事件...")
    result = extractor.extract_all(text)

    result['source_file'] = os.path.basename(input_path)

    if output_path is None:
        base_name = os.path.splitext(input_path)[0]
        output_path = f"{base_name}_events.json"

    extractor.save_to_json(result, output_path)

    print("\n" + "=" * 60)
    print("事件抽取结果摘要")
    print("=" * 60)
    print(f"源文件: {result['source_file']}")
    print(f"文本长度: {result['text_length']} 字符")
    print(f"段落数量: {result['paragraph_count']} 个")
    print(f"抽取事件: {result['total_events']} 个")
    print(f"主体分类: {', '.join(extractor.CATEGORY_NAMES)}")
    print("-" * 60)

    for event in result['events']:
        print(f"\n事件 {event['event_id']}:")
        if event.get('time'):
            print(f"  时间: {event['time']}")
        if event.get('location'):
            locations = event['location']
            print(f"  地点: {', '.join(locations) if isinstance(locations, list) else locations}")
        for cat in extractor.CATEGORY_NAMES:
            if event.get(cat):
                items = event[cat]
                print(f"  {cat}: {', '.join(items) if isinstance(items, list) else items}")
        if event.get('action'):
            print(f"  行为: {event['action']}")
        if event.get('original_text'):
            print(f"  原文: {event['original_text'][:80]}...")

    print("\n" + "=" * 60)

    return result


if __name__ == '__main__':
    import sys

    if len(sys.argv) < 2:
        print("使用方法: python llm_event_extractor.py <input.txt> [output.json] [api_key]")
        print("示例: python llm_event_extractor.py sample.txt result.json")
        sys.exit(1)

    input_file = sys.argv[1]
    output_file = sys.argv[2] if len(sys.argv) > 2 else None
    api_key_arg = sys.argv[3] if len(sys.argv) > 3 else None

    process_file(input_file, output_file, api_key_arg)
