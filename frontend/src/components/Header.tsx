import { useEffect, useState } from 'react'
import { useAuth } from '../auth/AuthContext'
import type { User } from '../api/types'

type Theme = 'system' | 'light' | 'dark'
const THEME_KEY = 'sf.theme'
const NEXT: Record<Theme, Theme> = { system: 'light', light: 'dark', dark: 'system' }
const LABEL: Record<Theme, string> = { system: 'Tema: automático', light: 'Tema: claro', dark: 'Tema: escuro' }

function readTheme(): Theme {
  try {
    const stored = window.localStorage.getItem(THEME_KEY)
    return stored === 'light' || stored === 'dark' ? stored : 'system'
  } catch {
    return 'system'
  }
}

/** Automático segue o sistema operacional; claro/escuro é a escolha manual (carimbo em <html data-theme>). */
function useTheme() {
  const [theme, setTheme] = useState<Theme>(readTheme)

  useEffect(() => {
    const root = document.documentElement
    if (theme === 'system') root.removeAttribute('data-theme')
    else root.setAttribute('data-theme', theme)
    try {
      if (theme === 'system') window.localStorage.removeItem(THEME_KEY)
      else window.localStorage.setItem(THEME_KEY, theme)
    } catch {
      // storage indisponível: a escolha vale só nesta sessão
    }
  }, [theme])

  return { theme, cycle: () => setTheme((t) => NEXT[t]) }
}

const ROLE_LABEL = { ADMIN: 'Administrador', TECNICO: 'Técnico', SOLICITANTE: 'Solicitante' } as const

export function Header({ user }: { user: User }) {
  const { logout } = useAuth()
  const { theme, cycle } = useTheme()

  return (
    <header className="app-header">
      <div className="brand">ServiceFlow <span>· Indicadores</span></div>
      <div className="header-actions">
        <span className="user-chip">
          <strong>{user.name}</strong>
          {user.name !== ROLE_LABEL[user.role] && <> · {ROLE_LABEL[user.role]}</>}
        </span>
        <button type="button" className="btn" onClick={cycle}>{LABEL[theme]}</button>
        <button type="button" className="btn" onClick={() => void logout()}>Sair</button>
      </div>
    </header>
  )
}
