import type { Period } from '../api/types'
import { PRESETS, type PeriodPreset } from '../lib/period'
import { CheckIcon } from './Icons'

interface Props {
  preset: PeriodPreset
  custom: Period
  error: string | null
  onPreset: (preset: PeriodPreset) => void
  onCustom: (period: Period) => void
}

/**
 * Filtro de período: UMA linha acima de tudo que ele escopa (nenhum gráfico tem filtro próprio).
 * Presets primeiro (ninguém quer brigar com um calendário para "últimos 30 dias"), intervalo
 * personalizado depois. A seleção é marcada por um check + negrito, não só por cor.
 */
export function PeriodFilter({ preset, custom, error, onPreset, onCustom }: Props) {
  return (
    <div className="filters">
      <div className="segmented" role="group" aria-label="Período">
        {[...PRESETS, { id: 'custom' as const, label: 'Personalizado' }].map((p) => (
          <button key={p.id} type="button" aria-pressed={preset === p.id} onClick={() => onPreset(p.id)}>
            {preset === p.id ? <CheckIcon className="check" /> : <span className="check" />}
            {p.label}
          </button>
        ))}
      </div>

      {preset === 'custom' && (
        <span className="custom-range">
          <label>
            <span className="sr-only">Data inicial</span>
            <input className="date-input" type="date" value={custom.from} max={custom.to || undefined}
              onChange={(e) => onCustom({ ...custom, from: e.target.value })} />
          </label>
          até
          <label>
            <span className="sr-only">Data final</span>
            <input className="date-input" type="date" value={custom.to} min={custom.from || undefined}
              onChange={(e) => onCustom({ ...custom, to: e.target.value })} />
          </label>
        </span>
      )}

      {error && <span className="filter-error" role="alert">{error}</span>}
      <span className="filter-hint">Datas inclusivas, no fuso da empresa</span>
    </div>
  )
}
