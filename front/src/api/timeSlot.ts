import request from '@/utils/request'

// 获取时间段列表
export function getTimeSlotList() {
  return request.get<any, { code: number; message: string; data: any[] }>('/time-slots')
}

// 获取时间段详情
export function getTimeSlotById(id: number) {
  return request.get<any, { code: number; message: string; data: any }>(`/time-slots/${id}`)
}

// 新增时间段
export function addTimeSlot(data: any) {
  return request.post('/time-slots', data)
}

// 更新时间段
export function updateTimeSlot(data: any) {
  return request.put('/time-slots', data)
}

// 删除时间段
export function deleteTimeSlot(id: number) {
  return request.delete(`/time-slots/${id}`)
}
