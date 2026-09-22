import axios from 'axios'
import { ElMessage } from 'element-plus'
import type { AxiosInstance, AxiosRequestConfig } from 'axios'

/** 后端统一响应结构 */
export interface Result<T = any> {
  code: number
  message: string
  data: T
}

const service = axios.create({
  baseURL: '/api',
  timeout: 10000,
})

// 请求拦截器
service.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  },
)

// 响应拦截器
service.interceptors.response.use(
  (response) => {
    // 文件下载（blob）直接返回原始响应，不做 JSON 解析
    if (response.config.responseType === 'blob') {
      return response
    }
    const res = response.data
    if (res.code === 200) {
      return res
    } else {
      ElMessage.error(res.message || '请求失败')
      return Promise.reject(new Error(res.message || '请求失败'))
    }
  },
  (error) => {
    if (error.response) {
      if (error.response.status === 401) {
        ElMessage.error('登录已过期,请重新登录')
        localStorage.removeItem('token')
        localStorage.removeItem('userInfo')
        window.location.href = '/'
      } else {
        ElMessage.error(error.response.data?.message || '请求失败')
      }
    } else {
      ElMessage.error('网络错误,请检查网络连接')
    }
    return Promise.reject(error)
  },
)

/**
 * 类型化请求封装：拦截器已将响应解包为 Result<T>，
 * 故方法默认返回 Promise<Result<T>> 而非 AxiosResponse。
 * blob 下载场景请自行断言为 AxiosResponse。
 * 泛型顺序与 axios 保持一致：<T = 响应体, R = 返回值, D = 请求体>
 */
type Http = {
  get<T = any, R = Result<T>, D = any>(url: string, config?: AxiosRequestConfig<D>): Promise<R>
  post<T = any, R = Result<T>, D = any>(url: string, data?: D, config?: AxiosRequestConfig<D>): Promise<R>
  put<T = any, R = Result<T>, D = any>(url: string, data?: D, config?: AxiosRequestConfig<D>): Promise<R>
  delete<T = any, R = Result<T>, D = any>(url: string, config?: AxiosRequestConfig<D>): Promise<R>
  interceptors: AxiosInstance['interceptors']
  defaults: AxiosInstance['defaults']
}

export default service as unknown as Http
