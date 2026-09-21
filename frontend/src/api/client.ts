import { tokenStore } from '../auth/tokenStore'
import { localRequest } from '../local/localApi'
import { STANDALONE } from '../local/mode'
import { ApiError } from './errors'
import type { TokenResponse } from './types'

// Mesma origem: em desenvolvimento o Vite encaminha /api para a API (proxy); em produção o nginx faz o mesmo.
// Sem CORS a configurar. VITE_API_BASE permite apontar para outra origem, se necessário.
const BASE: string = import.meta.env.VITE_API_BASE ?? ''

export { ApiError }

async function toApiError(res: Response): Promise<ApiError> {
  try {
    const body = (await res.json()) as { title?: string; detail?: string }
    return new ApiError(res.status, body.title ?? res.statusText, body.detail ?? '')
  } catch {
    return new ApiError(res.status, res.statusText, '')
  }
}

// ---------------------------------------------------------------- sessão expirada

let sessionExpiredHandler: (() => void) | null = null

/** A camada de autenticação registra aqui o que fazer quando o servidor recusa a renovação da sessão. */
export function onSessionExpired(handler: (() => void) | null) {
  sessionExpiredHandler = handler
}

// ---------------------------------------------------------------- renovação (refresh)

let inflightRefresh: Promise<boolean> | null = null

/**
 * Troca o refresh token por um novo par de tokens. SINGLE-FLIGHT: se já houver uma renovação em
 * andamento, quem chama recebe a MESMA promessa em vez de disparar outra.
 *
 * Isto é essencial neste sistema: o refresh token é de USO ÚNICO e a API trata a reapresentação
 * de um token já usado como roubo, encerrando todas as sessões do usuário. Sem o single-flight,
 * cinco requisições recebendo 401 ao mesmo tempo (ou o StrictMode do React executando um efeito
 * duas vezes) gerariam cinco refreshes com o mesmo token e derrubariam o próprio usuário.
 *
 * Resolve true = sessão renovada; false = servidor recusou (sessão encerrada). Falha de REDE
 * rejeita a promessa e NÃO apaga os tokens (pode ser passageira: o usuário segue logado).
 */
export function refreshSession(): Promise<boolean> {
  inflightRefresh ??= withCrossTabLock(doRefresh).finally(() => {
    inflightRefresh = null
  })
  return inflightRefresh
}

/** Entre ABAS o localStorage é compartilhado: serializa a renovação com a Web Locks API (quando existe). */
function withCrossTabLock<T>(task: () => Promise<T>): Promise<T> {
  if (typeof navigator !== 'undefined' && navigator.locks?.request) {
    return navigator.locks.request('sf-refresh', task)
  }
  return task()
}

async function doRefresh(): Promise<boolean> {
  // Lido AQUI (já com o lock): se outra aba renovou enquanto esperávamos, pegamos o token novo.
  const refreshToken = tokenStore.getRefresh()
  if (!refreshToken) return false

  const res = await fetch(`${BASE}/api/auth/refresh`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ refreshToken }),
  })
  if (!res.ok) {
    tokenStore.clear()
    sessionExpiredHandler?.()
    return false
  }
  tokenStore.set((await res.json()) as TokenResponse)
  return true
}

// ---------------------------------------------------------------- requisições

interface RequestOptions {
  method?: string
  body?: unknown
  /** false = rota pública (login/refresh): sem Authorization e sem tentar renovar em caso de 401. */
  auth?: boolean
}

function send(path: string, { method = 'GET', body, auth = true }: RequestOptions): Promise<Response> {
  const headers: Record<string, string> = {}
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  const token = tokenStore.getAccess()
  if (auth && token) headers.Authorization = `Bearer ${token}`

  return fetch(`${BASE}${path}`, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  })
}

export async function apiFetch<T>(path: string, options: RequestOptions = {}): Promise<T> {
  // Modo local: sem rede. Um "motor" no navegador responde no lugar da API (ver src/local/).
  if (STANDALONE) return localRequest<T>(path, options)

  let res = await send(path, options)

  // Access token expirado (ou ausente após um recarregamento): renova UMA vez e repete a chamada.
  if (res.status === 401 && options.auth !== false) {
    const renewed = await refreshSession()
    if (!renewed) throw await toApiError(res)
    res = await send(path, options)
  }

  if (!res.ok) throw await toApiError(res)
  if (res.status === 204) return undefined as T
  return (await res.json()) as T
}
