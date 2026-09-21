import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { tokenStore } from '../auth/tokenStore'
import { ApiError, apiFetch, onSessionExpired, refreshSession } from './client'
import { json, mockFetch } from '../test/fixtures'

const authOf = (init?: RequestInit) => (init?.headers as Record<string, string> | undefined)?.Authorization

beforeEach(() => {
  tokenStore.clear()
  tokenStore.set({ accessToken: 'old-access', refreshToken: 'refresh-1' })
})

afterEach(() => {
  vi.unstubAllGlobals()
  onSessionExpired(null)
})

describe('apiFetch', () => {
  it('envia o access token e devolve o JSON', async () => {
    const fetchMock = mockFetch({ '/api/data': () => ({ ok: true }) })

    await expect(apiFetch('/api/data')).resolves.toEqual({ ok: true })
    expect(authOf(fetchMock.mock.calls[0][1])).toBe('Bearer old-access')
  })

  it('204 devolve undefined', async () => {
    mockFetch({ '/api/x': () => new Response(null, { status: 204 }) })
    await expect(apiFetch('/api/x', { method: 'POST' })).resolves.toBeUndefined()
  })

  it('traduz o ProblemDetail da API em ApiError', async () => {
    mockFetch({ '/api/x': () => json({ title: 'Regra de negócio violada', detail: 'Período inválido' }, 422) })

    const error = await apiFetch('/api/x').catch((e: unknown) => e)
    expect(error).toBeInstanceOf(ApiError)
    expect(error).toMatchObject({ status: 422, title: 'Regra de negócio violada', message: 'Período inválido' })
  })

  it('401 -> renova o token UMA vez e repete a chamada com o token novo', async () => {
    const fetchMock = mockFetch({
      '/api/auth/refresh': () => ({ accessToken: 'new-access', refreshToken: 'refresh-2', tokenType: 'Bearer', expiresInSeconds: 900 }),
      '/api/data': (_url, init) => (authOf(init) === 'Bearer new-access' ? { ok: true } : json({ title: 'Não autenticado' }, 401)),
    })

    await expect(apiFetch('/api/data')).resolves.toEqual({ ok: true })

    const urls = fetchMock.mock.calls.map(([u]) => String(u))
    expect(urls).toEqual(['/api/data', '/api/auth/refresh', '/api/data'])
    expect(tokenStore.getRefresh()).toBe('refresh-2') // o refresh token ROTACIONOU e o novo foi guardado
  })

  it('várias requisições com 401 ao mesmo tempo compartilham UM refresh (senão a API leria reuso e derrubaria a sessão)', async () => {
    let refreshCalls = 0
    const fetchMock = mockFetch({
      '/api/auth/refresh': async () => {
        refreshCalls++
        await new Promise((r) => setTimeout(r, 30)) // renovação demorada: as outras chamadas chegam enquanto ela corre
        return { accessToken: 'new-access', refreshToken: 'refresh-2', tokenType: 'Bearer', expiresInSeconds: 900 }
      },
      '/api/': (_url, init) => (authOf(init) === 'Bearer new-access' ? { ok: true } : json({ title: 'Não autenticado' }, 401)),
    })

    const results = await Promise.all([apiFetch('/api/a'), apiFetch('/api/b'), apiFetch('/api/c'), apiFetch('/api/d'), apiFetch('/api/e')])

    expect(results).toHaveLength(5)
    expect(refreshCalls).toBe(1)
    expect(fetchMock.mock.calls.filter(([u]) => String(u).includes('/api/auth/refresh'))).toHaveLength(1)
  })

  it('refresh recusado: limpa a sessão, avisa a interface e propaga o 401', async () => {
    const expired = vi.fn()
    onSessionExpired(expired)
    mockFetch({
      '/api/auth/refresh': () => json({ title: 'Não autenticado' }, 401),
      '/api/data': () => json({ title: 'Não autenticado', detail: 'Token expirado' }, 401),
    })

    await expect(apiFetch('/api/data')).rejects.toMatchObject({ status: 401 })
    expect(expired).toHaveBeenCalledTimes(1)
    expect(tokenStore.getRefresh()).toBeNull()
    expect(tokenStore.getAccess()).toBeNull()
  })

  it('falha de REDE ao renovar não desloga (pode ser passageira): tokens preservados', async () => {
    const expired = vi.fn()
    onSessionExpired(expired)
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
      if (String(input).includes('/api/auth/refresh')) throw new TypeError('Failed to fetch')
      return json({ title: 'Não autenticado' }, 401)
    }))

    await expect(apiFetch('/api/data')).rejects.toBeInstanceOf(TypeError)
    expect(expired).not.toHaveBeenCalled()
    expect(tokenStore.getRefresh()).toBe('refresh-1')
  })

  it('não entra em laço: se o 401 persiste depois de renovar, desiste', async () => {
    const fetchMock = mockFetch({
      '/api/auth/refresh': () => ({ accessToken: 'new', refreshToken: 'refresh-2', tokenType: 'Bearer', expiresInSeconds: 900 }),
      '/api/data': () => json({ title: 'Não autenticado' }, 401),
    })

    await expect(apiFetch('/api/data')).rejects.toMatchObject({ status: 401 })
    expect(fetchMock.mock.calls.filter(([u]) => String(u).includes('/api/auth/refresh'))).toHaveLength(1)
  })

  it('rotas públicas (auth:false) não enviam token nem tentam renovar', async () => {
    const fetchMock = mockFetch({ '/api/auth/login': () => json({ title: 'Não autenticado', detail: 'Credenciais inválidas' }, 401) })

    await expect(apiFetch('/api/auth/login', { method: 'POST', body: {}, auth: false })).rejects.toMatchObject({
      status: 401, message: 'Credenciais inválidas',
    })
    expect(authOf(fetchMock.mock.calls[0][1])).toBeUndefined()
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })
})

describe('refreshSession', () => {
  it('sem refresh token devolve false sem chamar a rede', async () => {
    tokenStore.clear()
    const fetchMock = mockFetch({})

    await expect(refreshSession()).resolves.toBe(false)
    expect(fetchMock).not.toHaveBeenCalled()
  })
})
