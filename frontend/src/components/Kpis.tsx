import type { Summary } from '../api/types'
import { formatInt, formatMinutes, formatRate } from '../lib/format'
import { AlertIcon, CheckIcon } from './Icons'

/**
 * Linha de indicadores. A figura-herói (a única do painel) é a conformidade de SLA: é o número que
 * a operação persegue. Os demais são "stat tiles". Sem dados ainda: traço "—", nunca um 0 enganoso.
 */
export function Kpis({ summary }: { summary: Summary | undefined }) {
  const s = summary
  const breached = s?.current.breachedOpen

  return (
    <div className="kpis" aria-busy={!s}>
      <div className="tile hero">
        <div className="tile-label">SLA cumprido</div>
        <div className="tile-value">{formatRate(s?.slaComplianceRate)}</div>
        <div className="tile-sub">
          {s ? `${formatInt(s.resolvedWithinSla)} de ${formatInt(s.resolved)} resolvidos dentro do prazo` : 'Carregando…'}
        </div>
        <div
          className="meter"
          role="meter"
          aria-label="SLA cumprido"
          aria-valuemin={0}
          aria-valuemax={100}
          aria-valuenow={s?.slaComplianceRate != null ? Math.round(s.slaComplianceRate * 100) : undefined}
        >
          <span style={{ width: `${(s?.slaComplianceRate ?? 0) * 100}%` }} />
        </div>
      </div>

      <div className="tile">
        <div className="tile-label">Abertos no período</div>
        <div className="tile-value">{s ? formatInt(s.opened) : '—'}</div>
      </div>

      <div className="tile">
        <div className="tile-label">Resolvidos no período</div>
        <div className="tile-value">{s ? formatInt(s.resolved) : '—'}</div>
      </div>

      <div className="tile">
        <div className="tile-label">Tempo médio de resolução</div>
        <div className="tile-value">{formatMinutes(s?.avgResolutionMinutes)}</div>
        <div className="tile-sub">Mediana {formatMinutes(s?.medianResolutionMinutes)}</div>
      </div>

      <div className="tile">
        <div className="tile-label">SLA estourado agora</div>
        <div className="tile-value">{breached != null ? formatInt(breached) : '—'}</div>
        {breached != null && (
          breached > 0
            ? <div className="status status-critical"><AlertIcon /> Requer atenção</div>
            : <div className="status status-good"><CheckIcon /> Tudo em dia</div>
        )}
      </div>
    </div>
  )
}
