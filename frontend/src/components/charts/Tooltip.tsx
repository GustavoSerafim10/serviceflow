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
  /** Altura do gráfico (em px). Sem ela, o tooltip só encosta em 0 no topo — pode transbordar por baixo
      do cartão (ex: a última linha de um gráfico de barras) e sobrepor o conteúdo seguinte da página. */
  containerHeight?: number
  title: string
  rows: TooltipRow[]
}

const WIDTH = 176
// Estimativa de altura (título + uma linha por dado): suficiente para decidir se cabe sem medir o DOM.
const TITLE_H = 26
const ROW_H = 20

/**
 * Tooltip de gráfico. Os valores LIDERAM (negrito, alto contraste) e os rótulos seguem em cor
 * secundária: o leitor já sabe qual série está olhando e quer o número. As chaves são traços
 * curtos na cor da série (não caixas). Todo texto entra como nó de texto do React (escapado):
 * rótulos de categoria vêm da API e nunca devem ser tratados como HTML.
 *
 * Tooltips melhoram, nunca condicionam: todo valor também existe na visão de tabela.
 */
export function Tooltip({ x, y, containerWidth, containerHeight, title, rows }: Props) {
  const flip = x + 14 + WIDTH > containerWidth
  const left = flip ? x - 14 - WIDTH : x + 14

  // Mesma ideia do flip horizontal, mas como grude no teto: nunca deixa o tooltip passar do fundo do
  // gráfico (ele fica na área de um cartão sem overflow:hidden — sem isso, extrapolava para o cartão de baixo).
  const height = TITLE_H + rows.length * ROW_H
  const top = containerHeight != null ? Math.max(0, Math.min(y, containerHeight - height)) : Math.max(0, y)

  return (
    <div className="tooltip" style={{ left: Math.max(0, left), top, width: WIDTH }} role="presentation">
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
