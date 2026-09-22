import request from '@/utils/request'

// 获取宿舍楼列表(status可选, 1=仅启用)
export function getDormitoryList(params?: { status?: number }) {
  return request.get<any, { code: number; message: string; data: any[] }>('/dormitories', { params })
}

// 获取宿舍楼详情
export function getDormitoryById(id: number) {
  return request.get<any, { code: number; message: string; data: any }>(`/dormitories/${id}`)
}

// 新增宿舍楼
export function addDormitory(data: any) {
  return request.post('/dormitories', data)
}

// 更新宿舍楼
export function updateDormitory(data: any) {
  return request.put('/dormitories', data)
}

// 删除宿舍楼
export function deleteDormitory(id: number) {
  return request.delete(`/dormitories/${id}`)
}
