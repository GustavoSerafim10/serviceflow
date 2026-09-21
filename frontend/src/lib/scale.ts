/** Escala "bonita" para contagens: passos de 1, 2, 5 × 10ⁿ, sempre começando em 0. */
export function niceScale(max: number, targetTicks = 4): { max: number; ticks: number[] } {
  if (max <= 0) return { max: targetTicks, ticks: Array.from({ length: targetTicks + 1 }, (_, i) => i) }
  const raw = max / targetTicks
  const magnitude = 10 ** Math.floor(Math.log10(raw))
  const normalized = raw / magnitude
  const step = Math.max(1, (normalized <= 1 ? 1 : normalized <= 2 ? 2 : normalized <= 5 ? 5 : 10) * magnitude)
  const top = Math.ceil(max / step) * step
  const ticks: number[] = []
  for (let v = 0; v <= top; v += step) ticks.push(v)
  return { max: top, ticks }
}
