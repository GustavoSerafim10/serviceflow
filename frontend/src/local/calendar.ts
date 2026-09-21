const MINUTE = 60_000
const isBusinessDay = (d: Date) => d.getDay() >= 1 && d.getDay() <= 5

/**
 * Soma minutos ÚTEIS (08:00–18:00, segunda a sexta, no fuso do navegador) a um instante: a mesma regra do
 * BusinessCalendar do backend. Ex: sexta 17:00 + 120 min úteis = segunda 09:00. Usado no prazo de SLA de
 * prioridades marcadas como "horário comercial".
 */
export function addBusinessMinutes(from: number, minutes: number): number {
  const at = (base: Date, hour: number) => new Date(base.getFullYear(), base.getMonth(), base.getDate(), hour, 0, 0, 0)
  const nextStart = (base: Date) => {
    const d = new Date(base.getFullYear(), base.getMonth(), base.getDate() + 1, 8, 0, 0, 0)
    while (!isBusinessDay(d)) d.setDate(d.getDate() + 1)
    return d
  }

  let current = new Date(from)
  if (!isBusinessDay(current) || current >= at(current, 18)) current = nextStart(current)
  else if (current < at(current, 8)) current = at(current, 8)

  let remaining = minutes
  for (;;) {
    const available = (at(current, 18).getTime() - current.getTime()) / MINUTE
    if (remaining <= available) return current.getTime() + remaining * MINUTE
    remaining -= available
    current = nextStart(current)
  }
}

/** Prazo de SLA: tempo corrido (24x7) ou só horas úteis, conforme a regra da prioridade. */
export function dueAt(openedAt: number, resolutionMinutes: number, businessHours: boolean): number {
  return businessHours ? addBusinessMinutes(openedAt, resolutionMinutes) : openedAt + resolutionMinutes * MINUTE
}
