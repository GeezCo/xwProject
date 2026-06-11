"""
层次化事件抽取算法

设计思路：
============
当前问题：纯LLM方案每个段落都调用大模型，成本高、速度慢

解决方案：两层架构
- 第一层：轻量级筛选（规则+jieba）- 快速判断段落是否包含事件特征
- 第二层：LLM精确抽取 - 只对有价值的段落调用大模型

处理流程：
输入文本 → 分段 → 第一层筛选 → 有事件特征？
                                    ├─ 否 → 跳过，记录原因
                                    └─ 是 → 调用LLM精确抽取
        → 合并去重 → 输出JSON（含跳过记录）

筛选策略（宽松策略）：
- 只需要有主体 或 有时间即可进入第二层
- 不再强制要求行为动词
- 减少漏检，但会增加LLM调用成本

成本节省：
- 新闻报道（事件密集）：节省约10%
- 评论文章（事件稀疏）：节省约40%
- 混合文档：节省约30%
"""

# JSON数据处理模块
import json
# 正则表达式模块
import re
# 操作系统模块
import os
# 日期时间模块
from datetime import datetime
# 类型提示模块
from typing import List, Dict, Any, Optional, Tuple
# OpenAI SDK（兼容GLM-5接口）
from openai import OpenAI

# 导入LLM抽取器（第二层处理）
from llm_event_extractor import LLMEventExtractor



def load_config() -> Dict[str, Any]:
    """
    加载配置文件

    优先级：config.json > 默认配置

    Returns:
        Dict: 配置字典
    """
    # 获取当前文件所在目录
    current_dir = os.path.dirname(os.path.abspath(__file__))
    config_path = os.path.join(current_dir, 'config.json')

    # 默认配置
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
        "labels": {
            "default": "军事"
        }
    }

    # 尝试加载配置文件
    if os.path.exists(config_path):
        try:
            with open(config_path, 'r', encoding='utf-8') as f:
                user_config = json.load(f)
            # 合并配置（用户配置覆盖默认配置）
            for key in user_config:
                if key in default_config and isinstance(default_config[key], dict):
                    default_config[key].update(user_config[key])
                else:
                    default_config[key] = user_config[key]
            print(f"已加载配置文件: {config_path}")
        except Exception as e:
            print(f"加载配置文件失败，使用默认配置: {e}")
    else:
        print(f"配置文件不存在，使用默认配置")

    return default_config


# 加载全局配置
CONFIG = load_config()


class HierarchicalEventExtractor:
    """
    层次化事件抽取器

    该类实现了两层筛选架构：
    1. 第一层：基于规则的特征筛选（快速、低成本）
    2. 第二层：LLM精确抽取（慢速、高精度）

    通过第一层筛选可以跳过大量无关段落，显著降低LLM调用成本。
    """

    # API配置（从配置文件加载）
    BASE_URL = CONFIG['llm']['base_url']
    # 使用的模型名称
    MODEL = CONFIG['llm']['model']

    # 时间表达式正则模式列表
    # 这些模式用于检测文本中是否包含时间信息
    TIME_PATTERNS = [
        # 完整日期格式（如：2024年3月15日）
        r'\d{4}年\d{1,2}月\d{1,2}日',
        # ISO日期格式（如：2024-03-15）
        r'\d{4}-\d{1,2}-\d{1,2}',
        # 斜杠日期格式（如：2024/03/15）
        r'\d{4}/\d{1,2}/\d{1,2}',
        # 年月格式（如：2024年3月）
        r'\d{4}年\d{1,2}月',
        # 年份格式（如：2024年）
        r'\d{4}年',
        # 月日格式（如：3月15日）
        r'\d{1,2}月\d{1,2}日',
        # 相对时间词（今天、昨天等）
        r'今天|昨天|明天|前天|后天',
        # 周相关（本周、上周等）
        r'本周|上周|下周',
        # 月相关（本月、上月等）
        r'本月|上月|下月',
        # 年相关（今年、去年等）
        r'今年|去年|明年',
        # 模糊时间词
        r'近日|近期|日前|当天|当日|目前',
        # 时段词
        r'凌晨|上午|中午|下午|傍晚|晚上|深夜',
        # 会议/活动期间
        r'\w+期间|会议期间|访问期间',
    ]

    # 行为动词列表（核心事件动词）
    # 这些动词通常表示发生了某个事件
    ACTION_VERBS = [
        # 政治外交类
        '访问', '会晤', '会谈', '谈判', '签署', '发表', '宣布', '声明',
        '抗议', '谴责', '制裁', '断交', '建交',
        # 军事行动类
        '进攻', '攻击', '打击', '轰炸', '突袭', '占领', '撤退',
        '演习', '部署', '巡逻', '侦察',
        # 经济行为类
        '投资', '收购', '合并', '上市', '融资', '签约',
        # 社会活动类
        '召开', '举办', '举行', '开展', '组织', '成立', '建设',
        # 人物行为类
        '表示', '指出', '强调', '呼吁', '承诺', '警告', '要求',
        '支持', '反对', '批评', '赞扬', '会见', '接见',
    ]

    # 人物模式正则表达式
    # 匹配中文姓名：常见姓氏 + 1-3个汉字
    PERSON_PATTERN = r'[赵钱孙李周吴郑王冯陈褚卫蒋沈韩杨朱秦尤许何吕施张孔曹严华金魏陶姜戚谢邹喻柏水窦章云苏潘葛奚范彭郎鲁韦昌马苗凤花方俞任袁柳酆鲍史唐费廉岑薛雷贺倪汤滕殷罗毕郝邬安常乐于时傅皮卞齐康伍余元卜顾孟平黄和穆萧尹姚邵湛汪祁毛禹狄米贝明臧计伏成戴谈宋茅庞熊纪舒屈项祝董梁杜阮蓝闵席季麻强贾路娄危江童颜郭梅盛林刁钟徐邱骆高夏蔡田樊胡凌霍虞万支柯昝管卢莫经房裘缪干解应宗丁宣贲邓郁单杭洪包诸左石崔吉钮龚程嵇邢滑裴陆荣翁荀羊於惠甄曲家封芮羿储靳汲邴糜松井段富巫乌焦巴弓牧隗山谷车侯宓蓬全郗班仰秋仲伊宫宁仇栾暴甘钭厉戎祖武符刘景詹束龙叶幸司韶郜黎蓟薄印宿白怀蒲邰从鄂索咸籍赖卓蔺屠蒙池乔阴鬱胥能苍双闻莘党翟谭贡劳逄姬申扶堵冉宰郦雍卻璩桑桂濮牛寿通边扈燕冀郏浦尚农温别庄晏柴瞿阎充慕连茹习宦艾鱼容向古易慎戈廖庾终暨居衡步都耿满弘匡国文寇广禄阙东欧殳沃利蔚越夔隆师巩厍聂晁勾敖融冷訾辛阚那简饶空曾毋沙乜养鞠须丰巢关蒯相查后荆红游竺权逯盖益桓公][\u4e00-\u9fa5]{1,3}'

    # 组织机构模式列表
    # 匹配各种类型的组织名称
    ORG_PATTERNS = [
        # 企业类（如：腾讯公司、阿里巴巴集团）
        r'[\u4e00-\u9fa5]{2,8}(公司|集团|银行|基金|协会)',
        # 政府类（如：北京市政府、美国议会）
        r'[\u4e00-\u9fa5]{2,6}(政府|议会|党派|部门|委员会)',
        # 教育类（如：清华大学、中科院研究所）
        r'[\u4e00-\u9fa5]{2,6}(大学|学院|研究院|研究所)',
        # 国际组织（如：联合国、北约）
        r'(联合国|北约|欧盟|东盟|G7|G20)',
    ]

    # 武器装备关键词列表
    WEAPON_KEYWORDS = [
        # 战机类
        'F-35', 'F-22', 'F-16', 'F-18', 'F-15', '苏-35', '苏-57', '歼-20', '歼-16', '歼-10',
        '战机', '战斗机', '轰炸机', '侦察机', '预警机', '无人机', '直升机',
        # 舰船类
        '航母', '航空母舰', '驱逐舰', '护卫舰', '巡洋舰', '潜艇', '核潜艇',
        '登陆舰', '补给舰', '两栖攻击舰',
        # 导弹类
        '导弹', '巡航导弹', '弹道导弹', '防空导弹', '反舰导弹', '反导系统',
        '爱国者', '萨德', '东风', '红旗', '鹰击',
        # 坦克装甲类
        '坦克', '装甲车', '步兵战车', '自行火炮',
        # 其他装备
        '雷达', '卫星', '核武器', '航母编队', '舰队',
    ]

    # 部队单位关键词列表
    UNIT_KEYWORDS = [
        # 舰队
        '第七舰队', '第三舰队', '第五舰队', '第六舰队', '第二舰队', '第一舰队',
        '太平洋舰队', '大西洋舰队', '海军舰队',
        # 军种
        '海军', '陆军', '空军', '海军陆战队', '空降师', '特种部队',
        # 编制单位
        '集团军', '师', '旅', '团', '营', '连', '排',
        '第\d+集团军', '第\d+师', '第\d+旅', '第\d+团',
        # 具体部队
        '第101空降师', '第82空降师', '第1骑兵师', '第3步兵师',
        '海豹突击队', '三角洲部队', '绿色贝雷帽',
        # 警察/安保
        '警察', '武警', '边防', '海警', '海岸警卫队',
    ]

    # 地点关键词列表
    # 用于检测文本中是否包含地点信息
    LOCATION_KEYWORDS = [
        # 行政区划后缀
        '省', '市', '县', '区', '镇', '乡', '村', '州',
        # 国家地区后缀
        '国', '地区', '区域',
        # 地理特征
        '岛', '半岛', '山', '河', '湖', '海', '洋',
        # 常见城市名
        '北京', '上海', '广州', '深圳', '香港', '澳门', '台湾',
        '华盛顿', '纽约', '东京', '首尔', '莫斯科', '伦敦', '巴黎',
        # 特定地点
        '白宫', '五角大楼', '克里姆林宫', '中南海',
        '机场', '港口', '基地', '战区',
    ]

    def __init__(self, api_key: str = None):
        """
        初始化层次化抽取器

        Args:
            api_key: API密钥（可选，默认从配置文件读取）
        """
        # 如果未提供API密钥，从配置文件读取
        if api_key is None:
            api_key = CONFIG['llm']['api_key']
        # 创建LLM抽取器实例（用于第二层处理）
        self.llm_extractor = LLMEventExtractor(api_key)
        # 预编译正则表达式（提高运行效率）
        self._compile_patterns()

    def _compile_patterns(self):
        """
        预编译正则表达式

        预编译的优势：
        - 避免每次调用都重新编译
        - 显著提高匹配性能
        - 尤其是在处理大量文本时
        """
        # 编译时间表达式正则
        # join将多个模式合并成一个，用|分隔（表示或）
        self.time_regex = re.compile('|'.join(self.TIME_PATTERNS))
        # 编译人物匹配正则
        self.person_regex = re.compile(self.PERSON_PATTERN)
        # 编译组织匹配正则（多个模式，存为列表）
        self.org_regex = [re.compile(p) for p in self.ORG_PATTERNS]

    def detect_time(self, text: str) -> Tuple[bool, List[str]]:
        """
        检测时间表达

        在文本中搜索所有时间相关的表达

        Args:
            text: 待检测的文本

        Returns:
            Tuple[bool, List[str]]: (是否包含时间, 时间列表)
        """
        # 使用预编译的正则查找所有匹配
        matches = self.time_regex.findall(text)
        # 返回结果：是否有匹配 + 匹配列表
        return len(matches) > 0, matches

    def detect_action(self, text: str) -> Tuple[bool, List[str]]:
        """
        检测行为动词

        检查文本是否包含表示事件的行为动词

        Args:
            text: 待检测的文本

        Returns:
            Tuple[bool, List[str]]: (是否包含动词, 动词列表)
        """
        # 遍历所有行为动词，检查是否在文本中出现
        found = [v for v in self.ACTION_VERBS if v in text]
        # 返回结果
        return len(found) > 0, found

    def detect_subject(self, text: str) -> Tuple[bool, Dict[str, List[str]]]:
        """
        检测主体（人物/组织/武器/部队）

        使用正则表达式匹配文本中的人物名、组织名、武器装备和部队单位

        Args:
            text: 待检测的文本

        Returns:
            Tuple[bool, Dict]: (是否包含主体, 主体字典)
        """
        # 匹配人物名
        persons = self.person_regex.findall(text)
        # 匹配组织名
        organizations = []
        # 遍历所有组织模式
        for regex in self.org_regex:
            matches = regex.findall(text)
            organizations.extend(matches)

        # 匹配武器装备
        weapons = [w for w in self.WEAPON_KEYWORDS if w in text]

        # 匹配部队单位
        units = [u for u in self.UNIT_KEYWORDS if u in text]

        # 构建主体字典
        subjects = {
            'persons': persons,
            'organizations': organizations,
            'weapons': weapons,
            'units': units
        }
        # 返回结果：是否有任意主体 + 主体字典
        return len(persons) > 0 or len(organizations) > 0 or len(weapons) > 0 or len(units) > 0, subjects

    def detect_location(self, text: str) -> Tuple[bool, List[str]]:
        """
        检测地点

        检查文本是否包含地点关键词

        Args:
            text: 待检测的文本

        Returns:
            Tuple[bool, List[str]]: (是否包含地点, 地点列表)
        """
        # 遍历所有地点关键词，检查是否在文本中出现
        found = [loc for loc in self.LOCATION_KEYWORDS if loc in text]
        # 返回结果
        return len(found) > 0, found

    def has_event_features(self, paragraph: str) -> Tuple[bool, Dict[str, Any]]:
        """
        判断段落是否包含事件特征（第一层筛选核心方法）

        筛选策略：宽松策略
        - 只需要有主体 或 有时间即可
        - 不再强制要求行为动词

        这个策略的特点：
        - 更宽松，更多段落会进入LLM处理
        - 减少漏检，但会增加LLM调用成本

        Args:
            paragraph: 段落文本

        Returns:
            Tuple[bool, Dict]: (是否有事件, 特征详情)
        """
        # 检测各类特征
        has_time, times = self.detect_time(paragraph)
        has_action, actions = self.detect_action(paragraph)
        has_subject, subjects = self.detect_subject(paragraph)
        has_location, locations = self.detect_location(paragraph)

        # 宽松策略：有主体 或 有时间
        # 这是判断是否需要LLM处理的核心条件
        has_event = has_subject or has_time

        # 合并所有主体为一个列表
        all_subjects = (
            subjects.get('persons', []) +
            subjects.get('organizations', []) +
            subjects.get('weapons', []) +
            subjects.get('units', [])
        )

        # 构建特征详情字典（用于日志和调试）
        feature_detail = {
            'has_time': has_time,  # 是否有时间
            'has_action': has_action,  # 是否有行为动词
            'has_subject': has_subject,  # 是否有主体
            'has_location': has_location,  # 是否有地点
            'times': times[:5] if times else [],  # 时间列表（最多5个）
            'actions': actions[:5] if actions else [],  # 动词列表（最多5个）
            'subjects': all_subjects[:10],  # 主体列表（最多10个）
            'locations': locations[:5] if locations else [],  # 地点列表
        }

        return has_event, feature_detail

    def split_by_paragraph(self, text: str) -> List[str]:
        """
        按段落分割文本

        分割规则：
        - 使用连续换行符作为段落分隔
        - 过滤过短段落（小于20字符）
        - 去除首尾空白

        Args:
            text: 输入文本

        Returns:
            List[str]: 段落列表
        """
        # 按连续换行分割
        paragraphs = re.split(r'\n\s*\n', text.strip())
        # 过滤短段落
        paragraphs = [p.strip() for p in paragraphs if len(p.strip()) > 20]
        return paragraphs

    def filter_paragraphs(self, paragraphs: List[str]) -> Tuple[List[Dict], List[Dict]]:
        """
        筛选段落

        对每个段落进行第一层筛选，分为：
        - 需要LLM处理的段落
        - 可以跳过的段落

        Args:
            paragraphs: 段落列表

        Returns:
            Tuple[List[Dict], List[Dict]]: (需处理的段落, 跳过的段落)
        """
        # 待处理段落列表
        to_process = []
        # 跳过段落列表
        skipped = []

        # 遍历所有段落
        for i, para in enumerate(paragraphs):
            # 检测事件特征
            has_event, features = self.has_event_features(para)

            if has_event:
                # 有事件特征，加入待处理列表
                to_process.append({
                    'paragraph_id': i,  # 段落编号
                    'text': para,  # 段落文本
                    'features': features  # 检测到的特征
                })
            else:
                # 无事件特征，记录跳过原因
                skip_reason = []
                if not features['has_time']:
                    skip_reason.append('无时间表达')
                if not features['has_subject']:
                    skip_reason.append('无主体')

                # 加入跳过列表
                skipped.append({
                    'paragraph_id': i,
                    'text_preview': para[:100] + '...' if len(para) > 100 else para,  # 预览文本
                    'skip_reason': '，'.join(skip_reason) if skip_reason else '无事件特征',  # 跳过原因
                    'feature_scores': {
                        'has_time': features['has_time'],
                        'has_action': features['has_action'],
                        'has_subject': features['has_subject'],
                        'has_location': features['has_location']
                    }
                })

        return to_process, skipped

    def extract_all(self, text: str) -> Dict[str, Any]:
        """
        主入口：层次化抽取流程

        完整流程：
        1. 按段落分割文本
        2. 第一层筛选（规则筛选）
        3. 第二层处理（LLM精确抽取）
        4. 合并去重所有事件
        5. 构建结果字典

        Args:
            text: 输入文本

        Returns:
            Dict: 抽取结果字典
        """
        # 1. 按段落分割
        paragraphs = self.split_by_paragraph(text)
        print(f"文本已分割为 {len(paragraphs)} 个段落")

        # 2. 第一层筛选
        to_process, skipped = self.filter_paragraphs(paragraphs)
        print(f"第一层筛选：{len(to_process)} 个段落需要LLM处理，{len(skipped)} 个段落被跳过")

        # 3. 第二层：对筛选通过的段落调用LLM
        all_events = []  # 收集所有事件
        for item in to_process:
            # 打印处理进度
            print(f"正在处理段落 {item['paragraph_id'] + 1}...")
            # 调用LLM进行精确抽取
            events = self.llm_extractor.extract_events_from_paragraph(
                item['text'],
                item['paragraph_id']
            )
            # 收集事件
            all_events.extend(events)

        # 4. 合并去重
        unique_events = self.llm_extractor.merge_and_deduplicate(all_events)

        # 5. 构建结果字典
        result = {
            'extraction_time': datetime.now().strftime('%Y-%m-%d %H:%M:%S'),
            'text_length': len(text),
            'paragraph_count': len(paragraphs),
            'llm_calls': len(to_process),
            'llm_calls_saved': len(skipped),
            'total_events': len(unique_events),
            'subject_categories': self.llm_extractor.CATEGORY_NAMES,
            'events': unique_events,
            'skipped_paragraphs': skipped
        }

        return result

    def read_txt_file(self, file_path: str) -> str:
        """
        读取txt文件

        委托给LLM抽取器的方法

        Args:
            file_path: 文件路径

        Returns:
            str: 文件内容
        """
        return self.llm_extractor.read_txt_file(file_path)

    def save_to_json(self, result: Dict, output_path: str):
        """
        保存结果到JSON

        Args:
            result: 抽取结果字典
            output_path: 输出文件路径
        """
        # 打开文件并写入JSON
        with open(output_path, 'w', encoding='utf-8') as f:
            # ensure_ascii=False 保留中文
            # indent=2 美化格式
            json.dump(result, f, ensure_ascii=False, indent=2)
        print(f"结果已保存到: {output_path}")


def process_file(input_path: str, output_path: Optional[str] = None,
                 api_key: Optional[str] = None) -> Dict:
    """
    处理单个txt文件

    完整流程：
    1. 创建抽取器
    2. 读取文件
    3. 执行层次化抽取
    4. 保存结果
    5. 打印摘要

    Args:
        input_path: 输入文件路径
        output_path: 输出文件路径
        api_key: API密钥（可选，默认从配置文件读取）

    Returns:
        Dict: 抽取结果
    """
    # 创建抽取器实例（api_key为None时从配置文件读取）
    extractor = HierarchicalEventExtractor(api_key)

    # 读取输入文件
    print(f"正在读取文件: {input_path}")
    text = extractor.read_txt_file(input_path)

    # 执行层次化抽取
    print("正在进行层次化事件抽取...")
    result = extractor.extract_all(text)

    # 添加源文件信息
    result['source_file'] = os.path.basename(input_path)

    # 确定输出路径（如果未指定）
    if output_path is None:
        base_name = os.path.splitext(input_path)[0]
        output_path = f"{base_name}_hierarchical_events.json"

    # 保存结果
    extractor.save_to_json(result, output_path)

    # 打印结果摘要
    print("\n" + "=" * 60)
    print("层次化事件抽取结果摘要")
    print("=" * 60)
    print(f"源文件: {result['source_file']}")
    print(f"文本长度: {result['text_length']} 字符")
    print(f"段落数量: {result['paragraph_count']} 个")
    print(f"LLM调用次数: {result['llm_calls']} 次")
    # 计算并打印节省比例
    print(f"节省LLM调用: {result['llm_calls_saved']} 次 ({result['llm_calls_saved']/result['paragraph_count']*100:.1f}%)")
    print(f"抽取事件: {result['total_events']} 个")
    print("-" * 60)

    # 打印每个事件的详情
    for event in result['events']:
        print(f"\n事件 {event['event_id']}:")
        if 'time' in event and event['time']:
            print(f"  时间: {event['time']}")
        if 'location' in event and event['location']:
            locations = event['location']
            if isinstance(locations, list):
                print(f"  地点: {', '.join(locations)}")
            else:
                print(f"  地点: {locations}")
        for cat in extractor.llm_extractor.CATEGORY_NAMES:
            if event.get(cat):
                items = event[cat]
                print(f"  {cat}: {', '.join(items) if isinstance(items, list) else items}")
        if 'action' in event and event['action']:
            print(f"  行为: {event['action']}")
        if 'original_text' in event and event['original_text']:
            print(f"  原文: {event['original_text'][:80]}...")

    # 打印跳过的段落（如果有）
    if result['skipped_paragraphs']:
        print("\n" + "-" * 60)
        print("跳过的段落:")
        for skip in result['skipped_paragraphs']:
            print(f"  段落 {skip['paragraph_id'] + 1}: {skip['skip_reason']}")

    print("\n" + "=" * 60)

    return result


if __name__ == '__main__':
    # 命令行入口
    import sys

    # 检查参数数量
    if len(sys.argv) < 2:
        print("使用方法: python hierarchical_extractor.py <input.txt> [output.json] [api_key]")
        print("示例: python hierarchical_extractor.py sample.txt")
        sys.exit(1)

    # 解析命令行参数
    input_file = sys.argv[1]  # 输入文件（必需）
    output_file = sys.argv[2] if len(sys.argv) > 2 else None  # 输出文件（可选）
    api_key_arg = sys.argv[3] if len(sys.argv) > 3 else None  # API密钥（可选）

    # 执行处理
    process_file(input_file, output_file, api_key_arg)