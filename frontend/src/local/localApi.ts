import { ApiError } from '../api/errors'
import type { Priority, TicketFilters, TicketStatus } from '../api/types'
import * as analytics from './analytics'
import { LocalEngine } from './engine'
import { createBrowserStorage } from './storage'

let engine: LocalEngine | null = null

/** O motor é único por página (criado sob demanda, com o armazenamento do navegador). */
export function getEngine(): LocalEngine {
  engine ??= new LocalEngine(createBrowserStorage())
  return engine
}

/** Só para testes: troca o motor (com armazenamento e relógio próprios). */
export function setEngine(next: LocalEngine | null) {
  engine = next
}

interface Options {
  method?: string
  body?: unknown
}

type Body = Record<string, unknown>
const asBody = (body: unknown): Body => (body && typeof body === 'object' ? (body as Body) : {})

/**
 * Faz o papel do servidor: recebe o MESMO método e caminho que o cliente HTTP enviaria e devolve o mesmo
 * formato de resposta (ou lança o mesmo ApiError). Assim as telas não sabem se falam com a API Java ou com o navegador.
 * Cobre o subconjunto da API que as telas usam.
 */
export function localRequest<T>(path: string, options: Options = {}): Promise<T> {
  try {
    return Promise.resolve(route(path, options) as T)
  } catch (error) {
    return Promise.reject(error)
  }
}

function route(path: string, { method = 'GET', body }: Options): unknown {
  const url = new URL(path, 'http://local')
  const e = getEngine()
  const params = url.searchParams
  const period = { from: params.get('from') ?? '', to: params.get('to') ?? '' }
  const num = (v: string | null) => (v === null || v === '' ? undefined : Number(v))
  const parts = url.pathname.split('/').filter(Boolean) // ["api", "tickets", "5", "status"]
  const [, resource, a, b] = parts
  const id = a !== undefined && /^\d+$/.test(a) ? Number(a) : undefined

  // ---- indicadores
  if (resource === 'analytics') {
    const state = e.data
    switch (a) {
      case 'summary': return analytics.summary(state, period, Date.now())
      case 'timeline': return analytics.timeline(state, period)
      case 'by-priority': return analytics.byPriority(state, period)
      case 'by-category': return analytics.byCategory(state, period)
      case 'by-technician': return analytics.byTechnician(state, period)
    }
  }

  // ---- usuário e equipe
  if (resource === 'users') {
    if (a === 'me') return e.me()
    if (method === 'GET' && a === undefined) return e.listUsers()
    if (method === 'POST' && a === undefined) return e.addTechnician(String(asBody(body).name ?? ''))
    if (method === 'DELETE' && id !== undefined) { e.deactivateUser(id); return undefined }
  }

  // ---- categorias
  if (resource === 'categories') {
    if (method === 'GET' && a === undefined) return e.listCategories()
    if (method === 'POST' && a === undefined) return e.createCategory(asBody(body) as { name?: string; description?: string | null })
    if (method === 'DELETE' && id !== undefined) { e.deactivateCategory(id); return undefined }
  }

  // ---- regras de SLA
  if (resource === 'sla-rules') {
    if (method === 'GET' && a === undefined) return e.listSlaRules()
    if (method === 'PUT' && a) return e.updateSlaRule(a as Priority, asBody(body) as { resolutionMinutes?: number; businessHours?: boolean })
  }

  // ---- sugestão automática: exige o serviço Python, que não roda no navegador
  if (resource === 'tickets' && a === 'suggestions') {
    if (method === 'POST') return { available: false, suggestionId: null, modelVersion: null, category: null, priority: null }
    throw new ApiError(404, 'Recurso não encontrado', 'Disponível apenas com o servidor')
  }

  // ---- chamados
  if (resource === 'tickets') {
    if (method === 'GET' && a === undefined) {
      const filters: TicketFilters = {
        status: (params.get('status') || undefined) as TicketStatus | undefined,
        priority: (params.get('priority') || undefined) as Priority | undefined,
        categoryId: num(params.get('categoryId')),
        assigneeId: num(params.get('assigneeId')),
        unassigned: params.get('unassigned') === 'true' ? true : undefined,
        slaStatus: (params.get('slaStatus') || undefined) as TicketFilters['slaStatus'],
        q: params.get('q') ?? undefined,
      }
      return e.listTickets(filters, num(params.get('page')) ?? 0, num(params.get('size')) ?? 20)
    }
    if (method === 'POST' && a === undefined) return e.createTicket(asBody(body))
    if (id !== undefined) {
      if (method === 'GET' && b === undefined) return e.getTicket(id)
      if (method === 'PUT' && b === 'assignment') return e.assign(id, asBody(body).technicianId as number | undefined)
      if (method === 'PATCH' && b === 'status') return e.changeStatus(id, asBody(body).status as TicketStatus | undefined)
      if (method === 'GET' && b === 'comments') return e.listComments(id)
      if (method === 'POST' && b === 'comments') return e.addComment(id, asBody(body))
      if (method === 'GET' && b === 'history') return e.listHistory(id)
    }
  }

  throw new ApiError(404, 'Recurso não encontrado', `Rota indisponível no modo local: ${method} ${url.pathname}`)
}
