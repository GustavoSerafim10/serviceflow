import { useEffect, useState } from 'react'

/** Roteamento mínimo por hash (#/chamados/12): sem dependência e funciona em qualquer hospedagem estática. */
export type Route =
  | { name: 'dashboard' }
  | { name: 'tickets' }
  | { name: 'new' }
  | { name: 'ticket'; id: number }
  | { name: 'categories' }
  | { name: 'settings' }

export function parseRoute(hash: string): Route {
  const path = hash.replace(/^#\/?/, '').replace(/\/+$/, '')
  if (path === 'chamados') return { name: 'tickets' }
  if (path === 'chamados/novo') return { name: 'new' }
  const ticket = /^chamados\/(\d+)$/.exec(path)
  if (ticket) return { name: 'ticket', id: Number(ticket[1]) }
  if (path === 'categorias') return { name: 'categories' }
  if (path === 'configuracoes') return { name: 'settings' }
  return { name: 'dashboard' }
}

export const paths = {
  dashboard: '#/',
  tickets: '#/chamados',
  new: '#/chamados/novo',
  ticket: (id: number) => `#/chamados/${id}`,
  categories: '#/categorias',
  settings: '#/configuracoes',
}

export function navigate(path: string) {
  window.location.hash = path
}

export function useRoute(): Route {
  const [route, setRoute] = useState<Route>(() => parseRoute(window.location.hash))
  useEffect(() => {
    const onChange = () => {
      setRoute(parseRoute(window.location.hash))
      window.scrollTo?.(0, 0)
    }
    window.addEventListener('hashchange', onChange)
    return () => window.removeEventListener('hashchange', onChange)
  }, [])
  return route
}
