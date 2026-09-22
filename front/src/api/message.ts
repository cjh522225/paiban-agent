import request from '@/utils/request'

// 获取消息列表
export function getMessageList() {
  return request.get<any, { code: number; message: string; data: any[] }>('/messages')
}

// 获取消息详情
export function getMessageById(id: number) {
  return request.get<any, { code: number; message: string; data: any }>(`/messages/${id}`)
}

// 发送消息
export function addMessage(data: any) {
  return request.post('/messages', data)
}

// 更新消息
export function updateMessage(id: number, data: any) {
  return request.put(`/messages/${id}`, data)
}

// 删除消息（撤销发送）
export function deleteMessage(id: number) {
  return request.delete(`/messages/${id}`)
}

// 上传附件
export function uploadAttachment(file: File) {
  const fd = new FormData()
  fd.append('file', file)
  return request.post<any, { code: number; message: string; data: string }>('/messages/upload', fd, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

// 登录后标记所有未查看消息为已查看
export function markViewed() {
  return request.post<any, { code: number; message: string; data: any }>('/messages/mark-viewed')
}

// 单条标记已读
export function markMessageRead(id: number) {
  return request.post<any, { code: number; message: string; data: any }>(`/messages/${id}/read`)
}

// 全部已读
export function markAllRead() {
  return request.post<any, { code: number; message: string; data: any }>('/messages/read-all')
}

// 获取当前管理员的草稿
export function getDraft() {
  return request.get<any, { code: number; message: string; data: any }>('/messages/draft')
}

// 保存草稿（有则更新，无则插入）
export function saveDraft(data: any) {
  return request.post('/messages/draft', data)
}

// 删除当前管理员的草稿
export function deleteDraft() {
  return request.delete('/messages/draft')
}
