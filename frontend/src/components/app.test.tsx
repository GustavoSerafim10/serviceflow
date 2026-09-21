import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { User } from '../api/types'
import { onSessionExpired } from '../api/client'
import App from '../App'
import { AuthProvider } from '../auth/AuthContext'
import { tokenStore } from '../auth/tokenStore'
import {
  admin, byCategory, byPriority, byTechnician, json, mockFetch, requester, suggestionMetrics, summary,
  technician, timeline, tokens,
} from '../test/fixtures'

function renderApp() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <AuthProvider>
        <App />
      </AuthProvider>
    </QueryClientProvider>,
  )
}

/** API simulada completa: login, usuário logado e todos os endpoints do painel. */
function api(me: User) {
  return mockFetch({
    '/api/auth/login': () => tokens,
    '/api/auth/refresh': () => tokens,
    '/api/auth/logout': () => new Response(null, { status: 204 }),
    '/api/users/me': () => me,
    '/api/analytics/summary': () => summary,
    '/api/analytics/timeline': () => timeline,
    '/api/analytics/by-priority': () => byPriority,
    '/api/analytics/by-category': () => byCategory,
    '/api/analytics/by-technician': () => byTechnician,
    '/api/tickets/suggestions/metrics': () => suggestionMetrics,
  })
}

beforeEach(() => tokenStore.clear())
afterEach(() => {
  vi.unstubAllGlobals()
  onSessionExpired(null)
})

describe('autenticação', () => {
  it('sem sessão guardada mostra o login (e nenhuma chamada à API)', async () => {
    const fetchMock = api(admin)
    renderApp()

    expect(await screen.findByRole('button', { name: 'Entrar' })).toBeInTheDocument()
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('login com credenciais erradas mostra o erro e continua no login', async () => {
    mockFetch({ '/api/auth/login': () => json({ title: 'Não autenticado', detail: 'Credenciais inválidas' }, 401) })
    renderApp()

    await userEvent.type(await screen.findByLabelText('E-mail'), 'a@b.com')
    await userEvent.type(screen.getByLabelText('Senha'), 'errada')
    await userEvent.click(screen.getByRole('button', { name: 'Entrar' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('E-mail ou senha inválidos.')
    expect(screen.getByRole('button', { name: 'Entrar' })).toBeEnabled()
  })

  it('login correto leva ao painel e guarda o refresh token', async () => {
    api(admin)
    renderApp()

    await userEvent.type(await screen.findByLabelText('E-mail'), 'admin@x.com')
    await userEvent.type(screen.getByLabelText('Senha'), 'segredo123')
    await userEvent.click(screen.getByRole('button', { name: 'Entrar' }))

    expect(await screen.findByText('76,9%')).toBeInTheDocument()
    expect(tokenStore.getRefresh()).toBe('refresh-1')
  })

  it('restaura a sessão ao abrir a página quando há refresh token (uma única renovação)', async () => {
    tokenStore.set({ accessToken: '', refreshToken: 'refresh-guardado' })
    const fetchMock = api(admin)
    renderApp()

    expect(await screen.findByText('76,9%')).toBeInTheDocument()
    expect(fetchMock.mock.calls.filter(([u]) => String(u).includes('/api/auth/refresh'))).toHaveLength(1)
  })

  it('sair volta ao login, revoga o token no servidor e apaga o armazenamento local', async () => {
    tokenStore.set({ accessToken: 'a', refreshToken: 'refresh-guardado' })
    const fetchMock = api(admin)
    renderApp()

    await userEvent.click(await screen.findByRole('button', { name: 'Sair' }))

    expect(await screen.findByRole('button', { name: 'Entrar' })).toBeInTheDocument()
    expect(tokenStore.getRefresh()).toBeNull()
    expect(fetchMock.mock.calls.some(([u]) => String(u).includes('/api/auth/logout'))).toBe(true)
  })

  it('refresh recusado ao restaurar: cai no login', async () => {
    tokenStore.set({ accessToken: '', refreshToken: 'revogado' })
    mockFetch({ '/api/auth/refresh': () => json({ title: 'Não autenticado' }, 401) })
    renderApp()

    expect(await screen.findByRole('button', { name: 'Entrar' })).toBeInTheDocument()
  })
})

describe('painel', () => {
  async function openDashboard(user: User) {
    tokenStore.set({ accessToken: 'a', refreshToken: 'r' })
    const fetchMock = api(user)
    renderApp()
    await screen.findByText('76,9%')
    return fetchMock
  }

  it('mostra os indicadores da API formatados em pt-BR', async () => {
    await openDashboard(admin)

    expect(screen.getByText('90 de 117 resolvidos dentro do prazo')).toBeInTheDocument()
    expect(screen.getByText('19h 02min')).toBeInTheDocument()   // MTTR
    expect(screen.getByText('Mediana 10h 53min')).toBeInTheDocument()
    expect(screen.getByText('Requer atenção')).toBeInTheDocument() // SLA estourado: ícone + texto, nunca só cor
    const technicians = screen.getByRole('region', { name: 'Desempenho por técnico' })
    expect(within(technicians).getByText('Carla Dias')).toBeInTheDocument()
    expect(within(technicians).getByText('59,0%')).toBeInTheDocument()
  })

  it('prioridade sem chamados aparece com valor e sem inventar taxa', async () => {
    await openDashboard(admin)
    await userEvent.click(within(screen.getByRole('region', { name: 'Abertos por prioridade' })).getByRole('button', { name: 'Ver tabela' }))

    const table = screen.getByRole('table', { name: 'Indicadores por prioridade' })
    const p4 = within(table).getByText('P4 · Baixo').closest('tr') as HTMLElement
    expect(within(p4).getAllByText('—')).toHaveLength(2) // SLA e tempo médio: traço, não 0%
  })

  it('todo gráfico tem a tabela-gêmea com os mesmos dados', async () => {
    await openDashboard(admin)
    const card = screen.getByRole('region', { name: 'Chamados por dia' })

    await userEvent.click(within(card).getByRole('button', { name: 'Ver tabela' }))
    const table = within(card).getByRole('table')
    expect(within(table).getAllByRole('row')).toHaveLength(1 + timeline.items.length)

    await userEvent.click(within(card).getByRole('button', { name: 'Ver gráfico' }))
    expect(within(card).queryByRole('table')).toBeNull()
  })

  it('trocar o preset refaz TODAS as consultas com o novo período', async () => {
    const fetchMock = await openDashboard(admin)
    fetchMock.mockClear()

    await userEvent.click(screen.getByRole('button', { name: /Últimos 7 dias/ }))

    await waitFor(() => {
      const analytics = fetchMock.mock.calls.map(([u]) => String(u)).filter((u) => u.includes('/api/analytics/'))
      expect(analytics).toHaveLength(5)
      const froms = new Set(analytics.map((u) => new URL(u, 'http://x').searchParams.get('from')))
      expect(froms.size).toBe(1) // todos escopados pelo MESMO filtro
    })
  })

  it('período personalizado inválido mostra o erro e não consulta a API', async () => {
    const fetchMock = await openDashboard(admin)
    fetchMock.mockClear()

    await userEvent.click(screen.getByRole('button', { name: /Personalizado/ }))
    const [from, to] = [screen.getByLabelText('Data inicial'), screen.getByLabelText('Data final')]
    await userEvent.clear(to)
    await userEvent.type(to, '2026-01-01')
    await userEvent.clear(from)
    await userEvent.type(from, '2026-03-01')

    expect(await screen.findByText('A data inicial não pode ser posterior à final.')).toBeInTheDocument()
    expect(fetchMock.mock.calls.filter(([u]) => String(u).includes('/api/analytics/'))).toHaveLength(0)
  })

  it('ADMIN vê a qualidade das sugestões; TECNICO não (e a API nem é consultada)', async () => {
    await openDashboard(admin)
    expect(screen.getByRole('region', { name: 'Qualidade das sugestões automáticas' })).toBeInTheDocument()
    expect(screen.getByText('73,0%')).toBeInTheDocument()
  })

  it('TECNICO não vê o painel de sugestões nem consulta essa rota', async () => {
    const fetchMock = await openDashboard(technician)

    expect(screen.queryByRole('region', { name: 'Qualidade das sugestões automáticas' })).toBeNull()
    expect(fetchMock.mock.calls.some(([u]) => String(u).includes('/suggestions/metrics'))).toBe(false)
  })

  it('SOLICITANTE recebe aviso de acesso e nenhuma consulta de analytics é feita', async () => {
    tokenStore.set({ accessToken: 'a', refreshToken: 'r' })
    const fetchMock = api(requester)
    renderApp()

    expect(await screen.findByText('Sem acesso aos indicadores')).toBeInTheDocument()
    expect(fetchMock.mock.calls.some(([u]) => String(u).includes('/api/analytics/'))).toBe(false)
  })

  it('erro da API mostra aviso com "Tentar novamente"', async () => {
    tokenStore.set({ accessToken: 'a', refreshToken: 'r' })
    mockFetch({
      '/api/auth/refresh': () => tokens,
      '/api/users/me': () => admin,
      '/api/analytics/': () => json({ title: 'Erro interno', detail: 'Falhou' }, 500),
      '/api/tickets/suggestions/metrics': () => suggestionMetrics,
    })
    renderApp()

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('Não foi possível carregar os indicadores')
    expect(within(alert).getByRole('button', { name: 'Tentar novamente' })).toBeInTheDocument()
  })
})
