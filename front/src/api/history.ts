import request from '@/utils/request'

export function exportHistoryExcel(params: { type: string; startDate: string; endDate: string; locationId?: string; week?: number }) {
  return request.get('/history/export', {
    params,
    responseType: 'blob',
  })
}
