import request from '@/utils/request'

// 获取用户列表
export function getUserList() {
  return request.get<any, { code: number; message: string; data: any[] }>('/users')
}

// 获取员工列表
export function getStaffList() {
  return request.get<any, { code: number; message: string; data: any[] }>('/users/staff')
}

// 获取用户详情
export function getUserById(id: number) {
  return request.get<any, { code: number; message: string; data: any }>(`/users/${id}`)
}

// 新增用户
export function addUser(data: any) {
  return request.post('/users', data)
}

// 更新用户
export function updateUser(data: any) {
  return request.put('/users', data)
}

// 删除用户
export function deleteUser(id: number) {
  return request.delete(`/users/${id}`)
}
