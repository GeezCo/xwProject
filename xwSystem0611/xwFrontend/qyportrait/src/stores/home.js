import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { getToken, setToken as setTokenStorage, removeToken } from '@/utils/auth'
import request from '@/utils/request'

export const useHomeStore = defineStore('home', () => {
  const token = ref(getToken() || '')
  const userInfo = ref(null)
  const theme = ref(localStorage.getItem('theme') || 'light')  // 默认白日模式
  const fusionData = ref(null)
  const minioPrefix = ref('')

  const isLoggedIn = computed(() => !!token.value)

  async function fetchConfig() {
    try {
      const res = await request.get('/uygur/config')
      if (res.data?.minioPrefix) {
        minioPrefix.value = res.data.minioPrefix
      }
    } catch (e) {
      console.warn('[config] 获取配置失败, 使用默认值', e)
    }
  }

  function setToken(val) {
    token.value = val
    setTokenStorage(val)
  }

  function logout() {
    token.value = ''
    userInfo.value = null
    removeToken()
  }

  function toggleTheme() {
    theme.value = theme.value === 'dark' ? 'light' : 'dark'
    localStorage.setItem('theme', theme.value)
    document.documentElement.setAttribute('data-theme', theme.value)
  }

  function initTheme() {
    document.documentElement.setAttribute('data-theme', theme.value)
  }

  function setFusionData(data) {
    fusionData.value = data
  }

  function clearFusionData() {
    fusionData.value = null
  }

  return { token, userInfo, theme, fusionData, minioPrefix, isLoggedIn, setToken, logout, toggleTheme, initTheme, setFusionData, clearFusionData, fetchConfig }
})
