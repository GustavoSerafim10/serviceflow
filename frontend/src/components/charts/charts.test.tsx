import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { BarChart } from './BarChart'
import { LineChart } from './LineChart'

const series = [
  { key: 'a', label: 'Abertos', color: 'var(--series-1)' },
  { key: 'b', label: 'Resolvidos', color: 'var(--series-2)' },
]
const points = [
  { x: '2026-09-19', values: { a: 2, b: 1 } },
  { x: '2026-09-20', values: { a: 0, b: 2 } },
  { x: '2026-09-21', values: { a: 6, b: 1 } },
]
const props = {
  points, series, ariaLabel: 'Chamados por dia',
  formatXShort: (x: string) => x.slice(5), formatXLong: (x: string) => `dia ${x}`,
}

describe('LineChart', () => {
  it('mostra legenda (2+ séries) e desenha uma linha de 2px por série', () => {
    const { container } = render(<LineChart {...props} />)

    expect(screen.getByLabelText('Legenda')).toHaveTextContent('Abertos')
    expect(screen.getByLabelText('Legenda')).toHaveTextContent('Resolvidos')
    const paths = container.querySelectorAll('svg path')
    expect(paths).toHaveLength(2)
    paths.forEach((p) => expect(p).toHaveAttribute('stroke-width', '2'))
  })

  it('uma série só não tem legenda (o título já diz o que é)', () => {
    render(<LineChart {...props} series={[series[0]]} />)
    expect(screen.queryByLabelText('Legenda')).toBeNull()
  })

  it('teclado: as setas percorrem os dias e o tooltip lista TODAS as séries naquele X', () => {
    render(<LineChart {...props} />)
    const chart = screen.getByRole('group', { name: /Chamados por dia/ })

    fireEvent.keyDown(chart, { key: 'ArrowRight' })
    let tooltip = document.querySelector('.tooltip') as HTMLElement
    expect(tooltip).toHaveTextContent('dia 2026-09-19')
    expect(tooltip).toHaveTextContent('2Abertos')
    expect(tooltip).toHaveTextContent('1Resolvidos')

    fireEvent.keyDown(chart, { key: 'End' })
    tooltip = document.querySelector('.tooltip') as HTMLElement
    expect(tooltip).toHaveTextContent('dia 2026-09-21')
    expect(tooltip).toHaveTextContent('6Abertos')

    fireEvent.keyDown(chart, { key: 'Escape' })
    expect(document.querySelector('.tooltip')).toBeNull()
  })

  it('anuncia o ponto ativo para leitores de tela', () => {
    render(<LineChart {...props} />)
    fireEvent.keyDown(screen.getByRole('group', { name: /Chamados por dia/ }), { key: 'Home' })

    expect(document.querySelector('[aria-live="polite"]')).toHaveTextContent('dia 2026-09-19: Abertos 2, Resolvidos 1')
  })

  it('não quebra sem dados', () => {
    const { container } = render(<LineChart {...props} points={[]} />)
    expect(container.querySelector('svg')).toBeInTheDocument()
  })

  it('rótulos diretos só aparecem quando as pontas das linhas não colidem', () => {
    // pontas em 6 e 1 (bem separadas): rótulos diretos presentes
    const { container, rerender } = render(<LineChart {...props} />)
    expect(container.querySelectorAll('text.direct')).toHaveLength(2)

    // pontas iguais (colidiriam): ficam só a legenda e o tooltip
    rerender(<LineChart {...props} points={[{ x: '2026-09-19', values: { a: 3, b: 3 } }]} />)
    expect(container.querySelectorAll('text.direct')).toHaveLength(0)
  })
})

describe('BarChart', () => {
  const rows = [
    { key: 'p1', label: 'P1 · Crítico', value: 15, color: 'var(--prio-1)', details: [{ value: '15', label: 'abertos' }, { value: '80,0%', label: 'SLA cumprido' }] },
    { key: 'p3', label: 'P3 · Médio', value: 53, color: 'var(--prio-3)', details: [{ value: '53', label: 'abertos' }] },
  ]

  it('cada barra tem o valor na ponta e largura proporcional (a maior nunca ocupa a trilha inteira)', () => {
    const { container } = render(<BarChart rows={rows} ariaLabel="Por prioridade" />)

    expect(screen.getByRole('list', { name: 'Por prioridade' })).toBeInTheDocument()
    expect(screen.getAllByRole('listitem')).toHaveLength(2)
    const bars = container.querySelectorAll<HTMLElement>('.bar')
    expect(parseFloat(bars[1].style.width)).toBeCloseTo(84, 0)           // a maior: 84% da trilha (sobra espaço p/ o valor)
    expect(parseFloat(bars[0].style.width)).toBeCloseTo((15 / 53) * 84, 0)
    expect(screen.getByText('53')).toBeInTheDocument()
  })

  it('foco (teclado) mostra o mesmo tooltip do hover, com o valor em destaque', () => {
    render(<BarChart rows={rows} ariaLabel="Por prioridade" />)

    fireEvent.focus(screen.getAllByRole('listitem')[0])

    const tooltip = document.querySelector('.tooltip') as HTMLElement
    expect(tooltip).toHaveTextContent('P1 · Crítico')
    expect(tooltip).toHaveTextContent('80,0%SLA cumprido')
    expect(tooltip.querySelector('.tooltip-value')).toHaveTextContent('15')

    fireEvent.blur(screen.getAllByRole('listitem')[0])
    expect(document.querySelector('.tooltip')).toBeNull()
  })

  it('rótulos vindos da API são texto, nunca HTML', () => {
    const evil = [{ ...rows[0], key: 'x', label: '<img src=x onerror=alert(1)>' }]
    const { container } = render(<BarChart rows={evil} ariaLabel="x" />)

    expect(container.querySelector('img')).toBeNull()
    expect(screen.getByText('<img src=x onerror=alert(1)>')).toBeInTheDocument()
  })
})
