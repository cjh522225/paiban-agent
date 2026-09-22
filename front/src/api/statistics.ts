import request from '@/utils/request'

export function getAdminStatistics() {
  return request.get<any, { code: number; message: string; data: Record<string, any> }>('/statistics/admin')
}

export function getPendingLeaves() {
  return request.get<any, { code: number; message: string; data: any[] }>('/statistics/pending-leaves')
}

export function getRecentMessages() {
  return request.get<any, { code: number; message: string; data: any[] }>('/statistics/recent-messages')
}

export function getDutyCounts(type?: string, startDate?: string, endDate?: string) {
  const params: Record<string, string> = {}
  if (type) params.type = type
  if (startDate) params.startDate = startDate
  if (endDate) params.endDate = endDate
  return request.get<any, { code: number; message: string; data: any[] }>('/statistics/duty-counts', { params })
}

export function exportDutyCountsExcel(params: { type: string; startDate?: string; endDate?: string }) {
  return request.get('/statistics/export/duty-counts', {
    params,
    responseType: 'blob',
  })
}
