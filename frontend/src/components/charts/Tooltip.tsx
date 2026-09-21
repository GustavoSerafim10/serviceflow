export interface TooltipRow {
  /** Cor da chave de linha; sem cor = linha sem chave (tooltips de barras). */
  color?: string
  value: string
  label: string
}

interface Props {
  x: number
  y: number
  containerWidth: number
  title: string
  rows: TooltipRow[]
}

const WIDTH = 176

/**
 * Tooltip de gráfico. Os valores LIDERAM (negrito, alto contraste) e os rótulos seguem em cor
 * secundária: o leitor já sabe qual série está olhando e quer o número. As chaves são traços
 * curtos na cor da série (não caixas). Todo texto entra como nó de texto do React (escapado):
 * rótulos de categoria vêm da API e nunca devem ser tratados como HTML.
 *
 * Tooltips melhoram, nunca condicionam: todo valor também existe na visão de tabela.
 */
export function Tooltip({ x, y, containerWidth, title, rows }: Props) {
  const flip = x + 14 + WIDTH > containerWidth
  const left = flip ? x - 14 - WIDTH : x + 14

  return (
    <div className="tooltip" style={{ left: Math.max(0, left), top: Math.max(0, y), width: WIDTH }} role="presentation">
      <div className="tooltip-title">{title}</div>
      {rows.map((row) => (
        <div className="tooltip-row" key={row.label}>
          {row.color && <span className="key-line" style={{ background: row.color }} />}
          <span className="tooltip-value">{row.value}</span>
          <span className="tooltip-label">{row.label}</span>
        </div>
      ))}
    </div>
  )
}
