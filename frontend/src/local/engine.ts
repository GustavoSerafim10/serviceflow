import { ApiError } from '../api/errors'
import type {
  CategoryResponse, CommentResponse, HistoryResponse, PageResponse, Priority, SlaRuleResponse, SlaStatus,
  TicketFilters, TicketResponse, TicketStatus, User,
} from '../api/types'
import { dueAt } from './calendar'
import {
  ME_ID, emptyState, isValidState,
  type LComment, type LHistory, type LTicket, type LocalState, type StateStorage,
} from './storage'

/** Transições válidas (mesma máquina de estados do backend). ABERTO → EM_ATENDIMENTO só acontece ao atribuir. */
const TRANSITIONS: Record<TicketStatus, TicketStatus[]> = {
  ABERTO: ['CANCELADO'],
  EM_ATENDIMENTO: ['RESOLVIDO', 'CANCELADO'],
  RESOLVIDO: ['FECHADO', 'EM_ATENDIMENTO'],
  FECHADO: [],
  CANCELADO: [],
}
const isTerminal = (s: TicketStatus) => s === 'FECHADO' || s === 'CANCELADO'

const iso = (ms: number) => new Date(ms).toISOString()
const notFound = (what: string, id: number) => new ApiError(404, 'Recurso não encontrado', `${what} com id ${id} não foi encontrado(a)`)
const business = (detail: string) => new ApiError(422, 'Regra de negócio violada', detail)
const conflict = (detail: string) => new ApiError(409, 'Conflito', detail)
const invalid = (detail: string) => new ApiError(400, 'Erro de validação', detail)

/**
 * A "API" no navegador: as mesmas regras do backend Java (SLA em horário comercial, fluxo de status,
 * filtros, histórico), operando sobre um estado guardado em localStorage. Como só há UMA pessoa (você),
 * não há regras de permissão por papel: você pode abrir, atribuir, resolver e fechar.
 */
export class LocalEngine {
  private state: LocalState
  private readonly storage: StateStorage
  private readonly now: () => number

  constructor(storage: StateStorage, now: () => number = Date.now) {
    this.storage = storage
    this.now = now
    this.state = storage.load() ?? emptyState(now())
  }

  get persistent(): boolean {
    return this.storage.persistent
  }

  /** Estado atual (somente leitura), usado pelos indicadores. */
  get data(): Readonly<LocalState> {
    return this.state
  }

  private commit() {
    this.storage.save(this.state)
  }

  // ------------------------------------------------------------------ usuário e equipe

  me(): User {
    const me = this.state.users.find((u) => u.id === ME_ID)!
    return { id: me.id, name: me.name, email: 'local@serviceflow', role: 'ADMIN', active: true }
  }

  listUsers(): User[] {
    return this.state.users.map((u) => ({ id: u.id, name: u.name, email: `${u.id}@local`, role: u.role, active: u.active }))
  }

  addTechnician(name: string): User {
    const trimmed = name.trim()
    if (!trimmed) throw invalid('O nome é obrigatório')
    if (this.state.users.some((u) => u.active && u.name.toLowerCase() === trimmed.toLowerCase())) {
      throw conflict(`Já existe uma pessoa da equipe com o nome '${trimmed}'`)
    }
    const user = { id: ++this.state.seq.user, name: trimmed, role: 'TECNICO' as const, active: true }
    this.state.users.push(user)
    this.commit()
    return { id: user.id, name: user.name, email: `${user.id}@local`, role: user.role, active: true }
  }

  deactivateUser(id: number) {
    const user = this.state.users.find((u) => u.id === id)
    if (!user) throw notFound('Usuário', id)
    if (id === ME_ID) throw business('Você não pode se desativar')
    user.active = false
    this.commit()
  }

  // ------------------------------------------------------------------ categorias

  private categoryDto(c: LocalState['categories'][number]): CategoryResponse {
    return { id: c.id, name: c.name, description: c.description, active: c.active, createdAt: iso(c.createdAt) }
  }

  listCategories(): CategoryResponse[] {
    return this.state.categories.map((c) => this.categoryDto(c))
  }

  createCategory(body: { name?: string; description?: string | null }): CategoryResponse {
    const name = (body.name ?? '').trim()
    if (!name) throw invalid('O nome é obrigatório')
    if (name.length > 100) throw invalid('O nome deve ter no máximo 100 caracteres')
    if (this.state.categories.some((c) => c.name.toLowerCase() === name.toLowerCase())) {
      throw conflict(`Já existe uma categoria com o nome '${name}'`)
    }
    const category = { id: ++this.state.seq.category, name, description: body.description?.trim() || null, active: true, createdAt: this.now() }
    this.state.categories.push(category)
    this.commit()
    return this.categoryDto(category)
  }

  deactivateCategory(id: number) {
    const category = this.state.categories.find((c) => c.id === id)
    if (!category) throw notFound('Categoria', id)
    category.active = false
    this.commit()
  }

  // ------------------------------------------------------------------ regras de SLA

  private slaDto(r: LocalState['slaRules'][number]): SlaRuleResponse {
    return { priority: r.priority, resolutionMinutes: r.resolutionMinutes, businessHours: r.businessHours, updatedAt: iso(r.updatedAt) }
  }

  listSlaRules(): SlaRuleResponse[] {
    return this.state.slaRules.map((r) => this.slaDto(r))
  }

  updateSlaRule(priority: Priority, body: { resolutionMinutes?: number; businessHours?: boolean }): SlaRuleResponse {
    const rule = this.state.slaRules.find((r) => r.priority === priority)
    if (!rule) throw notFound('Regra de SLA', 0)
    const minutes = body.resolutionMinutes
    if (minutes == null || !Number.isInteger(minutes) || minutes < 1 || minutes > 43200) {
      throw invalid('O prazo deve ser um número inteiro de minutos entre 1 e 43200')
    }
    if (typeof body.businessHours !== 'boolean') throw invalid('Informe se o prazo conta apenas em horário comercial')
    rule.resolutionMinutes = minutes
    rule.businessHours = body.businessHours
    rule.updatedAt = this.now()
    this.commit()
    return this.slaDto(rule)
  }

  // ------------------------------------------------------------------ chamados

  private userName(id: number | null): string | null {
    return id == null ? null : (this.state.users.find((u) => u.id === id)?.name ?? '—')
  }

  /** Situação do SLA, calculada na consulta: resolvido compara a resolução ao prazo; aberto compara "agora". */
  slaStatusOf(t: LTicket): SlaStatus | null {
    if (t.status === 'CANCELADO') return null
    return (t.resolvedAt ?? this.now()) > t.slaDueAt ? 'ESTOURADO' : 'DENTRO_DO_PRAZO'
  }

  private ticketDto(t: LTicket): TicketResponse {
    return {
      id: t.id, title: t.title, description: t.description,
      categoryId: t.categoryId, categoryName: this.state.categories.find((c) => c.id === t.categoryId)?.name ?? '—',
      priority: t.priority, status: t.status,
      requesterId: t.requesterId, requesterName: this.userName(t.requesterId) ?? '—',
      assigneeId: t.assigneeId, assigneeName: this.userName(t.assigneeId),
      createdAt: iso(t.createdAt), updatedAt: iso(t.updatedAt), slaDueAt: iso(t.slaDueAt),
      resolvedAt: t.resolvedAt == null ? null : iso(t.resolvedAt), slaStatus: this.slaStatusOf(t),
    }
  }

  private getRaw(id: number): LTicket {
    const ticket = this.state.tickets.find((t) => t.id === id)
    if (!ticket) throw notFound('Chamado', id)
    return ticket
  }

  private record(ticket: LTicket, action: LHistory['action'], details: string) {
    this.state.history.push({ id: ++this.state.seq.history, ticketId: ticket.id, actorId: ME_ID, action, details, createdAt: this.now() })
  }

  createTicket(body: { title?: string; description?: string; categoryId?: number; priority?: Priority }): TicketResponse {
    const title = (body.title ?? '').trim()
    const description = (body.description ?? '').trim()
    if (!title) throw invalid('O título é obrigatório')
    if (title.length > 150) throw invalid('O título deve ter no máximo 150 caracteres')
    if (!description) throw invalid('A descrição é obrigatória')
    if (description.length > 4000) throw invalid('A descrição deve ter no máximo 4000 caracteres')
    if (!body.priority) throw invalid('A prioridade é obrigatória')

    const category = this.state.categories.find((c) => c.id === body.categoryId)
    if (!category) throw business(`Categoria não encontrada com id ${body.categoryId}`)
    if (!category.active) throw business(`A categoria '${category.name}' está inativa`)

    const rule = this.state.slaRules.find((r) => r.priority === body.priority)!
    const now = this.now()
    const ticket: LTicket = {
      id: ++this.state.seq.ticket, title, description, categoryId: category.id, priority: body.priority, status: 'ABERTO',
      requesterId: ME_ID, assigneeId: null, createdAt: now, updatedAt: now, resolvedAt: null,
      // Snapshot: o prazo é calculado UMA vez, na abertura. Mudar a regra depois não altera chamados existentes.
      slaDueAt: dueAt(now, rule.resolutionMinutes, rule.businessHours),
    }
    this.state.tickets.push(ticket)
    this.record(ticket, 'CREATED', `Chamado aberto com prioridade ${ticket.priority} (prazo de SLA: ${iso(ticket.slaDueAt)})`)
    this.commit()
    return this.ticketDto(ticket)
  }

  getTicket(id: number): TicketResponse {
    return this.ticketDto(this.getRaw(id))
  }

  listTickets(filters: TicketFilters, page = 0, size = 20): PageResponse<TicketResponse> {
    const q = filters.q?.trim().toLowerCase()
    const matches = this.state.tickets.filter((t) => {
      if (filters.status && t.status !== filters.status) return false
      if (filters.priority && t.priority !== filters.priority) return false
      if (filters.categoryId != null && t.categoryId !== filters.categoryId) return false
      if (filters.assigneeId != null && t.assigneeId !== filters.assigneeId) return false
      if (filters.unassigned && t.assigneeId !== null) return false
      if (filters.slaStatus && this.slaStatusOf(t) !== filters.slaStatus) return false
      if (q && !t.title.toLowerCase().includes(q) && !t.description.toLowerCase().includes(q)) return false
      return true
    })
    // Ordem fixa: mais recentes primeiro (id desempata).
    matches.sort((a, b) => b.createdAt - a.createdAt || b.id - a.id)

    const pageSize = Math.min(Math.max(1, size), 100)
    const start = Math.max(0, page) * pageSize
    return {
      content: matches.slice(start, start + pageSize).map((t) => this.ticketDto(t)),
      page: Math.max(0, page), size: pageSize, totalElements: matches.length,
      totalPages: Math.ceil(matches.length / pageSize),
    }
  }

  assign(id: number, technicianId: number | undefined): TicketResponse {
    const ticket = this.getRaw(id)
    if (ticket.status !== 'ABERTO' && ticket.status !== 'EM_ATENDIMENTO') {
      throw business('Só é possível atribuir chamados abertos ou em atendimento')
    }
    const tech = this.state.users.find((u) => u.id === technicianId)
    if (!tech) throw business(`Usuário não encontrado com id ${technicianId}`)
    if (tech.role !== 'TECNICO' || !tech.active) throw business('O usuário informado não é um técnico ativo')
    if (ticket.assigneeId === tech.id) throw business('O chamado já está atribuído a este técnico')

    const previous = ticket.assigneeId
    ticket.assigneeId = tech.id
    this.record(ticket, 'ASSIGNED', previous == null
      ? `Atribuído a ${tech.name}`
      : `Reatribuído de ${this.userName(previous)} para ${tech.name}`)
    if (ticket.status === 'ABERTO') {
      ticket.status = 'EM_ATENDIMENTO'
      this.record(ticket, 'STATUS_CHANGED', 'Status: ABERTO → EM_ATENDIMENTO')
    }
    ticket.updatedAt = this.now()
    this.commit()
    return this.ticketDto(ticket)
  }

  changeStatus(id: number, to: TicketStatus | undefined): TicketResponse {
    const ticket = this.getRaw(id)
    if (!to || !TRANSITIONS[ticket.status].includes(to)) {
      throw business(`Transição de status inválida: ${ticket.status} → ${to}`)
    }
    const from = ticket.status
    ticket.status = to
    if (to === 'RESOLVIDO') ticket.resolvedAt = this.now()
    else if (from === 'RESOLVIDO' && to === 'EM_ATENDIMENTO') ticket.resolvedAt = null // reaberto: o relógio volta a contar
    ticket.updatedAt = this.now()
    this.record(ticket, 'STATUS_CHANGED', `Status: ${from} → ${to}`)
    this.commit()
    return this.ticketDto(ticket)
  }

  allowedTransitions(status: TicketStatus): TicketStatus[] {
    return TRANSITIONS[status]
  }

  addComment(id: number, body: { body?: string }): CommentResponse {
    const ticket = this.getRaw(id)
    const text = (body.body ?? '').trim()
    if (!text) throw invalid('O comentário é obrigatório')
    if (text.length > 2000) throw invalid('O comentário deve ter no máximo 2000 caracteres')
    if (isTerminal(ticket.status)) throw business(`Não é possível comentar em um chamado ${ticket.status}`)

    const comment: LComment = { id: ++this.state.seq.comment, ticketId: id, authorId: ME_ID, body: text, createdAt: this.now() }
    this.state.comments.push(comment)
    this.record(ticket, 'COMMENT_ADDED', 'Comentário adicionado')
    this.commit()
    return this.commentDto(comment)
  }

  private commentDto(c: LComment): CommentResponse {
    return { id: c.id, ticketId: c.ticketId, authorId: c.authorId, authorName: this.userName(c.authorId) ?? '—', body: c.body, createdAt: iso(c.createdAt) }
  }

  listComments(id: number): CommentResponse[] {
    this.getRaw(id)
    return this.state.comments.filter((c) => c.ticketId === id).map((c) => this.commentDto(c))
  }

  listHistory(id: number): HistoryResponse[] {
    this.getRaw(id)
    return this.state.history.filter((h) => h.ticketId === id).map((h) => ({
      id: h.id, action: h.action, details: h.details, actorId: h.actorId, actorName: this.userName(h.actorId) ?? '—', createdAt: iso(h.createdAt),
    }))
  }

  // ------------------------------------------------------------------ backup

  exportJson(): string {
    return JSON.stringify(this.state, null, 2)
  }

  /** Substitui TODOS os dados pelo backup colado. Devolve false se o texto não for um backup válido. */
  importJson(text: string): boolean {
    try {
      const parsed: unknown = JSON.parse(text)
      if (!isValidState(parsed)) return false
      this.state = parsed
      this.commit()
      return true
    } catch {
      return false
    }
  }

  reset() {
    this.state = emptyState(this.now())
    this.commit()
  }
}
