import { vi } from 'vitest'
import type {
  CategoryStat, PriorityStat, Report, SuggestionMetrics, Summary, TechnicianStat, TimelinePoint, User,
} from '../api/types'

export const period = { from: '2026-08-23', to: '2026-09-21' }

export const summary: Summary = {
  period,
  opened: 123,
  resolved: 117,
  resolvedWithinSla: 90,
  slaComplianceRate: 0.7692,
  avgResolutionMinutes: 1141.5,
  medianResolutionMinutes: 653,
  current: { total: 174, breachedOpen: 1, byStatus: { ABERTO: 5, EM_ATENDIMENTO: 2, RESOLVIDO: 39, FECHADO: 128 } },
}

export const timeline: Report<TimelinePoint> = {
  period,
  items: [
    { day: '2026-09-19', opened: 2, resolved: 1 },
    { day: '2026-09-20', opened: 0, resolved: 2 },
    { day: '2026-09-21', opened: 6, resolved: 1 },
  ],
}

export const byPriority: Report<PriorityStat> = {
  period,
  items: [
    { priority: 'P1', opened: 15, resolved: 15, resolvedWithinSla: 12, slaComplianceRate: 0.8, avgResolutionMinutes: 172.5 },
    { priority: 'P2', opened: 26, resolved: 26, resolvedWithinSla: 16, slaComplianceRate: 0.6154, avgResolutionMinutes: 467.3 },
    { priority: 'P3', opened: 53, resolved: 49, resolvedWithinSla: 37, slaComplianceRate: 0.7551, avgResolutionMinutes: 1119.8 },
    { priority: 'P4', opened: 0, resolved: 0, resolvedWithinSla: 0, slaComplianceRate: null, avgResolutionMinutes: null },
  ],
}

export const byCategory: Report<CategoryStat> = {
  period,
  items: [
    { categoryId: 1, categoryName: 'Software', opened: 35, resolved: 30, resolvedWithinSla: 24, slaComplianceRate: 0.8, avgResolutionMinutes: 900 },
    { categoryId: 2, categoryName: 'Rede', opened: 26, resolved: 25, resolvedWithinSla: 20, slaComplianceRate: 0.8, avgResolutionMinutes: 700 },
  ],
}

export const byTechnician: Report<TechnicianStat> = {
  period,
  items: [
    { technicianId: 1, technicianName: 'Ana Souza', resolved: 43, resolvedWithinSla: 37, slaComplianceRate: 0.8605, avgResolutionMinutes: 1118, inProgressNow: 0 },
    { technicianId: 3, technicianName: 'Carla Dias', resolved: 39, resolvedWithinSla: 23, slaComplianceRate: 0.5897, avgResolutionMinutes: 1482, inProgressNow: 2 },
  ],
}

export const suggestionMetrics: SuggestionMetrics = {
  models: [{
    modelVersion: 'v1-demo', offered: 115, usedInTickets: 115,
    categoryEvaluated: 115, categoryAccepted: 84, categoryAcceptanceRate: 0.7304,
    priorityEvaluated: 115, priorityAccepted: 68, priorityAcceptanceRate: 0.5913,
  }],
}

export const admin: User = { id: 1, name: 'Ana Admin', email: 'admin@x.com', role: 'ADMIN', active: true }
export const technician: User = { ...admin, id: 2, name: 'Tom Técnico', role: 'TECNICO' }
export const requester: User = { ...admin, id: 3, name: 'Bia Solicitante', role: 'SOLICITANTE' }

export const tokens = { accessToken: 'access-1', refreshToken: 'refresh-1', tokenType: 'Bearer', expiresInSeconds: 900 }

export function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

/**
 * Substitui o fetch global por um roteador simples: cada rota (prefixo do caminho) devolve um Response ou um
 * objeto (vira 200 JSON). Rota desconhecida = 404, para o teste falhar alto se o código chamar algo inesperado.
 */
export function mockFetch(routes: Record<string, (url: URL, init?: RequestInit) => Response | unknown | Promise<Response | unknown>>) {
  const fn = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = new URL(String(input), 'http://localhost')
    const match = Object.keys(routes).find((prefix) => url.pathname.startsWith(prefix))
    if (!match) return json({ title: 'Not Found', detail: `sem rota simulada: ${url.pathname}` }, 404)
    const result = await routes[match](url, init)
    return result instanceof Response ? result : json(result)
  })
  vi.stubGlobal('fetch', fn)
  return fn
}
