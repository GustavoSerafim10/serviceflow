import { useAuth } from './auth/AuthContext'
import { Dashboard } from './components/Dashboard'
import { Header } from './components/Header'
import { LoginPage } from './components/LoginPage'
import { ChartIcon, GearIcon, TagIcon, TicketIcon } from './components/Icons'
import { CategoriesPage } from './components/pages/CategoriesPage'
import { NewTicketPage } from './components/pages/NewTicketPage'
import { SettingsPage } from './components/pages/SettingsPage'
import { TicketDetailPage } from './components/pages/TicketDetailPage'
import { TicketsPage } from './components/pages/TicketsPage'
import { paths, useRoute, type Route } from './lib/router'
import { getEngine } from './local/localApi'
import { STANDALONE } from './local/mode'

const NAV: { label: string; href: string; icon: typeof ChartIcon; active: (r: Route) => boolean }[] = [
  { label: 'Indicadores', href: paths.dashboard, icon: ChartIcon, active: (r) => r.name === 'dashboard' },
  { label: 'Chamados', href: paths.tickets, icon: TicketIcon, active: (r) => r.name === 'tickets' || r.name === 'new' || r.name === 'ticket' },
  { label: 'Categorias', href: paths.categories, icon: TagIcon, active: (r) => r.name === 'categories' },
  { label: 'Configurações', href: paths.settings, icon: GearIcon, active: (r) => r.name === 'settings' },
]

function LocalBanner() {
  const persistent = getEngine().persistent
  return (
    <p className={`local-banner${persistent ? '' : ' warn'}`} role="note">
      {persistent ? (
        <>
          <strong>Modo local.</strong> Tudo o que você registra fica salvo <strong>só neste navegador</strong>: nada é enviado a nenhum servidor.
          Faça backup em <a href={paths.settings}>Configurações</a>. A sugestão de categoria e prioridade aqui é uma estimativa simples por
          palavras-chave — o modelo treinado (serviço Python) só está disponível com o servidor.
        </>
      ) : (
        <>
          <strong>Atenção:</strong> este navegador bloqueou o armazenamento local. Você pode usar o app normalmente, mas os dados
          serão perdidos ao fechar a página. Gere um backup em <a href={paths.settings}>Configurações</a> antes de sair.
        </>
      )}
    </p>
  )
}

function View({ route, role }: { route: Route; role: string }) {
  switch (route.name) {
    case 'tickets': return <TicketsPage />
    case 'new': return <NewTicketPage />
    case 'ticket': return <TicketDetailPage id={route.id} />
    case 'categories': return <CategoriesPage />
    case 'settings': return <SettingsPage />
    case 'dashboard':
      return role === 'SOLICITANTE' ? (
        <div className="notice">
          <h2>Sem acesso aos indicadores</h2>
          <p>Os indicadores operacionais são restritos a administradores e técnicos.</p>
        </div>
      ) : null
  }
}

/** Porteiro de autenticação: carregando -> nada; anônimo -> login; autenticado -> o app (com navegação por hash). */
export default function App() {
  const { state } = useAuth()
  const route = useRoute()

  if (state.status === 'loading') return <p className="empty" role="status">Carregando…</p>
  if (state.status === 'anonymous') return <LoginPage />

  const { user } = state
  const dashboard = route.name === 'dashboard' && user.role !== 'SOLICITANTE'
  return (
    <div className="page">
      <Header user={user} />
      {STANDALONE && <LocalBanner />}
      <nav className="nav" aria-label="Principal">
        {NAV.map((item) => (
          <a key={item.href} href={item.href} aria-current={item.active(route) ? 'page' : undefined}>
            <item.icon />
            {item.label}
          </a>
        ))}
      </nav>
      {dashboard ? <Dashboard user={user} /> : <View route={route} role={user.role} />}
    </div>
  )
}
