import request from '@/utils/request'

export function getMyMultiDuty() {
  return request.get<any, { code: number; message: string; data: any[] }>('/multi-duty/my')
}

export function getMultiDutyList(status?: string) {
  return request.get<any, { code: number; message: string; data: any[] }>('/multi-duty', { params: { status } })
}

export function submitMultiDuty(data: any) {
  return request.post<any, { code: number; message: string; data: any }>('/multi-duty', data)
}

export function approveMultiDuty(id: number, action: string) {
  return request.post<any, { code: number; message: string; data: any }>(`/multi-duty/${id}/approve?action=${action}`)
}

export function deleteMultiDuty(id: number) {
  return request.delete<any, { code: number; message: string; data: any }>(`/multi-duty/${id}`)
}
