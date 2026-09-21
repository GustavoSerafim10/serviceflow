import type { ReactNode } from 'react'

export interface Column<T> {
  header: string
  cell: (row: T) => ReactNode
}

interface Props<T> {
  caption: string
  columns: Column<T>[]
  rows: T[]
  rowKey: (row: T) => string | number
  empty?: string
}

/** Tabela de dados simples e semântica (caption para leitores de tela; números alinhados à direita). */
export function DataTable<T>({ caption, columns, rows, rowKey, empty = 'Sem dados no período.' }: Props<T>) {
  if (rows.length === 0) return <p className="empty">{empty}</p>

  return (
    <div className="table-wrap">
      <table className="data">
        <caption className="sr-only">{caption}</caption>
        <thead>
          <tr>
            {columns.map((c) => (
              <th key={c.header} scope="col">{c.header}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr key={rowKey(row)}>
              {columns.map((c) => (
                <td key={c.header}>{c.cell(row)}</td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
