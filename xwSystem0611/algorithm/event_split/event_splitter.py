"""
事件拆分模块

功能：将包含多个事件的报文文本进行智能拆分，
提取每个独立事件的时间、地点、内容，并进行深度分析。

调用方式：复用 数据抽取-新/config.json 的 LLM 配置
"""

import json
import os
import sys
import re
import logging

from openai import OpenAI

# 添加配置路径
sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..', '数据抽取-新'))
from llm_event_extractor import load_config

logger = logging.getLogger(__name__)

SYSTEM_PROMPT = "你是一个情报分析专家，擅长事件拆分和深度分析。请严格按照用户要求的JSON格式输出。"

USER_PROMPT_TEMPLATE = """请将以下报文文本拆分为独立事件，并对每个事件进行深度分析。

要求：
1. 识别文本中所有独立事件
2. 对每个事件提取：时间(time)、地点(location)、事件内容(event_content)
3. 对每个事件进行深度分析(event_analysis)，涵盖时间、空间、主体、行为、影响、关联等维度
4. 生成整体综合分析(overall_analysis)，包含趋势判断、风险预警、战略建议

以JSON格式输出，格式如下：
{{
  "events": [
    {{
      "time": "事件时间",
      "location": "事件地点",
      "event_content": "事件核心内容（一句话）",
      "event_analysis": "单事件深度分析（一段文本）"
    }}
  ],
  "overall_analysis": "整体综合分析（一段文本）"
}}

报文内容：
{text}"""


class EventSplitter:
    """事件拆分器：基于LLM的多事件拆分与分析"""

    def __init__(self):
        config = load_config()
        self.client = OpenAI(
            api_key=config['llm']['api_key'],
            base_url=config['llm']['base_url']
        )
        self.model = config['llm']['model']
        self.temperature = config['llm'].get('temperature', 0.1)
        self.max_tokens = config['llm'].get('max_tokens', 16000)
        self.retry_count = config.get('extraction', {}).get('retry_count', 3)

    def split_events(self, text: str) -> dict:
        prompt = USER_PROMPT_TEMPLATE.format(text=text)

        for attempt in range(self.retry_count):
            try:
                response = self.client.chat.completions.create(
                    model=self.model,
                    messages=[
                        {"role": "system", "content": SYSTEM_PROMPT},
                        {"role": "user", "content": prompt}
                    ],
                    temperature=self.temperature,
                    max_tokens=self.max_tokens,
                    extra_body={"chat_template_kwargs": {"enable_thinking": False}}
                )

                if response and response.choices:
                    msg = response.choices[0].message
                    content = msg.content
                    if (not content or not content.strip()) and hasattr(msg, 'reasoning_content'):
                        content = msg.reasoning_content

                    if not content:
                        logger.warning(f"[eventSplit] 第{attempt+1}次调用返回空内容")
                        continue

                    result = self._parse_json(content)
                    self._validate_result(result)
                    return result

            except ValueError as e:
                logger.warning(f"[eventSplit] 第{attempt+1}次JSON解析失败: {e}")
            except Exception as e:
                logger.error(f"[eventSplit] 第{attempt+1}次调用异常: {e}")

        raise RuntimeError(f"LLM调用失败，已重试{self.retry_count}次")

    def _validate_result(self, result: dict):
        if 'events' not in result:
            raise ValueError("响应缺少 events 字段")
        if 'overall_analysis' not in result:
            raise ValueError("响应缺少 overall_analysis 字段")
        for i, event in enumerate(result['events']):
            for field in ['time', 'location', 'event_content', 'event_analysis']:
                if field not in event:
                    raise ValueError(f"事件{i}缺少 {field} 字段")

    def _parse_json(self, content: str) -> dict:
        """三重 JSON 解析策略"""
        # 1. 直接解析
        try:
            return json.loads(content)
        except Exception:
            pass

        # 2. 提取 markdown 代码块
        match = re.search(r'```(?:json)?\s*(\{.*?\})\s*```', content, re.DOTALL)
        if match:
            try:
                return json.loads(match.group(1))
            except Exception:
                pass

        # 3. 花括号计数提取
        start = content.find('{')
        if start != -1:
            brace_count = 0
            for i in range(start, len(content)):
                if content[i] == '{':
                    brace_count += 1
                elif content[i] == '}':
                    brace_count -= 1
                    if brace_count == 0:
                        try:
                            return json.loads(content[start:i+1])
                        except Exception:
                            break

        raise ValueError("无法解析LLM返回的JSON")
