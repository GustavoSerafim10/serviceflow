const intFormat = new Intl.NumberFormat('pt-BR')

export const formatInt = (n: number): string => intFormat.format(n)

/** 0.7692 -> "76,9%". null (sem base de cálculo) -> "—": nunca um enganoso "0%". */
export function formatRate(rate: number | null | undefined): string {
  if (rate == null) return '—'
  return `${(rate * 100).toLocaleString('pt-BR', { minimumFractionDigits: 1, maximumFractionDigits: 1 })}%`
}

/** Minutos -> texto legível: "45 min", "3h 12min", "2d 4h". null -> "—". */
export function formatMinutes(minutes: number | null | undefined): string {
  if (minutes == null) return '—'
  const total = Math.round(minutes)
  if (total < 60) return `${total} min`
  if (total < 60 * 24) {
    const h = Math.floor(total / 60)
    const m = total % 60
    return m === 0 ? `${h}h` : `${h}h ${String(m).padStart(2, '0')}min`
  }
  const d = Math.floor(total / (60 * 24))
  const h = Math.round((total % (60 * 24)) / 60)
  return h === 0 ? `${d}d` : `${d}d ${h}h`
}

/**
 * Datas da API vêm como "AAAA-MM-DD" (sem horário). Interpretadas em UTC e formatadas em UTC para
 * que o fuso do navegador nunca desloque o dia (new Date("2026-01-05") em UTC-3 viraria 04/01).
 */
function dayToDate(day: string): Date {
  const [y, m, d] = day.split('-').map(Number)
  return new Date(Date.UTC(y, m - 1, d))
}

export const formatDayShort = (day: string): string =>
  dayToDate(day).toLocaleDateString('pt-BR', { day: '2-digit', month: '2-digit', timeZone: 'UTC' })

export const formatDayLong = (day: string): string =>
  dayToDate(day).toLocaleDateString('pt-BR', {
    weekday: 'short', day: '2-digit', month: '2-digit', year: 'numeric', timeZone: 'UTC',
  })

/** Data local -> "AAAA-MM-DD" (sem passar por UTC, que poderia trocar o dia perto da meia-noite). */
export function toIsoDay(date: Date): string {
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  return `${y}-${m}-${d}`
}
