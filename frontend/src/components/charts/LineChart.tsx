import { useState, type KeyboardEvent, type PointerEvent } from 'react'
import { formatInt } from '../../lib/format'
import { niceScale } from '../../lib/scale'
import { Tooltip } from './Tooltip'
import { useContainerWidth } from './useContainerWidth'

export interface LineSeries {
  key: string
  label: string
  /** Cor da MARCA (linha/marcador), ex: 'var(--series-1)'. Textos nunca usam a cor da série. */
  color: string
}

export interface LinePoint {
  x: string
  values: Record<string, number>
}

interface Props {
  points: LinePoint[]
  series: LineSeries[]
  /** Descrição do gráfico para leitores de tela. */
  ariaLabel: string
  formatXShort: (x: string) => string
  formatXLong: (x: string) => string
}

const HEIGHT = 264 // inclui a faixa do eixo X (não a exclui: evita rolagem interna no cartão)
const MARGIN = { top: 12, right: 92, bottom: 30, left: 40 }

/**
 * Gráfico de linhas de um eixo só (nunca dois eixos). Especificações do guia: linha de 2px com
 * junta redonda, marcador de fim de 8px com anel de 2px na cor da superfície, grade em hairline
 * sólida e discreta, crosshair que acha o X mais próximo, tooltip com todas as séries naquele X e
 * o mesmo comportamento por teclado (setas). Identidade: legenda sempre presente (2+ séries) e
 * rótulo direto no fim das linhas quando não colidem.
 */
export function LineChart({ points, series, ariaLabel, formatXShort, formatXLong }: Props) {
  const { ref, width } = useContainerWidth<HTMLDivElement>()
  const [active, setActive] = useState<number | null>(null)

  const n = points.length
  const plotW = Math.max(1, width - MARGIN.left - MARGIN.right)
  const plotH = HEIGHT - MARGIN.top - MARGIN.bottom

  const maxValue = Math.max(0, ...points.flatMap((p) => series.map((s) => p.values[s.key] ?? 0)))
  const scale = niceScale(maxValue)

  const xAt = (i: number) => MARGIN.left + (n <= 1 ? plotW / 2 : (i * plotW) / (n - 1))
  const yAt = (v: number) => MARGIN.top + plotH - (v / scale.max) * plotH

  const pathFor = (key: string) =>
    points.map((p, i) => `${i === 0 ? 'M' : 'L'}${xAt(i).toFixed(1)},${yAt(p.values[key] ?? 0).toFixed(1)}`).join(' ')

  // No máximo ~8 rótulos no eixo X, sempre incluindo o primeiro.
  const labelEvery = Math.max(1, Math.ceil(n / 8))

  // Rótulos diretos: só se as extremidades das linhas estiverem separadas o bastante (sem empilhar/deslocar).
  const lastEnds = series.map((s) => ({ s, y: yAt(points[n - 1]?.values[s.key] ?? 0) }))
  const canDirectLabel = lastEnds.every((a, i) => lastEnds.every((b, j) => i === j || Math.abs(a.y - b.y) >= 16))

  function indexFromPointer(e: PointerEvent<SVGRectElement>): number {
    const rect = e.currentTarget.ownerSVGElement!.getBoundingClientRect()
    const px = e.clientX - rect.left
    const raw = n <= 1 ? 0 : Math.round(((px - MARGIN.left) / plotW) * (n - 1))
    return Math.min(n - 1, Math.max(0, raw))
  }

  function onKeyDown(e: KeyboardEvent<HTMLDivElement>) {
    if (n === 0) return
    const current = active ?? (e.key === 'ArrowLeft' ? n : -1)
    if (e.key === 'ArrowRight') setActive(Math.min(n - 1, current + 1))
    else if (e.key === 'ArrowLeft') setActive(Math.max(0, current - 1))
    else if (e.key === 'Home') setActive(0)
    else if (e.key === 'End') setActive(n - 1)
    else if (e.key === 'Escape') setActive(null)
    else return
    e.preventDefault()
  }

  const activePoint = active != null ? points[active] : null
  const rows = activePoint
    ? series.map((s) => ({ color: s.color, value: formatInt(activePoint.values[s.key] ?? 0), label: s.label }))
    : []

  return (
    <div className="chart" ref={ref}>
      {series.length >= 2 && (
        <ul className="legend" aria-label="Legenda">
          {series.map((s) => (
            <li key={s.key}>
              <span className="key-line" style={{ background: s.color }} />
              {s.label}
            </li>
          ))}
        </ul>
      )}

      <div
        role="group"
        aria-label={`${ariaLabel}. Use as setas do teclado para percorrer os dias.`}
        tabIndex={0}
        onKeyDown={onKeyDown}
        onBlur={() => setActive(null)}
        className="line-chart"
      >
        <svg width={width} height={HEIGHT} aria-hidden="true" className="line-chart">
          {scale.ticks.map((t) => (
            <g key={t}>
              <line className={t === 0 ? 'baseline' : 'grid'} x1={MARGIN.left} x2={MARGIN.left + plotW} y1={yAt(t)} y2={yAt(t)} />
              <text x={MARGIN.left - 8} y={yAt(t) + 4} textAnchor="end">{formatInt(t)}</text>
            </g>
          ))}

          {points.map((p, i) =>
            i % labelEvery === 0 ? (
              <text key={p.x} x={xAt(i)} y={HEIGHT - 8} textAnchor="middle">{formatXShort(p.x)}</text>
            ) : null,
          )}

          {activePoint && active != null && (
            <line className="crosshair" x1={xAt(active)} x2={xAt(active)} y1={MARGIN.top} y2={MARGIN.top + plotH} />
          )}

          {series.map((s) => (
            <path
              key={s.key}
              d={pathFor(s.key)}
              fill="none"
              stroke={s.color}
              strokeWidth={2}
              strokeLinejoin="round"
              strokeLinecap="round"
            />
          ))}

          {/* marcador de fim (>= 8px) com anel de 2px na cor da superfície */}
          {n > 0 && series.map((s) => (
            <circle
              key={s.key}
              cx={xAt(active ?? n - 1)}
              cy={yAt(points[active ?? n - 1]?.values[s.key] ?? 0)}
              r={4}
              fill={s.color}
              stroke="var(--surface)"
              strokeWidth={2}
            />
          ))}

          {canDirectLabel && n > 0 && active == null && lastEnds.map(({ s, y }) => (
            <text key={s.key} className="direct" x={xAt(n - 1) + 12} y={y + 4}>{s.label}</text>
          ))}

          {/* alvo de ponteiro MAIOR que a marca: cobre toda a área do gráfico */}
          <rect
            x={MARGIN.left}
            y={MARGIN.top}
            width={plotW}
            height={plotH}
            fill="transparent"
            onPointerMove={(e) => setActive(indexFromPointer(e))}
            onPointerLeave={() => setActive(null)}
          />
        </svg>
      </div>

      {activePoint && active != null && (
        <Tooltip
          x={xAt(active)} y={MARGIN.top + 28} containerWidth={width} containerHeight={HEIGHT}
          title={formatXLong(activePoint.x)} rows={rows}
        />
      )}
      <div className="sr-only" aria-live="polite">
        {activePoint ? `${formatXLong(activePoint.x)}: ${rows.map((r) => `${r.label} ${r.value}`).join(', ')}` : ''}
      </div>
    </div>
  )
}
