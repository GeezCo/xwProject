"""
基于LLM的事件抽取算法

功能说明：
1. 读取txt文件的文本内容
2. 按段落分割文本
3. 调用GLM-5大模型进行事件抽取
4. 抽取原子化事件，每个事件包含：
   - 时间（time）
   - 地点（location）
   - 主体（subject）：人物和组织
   - 行为（action）
   - 原文句子（original_text）
5. 合并去重后输出结构化JSON

核心优势：
- 事件要素有关联关系
- 多事件不遗漏
- 同一事件不重复
"""

# JSON数据处理模块
import json
# 正则表达式模块
import re
# 操作系统模块
import os
# 日期时间模块
from datetime import datetime
# 并发执行模块
from concurrent.futures import ThreadPoolExecutor, as_completed
# 类型提示模块
from typing import List, Dict, Any, Optional
# OpenAI SDK（兼容GLM-5接口）
from openai import OpenAI


class LLMEventExtractor:
    """
    基于LLM的事件抽取器

    该类封装了调用大语言模型进行事件抽取的完整流程：
    1. 文本分段
    2. LLM调用
    3. 响应解析
    4. 事件去重
    """

    # API基础URL（Qwen模型服务地址）
    BASE_URL = "https://llmapi.paratera.com"
    # 使用的模型名称
    MODEL = "Qwen3.5-35B-A3B"

    # 系统提示词（定义LLM的角色和输出格式）
    SYSTEM_PROMPT = """你是一个事件抽取专家。请从给定文本中抽取所有原子化事件。

每个事件包含以下要素：
- time: 事件发生的具体时间
- location: 事件发生的地点列表，包括城市、国家、地区、基地、海域、空域等
  例如：["横须贺", "关岛", "南海", "波斯湾"]
- subject: 事件的参与者列表，仅包括：人物、组织机构、武器装备、部队单位
  例如：["拜登", "北约", "萨德反导系统", "标准导弹", "第七舰队"]
  注意：地点放入location字段，不要放入subject
- action: 主体执行的核心行为
- original_text: 原文句子

要求：
1. 抽取所有事件，不要遗漏
2. 同一事件只抽取一次
3. 未提及的字段直接省略，不要输出该key
4. 【重要】subject必须列出所有参与者
5. 【重要】location必须列出所有地点：如果文本提到多个地点，location必须全部包含
6. original_text是必填字段

示例：
输入："美横须贺、关岛的萨德反导系统，南海、波斯湾的标准导弹保持战备状态。"
输出：
{
  "location": ["横须贺", "关岛", "南海", "波斯湾"],
  "subject": ["萨德反导系统", "标准导弹"],
  "action": "保持战备状态",
  "original_text": "美横须贺、关岛的萨德反导系统，南海、波斯湾的标准导弹保持战备状态。"
}

严格按照JSON格式输出：
{
  "events": [
    {
      "time": "时间",
      "location": ["地点1", "地点2"],
      "subject": ["主体1", "主体2"],
      "action": "行为描述",
      "original_text": "原文句子"
    }
  ]
}"""

    # 用户提示词模板（包含待处理的文本）
    USER_PROMPT_TEMPLATE = """请从以下文本中抽取所有事件：

{text}"""

    def __init__(self, api_key: str):
        """
        初始化抽取器

        Args:
            api_key: API密钥，用于调用LLM接口
        """
        # 创建OpenAI客户端实例
        self.client = OpenAI(
            api_key=api_key,  # API密钥
            base_url=self.BASE_URL  # 服务地址
        )

    def read_txt_file(self, file_path: str) -> str:
        """
        读取txt文件内容

        支持多种编码自动检测，依次尝试：
        1. UTF-8（最常用）
        2. GBK（简体中文Windows）
        3. GB2312（老式中文编码）
        4. UTF-16（Unicode编码）

        Args:
            file_path: txt文件路径

        Returns:
            str: 文件文本内容

        Raises:
            FileNotFoundError: 文件不存在
            ValueError: 无法识别文件编码
        """
        # 定义要尝试的编码列表
        encodings = ['utf-8', 'gbk', 'gb2312', 'utf-16']

        # 遍历尝试每种编码
        for encoding in encodings:
            try:
                # 尝试用当前编码打开文件
                with open(file_path, 'r', encoding=encoding) as f:
                    # 读取文件内容
                    content = f.read()
                # 成功读取，打印编码信息
                print(f"成功读取文件，编码: {encoding}")
                # 返回文件内容
                return content
            except UnicodeDecodeError:
                # 当前编码不正确，尝试下一个
                continue
            except FileNotFoundError:
                # 文件不存在，抛出异常
                raise FileNotFoundError(f"文件不存在: {file_path}")

        # 所有编码都尝试失败，抛出异常
        raise ValueError(f"无法识别文件编码，尝试过: {encodings}")

    def split_by_paragraph(self, text: str) -> List[str]:
        """
        按段落分割文本

        分割规则：
        - 使用连续换行符（\n\s*\n）作为段落分隔
        - 过滤掉过短的段落（小于20字符）
        - 去除每段首尾空白

        Args:
            text: 输入文本

        Returns:
            List[str]: 段落列表
        """
        # 使用正则表达式按连续换行分割
        # \n\s*\n 匹配：换行符 + 任意空白 + 换行符
        paragraphs = re.split(r'\n\s*\n', text.strip())
        # 过滤短段落并去除首尾空白
        # 保留长度大于20字符的段落
        paragraphs = [p.strip() for p in paragraphs if len(p.strip()) > 20]

        # 打印分割结果
        print(f"文本已分割为 {len(paragraphs)} 个段落")
        # 返回段落列表
        return paragraphs

    def call_llm(self, paragraph: str, retry_count: int = 3) -> Optional[Dict]:
        """
        调用LLM API进行事件抽取

        调用流程：
        1. 构建消息（系统提示词 + 用户提示词）
        2. 发送请求到LLM
        3. 解析响应内容
        4. 失败时自动重试

        注意：不同模型可能返回content、reasoning_content或reasoning字段

        Args:
            paragraph: 单个段落文本
            retry_count: 重试次数（默认3次）

        Returns:
            Dict: API响应结果，失败返回None
        """
        # 记录调用信息
        print(f"[LLM调用] 模型: {self.MODEL}")
        print(f"[LLM调用] 输入长度: {len(paragraph)} 字符")
        print(f"[LLM调用] 输入预览: {paragraph[:200]}..." if len(paragraph) > 200 else f"[LLM调用] 输入内容: {paragraph}")

        # 重试循环
        for attempt in range(retry_count):
            try:
                # 调用OpenAI兼容接口
                response = self.client.chat.completions.create(
                    model=self.MODEL,
                    messages=[
                        # 系统消息：定义任务和输出格式
                        {"role": "system", "content": self.SYSTEM_PROMPT},
                        # 用户消息：包含待处理文本
                        {"role": "user", "content": self.USER_PROMPT_TEMPLATE.format(text=paragraph)}
                    ],
                    temperature=0.1,  # 低温度使输出更稳定
                    max_tokens=16000  # 最大输出token数（Qwen思考过程较长）
                )

                # 检查响应是否有效
                if response and response.choices and len(response.choices) > 0:
                    # 获取第一个响应消息
                    msg = response.choices[0].message
                    # 获取content字段
                    content = msg.content

                    # 多模型兼容处理：
                    # 1. 优先使用content字段
                    # 2. 如果content为空，检查reasoning_content字段（GLM-5）
                    # 3. 如果还为空，检查reasoning字段（Qwen3.5）
                    if content is None or not content.strip():
                        if hasattr(msg, 'reasoning_content') and msg.reasoning_content:
                            content = msg.reasoning_content
                        elif hasattr(msg, 'reasoning') and msg.reasoning:
                            content = msg.reasoning

                    # 检查内容是否有效
                    if content and content.strip():
                        # 转换为字典格式，统一后续处理
                        return {
                            'choices': [
                                {'message': {'content': content}}
                            ]
                        }
                    else:
                        # 内容为空，打印警告
                        print(f"API返回空内容，尝试重试 ({attempt + 1}/{retry_count})")
                else:
                    # 响应格式无效
                    print(f"API返回无效响应，尝试重试 ({attempt + 1}/{retry_count})")

            except Exception as e:
                # 捕获异常，打印错误信息
                print(f"API调用失败 (尝试 {attempt + 1}/{retry_count}): {e}")

            # 重试前等待（避免频繁请求）
            if attempt < retry_count - 1:
                import time
                time.sleep(2)  # 等待2秒

        # 所有重试都失败，返回None
        return None

    def parse_llm_response(self, response: Dict, paragraph: str) -> List[Dict]:
        """
        解析LLM响应，提取事件列表

        解析策略（依次尝试）：
        1. 直接解析JSON
        2. 移除markdown代码块标记后解析
        3. 提取第一个完整JSON对象

        Args:
            response: API响应字典
            paragraph: 原始段落文本（用于错误处理）

        Returns:
            List[Dict]: 事件列表
        """
        try:
            # 从响应中提取content
            content = response['choices'][0]['message']['content']

            # 记录LLM原始响应（用于调试）
            print(f"[LLM响应] 长度: {len(content) if content else 0} 字符")
            print(f"[LLM响应] 内容预览: {content[:500] if content and len(content) > 500 else content}...")

            # 检查内容是否为空
            if content is None or not content.strip():
                print(f"LLM返回空内容")
                return []

            # 去除首尾空白
            content = content.strip()

            # 方法1: 直接解析JSON
            try:
                result = json.loads(content)
                # 返回events数组
                return result.get('events', [])
            except json.JSONDecodeError:
                # 直接解析失败，尝试其他方法
                pass

            # 方法2: 移除markdown代码块标记后解析
            # LLM可能返回 ```json ... ``` 格式
            if '```' in content:
                # 使用正则提取代码块内容
                # (?:json)? 匹配可选的json标记
                match = re.search(r'```(?:json)?\s*([\s\S]*?)\s*```', content)
                if match:
                    # 提取代码块内容
                    json_str = match.group(1).strip()
                    try:
                        result = json.loads(json_str)
                        return result.get('events', [])
                    except json.JSONDecodeError:
                        # 解析失败，继续尝试其他方法
                        pass

            # 方法3: 查找JSON对象（支持Qwen等模型的输出格式）
            # 先尝试找到 "events" 键的位置，然后向前找到完整的JSON对象
            events_pos = content.find('"events"')
            if events_pos != -1:
                # 从这个位置向前找最近的 {
                start_idx = content.rfind('{', 0, events_pos)
                if start_idx != -1:
                    # 从start_idx开始找匹配的 }
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
                        try:
                            result = json.loads(json_str)
                            return result.get('events', [])
                        except json.JSONDecodeError:
                            pass

            # 所有方法都失败
            print(f"[解析失败] 无法解析JSON响应")
            print(f"[解析失败] 原始内容: {content[:1000] if len(content) > 1000 else content}")
            return []

        except (KeyError, IndexError) as e:
            # 响应格式错误
            print(f"响应格式错误: {e}")
            return []

    def extract_events_from_paragraph(self, paragraph: str, paragraph_id: int) -> List[Dict]:
        """
        对单个段落进行事件抽取

        处理流程：
        1. 调用LLM API
        2. 解析响应
        3. 为事件添加段落ID

        Args:
            paragraph: 段落文本
            paragraph_id: 段落编号

        Returns:
            List[Dict]: 事件列表
        """
        # 打印处理进度
        print(f"正在处理段落 {paragraph_id + 1}...")

        # 调用LLM进行抽取
        response = self.call_llm(paragraph)
        # 检查响应是否有效
        if response is None:
            return []

        # 解析响应，获取事件列表
        events = self.parse_llm_response(response, paragraph)

        # 为每个事件添加段落ID（用于追溯）
        for event in events:
            event['paragraph_id'] = paragraph_id

        # 打印抽取结果
        print(f"段落 {paragraph_id + 1} 抽取到 {len(events)} 个事件")
        return events

    def merge_and_deduplicate(self, all_events: List[Dict]) -> List[Dict]:
        """
        合并所有段落的事件并去重

        去重策略：
        - 基于original_text字段
        - 标准化文本（去除空白字符）后比较
        - 相同原文的事件只保留一个

        Args:
            all_events: 所有事件列表

        Returns:
            List[Dict]: 去重后的事件列表
        """
        # 已见文本集合（用于去重）
        seen_texts = set()
        # 去重后的事件列表
        unique_events = []

        # 遍历所有事件
        for event in all_events:
            # 获取原文
            original_text = event.get('original_text', '')
            # 标准化文本：去除所有空白字符
            # \s+ 匹配一个或多个空白字符
            normalized_text = re.sub(r'\s+', '', original_text)

            # 检查是否已存在
            if normalized_text not in seen_texts:
                # 新事件，添加到结果
                seen_texts.add(normalized_text)
                unique_events.append(event)

        # 分配事件ID（从1开始）
        for i, event in enumerate(unique_events, 1):
            event['event_id'] = i

        # 打印去重结果
        print(f"去重后剩余 {len(unique_events)} 个事件")
        return unique_events

    def extract_all(self, text: str, parallel: bool = False) -> Dict[str, Any]:
        """
        主入口：完整抽取流程

        处理流程：
        1. 按段落分割文本
        2. 对每个段落进行事件抽取
        3. 合并去重所有事件
        4. 构建结果字典

        Args:
            text: 输入文本
            parallel: 是否并行处理段落（默认False，串行更稳定）

        Returns:
            Dict: 包含所有事件的结果字典
        """
        # 1. 按段落分割
        paragraphs = self.split_by_paragraph(text)

        # 2. 对每个段落进行事件抽取
        all_events = []  # 收集所有事件

        # 判断是否并行处理
        if parallel and len(paragraphs) > 1:
            # 并行处理模式
            # 创建线程池，最多3个并发
            with ThreadPoolExecutor(max_workers=min(len(paragraphs), 3)) as executor:
                # 提交所有任务
                futures = {
                    executor.submit(self.extract_events_from_paragraph, p, i): i
                    for i, p in enumerate(paragraphs)
                }
                # 收集结果
                for future in as_completed(futures):
                    events = future.result()
                    all_events.extend(events)
        else:
            # 串行处理模式（更稳定）
            for i, paragraph in enumerate(paragraphs):
                events = self.extract_events_from_paragraph(paragraph, i)
                all_events.extend(events)

        # 3. 合并去重
        unique_events = self.merge_and_deduplicate(all_events)

        # 4. 构建结果字典
        result = {
            # 抽取时间
            'extraction_time': datetime.now().strftime('%Y-%m-%d %H:%M:%S'),
            # 文本长度
            'text_length': len(text),
            # 段落数量
            'paragraph_count': len(paragraphs),
            # 事件总数
            'total_events': len(unique_events),
            # 事件列表
            'events': unique_events
        }

        return result

    def save_to_json(self, result: Dict, output_path: str):
        """
        将结果保存为JSON文件

        Args:
            result: 抽取结果字典
            output_path: 输出文件路径
        """
        # 打开文件并写入JSON
        with open(output_path, 'w', encoding='utf-8') as f:
            # ensure_ascii=False 保留中文字符
            # indent=2 格式化缩进
            json.dump(result, f, ensure_ascii=False, indent=2)
        # 打印保存信息
        print(f"结果已保存到: {output_path}")


def process_file(input_path: str, output_path: Optional[str] = None, api_key: str = None) -> Dict:
    """
    处理单个txt文件

    完整流程：
    1. 读取文件
    2. 创建抽取器
    3. 执行抽取
    4. 保存结果
    5. 打印摘要

    Args:
        input_path: 输入txt文件路径
        output_path: 输出json文件路径（可选）
        api_key: API密钥（可选，默认使用内置密钥）

    Returns:
        Dict: 抽取结果
    """
    # 使用默认API密钥（如果未提供）
    if api_key is None:
        api_key = "sk-Sf3cCx7aSWyk_4KUFoi8Tw"

    # 创建抽取器实例
    extractor = LLMEventExtractor(api_key)

    # 读取输入文件
    print(f"正在读取文件: {input_path}")
    text = extractor.read_txt_file(input_path)

    # 执行事件抽取
    print("正在抽取事件...")
    result = extractor.extract_all(text)

    # 添加源文件信息
    result['source_file'] = os.path.basename(input_path)

    # 确定输出路径（如果未指定）
    if output_path is None:
        # 使用输入文件名 + _events.json
        base_name = os.path.splitext(input_path)[0]
        output_path = f"{base_name}_events.json"

    # 保存结果
    extractor.save_to_json(result, output_path)

    # 打印结果摘要
    print("\n" + "=" * 60)
    print("事件抽取结果摘要")
    print("=" * 60)
    print(f"源文件: {result['source_file']}")
    print(f"文本长度: {result['text_length']} 字符")
    print(f"段落数量: {result['paragraph_count']} 个")
    print(f"抽取事件: {result['total_events']} 个")
    print("-" * 60)

    # 打印每个事件的详情
    for event in result['events']:
        print(f"\n事件 {event['event_id']}:")
        # 时间（可选字段）
        if 'time' in event and event['time']:
            print(f"  时间: {event['time']}")
        # 地点（可选字段，支持列表或字符串）
        if 'location' in event and event['location']:
            locations = event['location']
            if isinstance(locations, list):
                print(f"  地点: {', '.join(locations)}")
            else:
                print(f"  地点: {locations}")
        # 主体（可选字段）
        if 'subject' in event and event['subject']:
            subjects = event['subject']
            if isinstance(subjects, list):
                print(f"  主体: {', '.join(subjects)}")
        # 行为（可选字段）
        if 'action' in event and event['action']:
            print(f"  行为: {event['action']}")
        # 原文（必填字段）
        if 'original_text' in event and event['original_text']:
            print(f"  原文: {event['original_text'][:50]}...")

    print("\n" + "=" * 60)

    return result


if __name__ == '__main__':
    # 命令行入口
    import sys

    # 检查参数数量
    if len(sys.argv) < 2:
        print("使用方法: python llm_event_extractor.py <input.txt> [output.json] [api_key]")
        print("示例: python llm_event_extractor.py sample.txt result.json")
        sys.exit(1)

    # 解析命令行参数
    input_file = sys.argv[1]  # 输入文件（必需）
    output_file = sys.argv[2] if len(sys.argv) > 2 else None  # 输出文件（可选）
    api_key_arg = sys.argv[3] if len(sys.argv) > 3 else None  # API密钥（可选）

    # 执行处理
    process_file(input_file, output_file, api_key_arg)