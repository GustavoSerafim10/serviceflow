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

// ------------------------------------------------------------------ chamados, categorias, SLA

export type TicketStatus = 'ABERTO' | 'EM_ATENDIMENTO' | 'RESOLVIDO' | 'FECHADO' | 'CANCELADO'
export type SlaStatus = 'DENTRO_DO_PRAZO' | 'ESTOURADO'
export type HistoryAction = 'CREATED' | 'ASSIGNED' | 'STATUS_CHANGED' | 'COMMENT_ADDED'

export interface TicketResponse {
  id: number
  title: string
  description: string
  categoryId: number
  categoryName: string
  priority: Priority
  status: TicketStatus
  requesterId: number
  requesterName: string
  assigneeId: number | null
  assigneeName: string | null
  createdAt: string
  updatedAt: string
  slaDueAt: string
  resolvedAt: string | null
  slaStatus: SlaStatus | null
}

export interface TicketFilters {
  status?: TicketStatus
  priority?: Priority
  categoryId?: number
  assigneeId?: number
  unassigned?: boolean
  slaStatus?: SlaStatus
  q?: string
}

export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface CommentResponse {
  id: number
  ticketId: number
  authorId: number
  authorName: string
  body: string
  createdAt: string
}

export interface HistoryResponse {
  id: number
  action: HistoryAction
  details: string
  actorId: number
  actorName: string
  createdAt: string
}

export interface CategoryResponse {
  id: number
  name: string
  description: string | null
  active: boolean
  createdAt: string
}

export interface SlaRuleResponse {
  priority: Priority
  resolutionMinutes: number
  businessHours: boolean
  updatedAt: string
}

export interface SuggestionResponse {
  available: boolean
  suggestionId: number | null
  modelVersion: string | null
  category: { id: number; name: string; confidence: number } | null
  priority: { priority: Priority; confidence: number } | null
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
