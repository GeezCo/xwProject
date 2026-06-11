<template>
  <div class="daily-brief-container">
    <!-- 顶部工具栏 -->
    <div class="toolbar">
      <el-date-picker
        v-model="dateRange"
        type="daterange"
        range-separator="至"
        start-placeholder="开始日期"
        end-placeholder="结束日期"
        format="YYYY-MM-DD"
        value-format="YYYY-MM-DD"
      />
      <el-button type="primary" @click="loadAllThemes">查询</el-button>
      <el-button type="warning" @click="triggerManualAnalysis">手动分析</el-button>
      <el-button @click="openKeywordConfig">配置关键词</el-button>
      <el-button @click="exportReport">导出报告</el-button>
    </div>

    <!-- 五大主题栏 -->
    <el-tabs v-model="activeTheme" type="card">
      <el-tab-pane label="美俄" name="usa_russia">
        <theme-panel
          ref="usaRussiaPanel"
          :theme-config="themes.usa_russia"
          :date-range="dateRange"
          @edit-keywords="openKeywordEditor('usa_russia')"
        />
      </el-tab-pane>
      <el-tab-pane label="印度" name="india">
        <theme-panel
          ref="indiaPanel"
          :theme-config="themes.india"
          :date-range="dateRange"
          @edit-keywords="openKeywordEditor('india')"
        />
      </el-tab-pane>
      <el-tab-pane label="南亚（除印度外）" name="south_asia">
        <theme-panel
          ref="southAsiaPanel"
          :theme-config="themes.south_asia"
          :date-range="dateRange"
          @edit-keywords="openKeywordEditor('south_asia')"
        />
      </el-tab-pane>
      <el-tab-pane label="中亚" name="central_asia">
        <theme-panel
          ref="centralAsiaPanel"
          :theme-config="themes.central_asia"
          :date-range="dateRange"
          @edit-keywords="openKeywordEditor('central_asia')"
        />
      </el-tab-pane>
      <el-tab-pane label="反恐" name="counter_terrorism">
        <theme-panel
          ref="counterTerrorismPanel"
          :theme-config="themes.counter_terrorism"
          :date-range="dateRange"
          @edit-keywords="openKeywordEditor('counter_terrorism')"
        />
      </el-tab-pane>
    </el-tabs>

    <!-- 关键词编辑对话框 -->
    <el-dialog v-model="keywordDialogVisible" :title="'编辑关键词 - ' + currentThemeName" width="600px">
      <el-form label-width="80px">
        <el-form-item label="关键词">
          <el-tag
            v-for="(keyword, index) in editingKeywords"
            :key="index"
            closable
            @close="removeKeyword(index)"
            style="margin: 4px"
          >
            {{ keyword }}
          </el-tag>
          <el-input
            v-model="newKeyword"
            placeholder="按 Enter 添加关键词"
            @keyup.enter="addKeyword"
            style="width: 200px; margin-top: 8px"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="keywordDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveKeywords">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import ThemePanel from './ThemePanel.vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'

// 日期范围（默认过去1天）
const dateRange = ref([
  new Date(Date.now() - 24 * 3600 * 1000).toISOString().split('T')[0],
  new Date().toISOString().split('T')[0]
])

const activeTheme = ref('usa_russia')

// 主题面板 refs
const usaRussiaPanel = ref(null)
const indiaPanel = ref(null)
const southAsiaPanel = ref(null)
const centralAsiaPanel = ref(null)
const counterTerrorismPanel = ref(null)

// __THEMES_CONFIG__

// 主题配置（关键词 + 分类ID）
const themes = reactive({
  usa_russia: {
    name: '美俄',
    keywords: [
      // 国家与地区
      '美国', '俄罗斯', '乌克兰', '白俄罗斯', '北约',
      // 领导人与政要
      '拜登', '普京', '泽连斯基', '布林肯', '奥斯汀', '沙利文',
      // 军事与武器
      'F-16', 'F-35', 'Su-57', '爱国者', 'HIMARS', '海马斯',
      'M1艾布拉姆斯', '豹2', '挑战者', '伊斯坎德尔', '匕首',
      'S-400', 'S-500', '俄亥俄级', '弗吉尼亚级', '北风之神',
      // 军事行动与事件
      '特别军事行动', '北约峰会', '慕尼黑安全会议', '五角大楼',
      '克里姆林宫', '顿巴斯', '克里米亚', '黑海舰队',
      // 国际组织
      '联合国安理会', 'G7', 'G20', '独联体', '集安组织'
    ],
    typeIds: []
  },
  india: {
    name: '印度',
    keywords: [
      // 国家与地区
      '印度', '新德里', '孟买', '加尔各答', '班加罗尔',
      // 领导人与政要
      '莫迪', '苏杰生', '拉杰纳特·辛格',
      // 军事与国防
      '印度空军', '印度海军', '印度陆军', '印度边防部队',
      '光辉战机', '阵风战机', '苏-30MKI', 'T-90坦克', '阿琼主战坦克',
      '维克兰特号', '维克拉玛蒂亚号', '歼敌者号', '烈火导弹',
      // 边界与争端
      '中印边界', '实际控制线', 'LAC', '拉达克', '阿克赛钦',
      '阿鲁纳恰尔邦', '藏南', '加勒万河谷', '班公湖',
      // 地缘政治
      '四方安全对话', 'QUAD', '印太战略', '印美关系', '印俄关系',
      '不结盟运动', '南亚区域合作联盟', 'SAARC', '孟中印缅经济走廊'
    ],
    typeIds: []
  },
  // __THEMES_CONTINUE__
  south_asia: {
    name: '南亚（除印度外）',
    keywords: [
      // 国家与首都
      '巴基斯坦', '伊斯兰堡', '卡拉奇', '拉合尔',
      '孟加拉国', '达卡', '吉大港',
      '斯里兰卡', '科伦坡', '康提',
      '尼泊尔', '加德满都',
      '不丹', '廷布',
      '马尔代夫', '马累',
      '阿富汗', '喀布尔', '坎大哈', '赫拉特',
      // 领导人
      '夏巴兹·谢里夫', '比拉瓦尔·布托', '哈西娜', '维克拉马辛哈',
      // 军事与安全
      '巴基斯坦军方', 'ISI', '巴基斯坦三军情报局',
      '枭龙战机', 'JF-17', 'F-16战隼',
      '塔利班', '阿富汗塔利班', '真主党', '拉什卡',
      // 地缘与争端
      '克什米尔', '印巴分治线', 'LoC', '瓜达尔港', '汉班托塔港',
      '中巴经济走廊', 'CPEC', '中国-巴基斯坦', '中国-孟加拉国',
      '孟加拉湾', '印度洋', '查戈斯群岛'
    ],
    typeIds: []
  },
  central_asia: {
    name: '中亚',
    keywords: [
      // 五国及首都
      '哈萨克斯坦', '阿斯塔纳', '努尔苏丹', '阿拉木图',
      '乌兹别克斯坦', '塔什干', '撒马尔罕', '布哈拉',
      '吉尔吉斯斯坦', '比什凯克', '奥什',
      '塔吉克斯坦', '杜尚别',
      '土库曼斯坦', '阿什哈巴德',
      // 领导人
      '托卡耶夫', '米尔济约耶夫', '扎帕罗夫', '拉赫蒙', '别尔德穆哈梅多夫',
      // 地缘政治
      '上海合作组织', 'SCO', '集体安全条约组织', 'CSTO',
      '欧亚经济联盟', 'EAEU', '中亚五国', 'C5+1',
      // 经济与能源
      '一带一路', '中欧班列', '中国-中亚峰会',
      '里海', '咸海', '图兰盆地', '费尔干纳盆地',
      '天然气管道', '石油管道', '中亚天然气管道',
      // 安全与极端主义
      '东突', '伊斯兰运动', '乌兹别克斯坦伊斯兰运动', 'IMU',
      '圣战者', '激进伊斯兰', '跨国贩毒', '阿富汗边境'
    ],
    typeIds: []
  },
  counter_terrorism: {
    name: '反恐',
    keywords: [
      // 恐怖组织
      'ISIS', 'ISIL', 'IS', '伊斯兰国', 'Daesh',
      '基地组织', 'Al-Qaeda', 'AQ',
      '塔利班', 'Taliban', '阿富汗塔利班', '巴基斯坦塔利班', 'TTP',
      '博科圣地', 'Boko Haram',
      '青年党', 'Al-Shabaab',
      '真主党', 'Hezbollah',
      '哈马斯', 'Hamas',
      '努斯拉阵线', 'Jabhat al-Nusra',
      '东伊运', 'ETIM', '东突厥斯坦伊斯兰运动',
      '乌兹别克斯坦伊斯兰运动', 'IMU',
      // 恐怖活动类型
      '自杀式袭击', '汽车炸弹', 'IED', '简易爆炸装置',
      '人质劫持', '斩首', '恐怖袭击', '爆炸袭击',
      '武装分子', '极端分子', '圣战者', 'Jihadi',
      // 反恐行动
      '反恐行动', '反恐演习', '联合反恐', '打击恐怖主义',
      '去极端化', '反激进化', '边境管控', '情报共享',
      '无人机打击', '斩首行动', '特种作战', '反恐特遣队',
      // 国际反恐合作
      '全球反恐联盟', '联合国反恐', 'FATF', '金融行动特别工作组',
      '国际刑警组织', 'Interpol', '五眼联盟', '北约反恐',
      // 地区热点
      '叙利亚', '伊拉克', '也门', '索马里', '利比亚', '马里',
      '阿富汗', '巴基斯坦', '菲律宾', '印尼', '新疆'
    ],
    typeIds: []
  }
})

// 关键词编辑对话框
const keywordDialogVisible = ref(false)
const currentThemeKey = ref('')
const currentThemeName = ref('')
const editingKeywords = ref([])
const newKeyword = ref('')

function openKeywordEditor(themeKey) {
  currentThemeKey.value = themeKey
  currentThemeName.value = themes[themeKey].name
  editingKeywords.value = [...themes[themeKey].keywords]
  keywordDialogVisible.value = true
}

function addKeyword() {
  const keyword = newKeyword.value.trim()
  if (keyword && !editingKeywords.value.includes(keyword)) {
    editingKeywords.value.push(keyword)
    newKeyword.value = ''
  }
}

function removeKeyword(index) {
  editingKeywords.value.splice(index, 1)
}

function saveKeywords() {
  if (editingKeywords.value.length === 0) {
    ElMessage.warning('至少保留一个关键词')
    return
  }
  themes[currentThemeKey.value].keywords = [...editingKeywords.value]
  keywordDialogVisible.value = false
  ElMessage.success('关键词已更新')

  // 保存到 localStorage
  const allKeywords = {}
  Object.keys(themes).forEach(key => {
    allKeywords[key] = themes[key].keywords
  })
  localStorage.setItem('dailyBriefKeywords', JSON.stringify(allKeywords))
}

function loadAllThemes() {
  ElMessage.info('正在查询各主题数据...')
}

async function triggerManualAnalysis() {
  ElMessageBox.confirm(
    `确认对 ${dateRange.value[0]} 至 ${dateRange.value[1]} 的报文进行事件分析？分析过程可能需要较长时间。`,
    '手动触发分析',
    { type: 'warning', confirmButtonText: '确认', cancelButtonText: '取消' }
  ).then(async () => {
    try {
      const res = await request.post('/api/eventAnalysis/trigger', {
        startDate: dateRange.value[0],
        endDate: dateRange.value[1]
      })
      ElMessage.success(res.msg || '分析任务已启动')
    } catch (e) {
      ElMessage.error('启动失败: ' + (e.message || '未知错误'))
    }
  }).catch(() => {
    // 用户取消
  })
}

function openKeywordConfig() {
  openKeywordEditor(activeTheme.value)
}

function exportReport() {
  ElMessage.info('功能开发中：导出每日要情报告')
}

onMounted(() => {
  // 从 localStorage 恢复用户自定义关键词
  try {
    const saved = localStorage.getItem('dailyBriefKeywords')
    if (saved) {
      const savedKeywords = JSON.parse(saved)
      Object.keys(savedKeywords).forEach(key => {
        if (themes[key]) {
          themes[key].keywords = savedKeywords[key]
        }
      })
    }
  } catch (e) {
    console.error('加载保存的关键词失败:', e)
  }
})
</script>

<style scoped>
.daily-brief-container {
  padding: 20px;
}

.toolbar {
  display: flex;
  gap: 12px;
  margin-bottom: 20px;
  align-items: center;
}
</style>
