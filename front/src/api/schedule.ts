import request from '@/utils/request'

export function getScheduleList(params: {
  type: string
  locationId: string
  startDate?: string
  endDate?: string
}) {
  return request.get<any, { code: number; message: string; data: any[] }>('/schedules', { params })
}

export function addSchedule(data: any) {
  return request.post<any, { code: number; message: string; data: any }>('/schedules', data)
}

export function autoSchedule(data: any) {
  return request.post<any, { code: number; message: string; data: any[] }>('/schedules/auto', data)
}

export function autoScheduleDormitory(data: {
  dormitoryIds?: number[]
  startDate: string
  endDate: string
  weekParity?: string
  semesterStart?: string
}) {
  return request.post<any, { code: number; message: string; data: any }>('/schedules/auto/dormitory', data, { timeout: 60000 })
}

export function autoScheduleOffice(data: {
  officeIds?: number[]
  startDate: string
  endDate: string
  weekParity?: string
}) {
  return request.post<any, { code: number; message: string; data: any }>('/schedules/auto/office', data, { timeout: 60000 })
}

export function exportDormitoryExcel(params: { startDate: string; endDate: string; week?: number }) {
  return request.get('/schedules/export/dormitory', {
    params,
    responseType: 'blob',
  })
}

export function exportOfficeExcel(params: { startDate: string; endDate: string; week?: number }) {
  return request.get('/schedules/export/office', {
    params,
    responseType: 'blob',
  })
}

export function deleteSchedule(id: number) {
  return request.delete<any, { code: number; message: string; data: any }>(`/schedules/${id}`)
}

export function batchDeleteSchedule(data: { type: string; locationId: string; startDate?: string; endDate?: string }) {
  return request.delete<any, { code: number; message: string; data: any }>('/schedules/batch', { data })
}

export function getMyScheduleList(params: { startDate?: string; endDate?: string }) {
  return request.get<any, { code: number; message: string; data: any[] }>("/schedules/my", { params })
}

// 管理员手动互换两条排班记录的人员
export function swapSchedules(data: { scheduleIdA: number; scheduleIdB: number; weekNumber?: number }) {
  return request.post<any, { code: number; message: string; data: any }>('/schedules/swap', data)
}
