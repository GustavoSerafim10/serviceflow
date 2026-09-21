import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { onSessionExpired, refreshSession } from '../api/client'
import * as api from '../api/endpoints'
import type { User } from '../api/types'
import { getEngine } from '../local/localApi'
import { STANDALONE } from '../local/mode'
import { tokenStore } from './tokenStore'

type AuthState =
  | { status: 'loading' }
  | { status: 'anonymous' }
  | { status: 'authenticated'; user: User }

interface AuthContextValue {
  state: AuthState
  login: (email: string, password: string) => Promise<void>
  logout: () => Promise<void>
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  // Modo local: não há login (é o seu computador); já entra como você.
  const [state, setState] = useState<AuthState>(
    STANDALONE ? { status: 'authenticated', user: getEngine().me() } : { status: 'loading' },
  )

  // O servidor recusou a renovação (refresh expirado, revogado ou reutilizado): volta ao login.
  useEffect(() => {
    onSessionExpired(() => setState({ status: 'anonymous' }))
    return () => onSessionExpired(null)
  }, [])

  // Ao abrir a página: se há refresh token guardado, tenta restaurar a sessão (o access token vive só em memória).
  useEffect(() => {
    if (STANDALONE) return
    let cancelled = false
    async function restore() {
      if (!tokenStore.getRefresh()) {
        setState({ status: 'anonymous' })
        return
      }
      try {
        // refreshSession é single-flight: o StrictMode do React roda este efeito duas vezes em
        // desenvolvimento, e duas renovações com o mesmo token seriam lidas como roubo.
        const renewed = await refreshSession()
        if (!renewed) return // onSessionExpired já mudou o estado
        const user = await api.getMe()
        if (!cancelled) setState({ status: 'authenticated', user })
      } catch {
        if (!cancelled) setState({ status: 'anonymous' }) // ex: API fora do ar; tokens preservados
      }
    }
    void restore()
    return () => {
      cancelled = true
    }
  }, [])

  const login = useCallback(async (email: string, password: string) => {
    await api.login(email, password)
    setState({ status: 'authenticated', user: await api.getMe() })
  }, [])

  const logout = useCallback(async () => {
    await api.logout()
    setState({ status: 'anonymous' })
  }, [])

  const value = useMemo(() => ({ state, login, logout }), [state, login, logout])
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

// eslint-disable-next-line react-refresh/only-export-components
export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext)
  if (!context) throw new Error('useAuth deve ser usado dentro de <AuthProvider>')
  return context
}
