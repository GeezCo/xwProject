"""
文本要素抽取算法

功能说明：
1. 读取txt文件的文本内容
2. 使用NLP技术抽取文本中的关键要素：
   - 时间：日期、时刻、时间段等
   - 地点：国家、城市、具体位置等
   - 主体：人物、组织、机构等
   - 行为：动词短语、事件描述等
3. 将抽取结果保存为JSON文件

依赖：pip install jieba
"""

import json
import re
import os
from datetime import datetime
import jieba
import jieba.posseg as pseg


class ElementExtractor:
    """文本要素抽取器"""

    def __init__(self):
        """初始化抽取器，加载自定义词典和规则"""
        # 时间正则表达式模式
        self.time_patterns = [
            # 完整日期格式
            r'\d{4}年\d{1,2}月\d{1,2}日',
            r'\d{4}-\d{1,2}-\d{1,2}',
            r'\d{4}/\d{1,2}/\d{1,2}',
            r'\d{4}\.\d{1,2}\.\d{1,2}',
            # 年月格式
            r'\d{4}年\d{1,2}月',
            r'\d{4}年',
            # 月日格式
            r'\d{1,2}月\d{1,2}日',
            # 具体时间
            r'\d{1,2}时\d{1,2}分',
            r'\d{1,2}:\d{2}',
            # 时间段
            r'\d{4}年\d{1,2}月\d{1,2}日至\d{1,2}日',
            r'\d{1,2}月\d{1,2}日至\d{1,2}日',
            # 相对时间
            r'今天|昨天|明天|前天|后天',
            r'本周|上周|下周',
            r'本月|上月|下月',
            r'今年|去年|明年',
            r'近日|近期|日前|当天|当日',
            r'凌晨|上午|中午|下午|傍晚|晚上|深夜',
            r'年初|年中|年末|季度|上半年|下半年',
        ]

        # 地点指示词
        self.location_indicators = [
            '在', '位于', '来自', '前往', '到达', '抵达', '访问',
            '于', '到', '赴', '驻', '处于', '所处'
        ]

        # 地点后缀词
        self.location_suffixes = [
            '省', '市', '县', '区', '镇', '乡', '村',
            '州', '郡', '自治区', '特别行政区',
            '国', '地区', '区域', '地带',
            '岛', '半岛', '群岛', '群岛',
            '山', '河', '湖', '海', '洋',
            '街道', '路', '大道', '大街', '巷',
            '机场', '港口', '码头', '车站', '基地',
            '大厦', '大楼', '中心', '广场', '公园',
            '学校', '医院', '银行', '公司', '工厂',
            '军营', '基地', '战区', '前线'
        ]

        # 行为动词列表
        self.action_verbs = [
            # 军事行动
            '进攻', '防守', '攻击', '打击', '轰炸', '突袭', '伏击',
            '占领', '撤退', '包围', '突围', '登陆', '空降',
            '演习', '训练', '部署', '调防', '巡逻', '侦察',
            # 政治行为
            '访问', '会晤', '会谈', '谈判', '签署', '发表',
            '宣布', '声明', '抗议', '谴责', '制裁', '断交',
            '选举', '投票', '任命', '辞职', '罢免',
            # 经济行为
            '投资', '收购', '合并', '破产', '上市', '融资',
            '进口', '出口', '贸易', '合作', '签约',
            # 社会行为
            '召开', '举办', '举行', '开展', '组织', '参与',
            '成立', '解散', '建设', '拆除', '修复', '改造',
            # 人物行为
            '说', '表示', '认为', '指出', '强调', '呼吁',
            '承诺', '警告', '威胁', '要求', '建议', '提议',
            '支持', '反对', '批评', '赞扬', '感谢', '祝贺'
        ]

        # 加载自定义词典
        self._load_custom_dict()

    def _load_custom_dict(self):
        """加载自定义词典，提高分词准确性"""
        # 添加常见地名
        locations = [
            '北京', '上海', '广州', '深圳', '香港', '澳门', '台湾',
            '华盛顿', '纽约', '洛杉矶', '旧金山', '芝加哥',
            '东京', '首尔', '平壤', '莫斯科', '伦敦', '巴黎', '柏林',
            '中东', '亚太', '欧洲', '非洲', '美洲', '东南亚',
            '南海', '东海', '黄海', '台海', '海峡',
            '太平洋', '大西洋', '印度洋'
        ]
        for loc in locations:
            jieba.add_word(loc, tag='ns')

        # 添加常见机构名
        organizations = [
            '联合国', '北约', '欧盟', '东盟', 'G7', 'G20',
            '美国国防部', '白宫', '五角大楼',
            '中国国防部', '外交部', '国防部',
            '美联储', '世界银行', '国际货币基金组织'
        ]
        for org in organizations:
            jieba.add_word(org, tag='nt')

    def read_txt_file(self, file_path):
        """
        读取txt文件内容

        Args:
            file_path: txt文件路径

        Returns:
            str: 文件文本内容
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
            except FileNotFoundError:
                raise FileNotFoundError(f"文件不存在: {file_path}")

        raise ValueError(f"无法识别文件编码，尝试过: {encodings}")

    def extract_time(self, text):
        """
        抽取时间要素

        Args:
            text: 输入文本

        Returns:
            list: 时间列表
        """
        times = []

        for pattern in self.time_patterns:
            matches = re.findall(pattern, text)
            times.extend(matches)

        # 使用jieba识别时间词性
        words = pseg.cut(text)
        for word, flag in words:
            if flag == 't':  # 时间词性标记
                if word not in times and len(word) > 1:
                    times.append(word)

        # 去重并保持顺序
        seen = set()
        unique_times = []
        for t in times:
            if t not in seen:
                seen.add(t)
                unique_times.append(t)

        return unique_times

    def extract_location(self, text):
        """
        抽取地点要素

        Args:
            text: 输入文本

        Returns:
            list: 地点列表
        """
        locations = []

        # 使用jieba识别地名
        words = pseg.cut(text)
        for word, flag in words:
            # ns: 地名, nz: 其他专名, f: 方位词
            if flag in ['ns', 'nz']:
                if len(word) >= 2:
                    locations.append(word)
            # 检查地点后缀
            for suffix in self.location_suffixes:
                if word.endswith(suffix) and len(word) >= 2:
                    locations.append(word)
                    break

        # 使用正则匹配带有地点指示词的位置
        for indicator in self.location_indicators:
            pattern = f'{indicator}([\u4e00-\u9fa5]{{2,10}})'
            matches = re.findall(pattern, text)
            for match in matches:
                # 过滤掉明显不是地点的词
                if any(suffix in match for suffix in self.location_suffixes) or \
                   any(loc in match for loc in ['市', '省', '县', '区', '国', '岛', '海', '港', '基地']):
                    locations.append(match)

        # 去重并保持顺序
        seen = set()
        unique_locations = []
        for loc in locations:
            if loc not in seen:
                seen.add(loc)
                unique_locations.append(loc)

        return unique_locations

    def extract_subject(self, text):
        """
        抽取主体要素（人物、组织、机构等）

        Args:
            text: 输入文本

        Returns:
            dict: 包含人物和组织两个列表
        """
        persons = []
        organizations = []

        # 使用jieba识别实体
        words = pseg.cut(text)
        for word, flag in words:
            # nr: 人名, nrt: 音译人名
            if flag in ['nr', 'nrt']:
                if len(word) >= 2:
                    persons.append(word)
            # nt: 机构团体, nz: 其他专名
            elif flag == 'nt':
                if len(word) >= 2:
                    organizations.append(word)

        # 匹配中文人名模式（2-4个字，常见姓氏开头）
        common_surnames = '赵钱孙李周吴郑王冯陈褚卫蒋沈韩杨朱秦尤许何吕施张孔曹严华金魏陶姜戚谢邹喻柏水窦章云苏潘葛奚范彭郎鲁韦昌马苗凤花方俞任袁柳酆鲍史唐费廉岑薛雷贺倪汤滕殷罗毕郝邬安常乐于时傅皮卞齐康伍余元卜顾孟平黄和穆萧尹姚邵湛汪祁毛禹狄米贝明臧计伏成戴谈宋茅庞熊纪舒屈项祝董梁杜阮蓝闵席季麻强贾路娄危江童颜郭梅盛林刁钟徐邱骆高夏蔡田樊胡凌霍虞万支柯昝管卢莫经房裘缪干解应宗丁宣贲邓郁单杭洪包诸左石崔吉钮龚程嵇邢滑裴陆荣翁荀羊於惠甄曲家封芮羿储靳汲邴糜松井段富巫乌焦巴弓牧隗山谷车侯宓蓬全郗班仰秋仲伊宫宁仇栾暴甘钭厉戎祖武符刘景詹束龙叶幸司韶郜黎蓟薄印宿白怀蒲邰从鄂索咸籍赖卓蔺屠蒙池乔阴鬱胥能苍双闻莘党翟谭贡劳逄姬申扶堵冉宰郦雍卻璩桑桂濮牛寿通边扈燕冀郏浦尚农温别庄晏柴瞿阎充慕连茹习宦艾鱼容向古易慎戈廖庾终暨居衡步都耿满弘匡国文寇广禄阙东欧殳沃利蔚越夔隆师巩厍聂晁勾敖融冷訾辛阚那简饶空曾毋沙乜养鞠须丰巢关蒯相查后荆红游竺权逯盖益桓公'
        person_pattern = f'[{common_surnames}][\u4e00-\u9fa5]{{1,3}}'
        potential_persons = re.findall(person_pattern, text)
        persons.extend([p for p in potential_persons if len(p) >= 2 and len(p) <= 4])

        # 匹配组织机构模式
        org_patterns = [
            r'[\u4e00-\u9fa5]{2,8}(公司|集团|银行|基金|协会|组织|机构|部门|委员会|政府|议会|党派|军队|部队)',
            r'(联合国|北约|欧盟|东盟|G7|G20)',
            r'[\u4e00-\u9fa5]{2,6}(大学|学院|研究院|研究所|实验室)',
        ]
        for pattern in org_patterns:
            matches = re.findall(pattern, text)
            for match in matches:
                if isinstance(match, tuple):
                    match = match[0] + match[1] if len(match) > 1 else match[0]
                organizations.append(match)

        # 去重
        persons = list(dict.fromkeys(persons))
        organizations = list(dict.fromkeys(organizations))

        return {
            'persons': persons,
            'organizations': organizations
        }

    def extract_action(self, text):
        """
        抽取行为要素

        Args:
            text: 输入文本

        Returns:
            list: 行为/事件列表
        """
        actions = []

        # 使用jieba分词，提取动词短语
        words = pseg.cut(text)

        # 提取动词和动词短语
        prev_word = ''
        prev_flag = ''
        for word, flag in words:
            # v: 动词, vn: 动名词
            if flag in ['v', 'vn']:
                # 检查是否是预定义的行为动词
                if word in self.action_verbs:
                    actions.append(word)
                # 或者是2字以上的动词
                elif len(word) >= 2:
                    actions.append(word)

            # 组合动词+名词形成行为短语
            if prev_flag in ['v', 'vn'] and flag in ['n', 'nz', 'nt']:
                phrase = prev_word + word
                if len(phrase) >= 3:
                    actions.append(phrase)

            prev_word = word
            prev_flag = flag

        # 匹配常见的行为模式
        action_patterns = [
            r'(签署|发表|宣布|声明)([\u4e00-\u9fa5]{2,8})',
            r'(访问|会晤|会谈|谈判)([\u4e00-\u9fa5]{2,8})',
            r'(举行|召开|举办)([\u4e00-\u9fa5]{2,8})',
            r'(开展|进行|实施)([\u4e00-\u9fa5]{2,8})',
        ]
        for pattern in action_patterns:
            matches = re.findall(pattern, text)
            for match in matches:
                action_phrase = ''.join(match)
                actions.append(action_phrase)

        # 去重
        actions = list(dict.fromkeys(actions))

        return actions

    def extract_all(self, text):
        """
        抽取所有要素

        Args:
            text: 输入文本

        Returns:
            dict: 包含所有要素的字典
        """
        result = {
            'extraction_time': datetime.now().strftime('%Y-%m-%d %H:%M:%S'),
            'text_length': len(text),
            'elements': {
                'time': self.extract_time(text),
                'location': self.extract_location(text),
                'subject': self.extract_subject(text),
                'action': self.extract_action(text)
            },
            'statistics': {}
        }

        # 统计信息
        result['statistics'] = {
            'time_count': len(result['elements']['time']),
            'location_count': len(result['elements']['location']),
            'person_count': len(result['elements']['subject']['persons']),
            'organization_count': len(result['elements']['subject']['organizations']),
            'action_count': len(result['elements']['action']),
            'total_elements': (
                len(result['elements']['time']) +
                len(result['elements']['location']) +
                len(result['elements']['subject']['persons']) +
                len(result['elements']['subject']['organizations']) +
                len(result['elements']['action'])
            )
        }

        return result

    def save_to_json(self, result, output_path):
        """
        将结果保存为JSON文件

        Args:
            result: 抽取结果字典
            output_path: 输出JSON文件路径
        """
        with open(output_path, 'w', encoding='utf-8') as f:
            json.dump(result, f, ensure_ascii=False, indent=2)
        print(f"结果已保存到: {output_path}")


def process_file(input_path, output_path=None):
    """
    处理单个txt文件

    Args:
        input_path: 输入txt文件路径
        output_path: 输出json文件路径（可选，默认在输入文件同目录下生成）

    Returns:
        dict: 抽取结果
    """
    # 创建抽取器实例
    extractor = ElementExtractor()

    # 读取文件
    print(f"正在读取文件: {input_path}")
    text = extractor.read_txt_file(input_path)

    # 抽取要素
    print("正在抽取文本要素...")
    result = extractor.extract_all(text)

    # 添加文件信息
    result['source_file'] = os.path.basename(input_path)

    # 确定输出路径
    if output_path is None:
        base_name = os.path.splitext(input_path)[0]
        output_path = f"{base_name}_extracted.json"

    # 保存结果
    extractor.save_to_json(result, output_path)

    # 打印摘要
    print("\n" + "=" * 50)
    print("抽取结果摘要")
    print("=" * 50)
    print(f"源文件: {result['source_file']}")
    print(f"文本长度: {result['text_length']} 字符")
    print(f"时间要素: {result['statistics']['time_count']} 个")
    print(f"地点要素: {result['statistics']['location_count']} 个")
    print(f"人物主体: {result['statistics']['person_count']} 个")
    print(f"组织机构: {result['statistics']['organization_count']} 个")
    print(f"行为要素: {result['statistics']['action_count']} 个")
    print(f"总计: {result['statistics']['total_elements']} 个要素")
    print("=" * 50)

    return result


if __name__ == '__main__':
    import sys

    # 命令行参数处理
    if len(sys.argv) < 2:
        print("使用方法: python element_extractor.py <input.txt> [output.json]")
        print("示例: python element_extractor.py sample.txt result.json")
        sys.exit(1)

    input_file = sys.argv[1]
    output_file = sys.argv[2] if len(sys.argv) > 2 else None

    # 执行抽取
    process_file(input_file, output_file)