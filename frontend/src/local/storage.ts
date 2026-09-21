import type { HistoryAction, Priority, Role, TicketStatus } from '../api/types'

const KEY = 'sf.local.v1'

// ------------------------------------------------------------------ modelo persistido (datas em ms)

export interface LCategory { id: number; name: string; description: string | null; active: boolean; createdAt: number }
export interface LUser { id: number; name: string; role: Role; active: boolean }
export interface LSlaRule { priority: Priority; resolutionMinutes: number; businessHours: boolean; updatedAt: number }
export interface LTicket {
  id: number; title: string; description: string; categoryId: number; priority: Priority; status: TicketStatus
  requesterId: number; assigneeId: number | null
  createdAt: number; updatedAt: number; slaDueAt: number; resolvedAt: number | null
}
export interface LComment { id: number; ticketId: number; authorId: number; body: string; createdAt: number }
export interface LHistory { id: number; ticketId: number; actorId: number; action: HistoryAction; details: string; createdAt: number }

export interface LocalState {
  version: 1
  seq: { category: number; user: number; ticket: number; comment: number; history: number }
  categories: LCategory[]
  users: LUser[]
  slaRules: LSlaRule[]
  tickets: LTicket[]
  comments: LComment[]
  history: LHistory[]
}

/** O usuário do modo local: uma pessoa só, que abre e atende os próprios chamados. */
export const ME_ID = 1

const DEFAULT_CATEGORIES: [string, string][] = [
  ['Rede', 'Conectividade, Wi-Fi, VPN e infraestrutura de rede'],
  ['Hardware', 'Computadores, notebooks, monitores e periféricos'],
  ['Software', 'Sistemas, aplicativos, instalações e atualizações'],
  ['Acesso e Senha', 'Contas, senhas, permissões e autenticação'],
  ['E-mail', 'Caixa de entrada, envio e recebimento, calendário'],
  ['Impressora', 'Impressoras, scanners, toner e filas de impressão'],
]

/** Estado inicial: SEM chamados. Já vem com as categorias e as regras de SLA padrão, e você como técnico. */
export function emptyState(now: number): LocalState {
  return {
    version: 1,
    seq: { category: DEFAULT_CATEGORIES.length, user: 1, ticket: 0, comment: 0, history: 0 },
    categories: DEFAULT_CATEGORIES.map(([name, description], i) => ({ id: i + 1, name, description, active: true, createdAt: now })),
    users: [{ id: ME_ID, name: 'Você', role: 'TECNICO', active: true }],
    slaRules: [
      { priority: 'P1', resolutionMinutes: 240, businessHours: false, updatedAt: now },
      { priority: 'P2', resolutionMinutes: 480, businessHours: false, updatedAt: now },
      { priority: 'P3', resolutionMinutes: 600, businessHours: true, updatedAt: now },
      { priority: 'P4', resolutionMinutes: 1800, businessHours: true, updatedAt: now },
    ],
    tickets: [], comments: [], history: [],
  }
}

/** Valida o formato de um estado lido do armazenamento ou colado pelo usuário (backup). */
export function isValidState(value: unknown): value is LocalState {
  const s = value as Partial<LocalState> | null
  return !!s && s.version === 1 && !!s.seq
    && Array.isArray(s.categories) && Array.isArray(s.users) && Array.isArray(s.slaRules)
    && Array.isArray(s.tickets) && Array.isArray(s.comments) && Array.isArray(s.history)
}

export interface StateStorage {
  load(): LocalState | null
  save(state: LocalState): void
  /** false = o navegador bloqueou o armazenamento: os dados só duram até fechar a página. */
  readonly persistent: boolean
}

/**
 * Guarda em localStorage. Se ele estiver indisponível (janela privada, bloqueado, página incorporada em
 * sandbox), cai para memória: o app continua funcionando, só sem persistir. Toda leitura/escrita é protegida.
 */
export function createBrowserStorage(): StateStorage {
  let memory: LocalState | null = null
  let persistent = true

  try {
    const probe = '__sf_probe__'
    window.localStorage.setItem(probe, '1')
    window.localStorage.removeItem(probe)
  } catch {
    persistent = false
  }

  return {
    get persistent() { return persistent },
    load() {
      if (!persistent) return memory
      try {
        const raw = window.localStorage.getItem(KEY)
        if (!raw) return null
        const parsed: unknown = JSON.parse(raw)
        return isValidState(parsed) ? parsed : null
      } catch {
        return null
      }
    },
    save(state) {
      memory = state
      if (!persistent) return
      try {
        window.localStorage.setItem(KEY, JSON.stringify(state))
      } catch {
        persistent = false // cota cheia ou bloqueio: segue em memória
      }
    },
  }
}
