<template>
  <div class="fusion-page">
    <!-- 顶部标题栏 -->
    <div class="fusion-header">
      <div class="fh-left">
        <span class="fh-title">报文融合结果</span>
        <span class="fh-info" v-if="fusionType === 'target'">目标: {{ targetName }} 的融合报告</span>
        <span class="fh-info" v-else>已选择 {{ sourceReports.length }} 篇报文进行融合</span>
      </div>
      <button class="back-btn" @click="router.back()">返回</button>
    </div>

    <!-- 主内容 -->
    <div class="fusion-body">
      <!-- 左侧报文列表 -->
      <div class="report-list">
        <div class="rl-title">已选报文列表</div>
        <div class="rl-body">
          <div v-for="item in sourceReports" :key="item.id" class="rl-item">
            <span class="rl-check">☑</span>
            <div class="rl-info">
              <div class="rl-item-title">{{ item.title }}</div>
              <div class="rl-item-meta">时间: {{ item.times }}　来源: {{ item.type }}</div>
            </div>
          </div>
        </div>
      </div>

      <!-- 右侧融合报告 -->
      <div class="report-panel">
        <!-- 融合等待（名言轮播） -->
        <div v-if="loading" class="fusion-loading">
          <el-icon class="is-loading" :size="32"><Loading /></el-icon>
          <div class="loading-status">正在融合报文，预计5-8分钟...</div>
          <transition name="quote-fade" mode="out-in">
            <div class="quote-card" :key="currentQuote.text">
              <div class="quote-text">"{{ currentQuote.text }}"</div>
              <div class="quote-author">—— {{ currentQuote.author }}
                <span v-if="currentQuote.source"> {{ currentQuote.source }}</span>
              </div>
            </div>
          </transition>
        </div>
        <template v-if="fusionReport && !loading">
          <!-- 报告标题 -->
          <div class="section title-section">
            <div class="section-header">融合报告标题</div>
            <div class="section-content">
              <div class="report-title-text">{{ fusionReport.title }}</div>
            </div>
          </div>

          <!-- 目标列表 -->
          <div class="section" v-if="entitiesTableData.length">
            <div class="section-header">目标列表</div>
            <div class="section-content">
              <table class="entity-table">
                <thead>
                  <tr>
                    <th>序号</th>
                    <th>实体类别</th>
                    <th>实体名称</th>
                    <th>实体行为</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="(row, idx) in entitiesTableData" :key="idx">
                    <td>{{ idx + 1 }}</td>
                    <td>{{ row.category }}</td>
                    <td>{{ row.name }}</td>
                    <td>{{ row.action }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>

          <!-- 详细内容 -->
          <div class="section">
            <div class="section-header">详细内容</div>
            <div class="section-content detail-md" v-html="renderedContent"></div>
          </div>

          <!-- 事件时间线 -->
          <div class="section" v-if="timelineData.length">
            <div class="section-header">事件时间线</div>
            <div class="section-content">
              <div v-for="(tl, idx) in timelineData" :key="idx" class="timeline-item">
                <span class="tl-dot">●</span>
                <span class="tl-date">{{ tl.time }}</span>
                <span class="tl-desc">{{ tl.description }}</span>
              </div>
            </div>
          </div>

          <!-- 综合标签 -->
          <div class="section" v-if="labelsData.length">
            <div class="section-header">综合标签</div>
            <div class="section-content">
              <div class="tags-list">
                <span class="tag-item" v-for="l in labelsData" :key="l">{{ l }}</span>
              </div>
            </div>
          </div>

          <!-- 操作按钮 -->
          <div class="action-bar">
            <button class="act-btn primary" @click="handleSave">保存报告</button>
            <button class="act-btn" @click="ElMessage.info('PDF导出功能开发中')">导出PDF</button>
            <button class="act-btn" @click="ElMessage.info('Word导出功能开发中')">导出Word</button>
            <button class="act-btn" @click="handleRefusion">重新融合</button>
            <button class="act-btn" @click="ElMessage.info('编辑功能开发中')">编辑报告</button>
          </div>
        </template>
      </div>
    </div>

    <!-- 底部状态栏 -->
    <div class="fusion-footer">
      <span class="status-item">融合状态: {{ loading ? '处理中...' : (fusionReport ? '已完成' : '等待中') }}</span>
      <span class="status-item" v-if="fusionReport">融合时间: {{ fusionReport.createTime }}</span>
      <span class="status-item" v-if="fusionReport">使用模型: {{ fusionReport.modelUsed || 'GLM-5' }}</span>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Loading } from '@element-plus/icons-vue'
import { marked } from 'marked'
import { QUOTES } from '@/constants/quotes'
import { useHomeStore } from '@/stores/home'
import request from '@/utils/request'

const router = useRouter()
const store = useHomeStore()

const loading = ref(false)
const fusionReport = ref(null)
const sourceReports = ref([])
const fusionType = ref('standard')
const targetName = ref('')

const currentQuote = ref(QUOTES[Math.floor(Math.random() * QUOTES.length)])
let quoteTimer = null

watch(loading, (val) => {
  if (val) {
    currentQuote.value = QUOTES[Math.floor(Math.random() * QUOTES.length)]
    quoteTimer = setInterval(() => {
      currentQuote.value = QUOTES[Math.floor(Math.random() * QUOTES.length)]
    }, 8000)
  } else {
    clearInterval(quoteTimer)
  }
})

onBeforeUnmount(() => {
  clearInterval(quoteTimer)
})

const timelineData = computed(() => {
  if (!fusionReport.value?.timeline) return []
  const tl = fusionReport.value.timeline
  if (typeof tl === 'string') {
    try { return JSON.parse(tl) } catch { return [] }
  }
  return tl
})

const renderedContent = computed(() => {
  if (!fusionReport.value?.content) return ''
  return marked(fusionReport.value.content)
})

const entitiesData = computed(() => {
  if (!fusionReport.value?.entities) {
    console.log('[目标列表] fusionReport.entities 为空')
    return null
  }
  const ent = fusionReport.value.entities
  if (typeof ent === 'string') {
    try {
      const parsed = JSON.parse(ent)
      console.log('[目标列表] entities 解析成功:', Array.isArray(parsed) ? `数组(${parsed.length}行)` : `对象(${Object.keys(parsed).length}类)`)
      return parsed
    } catch (e) {
      console.error('[目标列表] entities 解析失败:', ent)
      return null
    }
  }
  console.log('[目标列表] entities 已是对象:', Array.isArray(ent) ? `数组(${ent.length}行)` : `对象(${Object.keys(ent).length}类)`)
  return ent
})

const entitiesTableData = computed(() => {
  const ent = entitiesData.value
  if (!ent) return []

  // 新数据结构：直接是表格行数组 [{category, name, action}, ...]
  if (Array.isArray(ent)) {
    return ent.map(row => ({
      category: row.category || '',
      name: row.name || '',
      action: row.action || '-'
    }))
  }

  // 向后兼容：老数据结构是字典 {category: [names]}
  const rows = []
  for (const [category, names] of Object.entries(ent)) {
    if (names && names.length) {
      names.forEach(name => {
        rows.push({
          category,
          name,
          action: '-'
        })
      })
    }
  }
  return rows
})

const labelsData = computed(() => {
  if (!fusionReport.value?.labels) return []
  const lb = fusionReport.value.labels
  if (typeof lb === 'string') {
    try { return JSON.parse(lb) } catch { return [] }
  }
  return lb
})

onMounted(() => {
  const fusionData = store.fusionData
  if (fusionData?.reports) {
    sourceReports.value = fusionData.reports
    fusionType.value = fusionData.fusionType || 'standard'
    targetName.value = fusionData.targetName || ''
    createFusion()
  }
})

async function createFusion() {
  loading.value = true
  console.log('[融合] 开始创建融合报告, 报文数:', sourceReports.value.length)
  try {
    const res = await request.post('/api/fusion/create', {
      reports: sourceReports.value,
      fusionType: fusionType.value,
      customTitle: fusionType.value === 'target' ? `${targetName.value}相关报文融合` : undefined
    }, { timeout: 600000 })
    fusionReport.value = res.data
    console.log('[融合] 融合完成:', {
      title: res.data?.title,
      timeline: res.data?.timeline?.length,
      entities: res.data?.entities,
      labels: res.data?.labels
    })
    console.log('[融合] sourceIds 原始值:', res.data?.sourceIds)
    console.log('[融合] sourceIds 类型:', typeof res.data?.sourceIds)
    console.log('[融合] entities 原始值:', res.data?.entities)
    console.log('[融合] entities 类型:', typeof res.data?.entities)
    ElMessage.success('融合完成')
  } catch (e) {
    console.error('[融合] 融合失败:', e)
    ElMessage.error('融合失败，请重试')
  } finally {
    loading.value = false
  }
}

async function handleSave() {
  if (!fusionReport.value) return
  console.log('[保存] 保存融合报告:', fusionReport.value.title)
  try {
    await request.post('/api/fusion/save', fusionReport.value)
    ElMessage.success('报告已保存')
  } catch {
    ElMessage.error('保存失败')
  }
}

function handleRefusion() {
  fusionReport.value = null
  createFusion()
}
</script>

<style scoped>
.fusion-page {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 182px);
}

.fusion-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 20px;
  background: var(--bg-panel-strong);
  border-bottom: 1px solid var(--border-color);
  box-shadow: var(--shadow-panel);
  flex-shrink: 0;
}

.fh-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.fh-title {
  color: var(--color-accent);
  font-size: 18px;
  font-weight: bold;
}

.fh-info {
  color: var(--color-text);
  font-size: 13px;
}

.back-btn {
  padding: 6px 18px;
  background: var(--bg-btn-primary);
  border: none;
  border-radius: 4px;
  color: var(--color-btn-text);
  font-size: 13px;
  cursor: pointer;
}

.fusion-body {
  flex: 1;
  display: flex;
  gap: 16px;
  padding: 16px;
  overflow: hidden;
}

/* 左侧报文列表 */
.report-list {
  width: 300px;
  flex-shrink: 0;
  background: var(--bg-panel);
  border: 1px solid var(--border-color);
  border-radius: 10px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  box-shadow: var(--shadow-card);
}

.rl-title {
  text-align: center;
  padding: 14px;
  color: var(--color-accent);
  font-size: 15px;
  font-weight: bold;
  border-bottom: 1px solid var(--border-color);
}

.rl-body {
  flex: 1;
  overflow-y: auto;
  padding: 10px;
}

.rl-item {
  display: flex;
  gap: 10px;
  padding: 12px;
  margin-bottom: 8px;
  background: var(--bg-card);
  border: 1px solid var(--border-color-light);
  border-radius: 5px;
}

.rl-check {
  color: var(--color-primary);
  font-size: 18px;
  flex-shrink: 0;
}

.rl-info {
  flex: 1;
  min-width: 0;
}

.rl-item-title {
  color: var(--color-title);
  font-size: 13px;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  margin-bottom: 4px;
}

.rl-item-meta {
  color: var(--color-text-secondary);
  font-size: 11px;
}

/* 右侧融合报告 */
.report-panel {
  flex: 1;
  background: var(--bg-panel);
  border: 1px solid var(--border-color);
  border-radius: 10px;
  overflow-y: auto;
  padding: 20px;
  box-shadow: var(--shadow-card);
}

.section {
  margin-bottom: 20px;
}

.section-header {
  font-size: 15px;
  color: var(--color-title-section);
  font-weight: bold;
  margin-bottom: 10px;
  padding-left: 10px;
  border-left: 3px solid var(--color-primary);
}

.section-content {
  padding: 12px 14px;
  background: var(--bg-card);
  border-radius: 5px;
  color: var(--color-text);
  font-size: 13px;
  line-height: 22px;
}

.title-section .section-content {
  background: var(--bg-tag-orange);
  border: 1px solid var(--color-title);
}

.report-title-text {
  font-size: 17px;
  color: var(--color-title);
  font-weight: bold;
}

/* 时间线 */
.timeline-item {
  display: flex;
  align-items: flex-start;
  margin-bottom: 10px;
  gap: 8px;
}

.tl-dot {
  color: var(--color-primary);
  font-size: 10px;
  margin-top: 4px;
}

.tl-date {
  color: var(--color-accent);
  font-weight: bold;
  font-size: 13px;
  min-width: 90px;
}

.tl-desc {
  color: var(--color-text);
  font-size: 13px;
}

/* 详细内容 */
.detail-md {
  max-height: 260px;
  overflow-y: auto;
  padding: 14px;
  line-height: 1.7;
}

.detail-md :deep(h2) {
  color: var(--color-title-section);
  font-size: 16px;
  font-weight: bold;
  margin: 14px 0 8px;
  padding-left: 8px;
  border-left: 3px solid var(--color-primary);
}

.detail-md :deep(h3) {
  color: var(--color-accent);
  font-size: 14px;
  margin: 12px 0 6px;
}

.detail-md :deep(p) {
  color: var(--color-text);
  font-size: 13px;
  margin: 6px 0;
}

.detail-md :deep(strong) {
  color: var(--color-text-strong);
}

/* 目标列表表格 */
.entity-table {
  width: 100%;
  border-collapse: collapse;
}

.entity-table th {
  background: var(--bg-panel-strong);
  color: var(--color-accent);
  font-weight: bold;
  font-size: 13px;
  padding: 8px 12px;
  text-align: left;
  border: 1px solid var(--border-color);
}

.entity-table td {
  color: var(--color-text);
  font-size: 13px;
  padding: 8px 12px;
  border: 1px solid var(--border-color-light);
  vertical-align: top;
}

.entity-table td:nth-child(1) {
  color: var(--color-text-secondary);
  text-align: center;
  width: 50px;
}

.entity-table td:nth-child(2) {
  color: var(--color-accent);
  font-weight: bold;
  width: 100px;
}

.entity-table td:nth-child(3) {
  width: 180px;
}

.entity-table td:nth-child(4) {
  color: var(--color-text-secondary);
}

/* 标签 */
.tags-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.tag-item {
  display: inline-block;
  padding: 3px 10px;
  background: var(--bg-tag);
  border: 1px solid var(--color-primary);
  border-radius: 4px;
  color: var(--color-tag-text);
  font-size: 12px;
}

/* 操作按钮 */
.action-bar {
  display: flex;
  justify-content: center;
  gap: 12px;
  padding: 16px;
  margin-top: 16px;
  border-top: 1px dashed var(--border-color);
}

.act-btn {
  padding: 8px 20px;
  border-radius: 4px;
  font-size: 13px;
  cursor: pointer;
  border: 1px solid var(--border-color-light);
  background: var(--bg-card);
  color: var(--color-text);
}

.act-btn.primary {
  background: var(--bg-btn-primary);
  border: none;
  color: var(--color-btn-text);
}

/* 底部状态栏 */
.fusion-footer {
  display: flex;
  justify-content: center;
  gap: 30px;
  padding: 10px 20px;
  background: var(--bg-panel-strong);
  border-top: 1px solid var(--border-color);
  flex-shrink: 0;
}

.status-item {
  font-size: 11px;
  color: var(--color-text-secondary);
}

/* 融合等待名言 */
.fusion-loading {
  text-align: center;
  padding: 40px 20px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 400px;
}
.loading-status { margin: 14px 0 24px; color: var(--color-text-secondary); font-size: 14px; }
.quote-card { padding: 18px 20px; background: var(--bg-card); border-left: 3px solid var(--color-primary); border-radius: 4px; text-align: left; max-width: 500px; width: 100%; }
.quote-text { color: var(--color-text-strong); font-size: 15px; line-height: 24px; font-style: italic; }
.quote-author { color: var(--color-text-secondary); font-size: 13px; margin-top: 10px; text-align: right; }
.quote-fade-enter-active, .quote-fade-leave-active { transition: opacity 0.5s ease; }
.quote-fade-enter-from, .quote-fade-leave-to { opacity: 0; }
</style>
