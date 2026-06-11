"""
图文报文仿真生成算法

功能说明：
1. 读取图片目录下的所有图片文件（jpg/png/bmp等）
2. 对每张图片调用视觉大模型理解图片内容
3. 根据图片内容自动匹配最合适的样例模板（也支持手动指定）
4. 基于匹配的样例模板 + 图片理解结果，生成与图片内容对应的报文
5. 每张图片对应一篇报文，支持txt/jsonl输出

使用方法：
    python image_data_generator.py <图片目录> <样例目录> <输出目录> [参数]

依赖：pip install openai
"""

import os
import re
import json
import base64
import random
import argparse
from datetime import datetime, timedelta
from typing import List, Dict, Any, Optional, Tuple
from concurrent.futures import ThreadPoolExecutor, as_completed
import threading
from openai import OpenAI

# 导入候选池管理器
from candidate_pools import CandidatePoolManager


class ImageDataGenerator:
    """基于视觉大模型的图文报文仿真生成器"""

    # 默认API配置
    DEFAULT_BASE_URL = "https://llmapi.paratera.com"
    DEFAULT_TEXT_MODEL = "GLM-5"
    DEFAULT_VISION_MODEL = "Qwen3-VL-235B-A22B-Instruct"
    DEFAULT_API_KEY = "sk-tD-lVnlZvR5WKHSlACfEVw"

    # 支持的图片格式
    SUPPORTED_IMAGE_FORMATS = {'.jpg', '.jpeg', '.png', '.bmp', '.webp', '.gif', '.tiff', '.tif'}

    # 样例分类摘要（用于自动匹配）
    SAMPLE_CATEGORIES = {
        "0.txt": {
            "type": "外交会晤",
            "desc": "领导人会晤、双边关系、外交会谈、合作协议签署",
            "keywords": ["会晤", "会谈", "领导人", "外交", "签署", "合作", "握手", "会议"]
        },
        "1.txt": {
            "type": "导弹战备",
            "desc": "导弹系统战备状态、防空部署、爱国者/萨德系统",
            "keywords": ["导弹", "防空", "战备", "爱国者", "萨德", "防御", "发射架", "雷达"]
        },
        "2.txt": {
            "type": "外军动向综述",
            "desc": "军事动向综合报道、重要军事事件、各方反应",
            "keywords": ["动向", "综述", "袭击", "拦截", "声明", "反应", "军事行动"]
        },
        "3.txt": {
            "type": "组织活动通报",
            "desc": "特定组织活动情况通报、人员动向、会面活动",
            "keywords": ["组织", "活动", "会面", "评论", "通报", "情报"]
        },
        "4.txt": {
            "type": "海空域活动",
            "desc": "飞机架次和舰船在特定海空域的活动情况",
            "keywords": ["飞机", "舰船", "空域", "海域", "起飞", "自卫队", "架次", "巡逻"]
        },
        "5.txt": {
            "type": "飞机侦察活动",
            "desc": "侦察机从基地起飞执行侦察任务的详细情况",
            "keywords": ["侦察", "侦察机", "MQ-9", "P-8", "基地", "升空", "无人机"]
        },
        "6.txt": {
            "type": "舰船综合情况",
            "desc": "海军舰船作战任务、演习训练、巡驶返港等综合情况",
            "keywords": ["舰船", "海军", "航母", "驱逐舰", "巡驶", "返港", "演习",
                         "港口", "船", "货船", "油轮", "集装箱", "码头", "泊位"]
        }
    }

    # 视觉理解提示词
    VISION_PROMPT = """请详细分析这张遥感/卫星/军事图片，提取以下信息：

1. **场景类型**：海域、港口、机场、军事基地、城市等
2. **目标识别**：图中标注或可见的目标（船只、飞机、车辆、建筑等），包括类型和数量
3. **地理特征**：海岸线、水域、陆地、道路、跑道等地理特征
4. **活动推断**：根据目标位置和状态推断可能的活动（停泊、航行、巡逻、装卸等）
5. **标注信息**：图片中的文字标注内容

请以结构化方式回答，提供尽可能多的细节。"""

    # 图文生成系统提示词
    IMAGE_SYSTEM_PROMPT = """你是一个专业的军事情报报文撰写助手。你的任务是基于卫星/遥感图片的分析结果和给定的样例模板，生成与图片内容对应的情报报文。

要求：
1. 报文内容必须与图片分析结果的场景一致
2. 保持样例模板的整体结构和风格
3. 使用候选池中的内容丰富细节：
   - 时间使用候选池提供的时间或当前日期
   - 地点根据图片场景从候选池中选择合理的具体地点
   - 装备型号从候选池中选择与图片目标类型匹配的具体型号
   - 行为描述从候选池中选择合理的具体行为
4. 所有占位符必须替换为具体内容，绝不能保留"某"、"XX"等占位符
5. 生成内容应合理、连贯、符合军事情报报文的专业性

输出格式要求（严格遵守）：
第一行必须是以【标题】开头的标题行，格式为：【标题】xxx
第二行为空行
从第三行开始是正文内容"""

    # 图文生成用户提示词模板
    USER_PROMPT_TEMPLATE_IMAGE = """请基于以下卫星图片分析结果和样例模板，生成一篇与图片内容对应的情报报文：

【图片分析结果】
{image_description}

【图片文件名】
{image_filename}

【参考样例模板】（请保持此样例的结构和风格）
{sample_text}

【候选池】（请从中选择与图片场景匹配的内容）
- 时间候选：{times}
- 地点候选：{locations}
- 人物候选：{persons}
- 组织候选：{organizations}
- 装备候选：{equipment}
- 行为候选：{actions}
{current_date_section}
【生成要求】
1. 报文内容必须基于图片分析结果，与图片中观测到的目标和场景一致
2. 保持样例模板的结构和文体风格
3. 使用候选池中与图片场景匹配的具体内容替换占位符
4. {extra_items_instruction}

请开始生成报文。"""

    # 样例匹配提示词
    MATCH_PROMPT_TEMPLATE = """根据以下图片分析结果，从给定的样例类型中选择最匹配的一个。

【图片分析结果】
{image_description}

【可选样例类型】
{sample_descriptions}

请只回复最匹配的样例文件名（如 "4.txt"），不要回复其他内容。"""

    def __init__(self, api_key: str = None, base_url: str = None,
                 text_model: str = None, vision_model: str = None,
                 item_diversity: str = 'low', content_diversity: str = 'high',
                 start_date: str = None, end_date: str = None,
                 output_format: str = 'jsonl'):
        """
        初始化图文报文仿真生成器

        Args:
            api_key: API密钥
            base_url: API基础URL
            text_model: 文本生成模型
            vision_model: 视觉理解模型
            item_diversity: 条目多样性
            content_diversity: 内容多样性
            start_date: 开始日期
            end_date: 结束日期
            output_format: 输出格式 (txt/jsonl)
        """
        self.api_key = api_key or self.DEFAULT_API_KEY
        self.base_url = base_url or self.DEFAULT_BASE_URL
        self.text_model = text_model or self.DEFAULT_TEXT_MODEL
        self.vision_model = vision_model or self.DEFAULT_VISION_MODEL
        self.output_format = output_format

        self.item_diversity = item_diversity
        self.content_diversity = content_diversity

        # 解析日期
        self.start_date = self._parse_date(start_date)
        self.end_date = self._parse_date(end_date)
        self.current_date = self.start_date

        # 创建客户端
        self.client = OpenAI(api_key=self.api_key, base_url=self.base_url)
        self.lock = threading.Lock()

        # jsonl文件管理
        self.jsonl_filepath = None
        self.jsonl_count = 0

        # 多样性参数
        self.diversity_params = CandidatePoolManager.get_fine_grained_params(
            item_diversity=item_diversity,
            content_diversity=content_diversity
        )

        # 打印配置
        self._safe_print("=" * 60)
        self._safe_print("图文报文仿真生成器")
        self._safe_print("=" * 60)
        self._safe_print(f"视觉模型: {self.vision_model}")
        self._safe_print(f"文本模型: {self.text_model}")
        self._safe_print(f"API地址: {self.base_url}")
        self._safe_print(f"日期: {self.start_date.strftime('%Y年%m月%d日')}")
        self._safe_print(f"输出格式: {self.output_format}")
        self._safe_print(f"条目多样性: {item_diversity}, 内容多样性: {content_diversity}")

    @staticmethod
    def _safe_print(msg: str):
        """安全打印，处理编码异常（如emoji等无法用GBK编码的字符）"""
        try:
            print(msg)
        except UnicodeEncodeError:
            # 移除无法编码的字符后重试
            cleaned = msg.encode('gbk', errors='ignore').decode('gbk', errors='ignore')
            print(cleaned)

    def _parse_date(self, date_str: str) -> datetime:
        """解析日期字符串"""
        if date_str is None or date_str.strip() == '':
            return datetime.now().replace(hour=0, minute=0, second=0, microsecond=0)
        formats = ['%Y-%m-%d', '%Y%m%d', '%Y/%m/%d']
        for fmt in formats:
            try:
                return datetime.strptime(date_str, fmt).replace(hour=0, minute=0, second=0, microsecond=0)
            except ValueError:
                continue
        self._safe_print(f"警告: 无法解析日期 '{date_str}'，使用今天")
        return datetime.now().replace(hour=0, minute=0, second=0, microsecond=0)

    # ========================================
    # Step 1: 图片读取与视觉理解
    # ========================================

    def scan_images(self, image_dir: str) -> List[str]:
        """
        扫描图片目录，返回所有图片文件路径

        Args:
            image_dir: 图片目录路径

        Returns:
            List[str]: 图片文件路径列表
        """
        if not os.path.exists(image_dir):
            raise FileNotFoundError(f"图片目录不存在: {image_dir}")

        image_files = []
        for filename in sorted(os.listdir(image_dir)):
            ext = os.path.splitext(filename)[1].lower()
            if ext in self.SUPPORTED_IMAGE_FORMATS:
                image_files.append(os.path.join(image_dir, filename))

        if not image_files:
            raise ValueError(f"图片目录中没有找到图片文件: {image_dir}")

        self._safe_print(f"共找到 {len(image_files)} 张图片")
        return image_files

    MAX_IMAGE_SIZE = 4 * 1024 * 1024  # 4MB

    def encode_image_base64(self, image_path: str) -> str:
        """将图片编码为base64字符串，超过4MB自动压缩"""
        file_size = os.path.getsize(image_path)
        if file_size <= self.MAX_IMAGE_SIZE:
            with open(image_path, 'rb') as f:
                return base64.b64encode(f.read()).decode('utf-8')

        try:
            from PIL import Image
            import io
            img = Image.open(image_path)
            scale = (self.MAX_IMAGE_SIZE / file_size) ** 0.5
            new_w = int(img.width * scale)
            new_h = int(img.height * scale)
            img = img.resize((new_w, new_h), Image.LANCZOS)
            buf = io.BytesIO()
            img.save(buf, format='JPEG', quality=85)
            self._safe_print(f"      图片压缩: {file_size//1024//1024}MB -> {buf.tell()//1024}KB ({img.width}x{img.height})")
            return base64.b64encode(buf.getvalue()).decode('utf-8')
        except ImportError:
            self._safe_print(f"      警告: Pillow未安装，无法压缩大图片，尝试原图发送")
            with open(image_path, 'rb') as f:
                return base64.b64encode(f.read()).decode('utf-8')

    def get_image_mime_type(self, image_path: str) -> str:
        """获取图片MIME类型"""
        ext = os.path.splitext(image_path)[1].lower()
        mime_map = {
            '.jpg': 'image/jpeg', '.jpeg': 'image/jpeg',
            '.png': 'image/png', '.bmp': 'image/bmp',
            '.webp': 'image/webp', '.gif': 'image/gif',
            '.tiff': 'image/tiff', '.tif': 'image/tiff'
        }
        return mime_map.get(ext, 'image/png')

    def understand_image(self, image_path: str, retry_count: int = 3) -> Optional[str]:
        """
        调用视觉模型理解图片内容

        Args:
            image_path: 图片文件路径
            retry_count: 重试次数

        Returns:
            str: 图片内容描述，失败返回None
        """
        image_base64 = self.encode_image_base64(image_path)
        file_size = os.path.getsize(image_path)
        mime_type = 'image/jpeg' if file_size > self.MAX_IMAGE_SIZE else self.get_image_mime_type(image_path)

        for attempt in range(retry_count):
            try:
                response = self.client.chat.completions.create(
                    model=self.vision_model,
                    messages=[{
                        "role": "user",
                        "content": [
                            {
                                "type": "image_url",
                                "image_url": {
                                    "url": f"data:{mime_type};base64,{image_base64}"
                                }
                            },
                            {
                                "type": "text",
                                "text": self.VISION_PROMPT
                            }
                        ]
                    }],
                    temperature=0.3,
                    max_tokens=2000
                )

                if response and response.choices and len(response.choices) > 0:
                    msg = response.choices[0].message
                    content = msg.content
                    # 兼容GLM的reasoning_content
                    if (content is None or not content.strip()) and hasattr(msg, 'reasoning_content'):
                        content = msg.reasoning_content
                    if content and content.strip():
                        return content.strip()

            except Exception as e:
                self._safe_print(f"  视觉理解失败 (尝试 {attempt + 1}/{retry_count}): {e}")

            if attempt < retry_count - 1:
                import time
                time.sleep(2)

        return None

    # ========================================
    # Step 2: 样例匹配
    # ========================================

    def match_sample_by_keywords(self, image_description: str) -> Optional[str]:
        """
        通过关键词规则匹配样例

        Args:
            image_description: 图片描述

        Returns:
            str: 匹配的样例文件名，无法确定时返回None
        """
        scores = {}
        desc_lower = image_description.lower()

        for sample_name, category in self.SAMPLE_CATEGORIES.items():
            score = 0
            for keyword in category["keywords"]:
                if keyword in image_description:
                    score += 1
                # 英文关键词匹配
                if keyword.lower() in desc_lower:
                    score += 1
            scores[sample_name] = score

        # 找最高分
        if scores:
            best = max(scores, key=scores.get)
            if scores[best] >= 2:  # 至少匹配2个关键词才认为可信
                return best

        return None

    def match_sample_by_llm(self, image_description: str,
                            samples: List[Tuple[str, str]]) -> Optional[str]:
        """
        通过LLM匹配样例（当关键词匹配不可信时使用）

        Args:
            image_description: 图片描述
            samples: 样例列表 [(文件名, 内容), ...]

        Returns:
            str: 匹配的样例文件名
        """
        # 构建样例描述
        sample_descs = []
        for name, category in self.SAMPLE_CATEGORIES.items():
            sample_descs.append(f"- {name}: {category['type']} - {category['desc']}")

        prompt = self.MATCH_PROMPT_TEMPLATE.format(
            image_description=image_description,
            sample_descriptions='\n'.join(sample_descs)
        )

        try:
            response = self.client.chat.completions.create(
                model=self.text_model,
                messages=[
                    {"role": "user", "content": prompt}
                ],
                temperature=0.1,
                max_tokens=50
            )

            if response and response.choices:
                msg = response.choices[0].message
                content = msg.content
                if (content is None or not content.strip()) and hasattr(msg, 'reasoning_content'):
                    content = msg.reasoning_content
                if content:
                    # 提取文件名
                    match = re.search(r'(\d+\.txt)', content.strip())
                    if match:
                        matched_name = match.group(1)
                        # 验证是否存在
                        sample_names = [s[0] for s in samples]
                        if matched_name in sample_names:
                            return matched_name

        except Exception as e:
            self._safe_print(f"  LLM样例匹配失败: {e}")

        return None

    def match_sample(self, image_description: str,
                     samples: List[Tuple[str, str]],
                     specified_sample: str = None) -> Tuple[str, str]:
        """
        匹配最合适的样例模板

        Args:
            image_description: 图片描述
            samples: 样例列表
            specified_sample: 手动指定的样例名（优先使用）

        Returns:
            Tuple[str, str]: (样例名, 样例内容)
        """
        # 手动指定优先
        if specified_sample:
            for name, text in samples:
                if name == specified_sample:
                    return (name, text)
            self._safe_print(f"  警告: 指定样例 {specified_sample} 不存在，使用自动匹配")

        # 关键词匹配
        matched_name = self.match_sample_by_keywords(image_description)

        # 关键词不够可信时，调用LLM
        if matched_name is None:
            matched_name = self.match_sample_by_llm(image_description, samples)

        # 兜底：默认使用 6.txt（舰船综合情况，最适合卫星图）
        if matched_name is None:
            matched_name = "6.txt"

        # 查找样例内容
        for name, text in samples:
            if name == matched_name:
                return (name, text)

        # 如果匹配的样例不在列表中，使用第一个
        return samples[0]

    # ========================================
    # Step 3: 报文生成
    # ========================================

    def generate_report(self, image_description: str, image_filename: str,
                        sample_text: str, sample_name: str,
                        retry_count: int = 3) -> Optional[Dict[str, str]]:
        """
        基于图片描述和样例模板生成报文

        Args:
            image_description: 图片内容描述
            image_filename: 图片文件名
            sample_text: 样例模板文本
            sample_name: 样例名称
            retry_count: 重试次数

        Returns:
            Dict: {'title': 标题, 'content': 正文}，失败返回None
        """
        # 获取候选池样本
        pool_samples = CandidatePoolManager.get_random_pool_samples(
            time_count=self.diversity_params['time_count'],
            location_count=self.diversity_params['location_count'],
            person_count=self.diversity_params['person_count'],
            org_count=self.diversity_params['org_count'],
            equipment_count=self.diversity_params['equipment_count'],
            action_count=self.diversity_params['action_count'],
            target_date=self.current_date
        )

        # 条目增加指令
        extra_min, extra_max = self.diversity_params['extra_items_range']
        extra_count = random.randint(extra_min, extra_max)
        if extra_count > 0:
            extra_items_instruction = f"可在样例基础上增加{extra_count}个相关事件条目"
        else:
            extra_items_instruction = "保持样例原有条目数量"

        # 当前时间信息
        current_date_info = pool_samples.get('current_date_info')
        if current_date_info:
            current_date_section = f"\n【当前时间】\n今天是{current_date_info['date_str']}（{current_date_info['weekday']}），请优先使用当前日期。\n"
        else:
            current_date_section = ""

        # 构建用户提示词
        user_prompt = self.USER_PROMPT_TEMPLATE_IMAGE.format(
            image_description=image_description,
            image_filename=image_filename,
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
                    model=self.text_model,
                    messages=[
                        {"role": "system", "content": self.IMAGE_SYSTEM_PROMPT},
                        {"role": "user", "content": user_prompt}
                    ],
                    temperature=self.diversity_params['temperature'],
                    max_tokens=4000
                )

                if response and response.choices and len(response.choices) > 0:
                    msg = response.choices[0].message
                    content = msg.content
                    if (content is None or not content.strip()) and hasattr(msg, 'reasoning_content'):
                        content = msg.reasoning_content
                    if content and content.strip():
                        result = self._parse_title_and_content(content.strip())
                        if result:
                            return result
                        else:
                            self._safe_print(f"  解析标题失败 (尝试 {attempt + 1}/{retry_count})")

            except Exception as e:
                self._safe_print(f"  报文生成失败 (尝试 {attempt + 1}/{retry_count}): {e}")

            if attempt < retry_count - 1:
                import time
                time.sleep(2)

        return None

    def _parse_title_and_content(self, text: str) -> Optional[Dict[str, str]]:
        """从生成文本中解析标题和正文"""
        title_match = re.match(r'【标题】(.+?)(?:\n|$)', text)
        if title_match:
            title = title_match.group(1).strip()
            remaining = text[title_match.end():].strip()
            return {'title': title, 'content': remaining}
        else:
            lines = text.split('\n', 1)
            if len(lines) >= 2:
                first_line = lines[0].strip()
                title = re.sub(r'^【.*?】', '', first_line).strip()
                content = lines[1].strip()
                if title and content:
                    return {'title': title, 'content': content}
        return None

    # ========================================
    # 结果保存
    # ========================================

    def _sanitize_filename(self, filename: str) -> str:
        """清理文件名中的非法字符"""
        illegal_chars = ['<', '>', ':', '"', '/', '\\', '|', '?', '*']
        safe_name = filename
        for char in illegal_chars:
            safe_name = safe_name.replace(char, '_')
        safe_name = safe_name.strip('. ')
        return safe_name or "untitled"

    def _init_jsonl_file(self, output_dir: str) -> str:
        """初始化jsonl文件（支持断点续传，已有文件则追加）"""
        os.makedirs(output_dir, exist_ok=True)
        date_str = self.start_date.strftime("%Y%m%d")
        filename = f"image_data_{date_str}.jsonl"
        self.jsonl_filepath = os.path.join(output_dir, filename)
        if os.path.exists(self.jsonl_filepath):
            existing = self._load_processed_images(self.jsonl_filepath)
            self.jsonl_count = len(existing)
            self._safe_print(f"JSONL文件已存在，含 {self.jsonl_count} 条记录，将追加写入")
        else:
            with open(self.jsonl_filepath, 'w', encoding='utf-8') as f:
                f.write('')
            self._safe_print(f"JSONL文件初始化: {self.jsonl_filepath}")
        return self.jsonl_filepath

    def _load_processed_images(self, jsonl_path: str) -> set:
        """从已有JSONL文件中加载已处理的图片文件名"""
        processed = set()
        try:
            with open(jsonl_path, 'r', encoding='utf-8') as f:
                for line in f:
                    line = line.strip()
                    if line:
                        data = json.loads(line)
                        if '图片' in data:
                            processed.add(data['图片'])
        except Exception:
            pass
        return processed

    def save_result(self, output_dir: str, image_filename: str,
                    result: Dict[str, str], index: int) -> str:
        """
        保存生成结果

        Args:
            output_dir: 输出目录
            image_filename: 图片文件名
            result: {'title': 标题, 'content': 正文}
            index: 序号

        Returns:
            str: 保存路径
        """
        title = result['title']
        content = result['content']
        # 从 start_date ~ end_date 范围内随机选择一个日期
        date_range_days = (self.end_date - self.start_date).days
        if date_range_days > 0:
            random_offset = random.randint(0, date_range_days)
            random_date = self.start_date + timedelta(days=random_offset)
        else:
            random_date = self.start_date
        time_str = random_date.strftime("%Y年%m月%d日")

        if self.output_format == 'jsonl':
            with self.lock:
                if self.jsonl_filepath is None:
                    self._init_jsonl_file(output_dir)
                data_item = {
                    "时间": time_str,
                    "标题": title,
                    "内容": content,
                    "图片": image_filename
                }
                with open(self.jsonl_filepath, 'a', encoding='utf-8') as f:
                    f.write(json.dumps(data_item, ensure_ascii=False) + '\n')
                self.jsonl_count += 1
                return f"jsonl_record_{self.jsonl_count}"
        else:
            # txt格式
            os.makedirs(output_dir, exist_ok=True)
            img_base = os.path.splitext(image_filename)[0]
            safe_title = self._sanitize_filename(title)
            if len(safe_title) > 40:
                safe_title = safe_title[:40]
            filename = f"{img_base}_{safe_title}.txt"
            filepath = os.path.join(output_dir, filename)

            with self.lock:
                with open(filepath, 'w', encoding='utf-8') as f:
                    f.write(f"【标题】{title}\n\n{content}")
            return filepath

    # ========================================
    # 样例读取
    # ========================================

    def read_sample_files(self, sample_dir: str) -> List[Tuple[str, str]]:
        """读取样例目录下的所有txt文件"""
        samples = []
        if not os.path.exists(sample_dir):
            raise FileNotFoundError(f"样例目录不存在: {sample_dir}")

        for filename in sorted(os.listdir(sample_dir)):
            if filename.endswith('.txt'):
                filepath = os.path.join(sample_dir, filename)
                try:
                    encodings = ['utf-8', 'gbk', 'gb2312', 'utf-16']
                    for encoding in encodings:
                        try:
                            with open(filepath, 'r', encoding=encoding) as f:
                                content = f.read()
                            if content:
                                samples.append((filename, content))
                                self._safe_print(f"读取样例: {filename} - {self.SAMPLE_CATEGORIES.get(filename, {}).get('type', '未分类')}")
                            break
                        except UnicodeDecodeError:
                            continue
                except Exception as e:
                    self._safe_print(f"读取样例失败 {filename}: {e}")

        if not samples:
            raise ValueError(f"样例目录中没有找到txt文件: {sample_dir}")

        return samples

    # ========================================
    # 主入口
    # ========================================

    def process_one_image(self, image_path: str, samples: List[Tuple[str, str]],
                          output_dir: str, index: int,
                          specified_sample: str = None) -> Dict[str, Any]:
        """
        处理单张图片：理解 → 匹配 → 生成 → 保存

        Args:
            image_path: 图片路径
            samples: 样例列表
            output_dir: 输出目录
            index: 序号
            specified_sample: 手动指定的样例

        Returns:
            Dict: 处理结果统计
        """
        image_filename = os.path.basename(image_path)
        result_info = {
            'image': image_filename,
            'status': 'failed',
            'sample_matched': None,
            'title': None
        }

        # Step 1: 视觉理解
        self._safe_print(f"\n  [{index}] 处理图片: {image_filename}")
        self._safe_print(f"      Step1: 调用视觉模型理解图片...")
        image_description = self.understand_image(image_path)

        if not image_description:
            self._safe_print(f"      [FAIL] 视觉理解失败，跳过")
            return result_info

        # 打印简要描述（截取前100字）
        brief = image_description[:100].replace('\n', ' ')
        self._safe_print(f"      图片描述: {brief}...")

        # Step 2: 样例匹配
        self._safe_print(f"      Step2: 匹配样例模板...")
        sample_name, sample_text = self.match_sample(
            image_description, samples, specified_sample
        )
        category_type = self.SAMPLE_CATEGORIES.get(sample_name, {}).get('type', '未知')
        self._safe_print(f"      匹配样例: {sample_name} ({category_type})")
        result_info['sample_matched'] = sample_name

        # Step 3: 生成报文
        self._safe_print(f"      Step3: 生成报文...")
        report = self.generate_report(
            image_description, image_filename, sample_text, sample_name
        )

        if not report:
            self._safe_print(f"      [FAIL] 报文生成失败")
            return result_info

        # 保存结果
        save_path = self.save_result(output_dir, image_filename, report, index)
        self._safe_print(f"      [OK] {report['title']}")

        result_info['status'] = 'success'
        result_info['title'] = report['title']
        return result_info

    def generate_all(self, image_dir: str, sample_dir: str, output_dir: str,
                     parallel: int = 1, specified_sample: str = None,
                     max_images: int = None) -> Dict[str, Any]:
        """
        主入口：处理所有图片

        Args:
            image_dir: 图片目录
            sample_dir: 样例目录
            output_dir: 输出目录
            parallel: 并行数
            specified_sample: 手动指定样例
            max_images: 最大处理图片数（None=全部）

        Returns:
            Dict: 生成统计信息
        """
        start_time = datetime.now()

        # 读取样例
        self._safe_print(f"\n读取样例目录: {sample_dir}")
        samples = self.read_sample_files(sample_dir)
        self._safe_print(f"共 {len(samples)} 个样例")

        # 扫描图片
        self._safe_print(f"\n扫描图片目录: {image_dir}")
        image_files = self.scan_images(image_dir)

        # 限制数量
        if max_images and max_images < len(image_files):
            image_files = image_files[:max_images]
            self._safe_print(f"限制处理数量: {max_images} 张")

        # 创建输出目录
        os.makedirs(output_dir, exist_ok=True)

        # 断点续传：跳过已处理的图片
        if self.output_format == 'jsonl':
            self._init_jsonl_file(output_dir)
            processed = self._load_processed_images(self.jsonl_filepath)
            if processed:
                before = len(image_files)
                image_files = [f for f in image_files if os.path.basename(f) not in processed]
                skipped = before - len(image_files)
                if skipped > 0:
                    self._safe_print(f"断点续传: 跳过已处理 {skipped} 张，剩余 {len(image_files)} 张")

        self._safe_print(f"\n{'='*60}")
        self._safe_print(f"开始处理 {len(image_files)} 张图片")
        self._safe_print(f"{'='*60}")

        # 处理每张图片
        all_results = []
        success_count = 0
        fail_count = 0

        if parallel <= 1:
            # 串行处理
            for idx, image_path in enumerate(image_files, 1):
                result = self.process_one_image(
                    image_path, samples, output_dir, idx, specified_sample
                )
                all_results.append(result)
                if result['status'] == 'success':
                    success_count += 1
                else:
                    fail_count += 1
        else:
            # 并行处理
            with ThreadPoolExecutor(max_workers=parallel) as executor:
                futures = {}
                for idx, image_path in enumerate(image_files, 1):
                    future = executor.submit(
                        self.process_one_image,
                        image_path, samples, output_dir, idx, specified_sample
                    )
                    futures[future] = idx

                for future in as_completed(futures):
                    result = future.result()
                    all_results.append(result)
                    if result['status'] == 'success':
                        success_count += 1
                    else:
                        fail_count += 1

        # 完成jsonl
        if self.output_format == 'jsonl' and self.jsonl_filepath:
            self._safe_print(f"\nJSONL文件已完成: {self.jsonl_filepath}，共 {self.jsonl_count} 条数据")

        # 生成统计
        end_time = datetime.now()
        duration = (end_time - start_time).total_seconds()

        summary = {
            'start_time': start_time.strftime('%Y-%m-%d %H:%M:%S'),
            'end_time': end_time.strftime('%Y-%m-%d %H:%M:%S'),
            'duration_seconds': duration,
            'image_count': len(image_files),
            'vision_model': self.vision_model,
            'text_model': self.text_model,
            'base_url': self.base_url,
            'output_format': self.output_format,
            'item_diversity': self.item_diversity,
            'content_diversity': self.content_diversity,
            'specified_sample': specified_sample,
            'results': all_results,
            'total_success': success_count,
            'total_failed': fail_count
        }

        # 保存报告
        report_path = os.path.join(output_dir, 'generation_report.json')
        with open(report_path, 'w', encoding='utf-8') as f:
            json.dump(summary, f, ensure_ascii=False, indent=2)
        self._safe_print(f"\n生成报告已保存: {report_path}")

        return summary

    def print_summary(self, summary: Dict):
        """打印生成摘要"""
        self._safe_print(f"\n{'='*60}")
        self._safe_print(f"图文报文仿真生成完成")
        self._safe_print(f"{'='*60}")
        self._safe_print(f"开始时间: {summary['start_time']}")
        self._safe_print(f"结束时间: {summary['end_time']}")
        self._safe_print(f"总耗时: {summary['duration_seconds']:.1f} 秒")
        self._safe_print(f"图片总数: {summary['image_count']}")
        self._safe_print(f"视觉模型: {summary['vision_model']}")
        self._safe_print(f"文本模型: {summary['text_model']}")
        if summary.get('specified_sample'):
            self._safe_print(f"指定样例: {summary['specified_sample']}")
        else:
            self._safe_print(f"样例匹配: 自动")
        self._safe_print(f"-" * 60)

        # 统计样例匹配分布
        match_dist = {}
        for r in summary['results']:
            if r['sample_matched']:
                match_dist[r['sample_matched']] = match_dist.get(r['sample_matched'], 0) + 1
        if match_dist:
            self._safe_print(f"样例匹配分布:")
            for name, count in sorted(match_dist.items()):
                cat = self.SAMPLE_CATEGORIES.get(name, {}).get('type', '未知')
                self._safe_print(f"  {name} ({cat}): {count} 张")

        self._safe_print(f"-" * 60)
        self._safe_print(f"生成成功: {summary['total_success']}")
        self._safe_print(f"生成失败: {summary['total_failed']}")
        self._safe_print(f"{'='*60}")


def main():
    """命令行入口"""
    parser = argparse.ArgumentParser(description='图文报文仿真生成器')
    parser.add_argument('image_dir', help='图片目录路径')
    parser.add_argument('sample_dir', help='样例目录路径')
    parser.add_argument('output_dir', help='输出目录路径')

    # 模型配置
    parser.add_argument('--vision_model', type=str, default=None,
                        help=f'视觉模型（默认: Qwen3-VL-235B-A22B-Instruct）')
    parser.add_argument('--text_model', type=str, default=None,
                        help=f'文本生成模型（默认: GLM-5）')
    parser.add_argument('--base_url', type=str, default=None,
                        help='API基础URL')
    parser.add_argument('--api_key', type=str, default=None,
                        help='API密钥')

    # 样例选择
    parser.add_argument('--sample', type=str, default=None,
                        help='手动指定样例文件名（如 4.txt），不指定则自动匹配')

    # 生成参数
    parser.add_argument('--parallel', type=int, default=1,
                        help='并行数（默认1）')
    parser.add_argument('--max_images', type=int, default=None,
                        help='最大处理图片数（默认全部）')
    parser.add_argument('--output_format', type=str, default='jsonl',
                        choices=['txt', 'jsonl'],
                        help='输出格式（默认jsonl）')

    # 多样性参数
    parser.add_argument('--item_diversity', type=str, default='low',
                        choices=['low', 'medium', 'high'],
                        help='条目多样性（默认low）')
    parser.add_argument('--content_diversity', type=str, default='high',
                        choices=['low', 'medium', 'high'],
                        help='内容多样性（默认high）')

    # 日期参数
    parser.add_argument('--start_date', type=str, default=None,
                        help='开始日期（格式：YYYY-MM-DD），默认今天')
    parser.add_argument('--end_date', type=str, default=None,
                        help='结束日期（格式：YYYY-MM-DD），默认与start_date相同。每张图片随机分配范围内的日期')

    args = parser.parse_args()

    # 创建生成器
    generator = ImageDataGenerator(
        api_key=args.api_key,
        base_url=args.base_url,
        text_model=args.text_model,
        vision_model=args.vision_model,
        item_diversity=args.item_diversity,
        content_diversity=args.content_diversity,
        start_date=args.start_date,
        end_date=args.end_date or args.start_date,  # 默认与start_date相同
        output_format=args.output_format
    )

    # 执行生成
    summary = generator.generate_all(
        image_dir=args.image_dir,
        sample_dir=args.sample_dir,
        output_dir=args.output_dir,
        parallel=args.parallel,
        specified_sample=args.sample,
        max_images=args.max_images
    )

    # 打印摘要
    generator.print_summary(summary)


if __name__ == '__main__':
    main()
