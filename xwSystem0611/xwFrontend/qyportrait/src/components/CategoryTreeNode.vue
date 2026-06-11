<template>
  <div>
    <div
      :class="['cat-item', 'cat-level-' + (depth + 1), { active: selectedIds.includes(node.id) }]"
      @click="$emit('toggle', node.id)"
    >
      <input type="checkbox" :checked="selectedIds.includes(node.id)" @click.stop>
      {{ node.name }}
      <span v-if="node.reportCount" class="report-count">({{ node.reportCount }})</span>
    </div>
    <template v-if="node.children && node.children.length > 0">
      <CategoryTreeNode
        v-for="child in node.children"
        :key="child.id"
        :node="child"
        :selectedIds="selectedIds"
        :depth="depth + 1"
        @toggle="$emit('toggle', $event)"
      />
    </template>
  </div>
</template>

<script setup>
defineProps({
  node: { type: Object, required: true },
  selectedIds: { type: Array, required: true },
  depth: { type: Number, default: 0 }
})

defineEmits(['toggle'])
</script>

<style scoped>
.cat-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  margin: 3px 8px;
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
  margin-left: 8px;
}

.cat-item.cat-level-2 {
  margin-left: 24px;
}

.cat-item.cat-level-3 {
  margin-left: 40px;
  font-size: 11px;
}

.cat-item.cat-level-4 {
  margin-left: 56px;
  font-size: 11px;
}

.cat-item.cat-level-5 {
  margin-left: 72px;
  font-size: 10px;
}
</style>
