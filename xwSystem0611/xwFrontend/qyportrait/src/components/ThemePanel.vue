<template>
  <div class="theme-panel">
    <!-- 事件表格 -->
    <el-table :data="allEvents" border stripe :max-height="600">
      <el-table-column prop="sourceTitle" label="来源报文" width="200" show-overflow-tooltip />
      <el-table-column prop="time" label="事件时间" width="150" />
      <el-table-column prop="event_content" label="事件内容" min-width="200" show-overflow-tooltip />
      <el-table-column prop="event_analysis" label="事件分析" min-width="300">
        <template #default="{ row }">
          <el-tooltip :content="row.event_analysis" placement="top">
            <div class="text-ellipsis">{{ row.event_analysis }}</div>
          </el-tooltip>
        </template>
      </el-table-column>
    </el-table>

    <!-- 加载状态 -->
    <div v-if="loading" class="loading-mask">
      <el-icon class="is-loading"><Loading /></el-icon>
      <span>正在加载事件数据...</span>
    </div>

    <!-- 空数据状态 -->
    <el-empty v-if="!loading && allEvents.length === 0" description="该时间段暂无相关报文" />

    <!-- 统计信息 -->
    <div v-if="!loading && allEvents.length > 0" class="stats">
      共识别 {{ allEvents.length }} 个事件
    </div>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { Loading } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'

const props = defineProps({
  themeConfig: Object,
  dateRange: Array
})

const allEvents = ref([])
const loading = ref(false)
const currentIndex = ref(0)
const totalCount = ref(0)
const reportCount = ref(0)

// 监听日期变化自动加载
watch(() => props.dateRange, () => {
  loadEvents()
}, { deep: true, immediate: true })

async function loadEvents() {
  if (!props.dateRange || props.dateRange.length !== 2) return

  loading.value = true
  allEvents.value = []
  currentIndex.value = 0

  try {
    // 直接查询已分析的事件（后台作业已完成分析）
    const res = await request.post('/api/eventAnalysis/query', {
      startDate: props.dateRange[0],
      endDate: props.dateRange[1],
      keywords: props.themeConfig.keywords
    })

    if (res.code === 1 && res.data && res.data.length > 0) {
      allEvents.value = res.data
      reportCount.value = res.data.length
    } else {
      allEvents.value = []
      reportCount.value = 0
      ElMessage.info('该时间段暂无相关事件数据')
    }

  } catch (e) {
    console.error('加载事件失败:', e)
    ElMessage.error('加载失败: ' + (e.message || '未知错误'))
  } finally {
    loading.value = false
    currentIndex.value = 0
  }
}

defineExpose({ loadEvents })
</script>

<style scoped>
.theme-panel {
  position: relative;
  padding: 16px;
}

.loading-mask {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(255, 255, 255, 0.8);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  z-index: 10;
}

.stats {
  margin-top: 16px;
  padding: 12px;
  background: #f5f7fa;
  border-radius: 4px;
  text-align: center;
  color: #606266;
}

.text-ellipsis {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
