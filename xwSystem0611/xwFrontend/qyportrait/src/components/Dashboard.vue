<template>
  <div class="dashboard-page">
    <!-- 顶部核心指标卡片 -->
    <div class="metrics-row">
      <div class="metric-card">
        <div class="mc-icon">📄</div>
        <div class="mc-info">
          <div class="mc-label">总报文数</div>
          <div class="mc-value">{{ overview.totalReports || 0 }}</div>
        </div>
      </div>
      <div class="metric-card">
        <div class="mc-icon">✅</div>
        <div class="mc-info">
          <div class="mc-label">已抽取报文</div>
          <div class="mc-value">{{ overview.extractedReports || 0 }}</div>
        </div>
      </div>
      <div class="metric-card">
        <div class="mc-icon">🔗</div>
        <div class="mc-info">
          <div class="mc-label">融合报告数</div>
          <div class="mc-value">{{ overview.fusionReports || 0 }}</div>
        </div>
      </div>
      <div class="metric-card">
        <div class="mc-icon">🏷️</div>
        <div class="mc-info">
          <div class="mc-label">标签总数</div>
          <div class="mc-value">{{ overview.totalLabels || 0 }}</div>
        </div>
      </div>
    </div>

    <!-- 图表网格 -->
    <div class="charts-grid">
      <div class="chart-card">
        <div class="chart-title">分类分布</div>
        <div ref="categoryChartRef" class="chart-container"></div>
      </div>
      <div class="chart-card">
        <div class="chart-title">模态类型分布</div>
        <div ref="modalChartRef" class="chart-container"></div>
      </div>
      <div class="chart-card">
        <div class="chart-title">抽取进度</div>
        <div class="progress-panel">
          <div class="progress-item">
            <span class="progress-label">已抽取</span>
            <div class="progress-bar">
              <div class="progress-fill" :style="{ width: extractionProgress + '%' }"></div>
            </div>
            <span class="progress-value">{{ extractionProgress }}%</span>
          </div>
          <div class="progress-stats">
            <div class="stat-item">
              <span class="stat-label">已完成:</span>
              <span class="stat-value">{{ overview.extractedReports || 0 }}</span>
            </div>
            <div class="stat-item">
              <span class="stat-label">未抽取:</span>
              <span class="stat-value">{{ (overview.totalReports || 0) - (overview.extractedReports || 0) }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 批量抽取操作面板 -->
    <div class="batch-panel">
      <div class="bp-header">批量属性抽取</div>
      <div class="bp-body">
        <div class="bp-controls">
          <div class="control-group">
            <label class="control-label">时间范围:</label>
            <el-date-picker
              v-model="batchDateRange"
              type="daterange"
              range-separator="至"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
              format="YYYY-MM-DD"
              value-format="YYYY-MM-DD"
              size="small"
              :disabled="batchRunning"
            />
          </div>
          <div class="control-group">
            <label class="control-label">抽取范围:</label>
            <el-radio-group v-model="batchScope" size="small" :disabled="batchRunning">
              <el-radio value="unextracted">仅未抽取</el-radio>
              <el-radio value="all">全部重新抽取</el-radio>
            </el-radio-group>
          </div>
          <div class="control-group">
            <button class="batch-btn primary" v-if="!batchRunning" @click="startBatchExtraction" :disabled="!batchDateRange">启动批量抽取</button>
            <button class="batch-btn danger" v-else @click="stopBatchExtraction">停止任务</button>
          </div>
        </div>

        <div v-if="batchTaskId" class="bp-progress">
          <div class="progress-header">
            <span class="progress-title">任务进度</span>
            <span class="progress-percent">{{ batchProgressPercent }}%</span>
          </div>
          <div class="progress-bar-large">
            <div class="progress-fill-large" :style="{ width: batchProgressPercent + '%' }"></div>
          </div>
          <div class="progress-counters">
            <div class="counter-item">
              <span class="counter-label">待抽取:</span>
              <span class="counter-value">{{ batchProgress.total - batchProgress.done - batchProgress.failed }}</span>
            </div>
            <div class="counter-item success">
              <span class="counter-label">已完成:</span>
              <span class="counter-value">{{ batchProgress.done }}</span>
            </div>
            <div class="counter-item error">
              <span class="counter-label">失败:</span>
              <span class="counter-value">{{ batchProgress.failed }}</span>
            </div>
            <div class="counter-item" v-if="batchProgress.eta > 0">
              <span class="counter-label">预计剩余:</span>
              <span class="counter-value">{{ formatEta(batchProgress.eta) }}</span>
            </div>
          </div>
          <div class="progress-logs">
            <div class="logs-header">执行日志</div>
            <div class="logs-body">
              <div v-for="(log, idx) in batchProgress.logs" :key="idx" class="log-item">{{ log }}</div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 数据库管理面板 -->
    <div class="db-management-panel">
      <div class="db-header">
        <span>数据库管理</span>
        <el-button type="primary" size="small" @click="openFieldManagement">字段管理</el-button>
      </div>
      <div class="db-body">
        <el-tabs v-model="dbActiveTab" type="border-card">
          <!-- Tab 1: 报文导入 -->
          <el-tab-pane label="报文导入" name="import">
            <div class="import-section">
              <div class="ip-controls">
                <div class="control-group">
                  <label class="control-label">默认分类:</label>
                  <el-select v-model="defaultCategoryId" placeholder="无sendUnitName时的默认分类" size="small" style="width: 240px">
                    <el-option label="未分类 (推荐)" :value="2" />
                    <el-option v-for="cat in allLeafCategories" :key="cat.id" :label="cat.name" :value="cat.id" />
                  </el-select>
                  <el-tooltip content="JSONL中有sendUnitName时自动创建分类，无则使用此默认分类" placement="top">
                    <el-icon style="margin-left: 4px; color: #909399; cursor: help;"><QuestionFilled /></el-icon>
                  </el-tooltip>
                </div>
                <div class="control-group">
                  <label class="control-label">JSONL文件:</label>
                  <el-upload
                    :auto-upload="false"
                    :show-file-list="true"
                    :limit="1"
                    accept=".jsonl"
                    :on-change="handleJsonlChange"
                    :on-remove="handleJsonlRemove"
                  >
                    <el-button size="small">选择JSONL</el-button>
                  </el-upload>
                </div>
                <div class="control-group">
                  <label class="control-label">图片文件:</label>
                  <el-upload
                    :auto-upload="false"
                    :show-file-list="false"
                    :limit="100"
                    accept="image/*"
                    :on-change="handleImageChange"
                    :on-remove="handleImageRemove"
                    multiple
                  >
                    <el-button size="small">选择图片</el-button>
                  </el-upload>
                  <el-tag v-if="importImages.length > 0" size="small" type="success" style="margin-left: 8px">{{ importImages.length }} 张</el-tag>
                </div>
                <div class="control-group">
                  <button class="batch-btn primary" @click="submitImportWithImages" :disabled="importLoading">
                    {{ importLoading ? '导入中...' : '开始导入' }}
                  </button>
                </div>
              </div>
              <div v-if="importResult" class="import-result">
                <span class="success-text">成功：{{ importResult.successCount }} 条</span>
                <span class="error-text">失败：{{ importResult.failCount }} 条</span>
                <span class="info-text">总行数：{{ importResult.totalLines }}</span>
                <span class="info-text" v-if="importResult.uploadedImages">已上传图片：{{ importResult.uploadedImages }} 张</span>
              </div>
              <el-alert type="info" :closable="false" style="margin-top: 16px">
                <template #title>
                  <div style="font-size: 13px; line-height: 1.8">
                    <strong>导入说明：</strong><br>
                    1. JSONL 文件格式：每行一个 JSON 对象，必须包含"标题"、"内容"、"时间"字段<br>
                    2. 图文报支持：在 JSON 中添加"图片"字段，值为图片文件名数组，如 ["1.jpg", "2.png"]<br>
                    3. 图片文件：选择对应的图片文件，系统自动匹配 JSONL 中的文件名上传至 MinIO
                  </div>
                </template>
              </el-alert>
            </div>
          </el-tab-pane>
          <!-- Tab 2: 数据管理 -->
          <el-tab-pane label="数据管理" name="data">
            <div class="data-management-section">
              <!-- 表信息 -->
              <div class="table-info">
                <el-tag type="info">当前表：origin_text</el-tag>
                <el-button type="primary" size="small" @click="openFieldDialog" style="margin-left: 16px">
                  字段管理
                </el-button>
                <el-button size="small" @click="fetchTableStructure">
                  刷新
                </el-button>
              </div>

              <!-- 字段列表表格 -->
              <el-table :data="tableFields" border style="margin-top: 16px" size="small">
                <el-table-column prop="fieldName" label="字段名" width="180" />
                <el-table-column prop="dataType" label="数据类型" width="120" />
                <el-table-column prop="maxLength" label="长度" width="80" />
                <el-table-column prop="isNullable" label="可空" width="80">
                  <template #default="{ row }">
                    <el-tag :type="row.isNullable === 'YES' ? 'success' : 'danger'" size="small">
                      {{ row.isNullable === 'YES' ? '是' : '否' }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="defaultValue" label="默认值" width="120" />
                <el-table-column prop="comment" label="注释" min-width="200" />
                <el-table-column prop="columnKey" label="键" width="60">
                  <template #default="{ row }">
                    <el-tag v-if="row.columnKey === 'PRI'" type="warning" size="small">主键</el-tag>
                  </template>
                </el-table-column>
              </el-table>
            </div>
          </el-tab-pane>
        </el-tabs>
      </div>
    </div>

    <!-- 分类管理面板 -->
    <div class="category-mgmt-panel">
      <div class="cmp-header">
        <span>分类管理</span>
        <el-button type="primary" size="small" @click="openCategoryDialog">管理分类</el-button>
      </div>
    </div>

    <!-- 分类管理对话框 -->
    <el-dialog v-model="categoryDialogVisible" title="分类管理" width="800px">
      <div class="category-dialog-body">
        <div class="category-form">
          <el-form :inline="true" size="small">
            <el-form-item label="分类名称">
              <el-input v-model="categoryForm.typeName" placeholder="请输入分类名称" style="width: 160px" />
            </el-form-item>
            <el-form-item label="分类级别">
              <el-radio-group v-model="categoryForm.isTopLevel" :disabled="categoryDialogMode === 'edit'">
                <el-radio :label="true">一级分类</el-radio>
                <el-radio :label="false">二级分类</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="父分类" v-if="!categoryForm.isTopLevel">
              <el-select v-model="categoryForm.parentId" placeholder="选择父分类" style="width: 140px" :disabled="categoryDialogMode === 'edit'">
                <el-option v-for="cat in parentCategoryList" :key="cat.id" :label="cat.typeName" :value="cat.id" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="saveCategory">
                {{ categoryDialogMode === 'add' ? '新增' : '保存' }}
              </el-button>
              <el-button @click="resetCategoryForm">重置</el-button>
            </el-form-item>
          </el-form>
        </div>

        <el-table :data="categoryTableData" stripe style="width: 100%; margin-top: 16px" size="small" height="400">
          <el-table-column prop="typeName" label="分类名称" min-width="160">
            <template #default="{ row }">
              <span :style="{ paddingLeft: row.level === 2 ? '20px' : '0' }">
                {{ row.level === 2 ? '└ ' : '' }}{{ row.typeName }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="级别" width="80">
            <template #default="{ row }">
              {{ row.level === 1 ? '一级' : '二级' }}
            </template>
          </el-table-column>
          <el-table-column prop="parentName" label="父分类" width="140" />
          <el-table-column label="操作" width="160" align="center">
            <template #default="{ row }">
              <el-button type="primary" size="small" @click="editCategory(row)">编辑</el-button>
              <el-button type="danger" size="small" @click="confirmDeleteCategory(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-dialog>

    <!-- 最近融合报告列表 -->
    <div class="fusion-list-panel">
      <div class="flp-header">最近融合报告</div>
      <div class="flp-body">
        <el-table :data="recentFusions" stripe style="width: 100%" size="small">
          <el-table-column prop="title" label="报告标题" min-width="200" />
          <el-table-column prop="createTime" label="创建时间" width="180" />
          <el-table-column label="操作" width="120" align="center">
            <template #default="{ row }">
              <el-button type="primary" size="small" @click="viewFusion(row.fusionId)">查看</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>

    <!-- 字段管理对话框 -->
    <el-dialog v-model="fieldDialogVisible" title="字段管理" width="900px">
      <el-tabs v-model="fieldManagementTab">
        <!-- Tab 1: 新增字段 -->
        <el-tab-pane label="新增字段" name="add">
          <el-form :model="addFieldForm" label-width="100px" size="small">
            <el-form-item label="字段名">
              <el-input v-model="addFieldForm.fieldName" placeholder="例如: extend4" style="width: 300px" />
            </el-form-item>
            <el-form-item label="数据类型">
              <el-select v-model="addFieldForm.dataType" style="width: 300px">
                <el-option label="VARCHAR" value="VARCHAR" />
                <el-option label="TEXT" value="TEXT" />
                <el-option label="INT" value="INT" />
                <el-option label="TINYINT" value="TINYINT" />
                <el-option label="DATETIME" value="DATETIME" />
              </el-select>
            </el-form-item>
            <el-form-item label="长度" v-if="addFieldForm.dataType === 'VARCHAR'">
              <el-input-number v-model="addFieldForm.length" :min="1" :max="10000" />
            </el-form-item>
            <el-form-item label="允许为空">
              <el-switch v-model="addFieldForm.nullable" />
            </el-form-item>
            <el-form-item label="默认值">
              <el-input v-model="addFieldForm.defaultValue" placeholder="留空表示无默认值" style="width: 300px" />
            </el-form-item>
            <el-form-item label="字段注释">
              <el-input v-model="addFieldForm.comment" placeholder="字段用途说明" style="width: 300px" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="submitAddField">添加字段</el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>

        <!-- __DIALOG_CONTINUE_1__ -->

        <!-- Tab 2: 修改字段 -->
        <el-tab-pane label="修改字段" name="modify">
          <el-alert type="warning" :closable="false" style="margin-bottom: 16px">
            修改字段可能影响现有数据，请谨慎操作！
          </el-alert>
          <el-form :model="modifyFieldForm" label-width="100px" size="small">
            <el-form-item label="选择字段">
              <el-select v-model="modifyFieldForm.fieldName" @change="loadFieldInfo" style="width: 300px">
                <el-option
                  v-for="field in editableFields"
                  :key="field.fieldName"
                  :label="field.fieldName"
                  :value="field.fieldName"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="新类型">
              <el-select v-model="modifyFieldForm.dataType" style="width: 300px">
                <el-option label="VARCHAR" value="VARCHAR" />
                <el-option label="TEXT" value="TEXT" />
                <el-option label="INT" value="INT" />
              </el-select>
            </el-form-item>
            <el-form-item label="新长度" v-if="modifyFieldForm.dataType === 'VARCHAR'">
              <el-input-number v-model="modifyFieldForm.length" :min="1" :max="10000" />
            </el-form-item>
            <el-form-item label="新注释">
              <el-input v-model="modifyFieldForm.comment" style="width: 300px" />
            </el-form-item>
            <el-form-item>
              <el-button type="warning" @click="submitModifyField">修改字段（需确认）</el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>

        <!-- Tab 3: 删除字段 -->
        <el-tab-pane label="删除字段" name="delete">
          <el-alert type="danger" :closable="false" style="margin-bottom: 16px">
            删除字段将永久删除该列的所有数据，无法恢复！
          </el-alert>
          <el-form :model="deleteFieldForm" label-width="100px" size="small">
            <el-form-item label="选择字段">
              <el-select v-model="deleteFieldForm.fieldName" style="width: 300px">
                <el-option
                  v-for="field in deletableFields"
                  :key="field.fieldName"
                  :label="field.fieldName + ' (' + (field.comment || '无注释') + ')'"
                  :value="field.fieldName"
                />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="danger" @click="submitDeleteField">删除字段（需多次确认）</el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>
      </el-tabs>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { QuestionFilled } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as echarts from 'echarts'
import request from '@/utils/request'

const router = useRouter()

const overview = ref({
  totalReports: 0,
  extractedReports: 0,
  fusionReports: 0,
  totalLabels: 0
})

const categoryDistribution = ref([])
const modalDistribution = ref([])
const recentFusions = ref([])

const categoryChartRef = ref(null)
const modalChartRef = ref(null)
let categoryChart = null
let modalChart = null

const batchDateRange = ref(null)
const batchScope = ref('unextracted')
const batchTaskId = ref(null)
const batchRunning = ref(false)
const batchProgress = ref({
  total: 0,
  done: 0,
  failed: 0,
  running: false,
  currentSid: null,
  logs: [],
  eta: 0
})

// 导入功能相关状态
const categories = ref([])
const importParentCategory = ref('')  // 兼容旧逻辑（已废弃）
const importChildCategory = ref('')   // 兼容旧逻辑（已废弃）
const defaultCategoryId = ref(2)       // 新：默认分类ID（"未分类"）
const allLeafCategories = ref([])      // 新：所有叶子节点
const importFile = ref(null)
const importLoading = ref(false)
const importResult = ref(null)
const importImages = ref([])           // 新增：待上传图片列表
const dbActiveTab = ref('import')      // 新增：数据库管理 Tab

// 字段管理相关状态
const tableFields = ref([])            // 表字段列表
const fieldDialogVisible = ref(false)  // 字段管理对话框
const fieldManagementTab = ref('add')  // 对话框 Tab
const addFieldForm = ref({
  fieldName: '',
  dataType: 'VARCHAR',
  length: 255,
  nullable: true,
  defaultValue: '',
  comment: ''
})
const modifyFieldForm = ref({
  fieldName: '',
  dataType: 'VARCHAR',
  length: 255,
  comment: ''
})
const deleteFieldForm = ref({
  fieldName: ''
})

// 分类管理相关状态
const categoryDialogVisible = ref(false)
const categoryDialogMode = ref('add')
const categoryForm = ref({
  id: null,
  typeName: '',
  parentId: null,
  isTopLevel: true
})
const categoryTableData = ref([])

let progressTimer = null

// 计算属性：一级分类列表
const parentCategoryList = computed(() => {
  return categories.value.filter(cat => !cat.parentId || cat.parentId === 0)
})

// 计算属性：二级分类列表（根据选中的一级分类过滤）
const importChildCategoryList = computed(() => {
  if (!importParentCategory.value) return []
  const parent = parentCategoryList.value.find(p => p.typeName === importParentCategory.value)
  return parent ? (parent.children || []) : []
})

// 计算属性：可编辑字段（排除主键和核心字段）
const editableFields = computed(() => {
  return tableFields.value.filter(f =>
    !['sid', 'title', 'content', 'times', 'type'].includes(f.fieldName)
  )
})

// 计算属性：可删除字段（仅扩展字段和非核心字段）
const deletableFields = computed(() => {
  return tableFields.value.filter(f =>
    f.fieldName.startsWith('extend') ||
    !['sid', 'title', 'content', 'times', 'type', 'is_extracted', 'modal_type', 'images'].includes(f.fieldName)
  )
})

const extractionProgress = computed(() => {
  if (overview.value.totalReports === 0) return 0
  return Math.round((overview.value.extractedReports / overview.value.totalReports) * 100)
})

const batchProgressPercent = computed(() => {
  if (batchProgress.value.total === 0) return 0
  return Math.round(((batchProgress.value.done + batchProgress.value.failed) / batchProgress.value.total) * 100)
})

function formatEta(seconds) {
  if (seconds < 60) return `${seconds}秒`
  const minutes = Math.floor(seconds / 60)
  if (minutes < 60) return `${minutes}分钟`
  const hours = Math.floor(minutes / 60)
  const mins = minutes % 60
  return `${hours}小时${mins}分钟`
}

async function fetchOverview() {
  try {
    const res = await request.get('/api/dashboard/overview')
    if (res.data) {
      overview.value = res.data
    }
  } catch (e) {
    console.error('获取概览数据失败:', e)
  }
}

async function fetchCategoryDistribution() {
  try {
    const res = await request.get('/api/dashboard/categoryDistribution')
    if (res.data) {
      categoryDistribution.value = res.data
      renderCategoryChart()
    }
  } catch (e) {
    console.error('获取分类分布失败:', e)
  }
}

async function fetchModalDistribution() {
  try {
    const res = await request.get('/api/dashboard/modalDistribution')
    if (res.data) {
      modalDistribution.value = res.data
      renderModalChart()
    }
  } catch (e) {
    console.error('获取模态分布失败:', e)
  }
}

async function fetchRecentFusions() {
  try {
    const res = await request.get('/api/dashboard/recentFusions')
    if (res.data) {
      recentFusions.value = res.data
    }
  } catch (e) {
    console.error('获取最近融合报告失败:', e)
  }
}

function renderCategoryChart() {
  if (!categoryChartRef.value) return
  if (!categoryChart) {
    categoryChart = echarts.init(categoryChartRef.value)
  }
  const option = {
    tooltip: { trigger: 'item' },
    series: [{
      type: 'pie',
      radius: '60%',
      data: categoryDistribution.value.map(item => ({
        name: item.type || '未分类',
        value: item.cnt
      })),
      emphasis: {
        itemStyle: {
          shadowBlur: 10,
          shadowOffsetX: 0,
          shadowColor: 'rgba(0, 0, 0, 0.5)'
        }
      }
    }]
  }
  categoryChart.setOption(option)
}

function renderModalChart() {
  if (!modalChartRef.value) return
  if (!modalChart) {
    modalChart = echarts.init(modalChartRef.value)
  }
  const option = {
    tooltip: { trigger: 'item' },
    series: [{
      type: 'pie',
      radius: ['40%', '70%'],
      data: modalDistribution.value.map(item => ({
        name: item.modal_type || '未知',
        value: item.cnt
      })),
      emphasis: {
        itemStyle: {
          shadowBlur: 10,
          shadowOffsetX: 0,
          shadowColor: 'rgba(0, 0, 0, 0.5)'
        }
      }
    }]
  }
  modalChart.setOption(option)
}

async function startBatchExtraction() {
  if (!batchDateRange.value || batchDateRange.value.length !== 2) {
    ElMessage.warning('请选择时间范围')
    return
  }
  try {
    const res = await request.post('/extraction/batch/start', null, {
      params: {
        startDate: batchDateRange.value[0],
        endDate: batchDateRange.value[1],
        scope: batchScope.value
      }
    })
    if (res.data) {
      batchTaskId.value = res.data.taskId
      batchRunning.value = true
      batchProgress.value = {
        total: res.data.totalCount,
        done: 0,
        failed: 0,
        running: true,
        currentSid: null,
        logs: [],
        eta: 0
      }
      ElMessage.success(`批量抽取任务已启动，共 ${res.data.totalCount} 篇报文`)
      startProgressPolling()
    }
  } catch (e) {
    ElMessage.error('启动批量抽取失败')
  }
}

async function stopBatchExtraction() {
  if (!batchTaskId.value) return
  try {
    await request.post(`/extraction/batch/stop/${batchTaskId.value}`)
    ElMessage.success('任务已停止')
    batchRunning.value = false
    stopProgressPolling()
  } catch (e) {
    ElMessage.error('停止任务失败')
  }
}

function startProgressPolling() {
  if (progressTimer) return
  progressTimer = setInterval(async () => {
    if (!batchTaskId.value) {
      stopProgressPolling()
      return
    }
    try {
      const res = await request.get(`/extraction/batch/progress/${batchTaskId.value}`)
      if (res.data) {
        batchProgress.value = res.data
        if (!res.data.running) {
          batchRunning.value = false
          stopProgressPolling()
          ElMessage.success('批量抽取任务已完成')
          fetchOverview()
        }
      }
    } catch (e) {
      console.error('查询进度失败:', e)
    }
  }, 3000)
}

function stopProgressPolling() {
  if (progressTimer) {
    clearInterval(progressTimer)
    progressTimer = null
  }
}

function viewFusion(id) {
  router.push(`/fusionDetail/${id}`)
}

// 获取分类树
async function fetchCategories() {
  try {
    const res = await request.get('/uygur/category')
    if (res.data) {
      categories.value = res.data
    }
    // 加载所有叶子节点用于导入
    const leafRes = await request.get('/api/category/leafs')
    if (leafRes.data) {
      allLeafCategories.value = leafRes.data
    }
  } catch (e) {
    console.error('获取分类失败:', e)
  }
}

// JSONL 文件选择处理
function handleJsonlChange(file) {
  importFile.value = file.raw
  importResult.value = null
}

// JSONL 文件移除处理
function handleJsonlRemove() {
  importFile.value = null
  importResult.value = null
}

// 图片文件选择处理
function handleImageChange(file) {
  importImages.value.push(file.raw)
}

// 图片文件移除处理
function handleImageRemove(file) {
  const index = importImages.value.findIndex(f => f.uid === file.uid)
  if (index > -1) {
    importImages.value.splice(index, 1)
  }
}

// 提交带图片的导入
async function submitImportWithImages() {
  if (!importFile.value) {
    ElMessage.warning('请选择JSONL文件')
    return
  }

  importLoading.value = true
  const formData = new FormData()
  formData.append('file', importFile.value)
  if (defaultCategoryId.value) {
    formData.append('defaultCategoryId', defaultCategoryId.value)
  }

  // 附加所有图片文件
  importImages.value.forEach((img) => {
    formData.append('images', img)
  })

  try {
    const res = await request.post('/uygur/importFromJsonlWithImages', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 600000
    })

    if (res.code === 1) {
      ElMessage.success(`导入完成`)
      importResult.value = res.data
      importFile.value = null
      importImages.value = []
      fetchOverview()
      fetchCategoryDistribution()
    } else {
      ElMessage.error(res.msg || '导入失败')
    }
  } catch (e) {
    ElMessage.error('导入失败: ' + (e.message || ''))
  } finally {
    importLoading.value = false
  }
}

// 加载表结构
async function fetchTableStructure() {
  try {
    const res = await request.get('/api/database/table/structure', {
      params: { tableName: 'origin_text' }
    })
    if (res.code === 1) {
      tableFields.value = res.data
    }
  } catch (e) {
    console.error('加载表结构失败:', e)
  }
}

// 打开字段管理对话框
function openFieldManagement() {
  fieldDialogVisible.value = true
  fieldManagementTab.value = 'add'
}

// 打开字段管理对话框（别名）
function openFieldDialog() {
  openFieldManagement()
}

// 加载字段信息（修改时）
function loadFieldInfo() {
  const field = tableFields.value.find(f => f.fieldName === modifyFieldForm.value.fieldName)
  if (field) {
    modifyFieldForm.value.dataType = field.dataType.toUpperCase()
    modifyFieldForm.value.length = field.maxLength || 255
    modifyFieldForm.value.comment = field.comment || ''
  }
}

// __FIELD_METHODS_CONTINUE__

// 新增字段
async function submitAddField() {
  if (!addFieldForm.value.fieldName || !addFieldForm.value.dataType) {
    ElMessage.warning('请填写字段名和数据类型')
    return
  }

  try {
    const res = await request.post('/api/database/field/add', {
      tableName: 'origin_text',
      ...addFieldForm.value
    })
    if (res.code === 1) {
      ElMessage.success('字段添加成功')
      await fetchTableStructure()
      fieldDialogVisible.value = false
      // 重置表单
      addFieldForm.value = {
        fieldName: '',
        dataType: 'VARCHAR',
        length: 255,
        nullable: true,
        defaultValue: '',
        comment: ''
      }
    } else {
      ElMessage.error(res.msg || '字段添加失败')
    }
  } catch (e) {
    ElMessage.error('字段添加失败: ' + (e.response?.data?.msg || e.message))
  }
}

// 修改字段（二次确认）
async function submitModifyField() {
  if (!modifyFieldForm.value.fieldName) {
    ElMessage.warning('请选择要修改的字段')
    return
  }

  try {
    await ElMessageBox.confirm(
      `确定要修改字段 "${modifyFieldForm.value.fieldName}" 吗？此操作可能影响现有数据！`,
      '修改确认',
      { type: 'warning' }
    )

    const res = await request.post('/api/database/field/modify', {
      tableName: 'origin_text',
      ...modifyFieldForm.value
    })
    if (res.code === 1) {
      ElMessage.success('字段修改成功')
      await fetchTableStructure()
      fieldDialogVisible.value = false
    } else {
      ElMessage.error(res.msg || '字段修改失败')
    }
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error('字段修改失败: ' + (e.response?.data?.msg || e.message))
    }
  }
}

// 删除字段（多次确认）
async function submitDeleteField() {
  if (!deleteFieldForm.value.fieldName) {
    ElMessage.warning('请选择要删除的字段')
    return
  }

  try {
    // 第一次确认
    await ElMessageBox.confirm(
      `确定要删除字段 "${deleteFieldForm.value.fieldName}" 吗？`,
      '删除警告',
      { type: 'warning' }
    )

    // 第二次确认
    await ElMessageBox.confirm(
      `删除后该字段的所有数据将永久丢失，无法恢复！真的要删除 "${deleteFieldForm.value.fieldName}" 吗？`,
      '最终确认',
      { type: 'error', confirmButtonText: '确认删除', cancelButtonText: '取消' }
    )

    const res = await request.post('/api/database/field/delete', {
      tableName: 'origin_text',
      fieldName: deleteFieldForm.value.fieldName
    })
    if (res.code === 1) {
      ElMessage.success('字段已删除')
      await fetchTableStructure()
      fieldDialogVisible.value = false
      deleteFieldForm.value.fieldName = ''
    } else {
      ElMessage.error(res.msg || '字段删除失败')
    }
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error('字段删除失败: ' + (e.response?.data?.msg || e.message))
    }
  }
}

// 提交导入（旧版，保留兼容）
async function submitImport() {
  if (!importFile.value) {
    ElMessage.warning('请选择JSONL文件')
    return
  }

  importLoading.value = true
  const formData = new FormData()
  formData.append('file', importFile.value)
  if (defaultCategoryId.value) {
    formData.append('defaultCategoryId', defaultCategoryId.value)
  }

  try {
    const res = await request.post('/uygur/importFromJsonl', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    if (res.code === 1) {
      ElMessage.success(`导入完成`)
      importResult.value = res.data
      importFile.value = null
      fetchOverview()
      fetchCategoryDistribution()
    } else {
      ElMessage.error(res.msg || '导入失败')
    }
  } catch (e) {
    ElMessage.error('导入失败')
  } finally {
    importLoading.value = false
  }
}

// 打开分类管理对话框
function openCategoryDialog() {
  buildCategoryTableData()
  categoryDialogVisible.value = true
}

// 构建表格数据（扁平化分类树）
function buildCategoryTableData() {
  const tableData = []
  for (const parent of categories.value) {
    if (!parent.parentId || parent.parentId === 0) {
      tableData.push({
        id: parent.id,
        typeName: parent.typeName,
        parentId: parent.parentId,
        level: 1,
        parentName: null
      })
      if (parent.children && parent.children.length > 0) {
        for (const child of parent.children) {
          tableData.push({
            id: child.id,
            typeName: child.typeName,
            parentId: child.parentId,
            level: 2,
            parentName: parent.typeName
          })
        }
      }
    }
  }
  categoryTableData.value = tableData
}

// 编辑分类
function editCategory(row) {
  categoryDialogMode.value = 'edit'
  categoryForm.value = {
    id: row.id,
    typeName: row.typeName,
    parentId: row.parentId,
    isTopLevel: row.level === 1
  }
}

// 重置表单
function resetCategoryForm() {
  categoryForm.value = {
    id: null,
    typeName: '',
    parentId: null,
    isTopLevel: true
  }
  categoryDialogMode.value = 'add'
}

// 保存分类（新增或修改）
async function saveCategory() {
  if (!categoryForm.value.typeName) {
    ElMessage.warning('请输入分类名称')
    return
  }

  if (!categoryForm.value.isTopLevel && !categoryForm.value.parentId) {
    ElMessage.warning('请选择父分类')
    return
  }

  try {
    if (categoryDialogMode.value === 'add') {
      const payload = {
        typeName: categoryForm.value.typeName,
        parentId: categoryForm.value.isTopLevel ? null : categoryForm.value.parentId
      }
      await request.post('/uygur/category', payload)
      ElMessage.success('新增成功')
    } else {
      await request.put(`/uygur/category/${categoryForm.value.id}`, {
        newTypeName: categoryForm.value.typeName
      })
      ElMessage.success('修改成功')
    }

    await fetchCategories()
    buildCategoryTableData()
    resetCategoryForm()
  } catch (e) {
    ElMessage.error('操作失败')
  }
}

// 确认删除分类
async function confirmDeleteCategory(row) {
  try {
    await ElMessageBox.confirm(
      `确定删除分类"${row.typeName}"吗？${row.level === 1 ? '该操作会同时删除其所有子分类。' : ''}`,
      '警告',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }
    )

    await request.delete(`/uygur/category/${row.id}`)
    ElMessage.success('删除成功')
    await fetchCategories()
    buildCategoryTableData()
    fetchCategoryDistribution()
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

onMounted(() => {
  fetchOverview()
  fetchCategoryDistribution()
  fetchModalDistribution()
  fetchRecentFusions()
  fetchCategories()
  fetchTableStructure()  // 加载表结构
})

onBeforeUnmount(() => {
  stopProgressPolling()
  if (categoryChart) {
    categoryChart.dispose()
    categoryChart = null
  }
  if (modalChart) {
    modalChart.dispose()
    modalChart = null
  }
})
</script>

<style scoped>
.dashboard-page {
  padding: 20px;
  background: var(--bg-main);
  min-height: calc(100vh - 182px);
}

.metrics-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 20px;
}

.metric-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 20px;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  box-shadow: var(--shadow-card);
}

.mc-icon {
  font-size: 36px;
}

.mc-info {
  flex: 1;
}

.mc-label {
  color: var(--color-text-secondary);
  font-size: 13px;
  margin-bottom: 4px;
}

.mc-value {
  color: var(--color-primary);
  font-size: 28px;
  font-weight: bold;
}

.charts-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
  margin-bottom: 20px;
}

.chart-card {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  padding: 16px;
  box-shadow: var(--shadow-card);
}

.chart-title {
  color: var(--color-title);
  font-size: 16px;
  font-weight: bold;
  margin-bottom: 12px;
}

.chart-container {
  width: 100%;
  height: 280px;
}

.progress-panel {
  padding: 20px 0;
}

.progress-item {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.progress-label {
  color: var(--color-text);
  font-size: 14px;
  min-width: 60px;
}

.progress-bar {
  flex: 1;
  height: 20px;
  background: var(--bg-disabled);
  border-radius: 10px;
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  background: linear-gradient(90deg, var(--color-primary), var(--color-accent));
  transition: width 0.3s;
}

.progress-value {
  color: var(--color-primary);
  font-size: 14px;
  font-weight: bold;
  min-width: 50px;
  text-align: right;
}

.progress-stats {
  display: flex;
  gap: 24px;
  margin-top: 12px;
}

.stat-item {
  display: flex;
  gap: 8px;
}

.stat-label {
  color: var(--color-text-secondary);
  font-size: 13px;
}

.stat-value {
  color: var(--color-text);
  font-size: 13px;
  font-weight: bold;
}

.batch-panel {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  margin-bottom: 20px;
  box-shadow: var(--shadow-card);
}

.bp-header {
  padding: 14px 20px;
  background: var(--bg-panel-strong);
  border-bottom: 1px solid var(--border-color);
  color: var(--color-title);
  font-size: 16px;
  font-weight: bold;
  border-radius: 8px 8px 0 0;
}

.bp-body {
  padding: 20px;
}

.bp-controls {
  display: flex;
  gap: 20px;
  align-items: center;
  margin-bottom: 20px;
}

.control-group {
  display: flex;
  align-items: center;
  gap: 8px;
}

.control-label {
  color: var(--color-text);
  font-size: 13px;
  white-space: nowrap;
}

.batch-btn {
  padding: 8px 20px;
  border: none;
  border-radius: 4px;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s;
}

.batch-btn.primary {
  background: var(--color-primary);
  color: #fff;
}

.batch-btn.primary:hover:not(:disabled) {
  background: var(--color-accent);
}

.batch-btn.danger {
  background: #f56c6c;
  color: #fff;
}

.batch-btn.danger:hover {
  background: #f78989;
}

.batch-btn:disabled {
  background: rgba(64, 158, 255, 0.2);
  color: rgba(64, 158, 255, 0.5);
  border: 1px solid rgba(64, 158, 255, 0.3);
  cursor: not-allowed;
  opacity: 0.6;
}

.bp-progress {
  background: var(--bg-panel);
  border: 1px solid var(--border-color);
  border-radius: 6px;
  padding: 16px;
}

.progress-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.progress-title {
  color: var(--color-title);
  font-size: 14px;
  font-weight: bold;
}

.progress-percent {
  color: var(--color-primary);
  font-size: 18px;
  font-weight: bold;
}

.progress-bar-large {
  height: 24px;
  background: var(--bg-disabled);
  border-radius: 12px;
  overflow: hidden;
  margin-bottom: 16px;
}

.progress-fill-large {
  height: 100%;
  background: linear-gradient(90deg, var(--color-primary), var(--color-accent));
  transition: width 0.3s;
  animation: shimmer 2s infinite;
}

@keyframes shimmer {
  0% { opacity: 0.8; }
  50% { opacity: 1; }
  100% { opacity: 0.8; }
}

.progress-counters {
  display: flex;
  gap: 24px;
  margin-bottom: 16px;
}

.counter-item {
  display: flex;
  gap: 8px;
}

.counter-item.success .counter-value {
  color: #67c23a;
}

.counter-item.error .counter-value {
  color: #f56c6c;
}

.counter-label {
  color: var(--color-text-secondary);
  font-size: 13px;
}

.counter-value {
  color: var(--color-text);
  font-size: 13px;
  font-weight: bold;
}

.progress-logs {
  background: var(--bg-main);
  border: 1px solid var(--border-color);
  border-radius: 4px;
  overflow: hidden;
}

.logs-header {
  padding: 8px 12px;
  background: var(--bg-panel-strong);
  border-bottom: 1px solid var(--border-color);
  color: var(--color-text);
  font-size: 12px;
  font-weight: bold;
}

.logs-body {
  max-height: 200px;
  overflow-y: auto;
  padding: 8px 12px;
}

.log-item {
  color: var(--color-text);
  font-size: 12px;
  line-height: 20px;
  font-family: 'Courier New', monospace;
}

.fusion-list-panel {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  box-shadow: var(--shadow-card);
}

.db-management-panel {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  margin-bottom: 20px;
  box-shadow: var(--shadow-card);
}

.db-header {
  padding: 14px 20px;
  background: var(--bg-panel-strong);
  border-bottom: 1px solid var(--border-color);
  color: var(--color-title);
  font-size: 16px;
  font-weight: bold;
  border-radius: 8px 8px 0 0;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.db-body {
  padding: 20px;
}

.import-section {
  padding: 0;
}
.category-mgmt-panel {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  margin-bottom: 20px;
  box-shadow: var(--shadow-card);
}

.ip-header,
.cmp-header {
  padding: 14px 20px;
  background: var(--bg-panel-strong);
  border-bottom: 1px solid var(--border-color);
  color: var(--color-title);
  font-size: 16px;
  font-weight: bold;
  border-radius: 8px 8px 0 0;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.cmp-header {
  border-bottom: none;
  border-radius: 8px;
}

.ip-body {
  padding: 20px;
}

.ip-controls {
  display: flex;
  gap: 20px;
  align-items: flex-start;
  flex-wrap: wrap;
}

.import-result {
  margin-top: 16px;
  padding: 10px 14px;
  background: var(--bg-panel);
  border: 1px solid var(--border-color);
  border-radius: 4px;
  font-size: 13px;
  display: flex;
  gap: 24px;
}

.import-result .success-text {
  color: #67c23a;
  font-weight: bold;
}

.import-result .error-text {
  color: #f56c6c;
  font-weight: bold;
}

.import-result .info-text {
  color: var(--color-text-secondary);
}

.category-dialog-body {
  padding: 0 6px;
}

.category-form {
  padding: 12px 14px;
  background: var(--bg-panel);
  border: 1px solid var(--border-color);
  border-radius: 6px;
}

.flp-header {
  padding: 14px 20px;
  background: var(--bg-panel-strong);
  border-bottom: 1px solid var(--border-color);
  color: var(--color-title);
  font-size: 16px;
  font-weight: bold;
  border-radius: 8px 8px 0 0;
}

.flp-body {
  padding: 20px;
}
</style>
