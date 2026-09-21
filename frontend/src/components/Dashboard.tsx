import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import {
  getByCategory, getByPriority, getByTechnician, getSuggestionMetrics, getSummary, getTimeline,
} from '../api/endpoints'
import type { Period, User } from '../api/types'
import { foldCategories } from '../lib/fold'
import { formatDayLong, formatDayShort, formatInt, formatMinutes, formatRate } from '../lib/format'
import { PRIORITY_INFO } from '../lib/labels'
import { paths } from '../lib/router'
import { STANDALONE } from '../local/mode'
import { periodForPreset, validatePeriod, type PeriodPreset } from '../lib/period'
import { BarChart, type BarRow } from './charts/BarChart'
import { LineChart, type LineSeries } from './charts/LineChart'
import { ChartCard } from './ChartCard'
import { DataTable } from './DataTable'
import { Kpis } from './Kpis'
import { PeriodFilter } from './PeriodFilter'

const TIMELINE_SERIES: LineSeries[] = [
  { key: 'opened', label: 'Abertos', color: 'var(--series-1)' },
  { key: 'resolved', label: 'Resolvidos', color: 'var(--series-2)' },
]

const STATUS_LABEL: Record<string, string> = {
  ABERTO: 'Abertos',
  EM_ATENDIMENTO: 'Em atendimento',
  RESOLVIDO: 'Resolvidos',
  FECHADO: 'Fechados',
}

function Meter({ rate }: { rate: number | null }) {
  if (rate == null) return <>—</>
  return (
    <>
      <span className="meter meter-inline" aria-hidden="true"><span style={{ width: `${rate * 100}%` }} /></span>
      {formatRate(rate)}
    </>
  )
}

export function Dashboard({ user }: { user: User }) {
  const [preset, setPreset] = useState<PeriodPreset>('30d')
  const [custom, setCustom] = useState<Period>(() => periodForPreset('30d'))

  const period = preset === 'custom' ? custom : periodForPreset(preset)
  const periodError = preset === 'custom' ? validatePeriod(custom) : null
  const enabled = periodError === null

  const options = { placeholderData: keepPreviousData, enabled } // refetch mantém o quadro anterior
  const summary = useQuery({ queryKey: ['summary', period], queryFn: () => getSummary(period), ...options })
  const timeline = useQuery({ queryKey: ['timeline', period], queryFn: () => getTimeline(period), ...options })
  const byPriority = useQuery({ queryKey: ['by-priority', period], queryFn: () => getByPriority(period), ...options })
  const byCategory = useQuery({ queryKey: ['by-category', period], queryFn: () => getByCategory(period), ...options })
  const byTechnician = useQuery({ queryKey: ['by-technician', period], queryFn: () => getByTechnician(period), ...options })
  const suggestions = useQuery({
    queryKey: ['suggestion-metrics'], queryFn: getSuggestionMetrics, enabled: user.role === 'ADMIN' && !STANDALONE,
  })
  const noTicketsYet = summary.data !== undefined && summary.data.current.total === 0

  const queries = [summary, timeline, byPriority, byCategory, byTechnician]
  const failed = queries.find((q) => q.isError)
  const fetching = queries.some((q) => q.isFetching)

  return (
    <>
      <PeriodFilter preset={preset} custom={custom} error={periodError} onPreset={setPreset} onCustom={setCustom} />

      {failed && (
        <div className="load-error" role="alert">
          Não foi possível carregar os indicadores{failed.error instanceof Error ? `: ${failed.error.message}` : '.'}{' '}
          <button className="btn" type="button" onClick={() => queries.forEach((q) => void q.refetch())}>Tentar novamente</button>
        </div>
      )}

      {noTicketsYet && (
        <p className="local-banner" role="note">
          Ainda não há chamados. Os indicadores aparecem conforme você abre e atende chamados:{' '}
          <a href={paths.new}>abrir o primeiro chamado</a>.
        </p>
      )}

      <div className="dashboard" data-stale={fetching && !!summary.data}>
        <Kpis summary={summary.data} />

        {/* ------------------------------------------------ série diária */}
        <ChartCard
          title="Chamados por dia"
          subtitle="Abertos e resolvidos em cada dia do período"
          chart={
            timeline.data ? (
              <LineChart
                points={timeline.data.items.map((p) => ({ x: p.day, values: { opened: p.opened, resolved: p.resolved } }))}
                series={TIMELINE_SERIES}
                ariaLabel="Gráfico de linhas: chamados abertos e resolvidos por dia"
                formatXShort={formatDayShort}
                formatXLong={formatDayLong}
              />
            ) : <p className="empty">Carregando…</p>
          }
          table={
            <DataTable
              caption="Chamados abertos e resolvidos por dia"
              rows={timeline.data?.items ?? []}
              rowKey={(p) => p.day}
              columns={[
                { header: 'Dia', cell: (p) => formatDayLong(p.day) },
                { header: 'Abertos', cell: (p) => formatInt(p.opened) },
                { header: 'Resolvidos', cell: (p) => formatInt(p.resolved) },
              ]}
            />
          }
        />

        <div className="grid-2">
          {/* ------------------------------------------------ por prioridade (ordinal) */}
          <ChartCard
            title="Abertos por prioridade"
            subtitle="Do mais urgente ao menos urgente"
            chart={
              byPriority.data ? (
                <BarChart
                  ariaLabel="Chamados abertos por prioridade"
                  rows={byPriority.data.items.map<BarRow>((p) => ({
                    key: p.priority,
                    label: PRIORITY_INFO[p.priority].label,
                    value: p.opened,
                    color: PRIORITY_INFO[p.priority].color,
                    details: [
                      { value: formatInt(p.opened), label: 'abertos' },
                      { value: formatInt(p.resolved), label: 'resolvidos' },
                      { value: formatRate(p.slaComplianceRate), label: 'SLA cumprido' },
                      { value: formatMinutes(p.avgResolutionMinutes), label: 'tempo médio' },
                    ],
                  }))}
                />
              ) : <p className="empty">Carregando…</p>
            }
            table={
              <DataTable
                caption="Indicadores por prioridade"
                rows={byPriority.data?.items ?? []}
                rowKey={(p) => p.priority}
                columns={[
                  { header: 'Prioridade', cell: (p) => PRIORITY_INFO[p.priority].label },
                  { header: 'Abertos', cell: (p) => formatInt(p.opened) },
                  { header: 'Resolvidos', cell: (p) => formatInt(p.resolved) },
                  { header: 'SLA', cell: (p) => formatRate(p.slaComplianceRate) },
                  { header: 'Tempo médio', cell: (p) => formatMinutes(p.avgResolutionMinutes) },
                ]}
              />
            }
          />

          {/* ------------------------------------------------ por categoria (nominal: uma cor só) */}
          <ChartCard
            title="Abertos por categoria"
            subtitle="As 7 maiores; as demais somadas em “Outras”"
            chart={
              byCategory.data ? (
                byCategory.data.items.length === 0 ? <p className="empty">Sem chamados abertos no período.</p> : (
                  <BarChart
                    ariaLabel="Chamados abertos por categoria"
                    rows={foldCategories(byCategory.data.items).map<BarRow>((c) => ({
                      key: String(c.categoryId),
                      label: c.categoryName,
                      value: c.opened,
                      color: 'var(--series-1)',
                      details: [
                        { value: formatInt(c.opened), label: 'abertos' },
                        { value: formatInt(c.resolved), label: 'resolvidos' },
                        { value: formatRate(c.slaComplianceRate), label: 'SLA cumprido' },
                        { value: formatMinutes(c.avgResolutionMinutes), label: 'tempo médio' },
                      ],
                    }))}
                  />
                )
              ) : <p className="empty">Carregando…</p>
            }
            table={
              <DataTable
                caption="Indicadores por categoria"
                rows={byCategory.data?.items ?? []}
                rowKey={(c) => c.categoryId}
                columns={[
                  { header: 'Categoria', cell: (c) => c.categoryName },
                  { header: 'Abertos', cell: (c) => formatInt(c.opened) },
                  { header: 'Resolvidos', cell: (c) => formatInt(c.resolved) },
                  { header: 'SLA', cell: (c) => formatRate(c.slaComplianceRate) },
                  { header: 'Tempo médio', cell: (c) => formatMinutes(c.avgResolutionMinutes) },
                ]}
              />
            }
          />
        </div>

        <div className="grid-2">
          {/* ------------------------------------------------ técnicos: tabela (várias colunas com significado) */}
          <section className="card" aria-label="Desempenho por técnico">
            <div className="card-head">
              <div>
                <h2 className="card-title">Desempenho por técnico</h2>
                <p className="card-sub">Resolvidos no período, por técnico atualmente responsável</p>
              </div>
            </div>
            <DataTable
              caption="Desempenho por técnico"
              rows={byTechnician.data?.items ?? []}
              rowKey={(t) => t.technicianId}
              empty={byTechnician.data ? 'Nenhum chamado resolvido no período.' : 'Carregando…'}
              columns={[
                { header: 'Técnico', cell: (t) => t.technicianName },
                { header: 'Resolvidos', cell: (t) => formatInt(t.resolved) },
                { header: 'SLA cumprido', cell: (t) => <Meter rate={t.slaComplianceRate} /> },
                { header: 'Tempo médio', cell: (t) => formatMinutes(t.avgResolutionMinutes) },
                { header: 'Em atendimento', cell: (t) => formatInt(t.inProgressNow) },
              ]}
            />
          </section>

          {/* ------------------------------------------------ retrato de agora */}
          <section className="card" aria-label="Situação atual">
            <div className="card-head">
              <div>
                <h2 className="card-title">Situação atual</h2>
                <p className="card-sub">Retrato de agora, independente do período</p>
              </div>
            </div>
            {summary.data ? (
              <dl className="now-list">
                {Object.entries(summary.data.current.byStatus).map(([status, total]) => (
                  <div key={status}>
                    <dt>{STATUS_LABEL[status] ?? status}</dt>
                    <dd>{formatInt(total)}</dd>
                  </div>
                ))}
              </dl>
            ) : <p className="empty">Carregando…</p>}
            <p className="card-note">Chamados cancelados não são contados.</p>
          </section>
        </div>

        {/* ------------------------------------------------ qualidade das sugestões (ADMIN) */}
        {user.role === 'ADMIN' && !STANDALONE && (
          <section className="card" aria-label="Qualidade das sugestões automáticas">
            <div className="card-head">
              <div>
                <h2 className="card-title">Qualidade das sugestões automáticas</h2>
                <p className="card-sub">Quanto os usuários aceitam do que o modelo sugere (todo o histórico)</p>
              </div>
            </div>
            <DataTable
              caption="Taxa de aceitação das sugestões por versão do modelo"
              rows={suggestions.data?.models ?? []}
              rowKey={(m) => m.modelVersion}
              empty={suggestions.isPending ? 'Carregando…' : 'Ainda não há sugestões registradas.'}
              columns={[
                { header: 'Versão do modelo', cell: (m) => m.modelVersion },
                { header: 'Oferecidas', cell: (m) => formatInt(m.offered) },
                { header: 'Viraram chamado', cell: (m) => formatInt(m.usedInTickets) },
                { header: 'Categoria aceita', cell: (m) => <Meter rate={m.categoryAcceptanceRate} /> },
                { header: 'Prioridade aceita', cell: (m) => <Meter rate={m.priorityAcceptanceRate} /> },
              ]}
            />
            <p className="card-note">Aceitação = sugestões aceitas ÷ sugestões que viraram chamado. Categoria e prioridade são avaliadas separadamente.</p>
          </section>
        )}
      </div>
    </>
  )
}
