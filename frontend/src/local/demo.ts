import type { Priority, TicketStatus } from '../api/types'
import { dueAt } from './calendar'
import { emptyState, ME_ID, type LComment, type LHistory, type LocalState, type LTicket, type LUser } from './storage'

/**
 * Dados de demonstração 100% fictícios para o modo local.
 *
 * Usados pelo botão "carregar dados de demonstração" do painel, para quem abre o site publicado
 * pela primeira vez ver os indicadores preenchidos. Gerados a partir de "agora" e com sorteio de
 * semente fixa, então o resultado é sempre o mesmo e nunca fica velho.
 */

const HOUR = 3_600_000
const DAY = 24 * HOUR

/** Gerador pseudoaleatório com semente fixa (mulberry32): mesma demo em toda execução. */
function rng(seed: number) {
  let a = seed
  return () => {
    a |= 0
    a = (a + 0x6d2b79f5) | 0
    let t = Math.imul(a ^ (a >>> 15), 1 | a)
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296
  }
}

const TITLES: Record<number, string[]> = {
  1: ['VPN não conecta fora da rede', 'Wi-Fi caindo na sala de reuniões', 'Lentidão no acesso à rede'],
  2: ['Monitor sem imagem', 'Notebook não liga', 'Teclado com teclas falhando'],
  3: ['Erro ao abrir o sistema de RH', 'Instalar pacote Office', 'Atualização travada do navegador'],
  4: ['Senha expirada no sistema', 'Liberar acesso à pasta do setor', 'Conta bloqueada após tentativas'],
  5: ['E-mails não chegam', 'Caixa de entrada cheia', 'Calendário não sincroniza'],
  6: ['Impressora sem toner', 'Fila de impressão travada', 'Scanner não envia para e-mail'],
}

export function demoState(now: number): LocalState {
  const random = rng(7)
  const pick = <T,>(items: T[]): T => items[Math.floor(random() * items.length)]
  const weighted = <T,>(items: [T, number][]): T => {
    const total = items.reduce((s, [, w]) => s + w, 0)
    let r = random() * total
    for (const [item, w] of items) {
      if ((r -= w) < 0) return item
    }
    return items[items.length - 1][0]
  }

  const state = emptyState(now)
  state.categories.forEach((c) => { c.createdAt = now - 90 * DAY })

  const users: LUser[] = [
    { id: ME_ID, name: 'Você', role: 'TECNICO', active: true },
    { id: 2, name: 'Ana Souza', role: 'TECNICO', active: true },
    { id: 3, name: 'Bruno Lima', role: 'TECNICO', active: true },
    { id: 4, name: 'Carla Mendes', role: 'SOLICITANTE', active: true },
    { id: 5, name: 'Diego Rocha', role: 'SOLICITANTE', active: true },
  ]
  const nameOf = (id: number) => users.find((u) => u.id === id)?.name ?? '—'
  const ruleOf = (p: Priority) => state.slaRules.find((r) => r.priority === p)!

  const tickets: LTicket[] = []
  const comments: LComment[] = []
  const history: LHistory[] = []
  const log = (h: Omit<LHistory, 'id'>) => history.push({ id: history.length + 1, ...h })

  for (let id = 1; id <= 46; id++) {
    const categoryId = 1 + Math.floor(random() * 6)
    const priority = weighted<Priority>([['P1', 1], ['P2', 3], ['P3', 5], ['P4', 3]])
    const createdAt = now - (1 + Math.floor(random() * 55)) * DAY - Math.floor(random() * 8) * HOUR
    const rule = ruleOf(priority)
    const slaDueAt = dueAt(createdAt, rule.resolutionMinutes, rule.businessHours)

    let status = weighted<TicketStatus>([['ABERTO', 4], ['EM_ATENDIMENTO', 5], ['RESOLVIDO', 12], ['FECHADO', 16], ['CANCELADO', 1]])
    if (createdAt > now - 3 * DAY) status = pick<TicketStatus>(['ABERTO', 'EM_ATENDIMENTO'])
    else if ((status === 'ABERTO' || status === 'EM_ATENDIMENTO') && createdAt < now - 6 * DAY) status = 'FECHADO'

    const requesterId = pick([4, 5])
    const assigneeId = status === 'ABERTO' ? null : pick([ME_ID, ME_ID, 2, 3])
    let resolvedAt: number | null = null
    if (status === 'RESOLVIDO' || status === 'FECHADO') {
      const late = random() < 0.18
      resolvedAt = late
        ? slaDueAt + (1 + Math.floor(random() * 30)) * HOUR
        : Math.round(createdAt + (slaDueAt - createdAt) * (0.2 + random() * 0.75))
      resolvedAt = Math.max(createdAt + HOUR, Math.min(resolvedAt, now - HOUR)) // nunca no futuro
    }

    tickets.push({
      id, title: pick(TITLES[categoryId]), description: 'Chamado de exemplo (dados fictícios).',
      categoryId, priority, status, requesterId, assigneeId,
      createdAt, updatedAt: resolvedAt ?? createdAt + HOUR, slaDueAt, resolvedAt,
    })

    log({ ticketId: id, actorId: requesterId, action: 'CREATED', details: `Chamado aberto com prioridade ${priority} (prazo de SLA: ${new Date(slaDueAt).toISOString()})`, createdAt })
    if (assigneeId !== null) {
      log({ ticketId: id, actorId: ME_ID, action: 'ASSIGNED', details: `Atribuído a ${nameOf(assigneeId)}`, createdAt: createdAt + HOUR / 2 })
      log({ ticketId: id, actorId: ME_ID, action: 'STATUS_CHANGED', details: 'Status: ABERTO → EM_ATENDIMENTO', createdAt: createdAt + HOUR / 2 })
    }
    if (resolvedAt !== null && assigneeId !== null) {
      const at = resolvedAt - HOUR / 4
      comments.push({ id: comments.length + 1, ticketId: id, authorId: assigneeId, body: 'Problema identificado e corrigido. Usuário confirmou o funcionamento.', createdAt: at })
      log({ ticketId: id, actorId: assigneeId, action: 'COMMENT_ADDED', details: 'Comentário adicionado', createdAt: at })
      log({ ticketId: id, actorId: assigneeId, action: 'STATUS_CHANGED', details: 'Status: EM_ATENDIMENTO → RESOLVIDO', createdAt: resolvedAt })
    }
  }

  return {
    ...state,
    seq: { ...state.seq, user: users.length, ticket: tickets.length, comment: comments.length, history: history.length },
    users, tickets, comments, history,
  }
}
