import { useAuth } from './auth/AuthContext'
import { Dashboard } from './components/Dashboard'
import { Header } from './components/Header'
import { LoginPage } from './components/LoginPage'

/** Porteiro de autenticação: carregando -> nada; anônimo -> login; autenticado -> painel (se o papel permitir). */
export default function App() {
  const { state } = useAuth()

  if (state.status === 'loading') return <p className="empty" role="status">Carregando…</p>
  if (state.status === 'anonymous') return <LoginPage />

  const { user } = state
  return (
    <div className="page">
      <Header user={user} />
      {user.role === 'SOLICITANTE' ? (
        <div className="notice">
          <h2>Sem acesso aos indicadores</h2>
          <p>
            Os indicadores operacionais são restritos a administradores e técnicos. Os solicitantes
            acompanham seus chamados pela própria tela de chamados.
          </p>
        </div>
      ) : (
        <Dashboard user={user} />
      )}
    </div>
  )
}
