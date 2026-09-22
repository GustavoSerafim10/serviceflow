import { useState, type FormEvent } from 'react'
import { ApiError } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import { LogoMark } from './Icons'

export function LoginPage() {
  const { login } = useAuth()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await login(email.trim(), password)
    } catch (err) {
      // 401 = credenciais (a API responde a mesma mensagem genérica para e-mail inexistente e senha errada)
      setError(err instanceof ApiError && err.status === 401
        ? 'E-mail ou senha inválidos.'
        : 'Não foi possível entrar. Verifique sua conexão e tente novamente.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <main className="login-wrap">
      <div style={{ width: 'min(380px, 100%)' }}>
        <div className="login-brand">
          <LogoMark className="brand-mark" />
          <span className="brand-word">ServiceFlow</span>
        </div>

        <form className="login-card" onSubmit={onSubmit}>
          <h1>Entrar</h1>
          <p className="sub">Acesse os indicadores da operação.</p>

          <div className="field">
            <label htmlFor="email">E-mail</label>
            <input id="email" type="email" autoComplete="username" required value={email} onChange={(e) => setEmail(e.target.value)} />
          </div>
          <div className="field">
            <label htmlFor="password">Senha</label>
            <input id="password" type="password" autoComplete="current-password" required value={password} onChange={(e) => setPassword(e.target.value)} />
          </div>

          {error && <p className="form-error" role="alert">{error}</p>}
          <button className="btn btn-primary" type="submit" disabled={busy} style={{ width: '100%' }}>
            {busy ? 'Entrando…' : 'Entrar'}
          </button>
        </form>
      </div>
    </main>
  )
}
