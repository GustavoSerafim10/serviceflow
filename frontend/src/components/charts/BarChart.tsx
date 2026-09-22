import { useRef, useState, type FocusEvent, type PointerEvent } from 'react'
import { formatInt } from '../../lib/format'
import { Tooltip, type TooltipRow } from './Tooltip'
import { useContainerWidth } from './useContainerWidth'

export interface BarRow {
  key: string
  label: string
  value: number
  /** Cor da barra (marca), ex: 'var(--series-1)'. */
  color: string
  /** Linhas do tooltip: o detalhe que não cabe no rótulo. */
  details: { label: string; value: string }[]
}

interface Props {
  rows: BarRow[]
  ariaLabel: string
}

// A barra mais longa ocupa no máximo esta fração da trilha: sobra espaço para o valor na PONTA
// (rótulo fora da barra: nunca é cortado nem sobrescrito por uma barra curta).
const MAX_FRACTION = 0.84

/**
 * Barras horizontais para comparar magnitude entre categorias. Cada barra é o seu próprio alvo de
 * hover/foco (sem crosshair) e "levanta" ao passar o mouse. Espessura <= 24px, ponta de dados
 * arredondada em 4px, base reta, valor na ponta.
 */
export function BarChart({ rows, ariaLabel }: Props) {
  const { ref, width } = useContainerWidth<HTMLDivElement>()
  const chartRef = useRef<HTMLDivElement | null>(null)
  const [hover, setHover] = useState<{ index: number; x: number; y: number; containerHeight: number } | null>(null)

  const max = Math.max(1, ...rows.map((r) => r.value))

  // ref.current só é lido aqui dentro (dispara em pointermove/focus, nunca durante a renderização) —
  // ler um ref no corpo do componente é o antipadrão que o React avisa (o valor pode ficar desatualizado).
  function place(index: number, el: HTMLElement, clientX?: number, clientY?: number) {
    const box = chartRef.current?.getBoundingClientRect()
    if (!box) return
    if (clientX != null && clientY != null) {
      setHover({ index, x: clientX - box.left, y: clientY - box.top + 12, containerHeight: box.height })
    } else {
      const r = el.getBoundingClientRect() // foco por teclado: ancora na linha
      setHover({ index, x: r.left - box.left + r.width * 0.55, y: r.top - box.top + r.height, containerHeight: box.height })
    }
  }

  const active = hover ? rows[hover.index] : null
  const tooltipRows: TooltipRow[] = active ? active.details : []

  return (
    <div
      className="chart"
      ref={(el) => {
        ref.current = el
        chartRef.current = el
      }}
    >
      <div className="bars" role="list" aria-label={ariaLabel}>
        {rows.map((row, i) => (
          <div
            key={row.key}
            className="bar-row"
            role="listitem"
            tabIndex={0}
            aria-label={`${row.label}: ${formatInt(row.value)}`}
            onPointerMove={(e: PointerEvent<HTMLDivElement>) => place(i, e.currentTarget, e.clientX, e.clientY)}
            onPointerLeave={() => setHover(null)}
            onFocus={(e: FocusEvent<HTMLDivElement>) => place(i, e.currentTarget)}
            onBlur={() => setHover(null)}
          >
            <span className="bar-label" title={row.label}>{row.label}</span>
            <span className="bar-track">
              {/* Sem barra (nem um tiquinho) quando o valor é zero: um traço colorido em "0" mais atrapalha
                  do que ajuda a comparar magnitude — o número já diz tudo. */}
              {row.value > 0 && (
                <span className="bar" style={{ width: `${(row.value / max) * MAX_FRACTION * 100}%`, background: row.color }} />
              )}
              <span className="bar-value">{formatInt(row.value)}</span>
            </span>
          </div>
        ))}
      </div>

      {active && hover && (
        <Tooltip
          x={hover.x} y={hover.y} containerWidth={width} containerHeight={hover.containerHeight}
          title={active.label} rows={tooltipRows}
        />
      )}
    </div>
  )
}
