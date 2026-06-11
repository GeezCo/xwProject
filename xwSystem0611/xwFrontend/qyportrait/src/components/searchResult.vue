<template>
  <div class="search-page">
    <!-- 筛选栏 -->
    <div class="filter-bar">
      <!-- <el-select v-model="filterSource" placeholder="数据来源" clearable size="small" class="filter-select" @change="handleSearch">
        <el-option v-for="cat in allCategories" :key="cat.id" :label="cat.typeName" :value="cat.id" />
      </el-select> -->
      <span class="total-num">{{ total }} 条结果</span>
    </div>

    <!-- 三栏布局 -->
    <div class="main-layout">
      <!-- 左侧目录 -->
      <div class="category-panel">
        <div class="cat-title">目 录</div>
        <div class="cat-group-header" @click="toggleGroup('source')">
          {{ sourceExpanded ? '▾' : '▸' }} 📁 信息来源
        </div>
        <template v-if="sourceExpanded">
          <template v-for="cat in categoryTree" :key="cat.id">
            <CategoryTreeNode
              :node="cat"
              :selectedIds="selectedTypeIds"
              :depth="0"
              @toggle="toggleCategorySelection"
            />
          </template>
        </template>
        <div class="cat-group-header" @click="toggleGroup('modal')">
          {{ modalExpanded ? '▾' : '▸' }} 📁 报文模态
        </div>
        <template v-if="modalExpanded">
          <div
            v-for="mt in modalTypes"
            :key="mt"
            class="cat-item"
            :class="{ active: selectedModalTypes.includes(mt) }"
            @click="toggleModalTypeSelection(mt)"
          >
            <input type="checkbox" :checked="selectedModalTypes.includes(mt)" @click.stop>
            {{ mt }}
          </div>
        </template>
      </div>

      <!-- 中间列表 -->
      <div class="list-panel">
        <!-- 高级筛选栏 -->
        <div class="advanced-filter-bar">
          <div class="filter-row">
            <span class="filter-label">关键词：</span>
            <div class="keyword-tag-box" @click="focusKeywordInput">
              <span v-for="(kw, idx) in filterKeywords" :key="idx" class="kw-tag">
                {{ kw }}
                <span class="kw-tag-close" @click.stop="removeKeyword(idx)">×</span>
              </span>
              <input
                ref="keywordInput"
                v-model="keywordInputValue"
                type="text"
                class="kw-input"
                placeholder="输入关键词后按回车"
                @keydown.enter.prevent="addKeyword"
                @keydown.backspace="handleBackspace"
              />
            </div>
          </div>
          <div class="filter-row">
            <span class="filter-label">时间范围：</span>
            <el-date-picker
              v-model="filterDateRange"
              type="daterange"
              range-separator="至"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
              format="YYYY-MM-DD"
              value-format="YYYY-MM-DD"
              clearable
              size="small"
              class="filter-date-picker"
            />
          </div>
          <div class="filter-row">
            <button class="filter-btn filter-btn-primary" @click="applyFilter">筛选</button>
            <button class="filter-btn filter-btn-clear" @click="clearFilter">清除</button>
            <span class="filter-info" v-if="filterKeywords.length > 0">
              关键词数: {{ filterKeywords.length }} (OR关系)
            </span>
          </div>
        </div>

        <div class="fusion-bar">
          <div class="fusion-left">
            <span class="fusion-count">已选择 {{ selectedItems.length }} 篇报文</span>
            <button class="fusion-btn" :disabled="selectedItems.length < 2 || selectedItems.length > 20" @click="handleFusion">报文融合</button>
            <span v-if="selectedItems.length > 0 && selectedItems.length < 2" class="fusion-tip">至少选择2篇</span>
            <span v-if="selectedItems.length > 20" class="fusion-tip">最多选择20篇</span>
            <button class="batch-btn batch-select-btn" :disabled="textList.length === 0" @click="selectAllCurrentPage">全选当前页</button>
            <button class="batch-btn batch-clear-btn" :disabled="selectedItems.length === 0" @click="clearAllSelected">清除所有</button>
          </div>
          <div class="fusion-divider"></div>
          <div class="fusion-right">
            <span class="target-label">目标搜索:</span>
            <input
              v-model="targetKeyword"
              class="target-input"
              placeholder="输入目标名称（如：第七舰队）"
              :disabled="targetSearching"
              @keyup.enter="handleTargetSearch"
            >
            <button class="target-btn" :disabled="!targetKeyword.trim() || targetSearching" @click="handleTargetSearch">
              {{ targetSearching ? '搜索中...' : '搜索' }}
            </button>
            <button v-if="isTargetMode" class="target-clear-btn" @click="exitTargetMode">清除</button>
          </div>
        </div>

        <div v-loading="listLoading" class="list-body">
          <div
            v-for="item in textList"
            :key="item.sid"
            class="list-item"
            :class="{ 'is-active': activeDetail?.sid === item.sid }"
            @click="showDetail(item)"
          >
            <div class="item-title">
              <el-checkbox
                :model-value="isSelected(item.sid)"
                @click.stop
                @change="toggleSelect(item)"
              />
              {{ item.title }}
              <span class="modal-badge" v-if="item.modalType === '图文报'">图</span>
            </div>
            <div class="item-time">发布时间：{{ item.times }}　来源：{{ getCategoryName(item.type) }}</div>
            <div class="item-tags" v-if="item.labels && item.labels.length">
              <span class="tag" v-for="label in item.labels" :key="label">{{ label }}</span>
            </div>
            <div class="item-content">{{ item.content }}</div>
          </div>
        </div>

        <div class="pagination">
          <el-pagination
            v-model:current-page="pageNum"
            v-model:page-size="pageSize"
            :total="total"
            layout="prev, pager, next, total"
            @current-change="fetchList"
          />
        </div>
      </div>

      <!-- 右侧详情 -->
      <div v-if="activeDetail" class="detail-panel">
        <div class="detail-close" @click="activeDetail = null">✕ 关闭详情</div>
        <div class="detail-scroll">
          <div class="detail-body">
            <div class="detail-title">{{ activeDetail.title }}</div>
            <div class="detail-meta">
              <div class="dm-row"><span class="dm-label">发布时间：</span>{{ activeDetail.times }}</div>
              <div class="dm-row"><span class="dm-label">数据来源：</span>{{ getCategoryName(activeDetail.type) }}</div>
            </div>
            <!-- 图片展示（图文报） -->
            <div class="detail-images" v-if="detailImages.length">
              <div class="img-grid" :class="{ single: detailImages.length === 1 }">
                <div v-for="(url, idx) in detailImages" :key="idx" class="img-item" @click="previewImage(idx)">
                  <img :src="url" :alt="'图片' + (idx+1)" loading="lazy" />
                </div>
              </div>
            </div>
            <div class="detail-text">{{ detailContent }}</div>
          </div>

          <!-- 抽取面板 -->
          <div class="extract-panel">
            <div class="extract-header">
              <span class="extract-title">属性抽取结果</span>
              <button
                class="extract-btn"
                :disabled="extracting"
                @click="handleExtract(true)"
              >{{ extractionResult ? '重新抽取' : '属性抽取' }}</button>
            </div>

            <div v-if="extracting" class="extracting-panel">
              <el-icon class="is-loading" :size="24"><Loading /></el-icon>
              <div class="extracting-status">属性抽取中，预计2-5分钟...</div>
              <transition name="quote-fade" mode="out-in">
                <div class="quote-card" :key="currentQuote.text">
                  <div class="quote-text">"{{ currentQuote.text }}"</div>
                  <div class="quote-author">—— {{ currentQuote.author }}
                    <span v-if="currentQuote.source"> {{ currentQuote.source }}</span>
                  </div>
                </div>
              </transition>
            </div>

            <template v-if="extractionResult && !extracting">
              <div class="overall-labels" v-if="extractionResult.labels?.length">
                <span class="ol-title">分类标签：</span>
                <span class="ol-tag" v-for="l in extractionResult.labels" :key="l">{{ l }}</span>
              </div>

              <div
                v-for="(event, idx) in extractionResult.events"
                :key="idx"
                class="event-card"
              >
                <div class="event-title">事件 {{ idx + 1 }}</div>
                <div class="event-field"><span class="ef-label">时间：</span>{{ event.time || '未提及' }}</div>
                <div class="event-field" v-if="event.location?.length">
                  <span class="ef-label">地点：</span>{{ event.location.join('、') }}
                </div>
                <!-- 实体分类展示 -->
                <template v-if="extractionResult.entities && event.subject">
                  <template v-for="(items, label) in extractionResult.entities" :key="label">
                    <div class="event-field" v-if="getEventEntityItems(event.subject, items).length > 0">
                      <span class="ef-label">{{ label }}：</span>{{ getEventEntityItems(event.subject, items).join('、') }}
                    </div>
                  </template>
                </template>
                <div class="event-field"><span class="ef-label">行为：</span>{{ event.action }}</div>
                <div class="event-field event-original" v-if="event.original_text">
                  <span class="ef-label">原文：</span><em>{{ event.original_text }}</em>
                </div>
                <div class="event-tags" v-if="event.labels?.length">
                  <span class="et-tag" v-for="t in event.labels" :key="t">{{ t }}</span>
                </div>
              </div>
            </template>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onActivated, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Loading } from '@element-plus/icons-vue'
import { QUOTES } from '@/constants/quotes'
import { useHomeStore } from '@/stores/home'
import request from '@/utils/request'
import CategoryTreeNode from './CategoryTreeNode.vue'

defineOptions({ name: 'SearchResult' })


const router = useRouter()
const store = useHomeStore()

const filterSource = ref(null)
const selectedTypeIds = ref([])
const selectedModalTypes = ref([])
const sourceExpanded = ref(true)
const modalExpanded = ref(true)
const modalTypes = ['文字报', '图文报', '声像报']

const targetKeyword = ref('')
const targetSearching = ref(false)
const isTargetMode = ref(false)

const categoryTree = ref([])  // 多层级分类树
const textList = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const listLoading = ref(false)

const selectedItems = ref([])
const activeDetail = ref(null)
const detailContent = ref('')
const extractionResult = ref(null)
const extracting = ref(false)

// 高级筛选
const filterKeywords = ref([])
const keywordInputValue = ref('')
const filterDateRange = ref(null)
const keywordInput = ref(null)

const allCategories = computed(() => {
  const result = []
  categories.value.forEach(cat => {
    result.push(cat)
    if (cat.children && cat.children.length) {
      result.push(...cat.children)
    }
  })
  return result
})

const MINIO_PREFIX = computed(() => store.minioPrefix || '')
const currentQuote = ref(QUOTES[Math.floor(Math.random() * QUOTES.length)])
let quoteTimer = null
const imagePreviewVisible = ref(false)
const imagePreviewIndex = ref(0)

const detailImages = computed(() => {
  if (!activeDetail.value?.images) return []
  try {
    const names = JSON.parse(activeDetail.value.images)
    return names.map(n => MINIO_PREFIX.value + n)
  } catch { return [] }
})

watch(extracting, (val) => {
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

function previewImage(idx) {
  imagePreviewIndex.value = idx
  imagePreviewVisible.value = true
}

onMounted(() => {
  fetchCategories()
  fetchList()
})

onActivated(() => {
  if (textList.value.length === 0) fetchList()
})

async function fetchCategories() {
  try {
    const res = await request.get('/api/category/tree')
    const tree = res.data?.data || res.data || []
    categoryTree.value = tree
    console.log('[分类] 获取分类树:', categoryTree.value)
  } catch {}
}

async function fetchList() {
  listLoading.value = true
  try {
    const params = {
      pageNum: pageNum.value,
      pageSize: pageSize.value
    }
    if (selectedTypeIds.value.length > 0) {
      params.typeIds = selectedTypeIds.value
    }
    if (selectedModalTypes.value.length > 0) {
      params.modalTypes = selectedModalTypes.value
    }
    if (filterKeywords.value.length > 0) {
      params.keywords = [...filterKeywords.value]
    }
    if (filterDateRange.value && filterDateRange.value.length === 2) {
      params.startTime = filterDateRange.value[0]
      params.endTime = filterDateRange.value[1]
    }
    console.log('[列表] 请求参数:', params)
    const res = await request.post('/uygur/getTextList', params)
    textList.value = res.data?.list || []
    total.value = res.data?.total || 0
    console.log('[列表] 返回数据:', { total: total.value, count: textList.value.length })
  } catch {} finally {
    listLoading.value = false
  }
}

function focusKeywordInput() {
  keywordInput.value?.focus()
}

function addKeyword() {
  const v = keywordInputValue.value.trim()
  if (!v) return
  if (filterKeywords.value.includes(v)) {
    keywordInputValue.value = ''
    return
  }
  if (filterKeywords.value.length >= 10) {
    ElMessage.warning('最多支持10个关键词')
    return
  }
  filterKeywords.value.push(v)
  keywordInputValue.value = ''
}

function removeKeyword(idx) {
  filterKeywords.value.splice(idx, 1)
}

function handleBackspace() {
  if (keywordInputValue.value === '' && filterKeywords.value.length > 0) {
    filterKeywords.value.pop()
  }
}

function applyFilter() {
  pageNum.value = 1
  fetchList()
}

function clearFilter() {
  filterKeywords.value = []
  keywordInputValue.value = ''
  filterDateRange.value = null
  pageNum.value = 1
  fetchList()
}

function selectAllCurrentPage() {
  const available = textList.value.filter(item => !isSelected(item.sid))
  const canAdd = 20 - selectedItems.value.length
  if (canAdd <= 0) {
    ElMessage.warning('已达20篇上限')
    return
  }
  const toAdd = available.slice(0, canAdd)
  selectedItems.value.push(...toAdd)
  if (toAdd.length < available.length) {
    ElMessage.warning(`已添加 ${toAdd.length} 篇，已达20篇上限`)
  } else {
    ElMessage.success(`已全选当前页 ${toAdd.length} 篇报文`)
  }
}

function clearAllSelected() {
  selectedItems.value = []
  ElMessage.success('已清除所有勾选')
}

function handleSearch() {
  pageNum.value = 1
  if (filterSource.value) {
    selectedTypeIds.value = [filterSource.value]
    selectedModalTypes.value = []
  }
  fetchList()
}

function toggleCategorySelection(id) {
  // 在分类树中递归查找节点
  function findNode(nodes, targetId) {
    for (const node of nodes) {
      if (node.id === targetId) return node
      if (node.children && node.children.length > 0) {
        const found = findNode(node.children, targetId)
        if (found) return found
      }
    }
    return null
  }

  // 收集节点及其所有子孙节点的 id
  function collectIds(node) {
    const ids = [node.id]
    if (node.children && node.children.length > 0) {
      for (const child of node.children) {
        ids.push(...collectIds(child))
      }
    }
    return ids
  }

  const targetNode = findNode(categoryTree.value, id)
  let idsToToggle = [id]
  if (targetNode) {
    idsToToggle = collectIds(targetNode)
  }

  // 判断当前是否已选中（以第一个 id 为准）
  const isSelected = selectedTypeIds.value.includes(id)
  if (isSelected) {
    // 取消选中：移除所有相关 id
    selectedTypeIds.value = selectedTypeIds.value.filter(x => !idsToToggle.includes(x))
  } else {
    // 选中：加入所有相关 id（去重）
    const set = new Set(selectedTypeIds.value)
    idsToToggle.forEach(x => set.add(x))
    selectedTypeIds.value = Array.from(set)
  }
  filterSource.value = null
  pageNum.value = 1
  fetchList()
}

function toggleModalTypeSelection(mt) {
  const idx = selectedModalTypes.value.indexOf(mt)
  if (idx > -1) {
    selectedModalTypes.value.splice(idx, 1)
  } else {
    selectedModalTypes.value.push(mt)
  }
  filterSource.value = null
  pageNum.value = 1
  fetchList()
}

function toggleGroup(group) {
  if (group === 'source') sourceExpanded.value = !sourceExpanded.value
  else modalExpanded.value = !modalExpanded.value
}

async function handleTargetSearch() {
  if (!targetKeyword.value.trim()) {
    ElMessage.warning('请输入目标名称')
    return
  }
  targetSearching.value = true
  try {
    const res = await request.post('/api/fusion/searchByTarget', null, {
      params: { targetName: targetKeyword.value.trim(), maxReports: 10 }
    })
    if (res.data && res.data.reports) {
      const reports = res.data.reports
      if (reports.length === 0) {
        ElMessage.warning('未找到包含该目标的报文')
        return
      }
      textList.value = reports.map(r => ({
        sid: r.id,
        title: r.title,
        content: r.content,
        times: r.times,
        type: r.type
      }))
      total.value = reports.length
      selectedItems.value = textList.value
      isTargetMode.value = true
      ElMessage.success(`找到 ${reports.length} 篇相关报文，已自动全选`)
    }
  } catch (e) {
    ElMessage.error('搜索失败')
  } finally {
    targetSearching.value = false
  }
}

function exitTargetMode() {
  isTargetMode.value = false
  targetKeyword.value = ''
  selectedItems.value = []
  fetchList()
}

function getCategoryName(typeId) {
  // 递归查找节点
  function findInTree(nodes, id) {
    for (const node of nodes) {
      if (node.id === id) return node.name
      if (node.children && node.children.length > 0) {
        const found = findInTree(node.children, id)
        if (found) return found
      }
    }
    return ''
  }
  return findInTree(categoryTree.value, typeId)
}

function isSelected(sid) {
  return selectedItems.value.some(i => i.sid === sid)
}

function toggleSelect(item) {
  const idx = selectedItems.value.findIndex(i => i.sid === item.sid)
  if (idx >= 0) {
    selectedItems.value.splice(idx, 1)
  } else {
    if (selectedItems.value.length >= 20) {
      ElMessage.warning('最多选择20篇报文')
      return
    }
    selectedItems.value.push(item)
  }
}

async function showDetail(item) {
  activeDetail.value = item
  extractionResult.value = null
  try {
    const res = await request.get('/uygur/detail/' + item.sid)
    detailContent.value = res.data?.content || item.content
  } catch {
    detailContent.value = item.content
  }
  fetchExtractionResult(item.sid)
}

async function fetchExtractionResult(sid) {
  try {
    const res = await request.get('/extraction/result/' + sid)
    if (res.data && res.data.events) {
      extractionResult.value = res.data
    }
  } catch {}
}

async function handleExtract(force = false) {
  if (!activeDetail.value) return
  extracting.value = true
  console.log('[抽取] 开始抽取, sid:', activeDetail.value.sid, ', force:', force)
  try {
    const res = await request.post('/extraction/extract', null, {
      params: { originTextId: activeDetail.value.sid, force },
      timeout: 1200000
    })
    if (res.data) {
      extractionResult.value = res.data
      console.log('[抽取] 抽取完成:', { events: res.data.events?.length, labels: res.data.labels })
      ElMessage.success('抽取完成')
    }
  } catch {
    ElMessage.error('抽取失败')
  } finally {
    extracting.value = false
  }
}

function getEventEntityItems(subject, entityItems) {
  if (!subject || !entityItems) return []
  return entityItems.filter(item => subject.includes(item))
}

async function handleFusion() {
  if (selectedItems.value.length < 2) {
    ElMessage.warning('至少选择2篇报文')
    return
  }
  if (selectedItems.value.length > 20) {
    ElMessage.warning('最多选择20篇报文')
    return
  }
  console.log('[融合] 已选报文:', selectedItems.value.map(i => ({ sid: i.sid, title: i.title })))
  const reports = []
  for (const item of selectedItems.value) {
    let extractResult = null
    try {
      const res = await request.get('/extraction/result/' + item.sid)
      if (res.data && res.data.events) extractResult = res.data
    } catch {}
    reports.push({
      id: item.sid,
      title: item.title,
      content: item.content,
      times: item.times,
      type: item.type,
      extractionResult: extractResult
    })
  }
  store.setFusionData({
    reports,
    fusionType: isTargetMode.value ? 'target' : 'manual',
    targetName: isTargetMode.value ? targetKeyword.value.trim() : null
  })
  router.push('/Home/fusionResult')
}
</script>

<style scoped>
.search-page {
  height: calc(100vh - 182px);
  display: flex;
  flex-direction: column;
}

.filter-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 0 12px;
  border-bottom: 1px solid var(--border-color-light);
  margin-bottom: 16px;
  flex-wrap: wrap;
  flex-shrink: 0;
}

.filter-select {
  width: 120px;
}

.total-num {
  margin-left: auto;
  color: var(--color-text);
  font-size: 13px;
}

.main-layout {
  display: flex;
  gap: 16px;
  flex: 1;
  min-height: 0;
}

/* 左侧目录 */
.category-panel {
  width: 220px;
  flex-shrink: 0;
  background: var(--bg-card-strong);
  border: 1px solid var(--border-color);
  border-radius: 10px;
  overflow-y: auto;
  box-shadow: var(--shadow-card);
}

.cat-title {
  text-align: center;
  padding: 12px;
  color: var(--color-accent);
  font-size: 16px;
  font-weight: bold;
  border-bottom: 1px solid var(--border-color);
}

.cat-group-header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 12px;
  background: var(--bg-panel-strong);
  border: 1px solid var(--border-color);
  border-radius: 4px;
  margin: 6px 8px;
  color: var(--color-accent);
  font-size: 13px;
  font-weight: bold;
  cursor: pointer;
}

.cat-level1 {
  padding: 8px 12px;
  margin: 4px 8px;
  background: var(--bg-panel-strong);
  border: 1px solid var(--border-color);
  border-radius: 4px;
  color: var(--color-accent);
  font-size: 13px;
  font-weight: bold;
  cursor: pointer;
  transition: all 0.2s;
}

.cat-level1:hover {
  background: var(--bg-card);
  border-color: var(--color-primary);
}

.cat-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px 8px 28px;
  margin: 3px 8px 3px 18px;
  background: var(--bg-panel);
  border: 1px solid var(--border-color-light);
  border-radius: 4px;
  color: var(--color-menu-text);
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s;
}

.cat-item input[type="checkbox"] {
  cursor: pointer;
  margin: 0;
}

.cat-item:hover {
  border-color: var(--border-color);
  color: var(--color-text-strong);
  background: var(--bg-panel-strong);
}

.cat-item.active {
  background: var(--bg-menu-active);
  border-color: var(--border-color);
  color: var(--color-menu-text-active);
}

.cat-item .report-count {
  margin-left: auto;
  font-size: 11px;
  color: var(--color-text-weak);
  font-weight: 500;
}

.cat-item.cat-level-1 {
  font-weight: 600;
  padding-left: 8px;
}

.cat-item.cat-level-2 {
  padding-left: 24px;
  margin-left: 26px;
}

.cat-item.cat-level-3 {
  padding-left: 40px;
  margin-left: 34px;
  font-size: 11px;
}

.cat-item.cat-level-4 {
  padding-left: 56px;
  margin-left: 42px;
  font-size: 11px;
}

.cat-item.cat-level-5 {
  padding-left: 72px;
  margin-left: 50px;
  font-size: 10px;
}

/* 一级分类：稍醒目，缩进浅 */
.cat-level-1 {
  margin: 4px 8px;
  padding: 8px 12px 8px 14px;
  font-size: 13px;
  font-weight: bold;
  color: var(--color-accent);
  background: var(--bg-panel-strong);
}

/* 二级分类：默认 .cat-item 样式 + 更深的缩进体现层级 */
.cat-level-2 {
  margin-left: 28px;
  padding-left: 24px;
}

/* 中间列表 */
.list-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
}

/* 高级筛选栏 */
.advanced-filter-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 12px 20px;
  align-items: center;
  padding: 10px 14px;
  background: var(--bg-card-strong);
  border: 1px solid var(--border-color);
  border-radius: 8px 8px 0 0;
  border-bottom: none;
}

.filter-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.filter-label {
  font-size: 13px;
  color: var(--color-text);
  white-space: nowrap;
}

.keyword-tag-box {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px;
  min-width: 280px;
  max-width: 420px;
  min-height: 32px;
  padding: 4px 8px;
  border: 1px solid var(--border-color);
  border-radius: 4px;
  background: var(--bg-panel);
  cursor: text;
}

.kw-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 8px;
  background: var(--color-accent);
  color: #fff;
  border-radius: 12px;
  font-size: 12px;
  white-space: nowrap;
}

.kw-tag-close {
  cursor: pointer;
  font-weight: bold;
  padding: 0 2px;
  line-height: 1;
}

.kw-tag-close:hover {
  opacity: 0.7;
}

.kw-input {
  flex: 1;
  min-width: 100px;
  border: none;
  outline: none;
  background: transparent;
  color: var(--color-text);
  font-size: 13px;
  padding: 2px 4px;
}

.filter-date-picker {
  width: 240px;
}

.filter-btn {
  padding: 5px 14px;
  font-size: 13px;
  border-radius: 4px;
  cursor: pointer;
  border: 1px solid var(--border-color);
  background: var(--bg-panel);
  color: var(--color-text);
  transition: all 0.2s;
}

.filter-btn-primary {
  background: var(--color-accent);
  color: #fff;
  border-color: var(--color-accent);
}

.filter-btn-primary:hover {
  opacity: 0.85;
}

.filter-btn-clear:hover {
  background: var(--bg-panel-strong);
}

.filter-info {
  font-size: 12px;
  color: var(--color-accent);
  margin-left: 6px;
}

.batch-btn {
  margin-left: 8px;
  padding: 5px 12px;
  font-size: 13px;
  border-radius: 4px;
  cursor: pointer;
  border: 1px solid var(--border-color);
  background: var(--bg-panel);
  color: var(--color-text);
  transition: all 0.2s;
}

.batch-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.batch-select-btn:hover:not(:disabled) {
  background: var(--color-accent);
  color: #fff;
  border-color: var(--color-accent);
}

.batch-clear-btn:hover:not(:disabled) {
  background: #c62828;
  color: #fff;
  border-color: #c62828;
}

.fusion-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 14px;
  background: var(--bg-panel-sticky);
  border: 1px solid var(--border-color-light);
  border-radius: 5px;
  margin-bottom: 10px;
  flex-shrink: 0;
  box-shadow: var(--shadow-panel);
}

.fusion-count {
  color: var(--color-accent);
  font-size: 13px;
}

.fusion-btn {
  padding: 5px 14px;
  background: var(--bg-btn-primary);
  border: none;
  border-radius: 4px;
  color: var(--color-btn-text);
  font-size: 13px;
  cursor: pointer;
}

.fusion-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.fusion-tip {
  color: var(--color-text-secondary);
  font-size: 11px;
}

.fusion-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.fusion-divider {
  width: 1px;
  height: 24px;
  background: var(--border-color);
}

.fusion-right {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
}

.target-label {
  color: var(--color-text);
  font-size: 13px;
  white-space: nowrap;
}

.target-input {
  flex: 1;
  max-width: 280px;
  padding: 6px 12px;
  background: var(--bg-input);
  border: 1px solid var(--border-color);
  border-radius: 4px;
  color: var(--color-text);
  font-size: 13px;
  outline: none;
  transition: border-color 0.2s;
}

.target-input:focus {
  border-color: var(--color-primary);
}

.target-input:disabled {
  background: var(--bg-disabled);
  cursor: not-allowed;
}

.target-btn {
  padding: 6px 16px;
  background: var(--color-primary);
  color: #fff;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 13px;
  white-space: nowrap;
  transition: all 0.2s;
}

.target-btn:hover:not(:disabled) {
  background: var(--color-accent);
  transform: translateY(-1px);
}

.target-btn:disabled {
  background: var(--bg-disabled);
  color: var(--color-text-disabled);
  cursor: not-allowed;
}

.target-clear-btn {
  padding: 6px 12px;
  background: var(--bg-tag-orange);
  color: var(--color-tag-text-orange);
  border: 1px solid var(--color-title);
  border-radius: 4px;
  cursor: pointer;
  font-size: 13px;
  white-space: nowrap;
  transition: all 0.2s;
}

.target-clear-btn:hover {
  opacity: 0.8;
  transform: translateY(-1px);
}

.list-body {
  flex: 1;
  overflow-y: auto;
}

.list-item {
  padding: 14px;
  margin-bottom: 8px;
  border-bottom: 1px dashed var(--border-color-dashed);
  transition: background 0.2s;
  cursor: pointer;
}

.list-item:hover {
  background: var(--bg-card);
}

.list-item.is-active {
  background: var(--bg-card-strong);
}

.item-title {
  color: var(--color-title);
  font-size: 17px;
  margin-bottom: 4px;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  display: flex;
  align-items: center;
  gap: 8px;
}

.item-time {
  color: var(--color-text);
  font-size: 12px;
  margin-bottom: 6px;
}

.item-tags {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
  margin-bottom: 6px;
}

.tag {
  padding: 2px 8px;
  background: var(--bg-tag);
  border: 1px solid var(--color-primary);
  border-radius: 3px;
  color: var(--color-tag-text);
  font-size: 11px;
}

.item-content {
  color: var(--color-text);
  font-size: 13px;
  line-height: 20px;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.modal-badge {
  display: inline-block;
  padding: 1px 6px;
  background: var(--bg-tag-orange);
  border: 1px solid var(--color-title);
  border-radius: 3px;
  color: var(--color-tag-text-orange);
  font-size: 10px;
  font-weight: bold;
  flex-shrink: 0;
}

/* 图片展示区 */
.detail-images { margin: 12px 0; }
.img-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 8px; }
.img-grid.single { grid-template-columns: 1fr; }
.img-item img { width: 100%; border-radius: 6px; cursor: zoom-in; object-fit: cover; max-height: 200px; }
.img-item:hover { opacity: 0.85; }

/* 抽取等待名言 */
.extracting-panel { text-align: center; padding: 24px 16px; }
.extracting-status { margin: 10px 0 16px; color: var(--color-text-secondary); font-size: 13px; }
.quote-card { padding: 14px; background: var(--bg-card); border-left: 3px solid var(--color-primary); border-radius: 4px; text-align: left; }
.quote-text { color: var(--color-text-strong); font-size: 14px; line-height: 22px; font-style: italic; }
.quote-author { color: var(--color-text-secondary); font-size: 12px; margin-top: 8px; text-align: right; }
.quote-fade-enter-active, .quote-fade-leave-active { transition: opacity 0.5s ease; }
.quote-fade-enter-from, .quote-fade-leave-to { opacity: 0; }

.pagination {
  flex-shrink: 0;
  display: flex;
  justify-content: flex-end;
  padding: 12px 0;
}

/* 右侧详情 */
.detail-panel {
  width: 340px;
  flex-shrink: 0;
  background: var(--bg-card-strong);
  border: 1px solid var(--border-color);
  border-radius: 10px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  box-shadow: var(--shadow-card);
}

.detail-close {
  padding: 10px 16px;
  background: var(--bg-panel-strong);
  color: var(--color-primary);
  font-size: 14px;
  cursor: pointer;
  border-radius: 10px 10px 0 0;
  flex-shrink: 0;
}

.detail-close:hover {
  color: var(--color-accent);
}

.detail-scroll {
  flex: 1;
  overflow-y: auto;
}

.detail-body {
  padding: 16px;
}

.detail-title {
  color: var(--color-title);
  font-size: 20px;
  line-height: 28px;
  margin-bottom: 14px;
}

.detail-meta {
  margin-bottom: 14px;
}

.detail-meta .dm-row {
  color: var(--color-text);
  font-size: 13px;
  line-height: 24px;
}

.dm-label {
  color: var(--color-accent);
  font-weight: bold;
  display: inline-block;
  width: 80px;
}

.detail-text {
  color: var(--color-text);
  font-size: 13px;
  line-height: 22px;
  white-space: pre-wrap;
  word-wrap: break-word;
}

/* 抽取面板 */
.extract-panel {
  margin: 12px 16px 16px;
  padding: 14px;
  background: var(--bg-extraction);
  border: 1px solid var(--border-color);
  border-radius: 4px;
}

.extract-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
  border-bottom: 1px dashed var(--border-color);
  padding-bottom: 8px;
}

.extract-title {
  color: var(--color-title-section);
  font-size: 14px;
  font-weight: bold;
}

.extract-btn {
  padding: 4px 12px;
  background: var(--bg-btn-primary);
  border: none;
  border-radius: 4px;
  color: var(--color-btn-text);
  font-size: 12px;
  cursor: pointer;
}

.extract-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.overall-labels {
  margin-bottom: 12px;
  padding: 8px;
  background: var(--bg-tag-orange);
  border-radius: 4px;
  border-left: 3px solid var(--color-title);
}

.ol-title {
  color: var(--color-title);
  font-weight: bold;
  font-size: 12px;
  margin-right: 6px;
}

.ol-tag {
  display: inline-block;
  padding: 2px 8px;
  margin: 2px;
  background: var(--bg-tag-orange);
  border: 1px solid var(--color-title);
  border-radius: 3px;
  color: var(--color-tag-text-orange);
  font-size: 11px;
}

.event-card {
  background: var(--bg-card);
  padding: 10px;
  margin-bottom: 8px;
  border-radius: 4px;
  border-left: 3px solid var(--color-primary);
}

.event-title {
  color: var(--color-primary);
  font-weight: bold;
  font-size: 13px;
  margin-bottom: 6px;
}

.event-field {
  font-size: 12px;
  line-height: 20px;
  color: var(--color-text);
}

.ef-label {
  color: var(--color-text-secondary);
  margin-right: 4px;
}

.event-original {
  margin-top: 4px;
  padding-top: 4px;
  border-top: 1px dashed var(--border-color-light);
  font-style: italic;
  opacity: 0.85;
}

.event-tags {
  margin-top: 6px;
  padding-top: 4px;
  border-top: 1px dashed var(--bg-tag);
}

.et-tag {
  display: inline-block;
  padding: 1px 6px;
  margin: 1px;
  background: var(--bg-tag);
  border: 1px solid var(--color-primary);
  border-radius: 3px;
  color: var(--color-tag-text);
  font-size: 10px;
}
</style>
