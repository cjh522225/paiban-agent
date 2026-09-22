import request from '@/utils/request'

// 获取办公室列表(status可选, 1=仅启用)
export function getOfficeList(params?: { status?: number }) {
  return request.get<any, { code: number; message: string; data: any[] }>('/offices', { params })
}

// 获取办公室详情
export function getOfficeById(id: number) {
  return request.get<any, { code: number; message: string; data: any }>(`/offices/${id}`)
}

// 新增办公室
export function addOffice(data: any) {
  return request.post('/offices', data)
}

// 更新办公室
export function updateOffice(data: any) {
  return request.put('/offices', data)
}

// 删除办公室
export function deleteOffice(id: number) {
  return request.delete(`/offices/${id}`)
}
