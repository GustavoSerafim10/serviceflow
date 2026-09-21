// Tipos que espelham os DTOs da API Java (records em dto/). Mantidos à mão: são poucos e estáveis.

export type Role = 'ADMIN' | 'TECNICO' | 'SOLICITANTE'
export type Priority = 'P1' | 'P2' | 'P3' | 'P4'

export interface TokenResponse {
  accessToken: string
  refreshToken: string
  tokenType: string
  expiresInSeconds: number
}

export interface User {
  id: number
  name: string
  email: string
  role: Role
  active: boolean
}

/** Período analisado (datas AAAA-MM-DD, inclusivas), como a API o devolve. */
export interface Period {
  from: string
  to: string
}

export interface Report<T> {
  period: Period
  items: T[]
}

export interface Current {
  total: number
  breachedOpen: number
  byStatus: Record<string, number>
}

export interface Summary {
  period: Period
  opened: number
  resolved: number
  resolvedWithinSla: number
  slaComplianceRate: number | null
  avgResolutionMinutes: number | null
  medianResolutionMinutes: number | null
  current: Current
}

export interface CategoryStat {
  categoryId: number
  categoryName: string
  opened: number
  resolved: number
  resolvedWithinSla: number
  slaComplianceRate: number | null
  avgResolutionMinutes: number | null
}

export interface PriorityStat {
  priority: Priority
  opened: number
  resolved: number
  resolvedWithinSla: number
  slaComplianceRate: number | null
  avgResolutionMinutes: number | null
}

export interface TechnicianStat {
  technicianId: number
  technicianName: string
  resolved: number
  resolvedWithinSla: number
  slaComplianceRate: number | null
  avgResolutionMinutes: number | null
  inProgressNow: number
}

export interface TimelinePoint {
  day: string
  opened: number
  resolved: number
}

export interface ModelMetrics {
  modelVersion: string
  offered: number
  usedInTickets: number
  categoryEvaluated: number
  categoryAccepted: number
  categoryAcceptanceRate: number | null
  priorityEvaluated: number
  priorityAccepted: number
  priorityAcceptanceRate: number | null
}

export interface SuggestionMetrics {
  models: ModelMetrics[]
}
