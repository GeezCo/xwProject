"""
数据生成算法

功能说明：
1. 基于【给定目录】下的样例进行仿写
2. 自动补全样例中的占位符（"XX"、"某人"、"某单位"、"某国"、"某地"等）
3. 随机进行一定扩写
4. 支持指定并行生成数量和总生成数量
5. 每篇样例都生成指定总数
6. 结果及时保存为txt文件
7. 【新增】基于候选池增强随机性，生成内容更多样化
8. 【新增】支持事件条目数量的随机正偏离

使用方法：
    python data_generator.py <样例目录> <输出目录> --total 100 --parallel 5 --diversity medium

依赖：pip install openai
"""

import os
import re
import json
import random
import argparse
from datetime import datetime, timedelta
from typing import List, Dict, Any, Optional, Tuple
from concurrent.futures import ThreadPoolExecutor, as_completed
import threading
from openai import OpenAI

# 导入候选池管理器
from candidate_pools import CandidatePoolManager


class DataGenerator:
    """基于LLM的数据生成器（支持候选池增强随机性）"""

    # 默认API配置
    DEFAULT_BASE_URL = "https://llmapi.paratera.com"
    DEFAULT_MODEL = "GLM-5"
    DEFAULT_API_KEY = "sk-tD-lVnlZvR5WKHSlACfEVw"

    # 基础系统提示词
    BASE_SYSTEM_PROMPT = """你是一个专业的文本生成助手。你的任务是基于给定的样例文本进行仿写。

要求：
1. 保持样例的整体结构和风格
2. 使用候选池中的内容替换占位符，确保每次生成都有随机性：
   - "XX"、"某人" → 从人物候选池中选择具体人名
   - "某单位"、"某机构" → 从组织候选池中选择具体组织
   - "某国" → 从国家候选池中选择具体国家
   - "某地"、"某地市"、"某基地"、"某空域"、"某海域" → 从地点候选池中选择具体地点
   - "某年"、"某月"、"某日"、"X日"、"X时" → 从时间候选池中选择具体时间
   - "某型飞机"、"某型舰船"、"A型飞机"、"B型飞机"等 → 从装备候选池中选择具体型号
   - "某号船"、"某号舰"、"某"号XX舰 → 从装备候选池中选择具体舰船名称，如"里根"号航母、"林肯"号驱逐舰
   - "某机场" → 从地点候选池中选择具体基地名称
   - "XX任务"、"XX活动" → 从行为候选池中选择具体任务类型
3. 【重要】所有"某"字占位符都必须替换为具体内容，绝不能保留"某"字
4. 时间、地点、人物、行为都要有随机性，不要总是选择相同的候选
5. 可以在样例基础上适当增加事件条目（增加1-3个），使内容更丰富
6. 生成的内容应该合理、连贯、符合逻辑

输出格式要求（严格遵守）：
第一行必须是以【标题】开头的标题行，格式为：【标题】xxx
第二行为空行
从第三行开始是正文内容

示例输出：
【标题】拜登与王毅在白宫举行会晤

2024年3月15日，美国总统拜登在白宫会晤了中国外交部长王毅..."""

    # 用户提示词模板（带候选池）
    USER_PROMPT_TEMPLATE_WITH_POOL = """请基于以下样例进行仿写，使用提供的候选池内容替换占位符，并增加随机性：

【样例文本】
{sample_text}

【候选池】（请从中随机选择，不要总选第一个）
- 时间候选：{times}
- 地点候选：{locations}
- 人物候选：{persons}
- 组织候选：{organizations}
- 装备候选：{equipment}
- 行为候选：{actions}
{current_date_section}
【条目数量要求】
{extra_items_instruction}

请开始生成，确保每次选择的候选内容都有随机性。"""

    USER_PROMPT_TEMPLATE_REAL_TIME = """【当前时间信息】
今天是{current_date}（{weekday}），当前时刻{current_time}。
请优先使用当前日期或近期日期替换样例中的时间占位符。
"""

    def __init__(self, api_key: str = None, base_url: str = None, model: str = None,
                 diversity: str = None,
                 item_diversity: str = 'medium', content_diversity: str = 'medium',
                 start_date: str = None, end_date: str = None,
                 output_format: str = 'txt'):
        """
        初始化生成器

        Args:
            api_key: API密钥（默认使用内置密钥）
            base_url: API基础URL（默认使用GLM-5地址）
            model: 模型名称（默认使用GLM-5）
            diversity: 多样性级别 (low/medium/high) - 旧接口，同时设置条目和内容多样性
            item_diversity: 条目多样性级别 (low/medium/high) - 控制事件条目数量增加
            content_diversity: 候选内容多样性级别 (low/medium/high) - 控制候选池大小和温度
            start_date: 开始日期（格式：YYYY-MM-DD），默认为今天
            end_date: 结束日期（格式：YYYY-MM-DD），默认为今天
            output_format: 输出格式 (txt/jsonl) - txt为单独文件，jsonl为汇总文件
        """
        # 使用默认值或用户指定值
        self.api_key = api_key or self.DEFAULT_API_KEY
        self.base_url = base_url or self.DEFAULT_BASE_URL
        self.model = model or self.DEFAULT_MODEL
        self.output_format = output_format

        # 解析日期范围
        self.start_date = self._parse_date(start_date)
        self.end_date = self._parse_date(end_date)
        self.current_date = self.start_date  # 当前生成日期（会在循环中更新）

        self.client = OpenAI(
            api_key=self.api_key,
            base_url=self.base_url
        )
        self.lock = threading.Lock()  # 用于线程安全的文件写入

        # jsonl格式：初始化文件路径和计数器（实时写入）
        self.jsonl_filepath = None
        self.jsonl_count = 0

        # 兼容旧接口：如果指定了 diversity，则同时设置两个维度
        if diversity is not None:
            item_diversity = diversity
            content_diversity = diversity
            self.diversity = diversity
        else:
            self.diversity = None

        self.item_diversity = item_diversity
        self.content_diversity = content_diversity

        # 获取细分多样性参数
        self.diversity_params = CandidatePoolManager.get_fine_grained_params(
            item_diversity=item_diversity,
            content_diversity=content_diversity
        )

        # 打印配置信息
        print(f"模型配置: base_url={self.base_url}, model={self.model}")
        print(f"日期范围: {self.start_date.strftime('%Y年%m月%d日')} 至 {self.end_date.strftime('%Y年%m月%d日')}")
        print(f"输出格式: {self.output_format}")
        if self.diversity:
            print(f"多样性级别: {diversity}, 温度: {self.diversity_params['temperature']}")
        else:
            print(f"条目多样性: {item_diversity}, 候选内容多样性: {content_diversity}, 温度: {self.diversity_params['temperature']}")

    def _parse_date(self, date_str: str) -> datetime:
        """
        解析日期字符串

        Args:
            date_str: 日期字符串（格式：YYYY-MM-DD），为空则返回今天

        Returns:
            datetime: 解析后的日期
        """
        if date_str is None or date_str.strip() == '':
            return datetime.now().replace(hour=0, minute=0, second=0, microsecond=0)

        # 支持多种格式
        formats = ['%Y-%m-%d', '%Y%m%d', '%Y/%m/%d']
        for fmt in formats:
            try:
                return datetime.strptime(date_str, fmt).replace(hour=0, minute=0, second=0, microsecond=0)
            except ValueError:
                continue

        # 解析失败，返回今天
        print(f"警告: 无法解析日期 '{date_str}'，使用今天")
        return datetime.now().replace(hour=0, minute=0, second=0, microsecond=0)

    def _get_date_range_list(self) -> List[datetime]:
        """
        获取日期范围内的所有日期列表

        Returns:
            List[datetime]: 日期列表
        """
        dates = []
        current = self.start_date
        while current <= self.end_date:
            dates.append(current)
            current += timedelta(days=1)
        return dates

    def read_sample_files(self, sample_dir: str) -> List[Tuple[str, str]]:
        """
        读取样例目录下的所有txt文件

        Args:
            sample_dir: 样例目录路径

        Returns:
            List[Tuple[str, str]]: [(文件名, 文件内容), ...]
        """
        samples = []

        if not os.path.exists(sample_dir):
            raise FileNotFoundError(f"样例目录不存在: {sample_dir}")

        for filename in os.listdir(sample_dir):
            if filename.endswith('.txt'):
                filepath = os.path.join(sample_dir, filename)
                try:
                    # 尝试多种编码
                    content = self._read_file_with_encoding(filepath)
                    if content:
                        samples.append((filename, content))
                        print(f"读取样例: {filename}")
                except Exception as e:
                    print(f"读取文件失败 {filename}: {e}")

        if not samples:
            raise ValueError(f"样例目录中没有找到txt文件: {sample_dir}")

        return samples

    def _read_file_with_encoding(self, filepath: str) -> Optional[str]:
        """尝试多种编码读取文件"""
        encodings = ['utf-8', 'gbk', 'gb2312', 'utf-16']

        for encoding in encodings:
            try:
                with open(filepath, 'r', encoding=encoding) as f:
                    return f.read()
            except UnicodeDecodeError:
                continue

        return None

    def generate_one(self, sample_text: str, sample_name: str, retry_count: int = 3) -> Optional[Dict[str, str]]:
        """
        基于样例生成一条数据（使用候选池增强随机性）

        Args:
            sample_text: 样例文本
            sample_name: 样例名称（用于日志）
            retry_count: 重试次数

        Returns:
            Dict: {'title': 标题, 'content': 正文}，失败返回None
        """
        # 每次生成都获取新的随机候选池样本（使用当前生成日期）
        pool_samples = CandidatePoolManager.get_random_pool_samples(
            time_count=self.diversity_params['time_count'],
            location_count=self.diversity_params['location_count'],
            person_count=self.diversity_params['person_count'],
            org_count=self.diversity_params['org_count'],
            equipment_count=self.diversity_params['equipment_count'],
            action_count=self.diversity_params['action_count'],
            target_date=self.current_date  # 使用当前生成日期
        )

        # 生成随机条目增加指令
        extra_min, extra_max = self.diversity_params['extra_items_range']
        extra_count = random.randint(extra_min, extra_max)
        if extra_count > 0:
            extra_items_instruction = f"请在样例基础上增加{extra_count}个相关事件条目，使内容更丰富。"
        else:
            extra_items_instruction = "保持样例原有条目数量，不需要额外增加。"

        # 构建当前时间信息部分
        current_date_info = pool_samples.get('current_date_info')
        if current_date_info:
            current_date_section = self.USER_PROMPT_TEMPLATE_REAL_TIME.format(
                current_date=current_date_info['date_str'],
                weekday=current_date_info['weekday'],
                current_time=current_date_info['time_str']
            )
        else:
            current_date_section = ""

        # 构建用户提示词
        user_prompt = self.USER_PROMPT_TEMPLATE_WITH_POOL.format(
            sample_text=sample_text,
            times='、'.join(pool_samples['times']),
            locations='、'.join(pool_samples['locations']),
            persons='、'.join(pool_samples['persons']),
            organizations='、'.join(pool_samples['organizations']),
            equipment='、'.join(pool_samples['equipment']),
            actions='、'.join(pool_samples['actions']),
            current_date_section=current_date_section,
            extra_items_instruction=extra_items_instruction
        )

        for attempt in range(retry_count):
            try:
                response = self.client.chat.completions.create(
                    model=self.model,
                    messages=[
                        {"role": "system", "content": self.BASE_SYSTEM_PROMPT},
                        {"role": "user", "content": user_prompt}
                    ],
                    temperature=self.diversity_params['temperature'],
                    max_tokens=4000
                )

                if response and response.choices and len(response.choices) > 0:
                    content = response.choices[0].message.content
                    if content and content.strip():
                        # 解析标题和正文
                        result = self._parse_title_and_content(content.strip())
                        if result:
                            return result
                        else:
                            print(f"解析标题失败，尝试重试 ({attempt + 1}/{retry_count})")

            except Exception as e:
                print(f"生成失败 (样例: {sample_name}, 尝试 {attempt + 1}/{retry_count}): {e}")

            if attempt < retry_count - 1:
                import time
                time.sleep(2)

        return None

    def _parse_title_and_content(self, text: str) -> Optional[Dict[str, str]]:
        """
        从生成文本中解析标题和正文

        Args:
            text: 生成的完整文本

        Returns:
            Dict: {'title': 标题, 'content': 正文}
        """
        # 匹配【标题】xxx 格式
        title_match = re.match(r'【标题】(.+?)(?:\n|$)', text)

        if title_match:
            title = title_match.group(1).strip()
            # 正文是标题行之后的内容（跳过空行）
            remaining = text[title_match.end():].strip()
            return {'title': title, 'content': remaining}
        else:
            # 尝试从第一行提取标题（如果没有【标题】标记）
            lines = text.split('\n', 1)
            if len(lines) >= 2:
                first_line = lines[0].strip()
                # 移除可能的【】标记
                title = re.sub(r'^【.*?】', '', first_line).strip()
                content = lines[1].strip() if len(lines) > 1 else ''
                if title and content:
                    return {'title': title, 'content': content}

        return None

    def _init_jsonl_file(self, output_dir: str) -> str:
        """
        初始化jsonl文件（首次写入时调用）

        Args:
            output_dir: 输出目录

        Returns:
            str: jsonl文件路径
        """
        os.makedirs(output_dir, exist_ok=True)

        # 生成文件名（基于日期范围）
        start_str = self.start_date.strftime("%Y%m%d")
        end_str = self.end_date.strftime("%Y%m%d")
        if start_str == end_str:
            filename = f"data_{start_str}.jsonl"
        else:
            filename = f"data_{start_str}_{end_str}.jsonl"

        self.jsonl_filepath = os.path.join(output_dir, filename)

        # 清空文件（如果已存在）
        with open(self.jsonl_filepath, 'w', encoding='utf-8') as f:
            f.write('')  # 创建空文件

        print(f"JSONL文件初始化: {self.jsonl_filepath}")
        return self.jsonl_filepath

    def save_result(self, output_dir: str, sample_name: str, result: Dict[str, str], index: int) -> str:
        """
        保存生成结果

        Args:
            output_dir: 输出目录
            sample_name: 样例名称
            result: {'title': 标题, 'content': 正文}
            index: 序号

        Returns:
            str: 保存的文件路径（txt格式）或记录标识（jsonl格式）
        """
        title = result['title']
        content = result['content']

        # 获取时间（用于jsonl格式）- 使用当前生成日期
        time_str = self.current_date.strftime("%Y年%m月%d日")

        if self.output_format == 'jsonl':
            # jsonl格式：实时写入文件
            with self.lock:
                # 初始化jsonl文件路径（首次写入时）
                if self.jsonl_filepath is None:
                    self._init_jsonl_file(output_dir)

                # 实时写入一条数据
                data_item = {
                    "时间": time_str,
                    "标题": title,
                    "内容": content
                }
                with open(self.jsonl_filepath, 'a', encoding='utf-8') as f:
                    f.write(json.dumps(data_item, ensure_ascii=False) + '\n')

                self.jsonl_count += 1
                return f"jsonl_record_{self.jsonl_count}"
        else:
            # txt格式：保存为单独文件
            # 创建样例对应的输出子目录
            sample_output_dir = os.path.join(output_dir, os.path.splitext(sample_name)[0])
            os.makedirs(sample_output_dir, exist_ok=True)

            # 清理标题中的非法文件名字符
            safe_title = self._sanitize_filename(title)

            # 如果标题过长，截取前50个字符
            if len(safe_title) > 50:
                safe_title = safe_title[:50]

            # 添加序号避免重名
            filename = f"{safe_title}.txt"
            filepath = os.path.join(sample_output_dir, filename)

            # 如果文件已存在，添加序号后缀
            if os.path.exists(filepath):
                filename = f"{safe_title}_{index:04d}.txt"
                filepath = os.path.join(sample_output_dir, filename)

            # 线程安全写入
            with self.lock:
                with open(filepath, 'w', encoding='utf-8') as f:
                    f.write(content)

            return filepath

    def finalize_jsonl_file(self) -> str:
        """
        完成jsonl文件写入（打印汇总信息）

        Returns:
            str: jsonl文件路径
        """
        if self.jsonl_filepath and self.jsonl_count > 0:
            print(f"JSONL文件已完成: {self.jsonl_filepath}，共 {self.jsonl_count} 条数据")
            return self.jsonl_filepath
        else:
            print("警告: 未生成任何数据")
            return None

    def _extract_time_from_content(self, content: str) -> str:
        """
        从正文内容中提取时间

        Args:
            content: 正文内容

        Returns:
            str: 提取的时间字符串，如果未找到则返回空字符串
        """
        # 匹配常见时间格式
        patterns = [
            r'(\d{4}年\d{1,2}月\d{1,2}日)',  # 2024年3月15日
            r'(\d{1,2}月\d{1,2}日)',          # 3月15日
            r'(\d{4}-\d{1,2}-\d{1,2})',       # 2024-03-15
            r'(\d{1,2}/\d{1,2}/\d{4})',       # 3/15/2024
        ]

        for pattern in patterns:
            match = re.search(pattern, content)
            if match:
                return match.group(1)

        return ""

    def _sanitize_filename(self, filename: str) -> str:
        """
        清理文件名中的非法字符

        Args:
            filename: 原始文件名

        Returns:
            str: 安全的文件名
        """
        # Windows非法字符
        illegal_chars = ['<', '>', ':', '"', '/', '\\', '|', '?', '*']
        safe_name = filename
        for char in illegal_chars:
            safe_name = safe_name.replace(char, '_')

        # 移除首尾空格和点
        safe_name = safe_name.strip('. ')

        # 如果清理后为空，使用默认名称
        if not safe_name:
            safe_name = "untitled"

        return safe_name

    def generate_for_sample(self, sample_name: str, sample_text: str,
                            output_dir: str, total: int, parallel: int) -> Dict[str, Any]:
        """
        为单个样例生成数据

        Args:
            sample_name: 样例名称
            sample_text: 样例文本
            output_dir: 输出目录
            total: 总生成数量
            parallel: 并行数量

        Returns:
            Dict: 生成统计信息
        """
        print(f"\n{'='*60}")
        print(f"开始处理样例: {sample_name}")
        print(f"目标生成数量: {total}, 并行数: {parallel}")
        print(f"{'='*60}")

        generated_count = 0
        failed_count = 0

        # 分批并行生成
        batch_size = parallel
        batch_count = (total + batch_size - 1) // batch_size

        for batch_idx in range(batch_count):
            # 计算当前批次需要生成的数量
            current_batch_size = min(batch_size, total - generated_count - failed_count)
            if current_batch_size <= 0:
                break

            print(f"\n批次 {batch_idx + 1}/{batch_count}, 生成数量: {current_batch_size}")

            # 并行生成
            with ThreadPoolExecutor(max_workers=parallel) as executor:
                futures = []
                for i in range(current_batch_size):
                    future = executor.submit(self.generate_one, sample_text, sample_name)
                    futures.append(future)

                # 收集结果并保存
                for future in as_completed(futures):
                    result = future.result()
                    if result:
                        generated_count += 1
                        # 及时保存
                        filepath = self.save_result(output_dir, sample_name, result, generated_count)
                        print(f"  [OK] 生成成功 {generated_count}/{total}: {result['title']}")
                    else:
                        failed_count += 1
                        print(f"  [FAIL] 生成失败")

            # 检查是否达到目标
            if generated_count >= total:
                break

        return {
            'sample_name': sample_name,
            'total_target': total,
            'generated': generated_count,
            'failed': failed_count
        }

    def generate_all(self, sample_dir: str, output_dir: str,
                     total: int, parallel: int) -> Dict[str, Any]:
        """
        主入口：处理所有样例

        Args:
            sample_dir: 样例目录
            output_dir: 输出目录
            total: 每个样例生成的总数
            parallel: 并行数量

        Returns:
            Dict: 完整的生成统计信息
        """
        start_time = datetime.now()

        # 读取样例
        print(f"读取样例目录: {sample_dir}")
        samples = self.read_sample_files(sample_dir)
        print(f"共找到 {len(samples)} 个样例文件")

        # 创建输出目录
        os.makedirs(output_dir, exist_ok=True)

        # 获取日期范围列表
        date_list = self._get_date_range_list()
        print(f"日期范围: {len(date_list)} 天")

        # 为每个日期、每个样例生成数据
        all_stats = []
        for target_date in date_list:
            # 设置当前生成日期
            self.current_date = target_date
            print(f"\n>>> 正在生成 {target_date.strftime('%Y年%m月%d日')} 的数据...")

            # 为每个样例生成数据
            for sample_name, sample_text in samples:
                stats = self.generate_for_sample(sample_name, sample_text, output_dir, total, parallel)
                all_stats.append(stats)

        # 汇总统计
        end_time = datetime.now()
        duration = (end_time - start_time).total_seconds()

        summary = {
            'start_time': start_time.strftime('%Y-%m-%d %H:%M:%S'),
            'end_time': end_time.strftime('%Y-%m-%d %H:%M:%S'),
            'duration_seconds': duration,
            'sample_count': len(samples),
            'date_count': len(date_list),
            'total_per_sample': total,
            'total_per_date': total * len(samples),
            'parallel_count': parallel,
            'model': self.model,
            'base_url': self.base_url,
            'start_date': self.start_date.strftime('%Y-%m-%d'),
            'end_date': self.end_date.strftime('%Y-%m-%d'),
            'output_format': self.output_format,
            'diversity': self.diversity,
            'diversity_params': self.diversity_params,
            'sample_stats': all_stats,
            'total_generated': sum(s['generated'] for s in all_stats),
            'total_failed': sum(s['failed'] for s in all_stats)
        }

        # 如果是jsonl格式，打印汇总信息
        if self.output_format == 'jsonl':
            self.finalize_jsonl_file()

        # 保存统计报告
        self._save_report(output_dir, summary)

        return summary

    def _save_report(self, output_dir: str, summary: Dict):
        """保存生成报告"""
        report_path = os.path.join(output_dir, 'generation_report.json')
        with open(report_path, 'w', encoding='utf-8') as f:
            json.dump(summary, f, ensure_ascii=False, indent=2)
        print(f"\n生成报告已保存: {report_path}")

    def print_summary(self, summary: Dict):
        """打印生成摘要"""
        print("\n" + "=" * 60)
        print("数据生成完成摘要")
        print("=" * 60)
        print(f"开始时间: {summary['start_time']}")
        print(f"结束时间: {summary['end_time']}")
        print(f"总耗时: {summary['duration_seconds']:.1f} 秒")
        print(f"样例数量: {summary['sample_count']}")
        print(f"每个样例目标: {summary['total_per_sample']}")
        print(f"并行数量: {summary['parallel_count']}")

        # 显示模型配置
        print(f"模型: {summary.get('model', 'N/A')}")
        print(f"API地址: {summary.get('base_url', 'N/A')}")
        print(f"实时时间: {'开启' if summary.get('use_real_time') else '关闭'}")

        # 显示多样性配置
        params = summary['diversity_params']
        if 'item_diversity' in params and 'content_diversity' in params:
            print(f"条目多样性: {params['item_diversity']}")
            print(f"候选内容多样性: {params['content_diversity']}")
        elif summary.get('diversity'):
            print(f"多样性级别: {summary['diversity']}")
        print(f"条目增加范围: {params['extra_items_range']}")
        print(f"温度: {params['temperature']}")

        print("-" * 60)
        print("各样例生成情况:")
        for stats in summary['sample_stats']:
            print(f"  {stats['sample_name']}: 成功 {stats['generated']}, 失败 {stats['failed']}")
        print("-" * 60)
        print(f"总计生成: {summary['total_generated']}")
        print(f"总计失败: {summary['total_failed']}")
        print("=" * 60)


def main():
    """命令行入口"""
    parser = argparse.ArgumentParser(description='基于LLM的数据生成器（支持候选池随机化）')
    parser.add_argument('sample_dir', help='样例目录路径')
    parser.add_argument('output_dir', help='输出目录路径')
    parser.add_argument('--total', type=int, default=10, help='每个样例生成的总数（默认10）')
    parser.add_argument('--parallel', type=int, default=1, help='并行生成数量（默认1）')

    # 模型配置参数
    parser.add_argument('--base_url', type=str, default=None,
                        help='API基础URL（默认: https://llmapi.paratera.com）')
    parser.add_argument('--model', type=str, default=None,
                        help='模型名称（默认: GLM-5）')
    parser.add_argument('--api_key', type=str, default=None,
                        help='API密钥（默认使用内置密钥）')

    # 多样性参数（支持细分配置）
    parser.add_argument('--diversity', type=str, default=None,
                        choices=['low', 'medium', 'high'],
                        help='多样性级别（旧接口）：同时设置条目和内容多样性')
    parser.add_argument('--item_diversity', type=str, default='medium',
                        choices=['low', 'medium', 'high'],
                        help='条目多样性：控制事件条目数量增加。low(0-1个)/medium(1-3个)/high(2-5个)')
    parser.add_argument('--content_diversity', type=str, default='medium',
                        choices=['low', 'medium', 'high'],
                        help='候选内容多样性：控制候选池大小和温度。low(保守)/medium(平衡)/high(丰富)')

    # 日期范围参数
    parser.add_argument('--start_date', type=str, default=None,
                        help='开始日期（格式：YYYY-MM-DD），默认为今天')
    parser.add_argument('--end_date', type=str, default=None,
                        help='结束日期（格式：YYYY-MM-DD），默认为今天。每个日期生成{total}条数据')

    # 输出格式参数
    parser.add_argument('--output_format', type=str, default='txt',
                        choices=['txt', 'jsonl'],
                        help='输出格式：txt为单独文件，jsonl为汇总文件（包含时间、标题、内容字段）')

    args = parser.parse_args()

    # 创建生成器（支持自定义模型配置和细分多样性配置）
    generator = DataGenerator(
        api_key=args.api_key,
        base_url=args.base_url,
        model=args.model,
        diversity=args.diversity,
        item_diversity=args.item_diversity,
        content_diversity=args.content_diversity,
        start_date=args.start_date,
        end_date=args.end_date,
        output_format=args.output_format
    )

    # 执行生成
    summary = generator.generate_all(
        sample_dir=args.sample_dir,
        output_dir=args.output_dir,
        total=args.total,
        parallel=args.parallel
    )

    # 打印摘要
    generator.print_summary(summary)


if __name__ == '__main__':
    main()