import axios from 'axios'
import { ElMessage } from 'element-plus'

const http = axios.create({ baseURL: '/api', timeout: 15000 })

// 请求携带登录态
http.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

http.interceptors.response.use(
  (res) => {
    const body = res.data
    // 文件流(导出下载)直接返回 Blob,不走 ApiResponse 解包
    if (body instanceof Blob) return body
    if (body && body.code !== undefined && body.code !== 200) {
      ElMessage.error(body.msg || '请求失败')
      return Promise.reject(new Error(body.msg))
    }
    return body ? body.data : null
  },
  (err) => {
    const msg = err.response?.data?.msg || err.message || '网络异常'
    ElMessage.error(msg)
    if (err.response?.status === 401) {
      localStorage.removeItem('token')
      location.hash = '#/login'
    }
    return Promise.reject(err)
  }
)

export default http
