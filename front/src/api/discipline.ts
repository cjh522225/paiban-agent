import request from '@/utils/request'

export function getDisciplineList(params?: any) {
  return request.get<any, { code: number; message: string; data: any[] }>('/discipline', { params })
}

export function getDisciplineSummary() {
  return request.get<any, { code: number; message: string; data: any[] }>('/discipline/summary')
}

export function addDiscipline(data: any) {
  return request.post<any, { code: number; message: string; data: any }>('/discipline', data)
}

export function deleteDiscipline(id: number) {
  return request.delete<any, { code: number; message: string; data: any }>(`/discipline/${id}`)
}

export function getDisciplineActions(handled?: string) {
  return request.get<any, { code: number; message: string; data: any[] }>('/discipline/actions', { params: { handled } })
}

export function handleDisciplineAction(id: number) {
  return request.post<any, { code: number; message: string; data: any }>(`/discipline/actions/${id}/handle`)
}
