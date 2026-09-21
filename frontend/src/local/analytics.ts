import type {
  CategoryStat, Current, Period, PriorityStat, Report, Summary, TechnicianStat, TimelinePoint,
} from '../api/types'
import { toIsoDay } from '../lib/format'
import type { LTicket, LocalState } from './storage'

/**
 * As mesmas agregações do backend (AnalyticsRepository/AnalyticsService), em memória, sobre os chamados
 * do navegador. Regras idênticas:
 *  - cancelados nunca entram;
 *  - "aberto no período" = criado na janela; "resolvido no período" = resolvido na janela
 *    (base do MTTR e da conformidade de SLA); "dentro do SLA" = resolvido até o prazo;
 *  - janela = [meia-noite de from, meia-noite do dia seguinte a to), no fuso do navegador.
 */

interface Window { start: number; end: number }

function windowOf(period: Period): Window {
  const [fy, fm, fd] = period.from.split('-').map(Number)
  const [ty, tm, td] = period.to.split('-').map(Number)
  return { start: new Date(fy, fm - 1, fd).getTime(), end: new Date(ty, tm - 1, td + 1).getTime() }
}

const inWindow = (t: number | null, w: Window): t is number => t !== null && t >= w.start && t < w.end
const active = (tickets: LTicket[]) => tickets.filter((t) => t.status !== 'CANCELADO')

const round = (value: number, digits: number) => Math.round(value * 10 ** digits) / 10 ** digits
const rate = (part: number, total: number): number | null => (total === 0 ? null : round(part / total, 4))
const mean = (values: number[]): number | null => (values.length ? round(values.reduce((s, v) => s + v, 0) / values.length, 1) : null)
const median = (values: number[]): number | null => {
  if (!values.length) return null
  const sorted = [...values].sort((a, b) => a - b)
  const mid = Math.floor(sorted.length / 2)
  return round(sorted.length % 2 ? sorted[mid] : (sorted[mid - 1] + sorted[mid]) / 2, 1)
}

/** O trio de números usado por todos os relatórios, para um grupo de chamados dentro da janela. */
function statsOf(group: LTicket[], w: Window) {
  const opened = group.filter((t) => inWindow(t.createdAt, w))
  const resolved = group.filter((t) => inWindow(t.resolvedAt, w))
  const within = resolved.filter((t) => t.resolvedAt! <= t.slaDueAt)
  const durations = resolved.map((t) => (t.resolvedAt! - t.createdAt) / 60_000)
  return {
    opened: opened.length,
    resolved: resolved.length,
    resolvedWithinSla: within.length,
    slaComplianceRate: rate(within.length, resolved.length),
    avgResolutionMinutes: mean(durations),
    medianResolutionMinutes: median(durations),
  }
}

const relevant = (tickets: LTicket[], w: Window) =>
  active(tickets).filter((t) => inWindow(t.createdAt, w) || inWindow(t.resolvedAt, w))

export function currentOf(tickets: LTicket[], now: number): Current {
  const byStatus: Record<string, number> = { ABERTO: 0, EM_ATENDIMENTO: 0, RESOLVIDO: 0, FECHADO: 0 }
  let breachedOpen = 0
  for (const t of active(tickets)) {
    byStatus[t.status]++
    if (t.resolvedAt === null && t.slaDueAt < now) breachedOpen++
  }
  return { total: active(tickets).length, breachedOpen, byStatus }
}

export function summary(state: LocalState, period: Period, now: number): Summary {
  const s = statsOf(active(state.tickets), windowOf(period))
  return { period, ...s, current: currentOf(state.tickets, now) }
}

export function byCategory(state: LocalState, period: Period): Report<CategoryStat> {
  const w = windowOf(period)
  const items = state.categories.map((c) => {
    const { medianResolutionMinutes: _median, ...s } = statsOf(relevant(state.tickets, w).filter((t) => t.categoryId === c.id), w)
    return { categoryId: c.id, categoryName: c.name, ...s } satisfies CategoryStat
  })
    .filter((c) => c.opened > 0 || c.resolved > 0)
    .sort((a, b) => b.opened - a.opened || a.categoryName.localeCompare(b.categoryName, 'pt-BR'))
  return { period, items }
}

/** Sempre P1..P4, as sem movimento zeradas (o eixo do gráfico é estável). */
export function byPriority(state: LocalState, period: Period): Report<PriorityStat> {
  const w = windowOf(period)
  const items = (['P1', 'P2', 'P3', 'P4'] as const).map((priority): PriorityStat => {
    const s = statsOf(relevant(state.tickets, w).filter((t) => t.priority === priority), w)
    return {
      priority, opened: s.opened, resolved: s.resolved, resolvedWithinSla: s.resolvedWithinSla,
      slaComplianceRate: s.slaComplianceRate, avgResolutionMinutes: s.avgResolutionMinutes,
    }
  })
  return { period, items }
}

export function byTechnician(state: LocalState, period: Period): Report<TechnicianStat> {
  const w = windowOf(period)
  const items = state.users.filter((u) => u.role === 'TECNICO').map((tech): TechnicianStat => {
    const mine = active(state.tickets).filter((t) => t.assigneeId === tech.id)
    const s = statsOf(mine, w)
    return {
      technicianId: tech.id, technicianName: tech.name,
      resolved: s.resolved, resolvedWithinSla: s.resolvedWithinSla,
      slaComplianceRate: s.slaComplianceRate, avgResolutionMinutes: s.avgResolutionMinutes,
      inProgressNow: mine.filter((t) => t.status === 'EM_ATENDIMENTO').length,
    }
  })
    .filter((t) => t.resolved > 0 || t.inProgressNow > 0)
    .sort((a, b) => b.resolved - a.resolved || a.technicianName.localeCompare(b.technicianName, 'pt-BR'))
  return { period, items }
}

/** Todos os dias do período, inclusive os sem movimento (zeros), no fuso do navegador. */
export function timeline(state: LocalState, period: Period): Report<TimelinePoint> {
  const w = windowOf(period)
  const opened = new Map<string, number>()
  const resolved = new Map<string, number>()
  const bump = (map: Map<string, number>, key: string) => map.set(key, (map.get(key) ?? 0) + 1)
  for (const t of active(state.tickets)) {
    if (inWindow(t.createdAt, w)) bump(opened, toIsoDay(new Date(t.createdAt)))
    if (inWindow(t.resolvedAt, w)) bump(resolved, toIsoDay(new Date(t.resolvedAt)))
  }

  const items: TimelinePoint[] = []
  for (let d = new Date(w.start); d.getTime() < w.end; d = new Date(d.getFullYear(), d.getMonth(), d.getDate() + 1)) {
    const day = toIsoDay(d)
    items.push({ day, opened: opened.get(day) ?? 0, resolved: resolved.get(day) ?? 0 })
  }
  return { period, items }
}
