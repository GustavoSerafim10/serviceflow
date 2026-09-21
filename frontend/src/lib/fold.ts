import type { CategoryStat } from '../api/types'

/**
 * Mantém as N maiores categorias (por chamados abertos) e dobra a cauda numa linha "Outras".
 * Regra do guia de visualização: mais de ~7 classes com significado viram tabela; no gráfico,
 * a cauda é dobrada (nunca se inventam mais cores). A tabela-gêmea continua listando todas.
 *
 * As médias da linha dobrada são ponderadas pela quantidade de resolvidos de cada categoria.
 */
export function foldCategories(items: CategoryStat[], keep = 7): CategoryStat[] {
  if (items.length <= keep + 1) return items // dobrar UMA só categoria em "Outras" seria absurdo

  const head = items.slice(0, keep)
  const tail = items.slice(keep)

  const resolved = sum(tail, (c) => c.resolved)
  const within = sum(tail, (c) => c.resolvedWithinSla)
  const weightedMinutes = sum(tail, (c) => (c.avgResolutionMinutes ?? 0) * c.resolved)

  const other: CategoryStat = {
    categoryId: -1,
    categoryName: 'Outras',
    opened: sum(tail, (c) => c.opened),
    resolved,
    resolvedWithinSla: within,
    slaComplianceRate: resolved === 0 ? null : within / resolved,
    avgResolutionMinutes: resolved === 0 ? null : weightedMinutes / resolved,
  }
  return [...head, other]
}

function sum<T>(items: T[], pick: (item: T) => number): number {
  return items.reduce((total, item) => total + pick(item), 0)
}
