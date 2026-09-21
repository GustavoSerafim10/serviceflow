import type { TokenResponse } from '../api/types'

/**
 * Onde os tokens vivem.
 *
 *  - access token (JWT, 15 min): SÓ em memória. Some ao recarregar a página, e tudo bem: o
 *    refresh token o repõe. Ficar fora do storage reduz o que um XSS consegue roubar.
 *  - refresh token (7 dias): em localStorage, para a sessão sobreviver a recarregamentos.
 *    TRADE-OFF conhecido: localStorage é legível por qualquer script da página (XSS). O
 *    ideal seria um cookie HttpOnly emitido pela API; como a API devolve o token no corpo,
 *    aceitamos o risco (mitigado pela rotação de uso único e pela detecção de reuso).
 */
const REFRESH_KEY = 'sf.refreshToken'

let accessToken: string | null = null

function safeStorage(): Storage | null {
  try {
    return window.localStorage
  } catch {
    return null // modo privado / storage bloqueado: a sessão só dura até recarregar
  }
}

export const tokenStore = {
  getAccess: () => accessToken,

  getRefresh: (): string | null => safeStorage()?.getItem(REFRESH_KEY) ?? null,

  set(tokens: Pick<TokenResponse, 'accessToken' | 'refreshToken'>) {
    accessToken = tokens.accessToken
    safeStorage()?.setItem(REFRESH_KEY, tokens.refreshToken)
  },

  clear() {
    accessToken = null
    safeStorage()?.removeItem(REFRESH_KEY)
  },
}
