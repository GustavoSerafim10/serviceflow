import { useState, type ReactNode } from 'react'

interface Props {
  title: string
  subtitle?: string
  /** O gráfico. */
  chart: ReactNode
  /** O gêmeo em tabela: equivalente completo e acessível do gráfico (nada fica só no tooltip). */
  table: ReactNode
  note?: string
}

/** Cartão de gráfico com alternância "gráfico / tabela". */
export function ChartCard({ title, subtitle, chart, table, note }: Props) {
  const [asTable, setAsTable] = useState(false)

  return (
    <section className="card" aria-label={title}>
      <div className="card-head">
        <div>
          <h2 className="card-title">{title}</h2>
          {subtitle && <p className="card-sub">{subtitle}</p>}
        </div>
        <button type="button" className="view-toggle" aria-pressed={asTable} onClick={() => setAsTable((v) => !v)}>
          {asTable ? 'Ver gráfico' : 'Ver tabela'}
        </button>
      </div>
      {asTable ? table : chart}
      {note && <p className="card-note">{note}</p>}
    </section>
  )
}
