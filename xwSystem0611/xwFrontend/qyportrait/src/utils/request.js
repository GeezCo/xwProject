import axios from 'axios'
import { ElMessage } from 'element-plus'
import { getToken } from './auth'

const request = axios.create({
  timeout: 30000
})

request.interceptors.request.use(config => {
  console.log(`[请求] ${config.method?.toUpperCase()} ${config.url}`, config.params || config.data || '')
  const token = getToken()
  if (token) {
    config.headers.Authorization = token
  }
  return config
})

request.interceptors.response.use(
  response => {
    const res = response.data
    console.log(`[响应] ${response.config.url}`, res)
    // 兼容两种响应格式：code=1 (旧接口) 和 code=200 (新接口)
    if (res.code !== undefined && res.code !== 1 && res.code !== 200) {
      console.error(`[响应错误] ${response.config.url}`, res)
      ElMessage.error(res.msg || res.message || '请求失败')
      return Promise.reject(new Error(res.msg || res.message || '请求失败'))
    }
    return res
  },
  error => {
    console.error(`[网络错误] ${error.config?.url}`, error.message)
    ElMessage.error(error.message || '网络异常')
    return Promise.reject(error)
  }
)

export default request
