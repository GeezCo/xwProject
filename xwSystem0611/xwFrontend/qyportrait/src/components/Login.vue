<template>
  <div class="page-wrapper">
    <div class="login-title">
      <h1>献 微 系 统</h1>
      <p>基于大模型的文章撰写与数据分析系统</p>
    </div>
    <div class="login-box">
      <div class="form-group">
        <label>账 号</label>
        <input
          v-model="username"
          type="text"
          placeholder="请输入用户名"
          @keyup.enter="handleLogin"
        >
      </div>
      <div class="form-group">
        <label>密 码</label>
        <input
          v-model="password"
          type="password"
          placeholder="请输入密码"
          @keyup.enter="handleLogin"
        >
      </div>
      <button class="login-btn" @click="handleLogin">登 录</button>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useHomeStore } from '@/stores/home'

const router = useRouter()
const store = useHomeStore()

const username = ref('admin')
const password = ref('password123')

onMounted(() => {
  store.initTheme()
})

function handleLogin() {
  if (!username.value || !password.value) return
  store.setToken('xianwei-token-' + Date.now())
  router.push('/Home/searchResult')
}
</script>

<style scoped>
.page-wrapper {
  min-height: 100vh;
  background: var(--login-bg);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.login-title {
  margin-bottom: 40px;
  text-align: center;
}

.login-title h1 {
  font-size: 36px;
  color: var(--color-accent);
  letter-spacing: 12px;
  font-weight: bold;
  text-shadow: 0 0 20px rgba(0, 154, 251, 0.3);
}

.login-title p {
  color: var(--color-text-secondary);
  font-size: 14px;
  margin-top: 8px;
}

.login-box {
  width: 480px;
  padding: 50px 40px;
  background: var(--login-box-bg);
  border: 1px solid var(--login-box-border);
  border-radius: 12px;
  box-shadow: var(--login-box-shadow);
  backdrop-filter: blur(10px);
}

.form-group {
  margin-bottom: 24px;
}

.form-group label {
  display: block;
  color: var(--color-text-strong);
  font-size: 16px;
  margin-bottom: 8px;
  letter-spacing: 2px;
}

.form-group input {
  width: 100%;
  height: 48px;
  padding: 0 16px;
  background: var(--bg-input);
  border: 1px solid var(--border-color-input);
  border-radius: 6px;
  color: var(--color-text-strong);
  font-size: 15px;
  outline: none;
  transition: border-color 0.3s;
}

.form-group input:focus {
  border-color: var(--color-primary);
}

.form-group input::placeholder {
  color: var(--color-text-secondary);
}

.login-btn {
  width: 100%;
  height: 52px;
  background: var(--bg-btn-primary);
  border: none;
  border-radius: 6px;
  color: var(--color-btn-text);
  font-size: 18px;
  cursor: pointer;
  margin-top: 16px;
  letter-spacing: 4px;
  transition: opacity 0.3s;
}

.login-btn:hover {
  opacity: 0.85;
}
</style>
