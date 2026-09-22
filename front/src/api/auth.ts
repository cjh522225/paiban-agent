import request from '@/utils/request'

export function login(data: { username: string; password: string }) {
  return request.post<any, { code: number; message: string; data: any }>('/auth/login', data)
}
