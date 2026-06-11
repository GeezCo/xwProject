"""
数据生成候选池配置

用于提供随机化的候选内容，增强生成结果的多样性。

候选池分类：
1. 时间池 - 日期、时刻、时间段
2. 地点池 - 海域、城市、基地、港口
3. 人物池 - 各国政要、军事人物
4. 组织池 - 国家、军事机构
5. 装备池 - 飞机、舰船、导弹型号
6. 行为池 - 军事行动、外交活动
"""

import random
from datetime import datetime, timedelta
from typing import List, Dict, Any


# ============================================================
# 时间候选池
# ============================================================

class TimePool:
    """时间候选池 - 生成随机时间"""

    # 月份列表
    MONTHS = list(range(1, 13))

    # 日期列表（避免31日，部分月份没有）
    DAYS = list(range(1, 29))

    # 小时列表
    HOURS = list(range(0, 24))

    # 分钟列表（15分钟间隔）
    MINUTES = [0, 15, 30, 45]

    @classmethod
    def random_date(cls, year: int = None) -> str:
        """
        生成随机日期

        Args:
            year: 年份，默认当前年

        Returns:
            str: 格式如 "3月15日"
        """
        if year is None:
            year = datetime.now().year
        month = random.choice(cls.MONTHS)
        day = random.choice(cls.DAYS)
        return f"{month}月{day}日"

    @classmethod
    def random_date_with_year(cls, year: int = None) -> str:
        """
        生成带年份的随机日期

        Returns:
            str: 格式如 "2024年3月15日"
        """
        if year is None:
            year = datetime.now().year
        month = random.choice(cls.MONTHS)
        day = random.choice(cls.DAYS)
        return f"{year}年{month}月{day}日"

    @classmethod
    def random_time(cls) -> str:
        """
        生成随机时刻

        Returns:
            str: 格式如 "14时30分"
        """
        hour = random.choice(cls.HOURS)
        minute = random.choice(cls.MINUTES)
        return f"{hour}时{minute}分" if minute > 0 else f"{hour}时"

    @classmethod
    def random_time_range(cls) -> str:
        """
        生成随机时间段

        Returns:
            str: 格式如 "10时至14时"
        """
        start_hour = random.choice(cls.HOURS)
        end_hour = random.choice([h for h in cls.HOURS if h > start_hour]) if start_hour < 23 else start_hour
        return f"{start_hour}时至{end_hour}时"

    @classmethod
    def random_datetime_range(cls) -> str:
        """
        生成随机日期时间范围

        Returns:
            str: 格式如 "3月15日10时至14时"
        """
        return f"{cls.random_date()}{cls.random_time_range()}"

    @classmethod
    def get_sample_times(cls, count: int = 5) -> List[str]:
        """
        获取随机时间样本

        Args:
            count: 样本数量

        Returns:
            List[str]: 时间样本列表
        """
        samples = []
        for _ in range(count):
            samples.append(cls.random_date())
        for _ in range(count):
            samples.append(cls.random_time())
        for _ in range(count):
            samples.append(cls.random_time_range())
        return samples

    @classmethod
    def get_real_time_samples(cls, count: int = 5, days_range: int = 7) -> List[str]:
        """
        获取基于实时时间的时间样本

        Args:
            count: 样本数量
            days_range: 日期范围（前后各days_range天）

        Returns:
            List[str]: 时间样本列表
        """
        now = datetime.now()
        samples = []

        # 当前日期
        samples.append(f"{now.month}月{now.day}日")
        samples.append(f"{now.year}年{now.month}月{now.day}日")

        # 前后days_range天内的日期
        for i in range(-days_range, days_range + 1):
            if i == 0:
                continue
            dt = now + timedelta(days=i)
            samples.append(f"{dt.month}月{dt.day}日")

        # 当前时刻
        samples.append(f"{now.hour}时")
        samples.append(f"{now.hour}时{now.minute}分")

        # 时间段（当前时刻前后）
        for _ in range(count):
            start_hour = max(0, now.hour - random.randint(1, 6))
            end_hour = min(23, now.hour + random.randint(1, 6))
            if start_hour < end_hour:
                samples.append(f"{start_hour}时至{end_hour}时")

        return samples

    @classmethod
    def get_current_date_info(cls) -> Dict[str, Any]:
        """
        获取当前日期信息

        Returns:
            Dict: 当前日期信息
        """
        now = datetime.now()
        return {
            'year': now.year,
            'month': now.month,
            'day': now.day,
            'hour': now.hour,
            'minute': now.minute,
            'date_str': f"{now.year}年{now.month}月{now.day}日",
            'short_date_str': f"{now.month}月{now.day}日",
            'time_str': f"{now.hour}时{now.minute}分" if now.minute > 0 else f"{now.hour}时",
            'weekday': ['周一', '周二', '周三', '周四', '周五', '周六', '周日'][now.weekday()]
        }

    @classmethod
    def get_date_based_samples(cls, target_date: datetime, count: int = 5, days_range: int = 3) -> List[str]:
        """
        获取基于指定日期的时间样本

        Args:
            target_date: 目标日期
            count: 样本数量
            days_range: 日期范围（前后各days_range天）

        Returns:
            List[str]: 时间样本列表
        """
        samples = []

        # 目标日期
        samples.append(f"{target_date.month}月{target_date.day}日")
        samples.append(f"{target_date.year}年{target_date.month}月{target_date.day}日")

        # 前后days_range天内的日期
        for i in range(-days_range, days_range + 1):
            if i == 0:
                continue
            dt = target_date + timedelta(days=i)
            samples.append(f"{dt.month}月{dt.day}日")

        # 时刻样本
        for _ in range(count):
            hour = random.randint(0, 23)
            minute = random.choice([0, 15, 30, 45])
            if minute > 0:
                samples.append(f"{hour}时{minute}分")
            else:
                samples.append(f"{hour}时")

        # 时间段
        for _ in range(count):
            start_hour = random.randint(0, 22)
            end_hour = random.randint(start_hour + 1, 23)
            samples.append(f"{start_hour}时至{end_hour}时")

        return samples

    @classmethod
    def get_date_info(cls, target_date: datetime) -> Dict[str, Any]:
        """
        获取指定日期的信息

        Args:
            target_date: 目标日期

        Returns:
            Dict: 日期信息
        """
        return {
            'year': target_date.year,
            'month': target_date.month,
            'day': target_date.day,
            'hour': target_date.hour,
            'minute': target_date.minute,
            'date_str': f"{target_date.year}年{target_date.month}月{target_date.day}日",
            'short_date_str': f"{target_date.month}月{target_date.day}日",
            'time_str': f"{target_date.hour}时{target_date.minute}分" if target_date.minute > 0 else f"{target_date.hour}时",
            'weekday': ['周一', '周二', '周三', '周四', '周五', '周六', '周日'][target_date.weekday()]
        }


# ============================================================
# 地点候选池
# ============================================================

class LocationPool:
    """地点候选池"""

    # 海域
    SEAS = [
        "南海", "东海", "黄海", "台海", "台湾海峡",
        "波斯湾", "红海", "地中海", "阿拉伯海", "日本海",
        "菲律宾海", "关岛海域", "夏威夷海域"
    ]

    # 城市地区
    CITIES = [
        # 中国
        "北京", "上海", "广州", "深圳", "香港", "台北",
        # 美国
        "华盛顿", "纽约", "洛杉矶", "旧金山", "夏威夷", "关岛",
        # 日本
        "东京", "横须贺", "冲绳", "嘉手纳", "佐世保",
        # 其他
        "首尔", "平壤", "莫斯科", "伦敦", "巴黎", "柏林",
        "中东", "亚太地区", "东南亚", "欧洲"
    ]

    # 军事基地
    BASES = [
        # 美军基地
        "关岛安德森空军基地", "横须贺海军基地", "嘉手纳空军基地",
        "佐世保海军基地", "吉布提莱蒙尼尔营基地", "乌代德空军基地",
        "第五舰队总部", "第七舰队总部", "珍珠港海军基地",
        # 其他基地
        "横田空军基地", "三泽空军基地", "厚木海军航空站"
    ]

    # 港口
    PORTS = [
        "横须贺港", "佐世保港", "珍珠港", "新加坡港",
        "迪拜港", "巴林港", "吉布提港"
    ]

    # 空域
    AIRSPACES = [
        "东海防空识别区", "南海空域", "台湾周边空域",
        "日本海空域", "菲律宾海空域", "关岛空域",
        "相关空域", "争议空域", "第一岛链空域",
        "第二岛链空域", "西太平洋空域", "中东空域"
    ]

    # 国家
    COUNTRIES = [
        "美国", "中国", "日本", "韩国", "朝鲜",
        "俄罗斯", "伊朗", "以色列", "印度", "巴基斯坦",
        "英国", "法国", "德国", "澳大利亚", "菲律宾"
    ]

    @classmethod
    def random_sea(cls) -> str:
        """随机海域"""
        return random.choice(cls.SEAS)

    @classmethod
    def random_city(cls) -> str:
        """随机城市"""
        return random.choice(cls.CITIES)

    @classmethod
    def random_base(cls) -> str:
        """随机基地"""
        return random.choice(cls.BASES)

    @classmethod
    def random_port(cls) -> str:
        """随机港口"""
        return random.choice(cls.PORTS)

    @classmethod
    def random_airspace(cls) -> str:
        """随机空域"""
        return random.choice(cls.AIRSPACES)

    @classmethod
    def random_country(cls) -> str:
        """随机国家"""
        return random.choice(cls.COUNTRIES)

    @classmethod
    def get_sample_locations(cls, count: int = 8) -> List[str]:
        """
        获取随机地点样本

        Args:
            count: 每类地点的数量

        Returns:
            List[str]: 地点样本列表
        """
        samples = []
        samples.extend(random.sample(cls.SEAS, min(count, len(cls.SEAS))))
        samples.extend(random.sample(cls.CITIES, min(count, len(cls.CITIES))))
        samples.extend(random.sample(cls.BASES, min(count // 2, len(cls.BASES))))
        samples.extend(random.sample(cls.AIRSPACES, min(count // 2, len(cls.AIRSPACES))))
        return samples


# ============================================================
# 人物候选池
# ============================================================

class PersonPool:
    """人物候选池"""

    # 美国政要
    US_LEADERS = [
        "拜登", "哈里斯", "布林肯", "奥斯汀", "米利",
        "沙利文", "耶伦", "雷蒙多"
    ]

    # 中国政要
    CN_LEADERS = [
        "习近平", "李强", "王毅", "张又侠", "魏凤和",
        "布林", "刘建超"
    ]

    # 日本政要
    JP_LEADERS = [
        "岸田文雄", "岸信夫", "林芳正", "滨田靖一",
        "木原稔"
    ]

    # 其他国家领导人
    OTHER_LEADERS = [
        "普京", "泽连斯基", "马克龙", "朔尔茨", "苏纳克",
        "文在寅", "尹锡悦", "金正恩", "莫迪", "埃尔多安",
        "内塔尼亚胡", "莱希", "鲁哈尼"
    ]

    # 军事人物姓氏（用于生成虚拟人物）
    MILITARY_SURNAMES = [
        "张", "李", "王", "刘", "陈", "杨", "赵", "黄", "周", "吴",
        "Smith", "Johnson", "Williams", "Brown", "Jones"
    ]

    # 军事人物头衔
    MILITARY_TITLES = [
        "上将", "中将", "少将", "准将", "司令", "参谋长",
        "Admiral", "General", "Commander", "Captain"
    ]

    @classmethod
    def random_us_leader(cls) -> str:
        """随机美国政要"""
        return random.choice(cls.US_LEADERS)

    @classmethod
    def random_cn_leader(cls) -> str:
        """随机中国政要"""
        return random.choice(cls.CN_LEADERS)

    @classmethod
    def random_jp_leader(cls) -> str:
        """随机日本政要"""
        return random.choice(cls.JP_LEADERS)

    @classmethod
    def random_other_leader(cls) -> str:
        """随机其他国家领导人"""
        return random.choice(cls.OTHER_LEADERS)

    @classmethod
    def random_leader(cls) -> str:
        """随机领导人"""
        all_leaders = cls.US_LEADERS + cls.CN_LEADERS + cls.JP_LEADERS + cls.OTHER_LEADERS
        return random.choice(all_leaders)

    @classmethod
    def random_military_person(cls) -> str:
        """生成随机军事人物名"""
        surname = random.choice(cls.MILITARY_SURNAMES)
        title = random.choice(cls.MILITARY_TITLES)
        return f"{surname}{title}"

    @classmethod
    def get_sample_persons(cls, count: int = 5) -> List[str]:
        """
        获取随机人物样本

        Args:
            count: 每类人物的数量

        Returns:
            List[str]: 人物样本列表
        """
        samples = []
        samples.extend(random.sample(cls.US_LEADERS, min(count, len(cls.US_LEADERS))))
        samples.extend(random.sample(cls.CN_LEADERS, min(count, len(cls.CN_LEADERS))))
        samples.extend(random.sample(cls.JP_LEADERS, min(count, len(cls.JP_LEADERS))))
        samples.extend(random.sample(cls.OTHER_LEADERS, min(count, len(cls.OTHER_LEADERS))))
        return samples


# ============================================================
# 组织候选池
# ============================================================

class OrganizationPool:
    """组织候选池"""

    # 国家
    COUNTRIES = LocationPool.COUNTRIES

    # 军事组织
    MILITARY_ORGS = [
        "美军", "美国海军", "美国空军", "美国陆军",
        "日本自卫队", "日本海上自卫队", "日本航空自卫队",
        "韩国军队", "朝鲜人民军",
        "北约", "北约部队",
        "第七舰队", "第五舰队", "第三舰队",
        "太平洋舰队", "印太司令部"
    ]

    # 政治组织
    POLITICAL_ORGS = [
        "白宫", "五角大楼", "国务院", "国防部",
        "外交部", "国家安全委员会",
        "联合国", "欧盟", "东盟"
    ]

    # 分离势力组织（虚拟化）
    SEPARATIST_ORGS = [
        "分离势力组织A", "分裂组织B", "极端组织C",
        "境外势力D", "激进团体E"
    ]

    @classmethod
    def random_country(cls) -> str:
        """随机国家"""
        return random.choice(cls.COUNTRIES)

    @classmethod
    def random_military_org(cls) -> str:
        """随机军事组织"""
        return random.choice(cls.MILITARY_ORGS)

    @classmethod
    def random_political_org(cls) -> str:
        """随机政治组织"""
        return random.choice(cls.POLITICAL_ORGS)

    @classmethod
    def random_org(cls) -> str:
        """随机组织"""
        all_orgs = cls.MILITARY_ORGS + cls.POLITICAL_ORGS
        return random.choice(all_orgs)

    @classmethod
    def get_sample_orgs(cls, count: int = 5) -> List[str]:
        """
        获取随机组织样本

        Args:
            count: 每类组织的数量

        Returns:
            List[str]: 组织样本列表
        """
        samples = []
        samples.extend(random.sample(cls.COUNTRIES, min(count, len(cls.COUNTRIES))))
        samples.extend(random.sample(cls.MILITARY_ORGS, min(count, len(cls.MILITARY_ORGS))))
        samples.extend(random.sample(cls.POLITICAL_ORGS, min(count, len(cls.POLITICAL_ORGS))))
        return samples


# ============================================================
# 装备候选池
# ============================================================

class EquipmentPool:
    """军事装备候选池"""

    # 军用飞机
    AIRCRAFT = [
        # 无人机
        "MQ-9A死神无人机", "MQ-9A无人机", "RQ-4全球鹰无人机",
        "MQ-4C人鱼海神无人机",
        # 侦察机
        "P-8A海神反潜巡逻机", "P-8A反潜巡逻机", "P-8A巡逻机",
        "RC-135侦察机", "RC-135W侦察机",
        "EP-3E电子侦察机", "E-3预警机", "E-2D预警机",
        # 战斗机
        "F-35战斗机", "F-35B战斗机", "F-22战斗机",
        "F-16战斗机", "F-15战斗机", "F/A-18战斗机",
        # 轰炸机
        "B-52轰炸机", "B-1B轰炸机", "B-2轰炸机",
        # 运输机
        "C-130运输机", "C-17运输机",
        # 其他
        "E-8联合星指挥机", "P-3C猎户座巡逻机", "S-3北欧海盗反潜机"
    ]

    # 舰船
    SHIPS = [
        # 驱逐舰
        "阿利·伯克级驱逐舰", "驱逐舰", "导弹驱逐舰",
        # 巡洋舰
        "提康德罗加级巡洋舰", "巡洋舰", "导弹巡洋舰",
        # 航母
        "航空母舰", "核动力航母", "林肯号航母", "里根号航母",
        # 两栖舰
        "两栖攻击舰", "两栖登陆舰", "船坞登陆舰",
        # 潜艇
        "核潜艇", "攻击型核潜艇", "战略核潜艇",
        # 其他
        "补给舰", "护卫舰", "巡逻艇", "扫雷舰", "医疗船"
    ]

    # 导弹系统
    MISSILES = [
        "爱国者导弹", "爱国者防空系统", "萨德反导系统",
        "战斧巡航导弹", "鱼叉反舰导弹", "标准导弹",
        "红旗-9防空导弹", "红旗-19反导导弹", "鹰击-12反舰导弹",
        "东风-21D反舰弹道导弹", "东风-26中程弹道导弹",
        "AIM-120空空导弹", "AIM-9响尾蛇导弹", "SM-2防空导弹",
        "SM-6防空导弹", "AGM-158联合防区外导弹"
    ]

    # 舰船名称模板
    SHIP_NAMES = [
        # 美国航母
        "里根", "林肯", "华盛顿", "罗斯福", "杜鲁门",
        "卡尔·文森", "斯坦尼斯", "尼米兹", "艾森豪威尔",
        # 美国驱逐舰
        "麦凯恩", "马斯廷", "菲茨杰拉德", "斯特雷特",
        "威廉·劳伦斯", "斯普鲁恩斯", "钟云",
        # 美国巡洋舰
        "提康德罗加", "温赖特", "诺曼底", "蒙特雷",
        # 日本舰船
        "出云", "加贺", "日向", "伊势", "雾岛",
        "金刚", "爱宕", "摩耶", "朝日",
        # 其他
        "首尔", "世宗大王", "文武大王"
    ]

    @classmethod
    def random_aircraft(cls) -> str:
        """随机飞机型号"""
        return random.choice(cls.AIRCRAFT)

    @classmethod
    def random_ship(cls) -> str:
        """随机舰船类型"""
        return random.choice(cls.SHIPS)

    @classmethod
    def random_ship_name(cls) -> str:
        """随机舰船名称"""
        name = random.choice(cls.SHIP_NAMES)
        ship_type = random.choice([
            "驱逐舰", "巡洋舰", "航母", "护卫舰", "登陆舰",
            "两栖攻击舰", "两栖登陆舰", "核潜艇", "补给舰"
        ])
        return f'"{name}号"{ship_type}'

    @classmethod
    def random_missile(cls) -> str:
        """随机导弹系统"""
        return random.choice(cls.MISSILES)

    @classmethod
    def get_sample_equipment(cls, count: int = 5) -> List[str]:
        """
        获取随机装备样本

        Args:
            count: 每类装备的数量

        Returns:
            List[str]: 装备样本列表
        """
        samples = []
        samples.extend(random.sample(cls.AIRCRAFT, min(count, len(cls.AIRCRAFT))))
        samples.extend(random.sample(cls.SHIPS, min(count, len(cls.SHIPS))))
        samples.extend(random.sample(cls.MISSILES, min(count, len(cls.MISSILES))))
        return samples


# ============================================================
# 行为候选池
# ============================================================

class ActionPool:
    """行为动作候选池"""

    # 侦察类行为
    RECONNAISSANCE_ACTIONS = [
        "侦察", "抵近侦察", "监视", "巡逻", "跟踪",
        "广域监视", "目标识别", "情报收集", "电子侦察"
    ]

    # 军事行动
    MILITARY_ACTIONS = [
        "演习", "军事演习", "联合演习", "实战化训练",
        "部署", "战略部署", "前沿部署",
        "打击", "精确打击", "空袭", "导弹袭击",
        "拦截", "防空拦截", "反导拦截"
    ]

    # 海上行动
    NAVAL_ACTIONS = [
        "巡航", "自由航行", "穿越海峡", "过航",
        "编队航行", "联合巡航", "海上巡逻",
        "港口访问", "补给休整", "返港休整"
    ]

    # 空中行动
    AIR_ACTIONS = [
        "起飞", "升空", "转场飞行", "巡航飞行",
        "降落", "返航", "空中加油"
    ]

    # 外交行为
    DIPLOMATIC_ACTIONS = [
        "会晤", "举行会谈", "通电话", "发表声明",
        "签署协议", "达成共识", "交换意见",
        "抗议", "谴责", "召见大使"
    ]

    # 任务类型
    MISSION_TYPES = [
        "侦察任务", "监视任务", "巡逻任务", "训练任务",
        "作战任务", "演习任务", "支援任务", "后勤任务"
    ]

    @classmethod
    def random_reconnaissance(cls) -> str:
        """随机侦察行为"""
        return random.choice(cls.RECONNAISSANCE_ACTIONS)

    @classmethod
    def random_military(cls) -> str:
        """随机军事行为"""
        return random.choice(cls.MILITARY_ACTIONS)

    @classmethod
    def random_naval(cls) -> str:
        """随机海上行为"""
        return random.choice(cls.NAVAL_ACTIONS)

    @classmethod
    def random_air(cls) -> str:
        """随机空中行为"""
        return random.choice(cls.AIR_ACTIONS)

    @classmethod
    def random_diplomatic(cls) -> str:
        """随机外交行为"""
        return random.choice(cls.DIPLOMATIC_ACTIONS)

    @classmethod
    def random_mission(cls) -> str:
        """随机任务类型"""
        return random.choice(cls.MISSION_TYPES)

    @classmethod
    def get_sample_actions(cls, count: int = 5) -> List[str]:
        """
        获取随机行为样本

        Args:
            count: 每类行为的数量

        Returns:
            List[str]: 行为样本列表
        """
        samples = []
        samples.extend(random.sample(cls.RECONNAISSANCE_ACTIONS, min(count, len(cls.RECONNAISSANCE_ACTIONS))))
        samples.extend(random.sample(cls.MILITARY_ACTIONS, min(count, len(cls.MILITARY_ACTIONS))))
        samples.extend(random.sample(cls.NAVAL_ACTIONS, min(count, len(cls.NAVAL_ACTIONS))))
        samples.extend(random.sample(cls.DIPLOMATIC_ACTIONS, min(count, len(cls.DIPLOMATIC_ACTIONS))))
        return samples


# ============================================================
# 综合候选池管理器
# ============================================================

class CandidatePoolManager:
    """候选池管理器 - 统一管理所有候选池"""

    @classmethod
    def get_random_pool_samples(cls,
                                 time_count: int = 5,
                                 location_count: int = 8,
                                 person_count: int = 5,
                                 org_count: int = 5,
                                 equipment_count: int = 5,
                                 action_count: int = 5,
                                 target_date: datetime = None) -> Dict[str, Any]:
        """
        获取随机化的候选池样本

        每次调用都会生成不同的样本，确保生成内容的多样性

        Args:
            time_count: 时间样本数量
            location_count: 地点样本数量
            person_count: 人物样本数量
            org_count: 组织样本数量
            equipment_count: 装备样本数量
            action_count: 行为样本数量
            target_date: 目标日期（生成该日期的时间候选池）

        Returns:
            Dict: 各类候选池样本
        """
        # 根据target_date生成时间样本
        if target_date:
            times = TimePool.get_date_based_samples(target_date, time_count)
            current_date_info = TimePool.get_date_info(target_date)
        else:
            times = TimePool.get_sample_times(time_count)
            current_date_info = None

        return {
            'times': times,
            'locations': LocationPool.get_sample_locations(location_count),
            'persons': PersonPool.get_sample_persons(person_count),
            'organizations': OrganizationPool.get_sample_orgs(org_count),
            'equipment': EquipmentPool.get_sample_equipment(equipment_count),
            'actions': ActionPool.get_sample_actions(action_count),
            'current_date_info': current_date_info
        }

    @classmethod
    def get_diversity_params(cls, diversity: str = 'medium') -> Dict[str, Any]:
        """
        根据多样性级别获取参数（兼容旧接口）

        Args:
            diversity: 多样性级别 (low/medium/high)

        Returns:
            Dict: 多样性参数
        """
        # 使用新的细分参数，两个维度使用相同级别
        return cls.get_fine_grained_params(item_diversity=diversity, content_diversity=diversity)

    @classmethod
    def get_item_diversity_params(cls, item_diversity: str = 'medium') -> Dict[str, Any]:
        """
        获取条目多样性参数

        Args:
            item_diversity: 条目多样性级别 (low/medium/high)

        Returns:
            Dict: 条目多样性参数
        """
        if item_diversity == 'low':
            return {
                'extra_items_range': (0, 1),  # 几乎不增加条目
            }
        elif item_diversity == 'medium':
            return {
                'extra_items_range': (1, 3),  # 适度增加条目
            }
        else:  # high
            return {
                'extra_items_range': (2, 5),  # 大幅增加条目
            }

    @classmethod
    def get_content_diversity_params(cls, content_diversity: str = 'medium') -> Dict[str, Any]:
        """
        获取候选内容多样性参数

        Args:
            content_diversity: 候选内容多样性级别 (low/medium/high)

        Returns:
            Dict: 候选内容多样性参数
        """
        if content_diversity == 'low':
            return {
                'time_count': 3,
                'location_count': 5,
                'person_count': 3,
                'org_count': 3,
                'equipment_count': 3,
                'action_count': 3,
                'temperature': 0.6  # 较低温度，生成更保守
            }
        elif content_diversity == 'medium':
            return {
                'time_count': 5,
                'location_count': 8,
                'person_count': 5,
                'org_count': 5,
                'equipment_count': 5,
                'action_count': 5,
                'temperature': 0.8  # 中等温度
            }
        else:  # high
            return {
                'time_count': 8,
                'location_count': 12,
                'person_count': 8,
                'org_count': 8,
                'equipment_count': 8,
                'action_count': 8,
                'temperature': 0.95  # 高温度，生成更随机
            }

    @classmethod
    def get_fine_grained_params(cls, item_diversity: str = 'medium',
                                 content_diversity: str = 'medium') -> Dict[str, Any]:
        """
        获取细分的多样性参数（支持独立配置条目和候选内容）

        Args:
            item_diversity: 条目多样性级别 (low/medium/high)
                - low: 几乎不增加条目，保持样例结构
                - medium: 适度增加1-3个条目
                - high: 大幅增加2-5个条目
            content_diversity: 候选内容多样性级别 (low/medium/high)
                - low: 候选池小，温度低，内容保守
                - medium: 候选池中等，温度中等
                - high: 候选池大，温度高，内容丰富

        Returns:
            Dict: 完整的多样性参数
        """
        item_params = cls.get_item_diversity_params(item_diversity)
        content_params = cls.get_content_diversity_params(content_diversity)

        # 合并参数
        return {
            **item_params,
            **content_params,
            'item_diversity': item_diversity,
            'content_diversity': content_diversity
        }


# ============================================================
# 测试代码
# ============================================================

if __name__ == '__main__':
    print("=" * 60)
    print("候选池测试")
    print("=" * 60)

    # 测试各类候选池
    print("\n【时间样本】")
    print(TimePool.get_sample_times(3))

    print("\n【地点样本】")
    print(LocationPool.get_sample_locations(5))

    print("\n【人物样本】")
    print(PersonPool.get_sample_persons(3))

    print("\n【组织样本】")
    print(OrganizationPool.get_sample_orgs(3))

    print("\n【装备样本】")
    print(EquipmentPool.get_sample_equipment(3))

    print("\n【行为样本】")
    print(ActionPool.get_sample_actions(3))

    print("\n" + "=" * 60)
    print("综合候选池样本（中等多样性）")
    print("=" * 60)

    samples = CandidatePoolManager.get_random_pool_samples()
    for category, items in samples.items():
        print(f"\n{category}: {items}")

    print("\n" + "=" * 60)
    print("多样性参数（旧接口）")
    print("=" * 60)

    for level in ['low', 'medium', 'high']:
        params = CandidatePoolManager.get_diversity_params(level)
        print(f"\n{level}: {params}")

    print("\n" + "=" * 60)
    print("细分多样性参数（新接口）")
    print("=" * 60)

    # 展示不同组合
    combinations = [
        ('low', 'low'),
        ('low', 'high'),
        ('high', 'low'),
        ('high', 'high'),
    ]
    for item_div, content_div in combinations:
        params = CandidatePoolManager.get_fine_grained_params(item_div, content_div)
        print(f"\n条目={item_div}, 内容={content_div}:")
        print(f"  条目增加范围: {params['extra_items_range']}, 温度: {params['temperature']}")
