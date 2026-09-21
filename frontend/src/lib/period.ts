import type { Period } from '../api/types'
import { toIsoDay } from './format'

export type PeriodPreset = '7d' | '30d' | '90d' | 'month' | 'custom'

export const PRESETS: { id: Exclude<PeriodPreset, 'custom'>; label: string }[] = [
  { id: '7d', label: 'Últimos 7 dias' },
  { id: '30d', label: 'Últimos 30 dias' },
  { id: '90d', label: 'Últimos 90 dias' },
  { id: 'month', label: 'Este mês' },
]

const addDays = (date: Date, days: number): Date => {
  const copy = new Date(date)
  copy.setDate(copy.getDate() + days)
  return copy
}

/** Período (datas inclusivas) de um preset, relativo a "hoje". */
export function periodForPreset(preset: Exclude<PeriodPreset, 'custom'>, today: Date = new Date()): Period {
  const to = toIsoDay(today)
  switch (preset) {
    case '7d': return { from: toIsoDay(addDays(today, -6)), to }
    case '30d': return { from: toIsoDay(addDays(today, -29)), to }
    case '90d': return { from: toIsoDay(addDays(today, -89)), to }
    case 'month': return { from: toIsoDay(new Date(today.getFullYear(), today.getMonth(), 1)), to }
  }
}

/** A API aceita no máximo 366 dias e exige from <= to; valida no cliente para dar um aviso claro. */
export function validatePeriod(period: Period): string | null {
  if (!period.from || !period.to) return 'Informe as duas datas.'
  if (period.from > period.to) return 'A data inicial não pode ser posterior à final.'
  const days = (Date.parse(period.to) - Date.parse(period.from)) / 86_400_000 + 1
  if (days > 366) return 'O período máximo é de 366 dias.'
  return null
}
