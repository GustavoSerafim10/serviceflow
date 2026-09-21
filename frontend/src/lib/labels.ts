import type { Priority, TicketStatus } from '../api/types'

// Prioridade é uma escala ORDINAL (a ordem importa): rampa de um matiz só, do mais urgente ao menos.
export const PRIORITY_INFO: Record<Priority, { label: string; short: string; color: string }> = {
  P1: { label: 'P1 · Crítico', short: 'P1', color: 'var(--prio-1)' },
  P2: { label: 'P2 · Alto', short: 'P2', color: 'var(--prio-2)' },
  P3: { label: 'P3 · Médio', short: 'P3', color: 'var(--prio-3)' },
  P4: { label: 'P4 · Baixo', short: 'P4', color: 'var(--prio-4)' },
}

export const PRIORITIES: Priority[] = ['P1', 'P2', 'P3', 'P4']

export const STATUS_LABEL: Record<TicketStatus, string> = {
  ABERTO: 'Aberto',
  EM_ATENDIMENTO: 'Em atendimento',
  RESOLVIDO: 'Resolvido',
  FECHADO: 'Fechado',
  CANCELADO: 'Cancelado',
}

export const STATUSES: TicketStatus[] = ['ABERTO', 'EM_ATENDIMENTO', 'RESOLVIDO', 'FECHADO', 'CANCELADO']

/** Ações de status oferecidas em cada estado (as que o backend aceita; ABERTO → EM_ATENDIMENTO só atribuindo). */
export const STATUS_ACTIONS: Record<TicketStatus, { to: TicketStatus; label: string; danger?: boolean }[]> = {
  ABERTO: [{ to: 'CANCELADO', label: 'Cancelar chamado', danger: true }],
  EM_ATENDIMENTO: [{ to: 'RESOLVIDO', label: 'Marcar como resolvido' }, { to: 'CANCELADO', label: 'Cancelar chamado', danger: true }],
  RESOLVIDO: [{ to: 'FECHADO', label: 'Fechar chamado' }, { to: 'EM_ATENDIMENTO', label: 'Reabrir' }],
  FECHADO: [],
  CANCELADO: [],
}

export const isTerminal = (s: TicketStatus) => s === 'FECHADO' || s === 'CANCELADO'

/**
 * O histórico vem com o texto no formato do backend ("Status: ABERTO → EM_ATENDIMENTO", datas em ISO).
 * Aqui vira texto legível: nomes de status em português e datas no fuso do navegador.
 */
export function prettifyHistory(details: string): string {
  return details
    .replace(/\b(ABERTO|EM_ATENDIMENTO|RESOLVIDO|FECHADO|CANCELADO)\b/g, (s) => STATUS_LABEL[s as TicketStatus].toLowerCase())
    .replace(/\d{4}-\d{2}-\d{2}T[\d:.]+Z/g, (iso) => formatDateTime(iso))
    .replace(/^Status: (\w)/, (_, first: string) => `Status: ${first.toUpperCase()}`)
}

export function formatDateTime(iso: string): string {
  return new Date(iso).toLocaleString('pt-BR', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' })
}
