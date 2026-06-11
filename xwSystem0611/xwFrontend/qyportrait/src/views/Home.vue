<template>
  <div class="home-container">
    <div class="top-nav">
      <div class="nav-left">
        <span class="logout" @click="handleLogout">&#x23FB; 退出登录</span>
        <button class="theme-btn" @click="store.toggleTheme()">
          {{ store.theme === 'dark' ? '🌙' : '☀️' }}
        </button>
      </div>
      <div class="nav-center">
        <div class="menu-item" :class="{ active: currentRoute === 'searchResult' }" @click="navigateTo('searchResult')">
          <el-icon class="menu-icon"><Document /></el-icon>
          <span>动态信息</span>
        </div>
        <div class="menu-item" :class="{ active: currentRoute === 'dashboard' }" @click="navigateTo('dashboard')">
          <el-icon class="menu-icon"><DataAnalysis /></el-icon>
          <span>数据看板</span>
        </div>
        <div class="menu-item" :class="{ active: currentRoute === 'DailyBrief' }" @click="navigateTo('dailyBrief')">
          <el-icon class="menu-icon"><Calendar /></el-icon>
          <span>每日要情</span>
        </div>
        <div class="menu-item" :class="{ active: currentRoute === 'categoryManagement' }" @click="navigateTo('categoryManagement')">
          <el-icon class="menu-icon"><Folder /></el-icon>
          <span>分类管理</span>
        </div>
      </div>
      <div class="nav-right">
        <div class="time-display">{{ clockText }}</div>
      </div>
    </div>

    <div class="router-view">
      <router-view v-slot="{ Component }">
        <keep-alive include="SearchResult">
          <component :is="Component" />
        </keep-alive>
      </router-view>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { Document, DataAnalysis, Calendar, Folder } from '@element-plus/icons-vue'
import { useHomeStore } from '@/stores/home'

const router = useRouter()
const route = useRoute()
const store = useHomeStore()
const clockText = ref('')
let timer = null

const currentRoute = computed(() => {
  return route.name
})

onMounted(() => {
  store.initTheme()
  store.fetchConfig()
  updateClock()
  timer = setInterval(updateClock, 1000)

  // 默认导航到搜索页
  if (route.path === '/Home' || route.path === '/Home/') {
    router.push('/Home/searchResult')
  }
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
})

function updateClock() {
  clockText.value = '北京时间 ' + new Date().toLocaleString('zh-CN', { hour12: false })
}

function handleLogout() {
  store.logout()
  router.push('/')
}

function navigateTo(routeName) {
  router.push(`/Home/${routeName}`)
}
</script>

<style scoped>
.home-container {
  min-height: 100vh;
  background: var(--bg-page);
}

.top-nav {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 30px;
  background: var(--bg-panel);
  border-bottom: 1px solid var(--border-color);
  box-shadow: var(--shadow-panel);
  height: 60px;
  box-sizing: border-box;
}

.nav-left {
  display: flex;
  align-items: center;
  gap: 12px;
  flex: 1;
}

.nav-left .logout {
  color: var(--color-text-strong);
  font-size: 15px;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 4px;
}

.nav-left .logout:hover {
  color: var(--color-accent);
}

.theme-btn {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: transparent;
  border: 1px solid var(--border-color);
  color: var(--color-accent);
  font-size: 16px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.3s;
}

.theme-btn:hover {
  background: var(--bg-card);
}

.nav-center {
  display: flex;
  gap: 12px;
  flex: 0 0 auto;
}

.nav-right {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  flex: 1;
}

.time-display {
  color: var(--color-accent);
  font-size: 15px;
}

.menu-item {
  display: flex;
  align-items: center;
  gap: 6px;
  height: 36px;
  padding: 0 18px;
  background: var(--bg-menu);
  border: 1px solid var(--border-color-light);
  border-radius: 18px;
  font-size: 14px;
  color: var(--color-menu-text);
  cursor: pointer;
  transition: all 0.3s;
}

.menu-icon {
  font-size: 16px;
}

.menu-item:hover {
  border-color: var(--border-color);
  color: var(--color-text-strong);
}

.menu-item.active {
  background: var(--bg-menu-active);
  border-color: var(--border-color);
  color: var(--color-menu-text-active);
}

.router-view {
  padding: 16px;
  min-height: calc(100vh - 60px);
}
</style>
