import request from '@/utils/request'

// 获取指定用户的空闲时间
export function getUserAvailability(userId: number) {
  return request.get<any, { code: number; message: string; data: any[] }>(`/availabilities/user/${userId}`)
}

// 获取当前登录用户的空闲时间
export function getMyAvailabilities() {
  return request.get<any, { code: number; message: string; data: any[] }>('/availabilities/my')
}

// 批量保存空闲时间
export function batchSaveAvailabilities(data: any[]) {
  return request.post<any, { code: number; message: string; data: any }>('/availabilities/batch', data)
}

// 获取所有空闲时间（管理员视图）
export function getAllAvailabilities() {
  return request.get<any, { code: number; message: string; data: any[] }>('/availabilities/all')
}

// 删除单条空闲时间
export function deleteAvailability(id: number) {
  return request.delete<any, { code: number; message: string; data: any }>(`/availabilities/${id}`)
}
