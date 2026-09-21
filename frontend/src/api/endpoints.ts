import { apiFetch } from './client'
import { tokenStore } from '../auth/tokenStore'
import type {
  CategoryStat, Period, PriorityStat, Report, SuggestionMetrics, Summary, TechnicianStat,
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

/** Métricas das sugestões (ADMIN); cobrem todo o histórico, não o período do filtro. */
export const getSuggestionMetrics = () => apiFetch<SuggestionMetrics>('/api/tickets/suggestions/metrics')
