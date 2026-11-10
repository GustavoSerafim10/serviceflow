import { describe, expect, it } from 'vitest'
import { demoState } from './demo'
import { isValidState } from './storage'

describe('dados de demonstração', () => {
  const now = Date.UTC(2026, 10, 10, 12)

  it('gera um estado válido e consistente', () => {
    const s = demoState(now)
    expect(isValidState(s)).toBe(true)
    expect(s.tickets.length).toBeGreaterThan(30)
    expect(s.seq.ticket).toBe(s.tickets.length)
    expect(s.seq.history).toBe(s.history.length)
    expect(s.seq.comment).toBe(s.comments.length)
  })

  it('é determinístico e não cria chamados no futuro', () => {
    const a = demoState(now)
    const b = demoState(now)
    expect(JSON.stringify(a)).toBe(JSON.stringify(b))
    expect(a.tickets.every((t) => t.createdAt <= now && (t.resolvedAt ?? 0) <= now)).toBe(true)
  })

  it('só deixa resolvidos e fechados com data de resolução', () => {
    for (const t of demoState(now).tickets) {
      expect(t.resolvedAt !== null).toBe(t.status === 'RESOLVIDO' || t.status === 'FECHADO')
      expect(t.assigneeId === null).toBe(t.status === 'ABERTO')
    }
  })
})
