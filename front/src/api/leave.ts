import request from '@/utils/request'

// 获取请假列表(管理员看全部,用户看自己)
export function getLeaveList() {
  return request.get<any, { code: number; message: string; data: any[] }>('/leaves')
}

// 获取待审批请假列表
export function getPendingLeaveList() {
  return request.get<any, { code: number; message: string; data: any[] }>('/leaves/pending')
}

// 获取请假详情
export function getLeaveById(id: number) {
  return request.get<any, { code: number; message: string; data: any }>(`/leaves/${id}`)
}

// 提交请假申请
export function addLeave(data: any) {
  return request.post('/leaves', data)
}

// 更新请假
export function updateLeave(data: any) {
  return request.put('/leaves', data)
}

// 审批请假
export function approveLeave(data: any) {
  return request.put('/leaves/approve', data)
}

// 删除请假
export function deleteLeave(id: number) {
  return request.delete(`/leaves/${id}`)
}
