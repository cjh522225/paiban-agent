import request from '@/utils/request'

export function submitSwapRequest(data: any) {
  return request.post<any, { code: number; message: string; data: any }>('/swap-requests', data)
}

export function getMySwapRequests() {
  return request.get<any, { code: number; message: string; data: any[] }>('/swap-requests/my')
}

export function getSwapRequests(params?: any) {
  return request.get<any, { code: number; message: string; data: any[] }>('/swap-requests', { params })
}

export function doneSwapRequest(id: number) {
  return request.post<any, { code: number; message: string; data: any }>(`/swap-requests/${id}/done`)
}

// 同意/拒绝换班申请（action: approved / rejected）
export function approveSwapRequest(id: number, action: string) {
  return request.post<any, { code: number; message: string; data: any }>(`/swap-requests/${id}/approve`, null, { params: { action } })
}

export function deleteSwapRequest(id: number) {
  return request.delete<any, { code: number; message: string; data: any }>(`/swap-requests/${id}`)
}
