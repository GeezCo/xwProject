import { createRouter, createWebHistory } from 'vue-router'
import { getToken } from '@/utils/auth'
import Login from '@/components/Login.vue'
import Home from '@/views/Home.vue'
import SearchResult from '@/components/SearchResult.vue'
import FusionResult from '@/components/FusionResult.vue'
import Dashboard from '@/components/Dashboard.vue'
import DailyBrief from '@/components/DailyBrief.vue'
import CategoryManagement from '@/components/CategoryManagement.vue'

const routes = [
  {
    path: '/',
    name: 'Login',
    component: Login
  },
  {
    path: '/Home',
    name: 'Home',
    component: Home,
    children: [
      {
        path: 'searchResult',
        name: 'SearchResult',
        component: SearchResult,
        meta: { keepAlive: true }
      },
      {
        path: 'fusionResult',
        name: 'FusionResult',
        component: FusionResult
      },
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: Dashboard
      },
      {
        path: 'dailyBrief',
        name: 'DailyBrief',
        component: DailyBrief
      },
      {
        path: 'categoryManagement',
        name: 'CategoryManagement',
        component: CategoryManagement
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  const token = getToken()
  if (to.path !== '/' && !token) {
    next('/')
  } else {
    next()
  }
})

export default router
