"""
事件拆分算法

功能说明：
1. 将包含多个事件的文本拆分为独立的原子化事件
2. 每个事件保留为一段完整文字（不做结构化抽取）
3. 自动补全全局时间/地点到每个事件中

使用方法：
    python event_splitter.py <input.txt> [output.json]
"""

import json
import re
import os
from datetime import datetime
from typing import List, Dict, Any, Optional
from openai import OpenAI


class EventSplitter:
    """
    事件拆分器

    处理流程：
    1. 规则预处理：按空行/编号粗拆段落
    2. LLM智能拆分：拆分事件 + 补全全局要素
    3. 结果验证：确保每个事件完整独立
    """

    # LLM API配置
    BASE_URL = "https://llmapi.paratera.com"
    DEFAULT_MODEL = "GLM-5"
    API_KEY = "sk-Sf3cCx7aSWyk_4KUFoi8Tw"

    # API调用参数
    TEMPERATURE = 0.3  # 较低温度保证稳定性
    MAX_TOKENS = 4000
    RETRY_COUNT = 3

    # 可用模型列表
    AVAILABLE_MODELS = [
        "GLM-5-Turbo",
        "GLM-5",
        "GLM-4.7",
        "Qwen3.5-35B-A3B",
        "Qwen3.5-27B",
        "Qwen3.5-122B-A10B",
        "Qwen3-32B"
    ]

    # ========== 第一层筛选规则 ==========

    # 时间表达式正则模式
    TIME_PATTERNS = [
        r'\d{4}年\d{1,2}月\d{1,2}日',  # 2024年3月15日
        r'\d{4}-\d{1,2}-\d{1,2}',       # 2024-03-15
        r'\d{4}/\d{1,2}/\d{1,2}',       # 2024/03/15
        r'\d{4}年\d{1,2}月',            # 2024年3月
        r'\d{4}年',                      # 2024年
        r'\d{1,2}月\d{1,2}日',          # 3月15日
        r'今天|昨天|明天|前天|后天',
        r'本周|上周|下周',
        r'本月|上月|下月',
        r'今年|去年|明年',
        r'近日|近期|日前|当天|当日|目前',
        r'凌晨|上午|中午|下午|傍晚|晚上|深夜',
        r'\w+期间|会议期间|访问期间',
    ]

    # 行为动词列表
    ACTION_VERBS = [
        # 政治外交类
        '访问', '会晤', '会谈', '谈判', '签署', '发表', '宣布', '声明',
        '抗议', '谴责', '制裁', '断交', '建交',
        # 军事行动类
        '进攻', '攻击', '打击', '轰炸', '突袭', '占领', '撤退',
        '演习', '部署', '巡逻', '侦察', '发射', '拦截',
        # 经济行为类
        '投资', '收购', '合并', '上市', '融资', '签约',
        # 社会活动类
        '召开', '举办', '举行', '开展', '组织', '成立', '建设',
        # 人物行为类
        '表示', '指出', '强调', '呼吁', '承诺', '警告', '要求',
        '支持', '反对', '批评', '赞扬', '会见', '接见', '决定',
    ]

    # 组织机构模式
    ORG_PATTERNS = [
        r'[\u4e00-\u9fa5]{2,8}(公司|集团|银行|基金|协会)',
        r'[\u4e00-\u9fa5]{2,6}(政府|议会|党派|部门|委员会)',
        r'[\u4e00-\u9fa5]{2,6}(大学|学院|研究院|研究所)',
        r'(联合国|北约|欧盟|东盟|G7|G20)',
    ]

    # 地点关键词
    LOCATION_KEYWORDS = [
        '省', '市', '县', '区', '镇', '乡', '村', '州',
        '国', '地区', '区域',
        '岛', '半岛', '山', '河', '湖', '海', '洋',
        '北京', '上海', '广州', '深圳', '香港', '澳门', '台湾',
        '华盛顿', '纽约', '东京', '首尔', '莫斯科', '伦敦', '巴黎',
        '白宫', '五角大楼', '克里姆林宫', '中南海',
        '机场', '港口', '基地', '战区',
    ]

    # LLM系统提示词
    SYSTEM_PROMPT = """你是一个事件分析专家。请完成以下任务：

**任务目标**
将输入文本拆分为独立的原子化事件，每个事件保留为一段完整文字。

**拆分规则**
1. 每个事件必须是一个独立、完整的信息单元
2. 如果一句话包含多个事件（如"甲地做A事，乙地做B事"），需要拆开
3. 使用转折词（"同日"、"另有"、"此外"、"同时"等）连接的内容，通常应拆分

**要素补全规则**
1. 识别文本中的全局时间（如开头写的"4月28日"）
2. 识别文本中的全局地点
3. 将全局时间/地点补全到每个事件文字中
4. 如果某事件已有明确时间/地点，则使用其自身的

**指代消解规则（重要）**
1. 指代词如"双方"、"其"、"该"、"此"、"两者"、"他们"等，必须替换为具体实体名称
2. 例如："拜登与奥斯汀会晤，双方达成共识" → "拜登与奥斯汀会晤" + "拜登与奥斯汀达成共识"
3. 确保每个事件独立阅读时，读者能理解事件涉及的人物、组织、地点

**输出格式（严格JSON）**
```json
{
  "global_time": "全局时间（如有则填写，无则null）",
  "global_location": "全局地点（如有则填写，无则null）",
  "events": [
    "事件1的完整文字",
    "事件2的完整文字"
  ]
}
```

**示例1**
输入："4月28日，美军在日本海进行军事演习，同日在菲律宾海进行侦察活动。"
输出：
```json
{
  "global_time": "4月28日",
  "global_location": null,
  "events": [
    "4月28日，美军在日本海进行军事演习。",
    "4月28日，美军在菲律宾海进行侦察活动。"
  ]
}
```

**示例2（指代消解）**
输入："11月16日，美国总统拜登与国防部长奥斯汀在五角大楼举行会晤，双方就全球热点地区局势达成共识。"
输出：
```json
{
  "global_time": "11月16日",
  "global_location": "五角大楼",
  "events": [
    "11月16日，美国总统拜登与国防部长奥斯汀在五角大楼举行会晤。",
    "11月16日，美国总统拜登与国防部长奥斯汀就全球热点地区局势达成共识。"
  ]
}
```"""

    # 用户提示词模板
    USER_PROMPT_TEMPLATE = """请分析以下文本，拆分为独立事件并补全要素：

{text}"""

    def __init__(self, api_key: Optional[str] = None, model: Optional[str] = None):
        """
        初始化拆分器

        Args:
            api_key: API密钥（可选，默认使用内置密钥）
            model: 模型名称（可选，默认使用GLM-5）
        """
        self.api_key = api_key or self.API_KEY
        self.model = model or self.DEFAULT_MODEL
        self.client = OpenAI(api_key=self.api_key, base_url=self.BASE_URL)
        # 预编译正则表达式
        self._compile_patterns()

    def _compile_patterns(self):
        """预编译正则表达式，提高运行效率"""
        self.time_regex = re.compile('|'.join(self.TIME_PATTERNS))
        self.org_regex = [re.compile(p) for p in self.ORG_PATTERNS]

    # ========== 第一层筛选方法 ==========

    def detect_time(self, text: str) -> bool:
        """检测是否包含时间表达"""
        return bool(self.time_regex.search(text))

    def detect_action(self, text: str) -> bool:
        """检测是否包含行为动词"""
        return any(verb in text for verb in self.ACTION_VERBS)

    def detect_subject(self, text: str) -> bool:
        """检测是否包含主体（人物/组织）"""
        # 检测组织
        for regex in self.org_regex:
            if regex.search(text):
                return True
        return False

    def has_event_features(self, text: str) -> Dict[str, Any]:
        """
        判断文本是否包含事件特征（第一层筛选核心方法）

        筛选策略（宽松策略）：
        - 只需要有主体 或 有时间即可
        - 不再强制要求行为动词

        Args:
            text: 待检测的文本

        Returns:
            Dict: 包含 has_event 和 features 详情
        """
        has_time = self.detect_time(text)
        has_action = self.detect_action(text)
        has_subject = self.detect_subject(text)

        # 宽松策略：有主体 或 有时间
        has_event = has_subject or has_time

        return {
            'has_event': has_event,
            'features': {
                'has_time': has_time,
                'has_action': has_action,
                'has_subject': has_subject,
            }
        }

    # ========== 文件处理方法 ==========

    def read_file(self, file_path: str) -> str:
        """
        读取文件内容（支持多种编码）

        Args:
            file_path: 文件路径

        Returns:
            str: 文件内容
        """
        encodings = ['utf-8', 'gbk', 'gb2312', 'utf-16']
        for encoding in encodings:
            try:
                with open(file_path, 'r', encoding=encoding) as f:
                    content = f.read()
                print(f"成功读取文件，编码: {encoding}")
                return content
            except UnicodeDecodeError:
                continue
        raise ValueError(f"无法读取文件: {file_path}")

    def preprocess_split(self, text: str) -> List[str]:
        r"""
        规则预处理：粗拆段落

        分割策略（优先级从高到低）：
        1. 按空行分割（\n\s*\n）
        2. 按数字编号分割（如"1.""2.""、"）
        3. 整体作为一个段落（交给LLM处理）

        Args:
            text: 输入文本

        Returns:
            List[str]: 段落列表
        """
        text = text.strip()

        # 策略1：按空行分割
        if re.search(r'\n\s*\n', text):
            segments = re.split(r'\n\s*\n', text)
            segments = [s.strip() for s in segments if s.strip()]
            if len(segments) > 1:
                print(f"按空行分割为 {len(segments)} 个段落")
                return segments

        # 策略2：按数字编号分割
        # 匹配：数字 + 点/顿号/括号，如 "1." "一、" "（1）"
        if re.search(r'[一二三四五六七八九十1-9][0-9]?[.、）]', text):
            # 在编号前插入分割点
            segments = re.split(r'(?=[一二三四五六七八九十1-9][0-9]?[.、）])', text)
            segments = [s.strip() for s in segments if s.strip()]
            if len(segments) > 1:
                print(f"按编号分割为 {len(segments)} 个段落")
                return segments

        # 策略3：整体作为一个段落
        print("未发现明显分隔符，作为单个段落处理")
        return [text]

    def call_llm(self, text: str) -> Optional[str]:
        """
        调用LLM API

        Args:
            text: 待处理的文本

        Returns:
            Optional[str]: LLM返回的内容，失败返回None
        """
        for attempt in range(self.RETRY_COUNT):
            try:
                response = self.client.chat.completions.create(
                    model=self.model,
                    messages=[
                        {"role": "system", "content": self.SYSTEM_PROMPT},
                        {"role": "user", "content": self.USER_PROMPT_TEMPLATE.format(text=text)}
                    ],
                    temperature=self.TEMPERATURE,
                    max_tokens=self.MAX_TOKENS
                )

                # 检查响应
                if response and response.choices:
                    msg = response.choices[0].message
                    content = msg.content

                    # GLM-5特殊处理：内容可能在reasoning_content字段
                    if (not content or not content.strip()) and hasattr(msg, 'reasoning_content'):
                        content = msg.reasoning_content

                    if content and content.strip():
                        return content.strip()

                print(f"API返回空内容，重试 {attempt + 1}/{self.RETRY_COUNT}")

            except Exception as e:
                print(f"API调用异常: {e}，重试 {attempt + 1}/{self.RETRY_COUNT}")

            # 重试前等待
            if attempt < self.RETRY_COUNT - 1:
                import time
                time.sleep(2)

        return None

    def parse_response(self, content: str) -> Dict[str, Any]:
        """
        解析LLM响应

        尝试多种方式解析JSON：
        1. 直接解析
        2. 提取markdown代码块
        3. 提取第一个完整JSON对象

        Args:
            content: LLM返回的原始内容

        Returns:
            Dict: 解析后的字典，至少包含events列表
        """
        if not content:
            return {"events": []}

        # 方法1：直接解析
        try:
            result = json.loads(content)
            if 'events' in result:
                return result
        except json.JSONDecodeError:
            pass

        # 方法2：提取markdown代码块
        if '```' in content:
            match = re.search(r'```(?:json)?\s*([\s\S]*?)\s*```', content)
            if match:
                try:
                    result = json.loads(match.group(1))
                    if 'events' in result:
                        return result
                except json.JSONDecodeError:
                    pass

        # 方法3：提取第一个完整JSON对象
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
                    try:
                        result = json.loads(content[start_idx:i + 1])
                        if 'events' in result:
                            return result
                        break
                    except json.JSONDecodeError:
                        break

        print("警告：无法解析JSON响应")
        return {"events": []}

    def validate_events(self, events: List[str]) -> List[Dict]:
        """
        验证并格式化事件列表

        Args:
            events: 事件文字列表

        Returns:
            List[Dict]: 格式化后的事件列表，每个事件包含event_id和content
        """
        validated = []
        for i, event_text in enumerate(events, 1):
            # 跳过空事件
            if not event_text or not event_text.strip():
                continue

            # 确保事件以句号结尾
            event_text = event_text.strip()
            if not event_text.endswith(('。', '！', '？', '.', '!', '?')):
                event_text += '。'

            validated.append({
                "event_id": i,
                "content": event_text
            })

        print(f"验证完成：{len(validated)} 个有效事件")
        return validated

    def split(self, text: str) -> Dict[str, Any]:
        """
        主入口：拆分事件

        处理流程：
        1. 第一层筛选：判断文本是否包含事件特征
        2. 规则预处理粗拆段落
        3. 对每个段落调用LLM进行精细拆分
        4. 合并所有事件
        5. 验证并格式化输出

        Args:
            text: 输入文本

        Returns:
            Dict: 拆分结果，包含全局要素和事件列表
        """
        print("=" * 50)
        print("开始事件拆分")
        print("=" * 50)

        # 1. 第一层筛选：判断是否包含事件特征
        print("\n[阶段0] 第一层筛选...")
        event_check = self.has_event_features(text)
        if not event_check['has_event']:
            features = event_check['features']
            skip_reason = []
            if not features['has_time']:
                skip_reason.append('无时间表达')
            if not features['has_subject']:
                skip_reason.append('无主体')

            print(f"  文本无事件特征，跳过LLM处理")
            print(f"  跳过原因: {', '.join(skip_reason)}")

            return {
                "source_text": text,
                "model": self.model,
                "has_event": False,
                "skip_reason": ', '.join(skip_reason),
                "features": features,
                "global_time": None,
                "global_location": None,
                "total_events": 0,
                "split_time": datetime.now().strftime('%Y-%m-%d %H:%M:%S'),
                "events": []
            }

        print(f"  检测到事件特征，继续处理")

        # 2. 规则预处理
        print("\n[阶段1] 规则预处理...")
        segments = self.preprocess_split(text)

        # 3. 对每个段落进行第一层筛选后调用LLM
        print("\n[阶段2] LLM智能拆分...")
        all_events = []
        global_time = None
        global_location = None
        skipped_segments = []

        for i, segment in enumerate(segments):
            # 先判断该段落是否有事件特征
            seg_check = self.has_event_features(segment)
            if not seg_check['has_event']:
                print(f"  段落 {i + 1}/{len(segments)}: 无事件特征，跳过")
                skipped_segments.append({
                    "segment_id": i,
                    "text_preview": segment[:50] + "..." if len(segment) > 50 else segment,
                    "skip_reason": "无事件特征"
                })
                continue

            print(f"  处理段落 {i + 1}/{len(segments)}...")
            llm_response = self.call_llm(segment)

            if llm_response:
                parsed = self.parse_response(llm_response)
                # 收集事件
                events = parsed.get('events', [])
                all_events.extend(events)
                # 更新全局要素（取第一个非空的）
                if not global_time and parsed.get('global_time'):
                    global_time = parsed.get('global_time')
                if not global_location and parsed.get('global_location'):
                    global_location = parsed.get('global_location')

                print(f"    拆分出 {len(events)} 个事件")
            else:
                print(f"    警告：LLM调用失败，保留原段落")
                all_events.append(segment)

        # 4. 验证并格式化
        print("\n[阶段3] 结果验证...")
        validated_events = self.validate_events(all_events)

        # 5. 构建结果
        result = {
            "source_text": text,
            "model": self.model,
            "has_event": True,
            "global_time": global_time,
            "global_location": global_location,
            "total_events": len(validated_events),
            "split_time": datetime.now().strftime('%Y-%m-%d %H:%M:%S'),
            "events": validated_events,
            "skipped_segments": skipped_segments,
            "llm_calls_saved": len(skipped_segments)
        }

        print("=" * 50)
        print(f"拆分完成：共 {len(validated_events)} 个事件")
        print("=" * 50)

        return result

    def save_result(self, result: Dict, output_path: str):
        """
        保存结果到JSON文件

        Args:
            result: 拆分结果
            output_path: 输出文件路径
        """
        with open(output_path, 'w', encoding='utf-8') as f:
            json.dump(result, f, ensure_ascii=False, indent=2)
        print(f"\n结果已保存: {output_path}")


def process_file(input_path: str, output_path: Optional[str] = None) -> Dict[str, Any]:
    """
    处理单个文件

    Args:
        input_path: 输入文件路径
        output_path: 输出文件路径（可选）

    Returns:
        Dict: 拆分结果
    """
    # 创建拆分器
    splitter = EventSplitter()

    # 读取文件
    print(f"读取文件: {input_path}")
    text = splitter.read_file(input_path)

    # 执行拆分
    result = splitter.split(text)

    # 添加源文件信息
    result['source_file'] = os.path.basename(input_path)

    # 确定输出路径
    if output_path is None:
        base_name = os.path.splitext(input_path)[0]
        output_path = f"{base_name}_split.json"

    # 保存结果
    splitter.save_result(result, output_path)

    return result


if __name__ == '__main__':
    import sys

    # 检查参数
    if len(sys.argv) < 2:
        print("使用方法: python event_splitter.py <input.txt> [output.json]")
        print("示例: python event_splitter.py sample.txt result.json")
        sys.exit(1)

    # 解析参数
    input_file = sys.argv[1]
    output_file = sys.argv[2] if len(sys.argv) > 2 else None

    # 执行处理
    process_file(input_file, output_file)