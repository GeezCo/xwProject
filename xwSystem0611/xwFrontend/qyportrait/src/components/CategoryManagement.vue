<template>
  <div class="category-management">
    <div class="header">
      <h2>分类管理</h2>
      <el-button type="primary" @click="showAddDialog(null)">
        <el-icon><Plus /></el-icon> 新增根分类
      </el-button>
    </div>

    <div class="content">
      <el-tree
        ref="treeRef"
        :data="categoryTree"
        node-key="id"
        :props="treeProps"
        :expand-on-click-node="false"
        default-expand-all
        draggable
        :allow-drag="allowDrag"
        :allow-drop="allowDrop"
        @node-drop="handleDrop"
        @node-drag-start="handleDragStart"
        @node-drag-end="handleDragEnd"
      >
        <template #default="{ node, data }">
          <div class="tree-node">
            <span class="node-label">
              <el-icon class="drag-handle" title="拖拽移动"><Rank /></el-icon>
              <el-icon v-if="data.isLeaf" class="node-icon leaf"><Document /></el-icon>
              <el-icon v-else class="node-icon folder"><Folder /></el-icon>
              <span class="node-name">{{ data.name }}</span>
              <el-tag v-if="data.level" size="small" effect="plain" class="level-tag">L{{ data.level }}</el-tag>
              <el-tag v-if="data.isLeaf" size="small" type="success" effect="plain">叶子</el-tag>
              <span v-if="data.reportCount" class="report-count">{{ data.reportCount }} 篇</span>
            </span>
            <span class="node-actions">
              <el-button
                v-if="data.level < 5"
                link
                type="primary"
                size="small"
                title="新增子分类"
                @click="showAddDialog(data)"
              >
                <el-icon><Plus /></el-icon>
              </el-button>
              <el-button
                link
                type="primary"
                size="small"
                title="编辑"
                @click="showEditDialog(data)"
              >
                <el-icon><Edit /></el-icon>
              </el-button>
              <el-button
                v-if="data.id !== 1 && data.id !== 2"
                link
                type="danger"
                size="small"
                title="删除"
                @click="handleDelete(data)"
              >
                <el-icon><Delete /></el-icon>
              </el-button>
            </span>
          </div>
        </template>
      </el-tree>
    </div>

    <!-- 新增/编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="500px"
    >
      <el-form :model="formData" label-width="100px">
        <el-form-item label="分类名称" required>
          <el-input v-model="formData.name" placeholder="请输入分类名称" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input
            v-model="formData.description"
            type="textarea"
            :rows="3"
            placeholder="请输入描述（可选）"
          />
        </el-form-item>
        <el-form-item v-if="!isEdit" label="父分类">
          <el-input :value="parentName" disabled />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitting">
          确定
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Edit, Delete, Rank, Document, Folder } from '@element-plus/icons-vue'
import request from '@/utils/request'

const categoryTree = ref([])
const treeRef = ref(null)

const treeProps = {
  children: 'children',
  label: 'name'
}

const dialogVisible = ref(false)
const dialogTitle = computed(() => isEdit.value ? '编辑分类' : '新增分类')
const isEdit = ref(false)
const submitting = ref(false)

const formData = ref({
  id: null,
  name: '',
  description: '',
  parentId: null
})

const parentName = ref('')

// 加载分类树
async function loadCategoryTree() {
  try {
    const res = await request.get('/api/category/tree')
    categoryTree.value = res.data?.data || res.data || []
    console.log('分类树加载成功:', categoryTree.value)
  } catch (error) {
    ElMessage.error('加载分类树失败')
    console.error(error)
  }
}

// 显示新增对话框
function showAddDialog(parentNode) {
  isEdit.value = false
  formData.value = {
    id: null,
    name: '',
    description: '',
    parentId: parentNode ? parentNode.id : null
  }
  parentName.value = parentNode ? parentNode.name : '根分类'
  dialogVisible.value = true
}

// 显示编辑对话框
function showEditDialog(node) {
  isEdit.value = true
  formData.value = {
    id: node.id,
    name: node.name,
    description: node.description || '',
    parentId: node.parentId
  }
  dialogVisible.value = true
}

// 提交表单
async function handleSubmit() {
  if (!formData.value.name || formData.value.name.trim() === '') {
    ElMessage.warning('分类名称不能为空')
    return
  }

  submitting.value = true
  try {
    if (isEdit.value) {
      // 更新分类
      await request.put('/api/category/update', {
        categoryId: formData.value.id,
        newName: formData.value.name,
        newDescription: formData.value.description
      })
      ElMessage.success('更新成功')
    } else {
      // 新增分类
      await request.post('/api/category/create', {
        name: formData.value.name,
        parentId: formData.value.parentId,
        description: formData.value.description
      })
      ElMessage.success('新增成功')
    }

    dialogVisible.value = false
    await loadCategoryTree()
  } catch (error) {
    const msg = error.response?.data?.message || error.message || '操作失败'
    ElMessage.error(msg)
    console.error(error)
  } finally {
    submitting.value = false
  }
}

// 删除分类
async function handleDelete(node) {
  try {
    await ElMessageBox.confirm(
      `确定要删除分类"${node.name}"吗？此操作会级联删除所有子分类！`,
      '警告',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )

    await request.delete(`/api/category/delete/${node.id}`)
    ElMessage.success('删除成功')
    await loadCategoryTree()
  } catch (error) {
    if (error !== 'cancel') {
      const msg = error.response?.data?.message || error.message || '删除失败'
      ElMessage.error(msg)
      console.error(error)
    }
  }
}

// 拖拽节点
async function handleDrop(dragNode, dropNode, dropType) {
  console.log('拖拽:', { dragNode: dragNode.data.name, dropNode: dropNode.data.name, dropType })

  try {
    let newParentId = null

    if (dropType === 'inner') {
      // 拖入到节点内部
      newParentId = dropNode.data.id
    } else {
      // 拖到节点前/后，与目标节点同级
      newParentId = dropNode.data.parentId
    }

    // 调用移动接口
    await request.post('/api/category/move', {
      categoryId: dragNode.data.id,
      newParentId: newParentId
    })

    ElMessage.success('移动成功')
    await loadCategoryTree()
  } catch (error) {
    const msg = error.response?.data?.message || error.message || '移动失败'
    ElMessage.error(msg)
    console.error(error)
    // 移动失败，刷新树恢复原状
    await loadCategoryTree()
  }
}

// 拖拽开始
function handleDragStart(node) {
  console.log('开始拖拽:', node.data.name)
}

// 拖拽结束
function handleDragEnd() {
  console.log('拖拽结束')
}

// 允许拖拽判断（禁止拖拽根分类和未分类）
function allowDrag(draggingNode) {
  // 禁止拖拽 id=1（根分类）和 id=2（未分类）
  if (draggingNode.data.id === 1 || draggingNode.data.id === 2) {
    return false
  }
  return true
}

// 允许放置判断（防止拖到自己的子节点）
function allowDrop(draggingNode, dropNode, type) {
  // 禁止拖到根分类外
  if (!dropNode.data.parentId && type !== 'inner') {
    return false
  }
  // 禁止拖到自己的子孙节点
  return !isDescendant(draggingNode.data, dropNode.data)
}

// 判断 node2 是否是 node1 的子孙节点
function isDescendant(node1, node2) {
  if (node1.id === node2.id) return true
  if (!node1.children || node1.children.length === 0) return false
  return node1.children.some(child => isDescendant(child, node2))
}

onMounted(() => {
  loadCategoryTree()
})
</script>

<style scoped>
.category-management {
  height: 100%;
  display: flex;
  flex-direction: column;
  padding: 20px;
  background: var(--bg-primary);
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  padding-bottom: 15px;
  border-bottom: 2px solid var(--border-color);
}

.header h2 {
  margin: 0;
  color: var(--color-text-strong);
  font-size: 24px;
}

.content {
  flex: 1;
  overflow: auto;
  background: var(--bg-panel);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  padding: 20px;
}

.tree-node {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 14px;
  padding-right: 10px;
}

.node-label {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
}

.drag-handle {
  color: var(--color-text-weak);
  cursor: grab;
  font-size: 16px;
  transition: color 0.2s;
}

.drag-handle:hover {
  color: var(--color-primary);
}

.drag-handle:active {
  cursor: grabbing;
}

.node-icon {
  font-size: 16px;
}

.node-icon.folder {
  color: #f39c12;
}

.node-icon.leaf {
  color: #3498db;
}

.node-name {
  font-weight: 500;
  color: var(--color-text);
}

.level-tag {
  font-size: 11px;
  font-weight: 600;
}

.report-count {
  font-size: 12px;
  color: var(--color-text-weak);
  background: var(--bg-primary);
  padding: 2px 8px;
  border-radius: 10px;
  font-weight: 500;
}

.node-actions {
  display: flex;
  gap: 5px;
  opacity: 0;
  transition: opacity 0.2s;
}

.tree-node:hover .node-actions {
  opacity: 1;
}

:deep(.el-tree-node__content) {
  height: 44px;
  margin: 3px 0;
  border-radius: 6px;
  padding-left: 10px !important;
  transition: all 0.2s;
  border: 2px solid transparent;
}

:deep(.el-tree-node__content:hover) {
  background-color: var(--bg-panel-strong);
  border-color: var(--border-color);
}

/* 拖拽时的样式 */
:deep(.el-tree-node.is-drop-inner > .el-tree-node__content) {
  background-color: #e8f4fd !important;
  border-color: #409eff !important;
  box-shadow: 0 0 8px rgba(64, 158, 255, 0.3);
}

:deep(.el-tree-node__content.is-dragging) {
  opacity: 0.5;
  background-color: #f0f0f0;
  cursor: grabbing;
}

/* 拖拽占位符 */
:deep(.el-tree__drop-indicator) {
  height: 3px;
  background-color: #409eff;
  box-shadow: 0 0 6px rgba(64, 158, 255, 0.5);
}

/* 层级缩进可视化 */
:deep(.el-tree-node__children) {
  padding-left: 4px;
  border-left: 2px dashed var(--border-color-light);
  margin-left: 14px;
}

:deep(.el-tree-node__expand-icon) {
  color: var(--color-text-weak);
  font-size: 14px;
  padding: 6px;
  transition: transform 0.3s, color 0.2s;
}

:deep(.el-tree-node__expand-icon:hover) {
  color: var(--color-primary);
}

/* 不同层级背景色 */
:deep(.el-tree-node) {
  position: relative;
}

:deep([role="treeitem"][aria-level="1"] > .el-tree-node__content) {
  background: linear-gradient(90deg, rgba(64, 158, 255, 0.08), transparent);
  font-weight: 600;
}

:deep([role="treeitem"][aria-level="2"] > .el-tree-node__content) {
  background: linear-gradient(90deg, rgba(103, 194, 58, 0.05), transparent);
}

:deep([role="treeitem"][aria-level="3"] > .el-tree-node__content) {
  background: linear-gradient(90deg, rgba(230, 162, 60, 0.05), transparent);
}

:deep([role="treeitem"][aria-level="4"] > .el-tree-node__content) {
  background: linear-gradient(90deg, rgba(245, 108, 108, 0.05), transparent);
}

:deep([role="treeitem"][aria-level="5"] > .el-tree-node__content) {
  background: linear-gradient(90deg, rgba(144, 147, 153, 0.05), transparent);
}

/* 禁止拖放的节点 */
:deep(.el-tree-node.is-drop-not-allow > .el-tree-node__content) {
  cursor: not-allowed;
  opacity: 0.6;
}
</style>
