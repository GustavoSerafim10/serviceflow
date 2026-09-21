import { apiFetch } from './client'
import { tokenStore } from '../auth/tokenStore'
import type {
  CategoryResponse, CategoryStat, CommentResponse, HistoryResponse, PageResponse, Period, Priority, PriorityStat,
  Report, SlaRuleResponse, SuggestionMetrics, SuggestionResponse, Summary, TechnicianStat, TicketFilters, TicketResponse, TicketStatus,
  TimelinePoint, TokenResponse, User,
} from './types'

// ------------------------------------------------------------------ autenticação

export async function login(email: string, password: string): Promise<void> {
  const tokens = await apiFetch<TokenResponse>('/api/auth/login', {
    method: 'POST',
    body: { email, password },
    auth: false,
  })
  tokenStore.set(tokens)
}

/** Encerra a sessão no servidor (revoga o refresh token) e limpa os tokens locais, aconteça o que acontecer. */
export async function logout(): Promise<void> {
  const refreshToken = tokenStore.getRefresh()
  tokenStore.clear()
  if (!refreshToken) return
  try {
    await apiFetch<void>('/api/auth/logout', { method: 'POST', body: { refreshToken }, auth: false })
  } catch {
    // melhor esforço: o token local já foi descartado
  }
}

export const getMe = () => apiFetch<User>('/api/users/me')

// ------------------------------------------------------------------ analytics

const query = (p: Period) => `?from=${p.from}&to=${p.to}`

export const getSummary = (p: Period) => apiFetch<Summary>(`/api/analytics/summary${query(p)}`)
export const getTimeline = (p: Period) => apiFetch<Report<TimelinePoint>>(`/api/analytics/timeline${query(p)}`)
export const getByPriority = (p: Period) => apiFetch<Report<PriorityStat>>(`/api/analytics/by-priority${query(p)}`)
export const getByCategory = (p: Period) => apiFetch<Report<CategoryStat>>(`/api/analytics/by-category${query(p)}`)
export const getByTechnician = (p: Period) => apiFetch<Report<TechnicianStat>>(`/api/analytics/by-technician${query(p)}`)

// ------------------------------------------------------------------ chamados

export function getTickets(filters: TicketFilters, page: number, size = 20) {
  const params = new URLSearchParams({ page: String(page), size: String(size) })
  if (filters.status) params.set('status', filters.status)
  if (filters.priority) params.set('priority', filters.priority)
  if (filters.categoryId != null) params.set('categoryId', String(filters.categoryId))
  if (filters.assigneeId != null) params.set('assigneeId', String(filters.assigneeId))
  if (filters.unassigned) params.set('unassigned', 'true')
  if (filters.slaStatus) params.set('slaStatus', filters.slaStatus)
  if (filters.q?.trim()) params.set('q', filters.q.trim())
  return apiFetch<PageResponse<TicketResponse>>(`/api/tickets?${params}`)
}

export const getTicket = (id: number) => apiFetch<TicketResponse>(`/api/tickets/${id}`)

export const createTicket = (body: { title: string; description: string; categoryId: number; priority: Priority; suggestionId?: number }) =>
  apiFetch<TicketResponse>('/api/tickets', { method: 'POST', body })

export const assignTicket = (id: number, technicianId: number) =>
  apiFetch<TicketResponse>(`/api/tickets/${id}/assignment`, { method: 'PUT', body: { technicianId } })

export const changeTicketStatus = (id: number, status: TicketStatus) =>
  apiFetch<TicketResponse>(`/api/tickets/${id}/status`, { method: 'PATCH', body: { status } })

/** Sugestão automática de categoria e prioridade (serviço Python). Responde `available: false` se indisponível. */
export const suggest = (title: string, description: string) =>
  apiFetch<SuggestionResponse>('/api/tickets/suggestions', { method: 'POST', body: { title, description } })

export const getComments = (id: number) => apiFetch<CommentResponse[]>(`/api/tickets/${id}/comments`)
export const addComment = (id: number, body: string) =>
  apiFetch<CommentResponse>(`/api/tickets/${id}/comments`, { method: 'POST', body: { body } })
export const getHistory = (id: number) => apiFetch<HistoryResponse[]>(`/api/tickets/${id}/history`)

// ------------------------------------------------------------------ categorias, equipe, SLA

export const getCategories = () => apiFetch<CategoryResponse[]>('/api/categories')
export const createCategory = (name: string, description: string) =>
  apiFetch<CategoryResponse>('/api/categories', { method: 'POST', body: { name, description: description || null } })
export const deactivateCategory = (id: number) => apiFetch<void>(`/api/categories/${id}`, { method: 'DELETE' })

export const getUsers = () => apiFetch<User[]>('/api/users')
/** Só existe no modo local (no servidor, criar usuário exige e-mail, senha e papel: fluxo do ADMIN). */
export const addTechnician = (name: string) => apiFetch<User>('/api/users', { method: 'POST', body: { name } })
export const deactivateUser = (id: number) => apiFetch<void>(`/api/users/${id}`, { method: 'DELETE' })

export const getSlaRules = () => apiFetch<SlaRuleResponse[]>('/api/sla-rules')
export const updateSlaRule = (priority: Priority, resolutionMinutes: number, businessHours: boolean) =>
  apiFetch<SlaRuleResponse>(`/api/sla-rules/${priority}`, { method: 'PUT', body: { resolutionMinutes, businessHours } })

/** Métricas das sugestões (ADMIN); cobrem todo o histórico, não o período do filtro. */
export const getSuggestionMetrics = () => apiFetch<SuggestionMetrics>('/api/tickets/suggestions/metrics')
