import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { getCategories, getTickets, getUsers } from '../../api/endpoints'
import type { Priority, SlaStatus, TicketFilters, TicketStatus } from '../../api/types'
import { formatInt } from '../../lib/format'
import { PRIORITIES, PRIORITY_INFO, STATUSES, STATUS_LABEL, formatDateTime } from '../../lib/labels'
import { paths } from '../../lib/router'
import { PriorityBadge, SlaBadge, StatusBadge } from '../Badges'
import { DataTable } from '../DataTable'

function useDebounced<T>(value: T, ms = 250): T {
  const [debounced, setDebounced] = useState(value)
  useEffect(() => {
    const id = setTimeout(() => setDebounced(value), ms)
    return () => clearTimeout(id)
  }, [value, ms])
  return debounced
}

export function TicketsPage() {
  const [status, setStatus] = useState('')
  const [priority, setPriority] = useState('')
  const [categoryId, setCategoryId] = useState('')
  const [assignee, setAssignee] = useState('') // '' | 'none' | id
  const [sla, setSla] = useState('')
  const [text, setText] = useState('')
  const [page, setPage] = useState(0)
  const q = useDebounced(text)

  const filters: TicketFilters = {
    status: (status || undefined) as TicketStatus | undefined,
    priority: (priority || undefined) as Priority | undefined,
    categoryId: categoryId ? Number(categoryId) : undefined,
    assigneeId: assignee && assignee !== 'none' ? Number(assignee) : undefined,
    unassigned: assignee === 'none' ? true : undefined,
    slaStatus: (sla || undefined) as SlaStatus | undefined,
    q: q || undefined,
  }
  const hasFilters = Object.values(filters).some((v) => v !== undefined)

  const tickets = useQuery({
    queryKey: ['tickets', filters, page],
    queryFn: () => getTickets(filters, page),
    placeholderData: keepPreviousData,
  })
  const categories = useQuery({ queryKey: ['categories'], queryFn: getCategories })
  const users = useQuery({ queryKey: ['users'], queryFn: getUsers, retry: false })

  const technicians = (users.data ?? []).filter((u) => u.role === 'TECNICO' && u.active)
  const data = tickets.data
  const change = (set: (v: string) => void) => (e: { target: { value: string } }) => {
    set(e.target.value)
    setPage(0) // qualquer filtro novo volta para a primeira página
  }

  return (
    <div className="stack">
      <div className="page-head">
        <h1 className="page-title">Chamados</h1>
        <a className="btn btn-primary" href={paths.new}>Novo chamado</a>
      </div>

      <section className="card" aria-label="Filtros">
        <div className="filters-grid">
          <div className="field">
            <label htmlFor="f-q">Buscar</label>
            <input id="f-q" className="input" type="search" placeholder="Título ou descrição" value={text} onChange={change(setText)} />
          </div>
          <div className="field">
            <label htmlFor="f-status">Status</label>
            <select id="f-status" className="select" value={status} onChange={change(setStatus)}>
              <option value="">Todos</option>
              {STATUSES.map((s) => <option key={s} value={s}>{STATUS_LABEL[s]}</option>)}
            </select>
          </div>
          <div className="field">
            <label htmlFor="f-priority">Prioridade</label>
            <select id="f-priority" className="select" value={priority} onChange={change(setPriority)}>
              <option value="">Todas</option>
              {PRIORITIES.map((p) => <option key={p} value={p}>{PRIORITY_INFO[p].label}</option>)}
            </select>
          </div>
          <div className="field">
            <label htmlFor="f-category">Categoria</label>
            <select id="f-category" className="select" value={categoryId} onChange={change(setCategoryId)}>
              <option value="">Todas</option>
              {(categories.data ?? []).map((c) => <option key={c.id} value={c.id}>{c.name}{c.active ? '' : ' (inativa)'}</option>)}
            </select>
          </div>
          <div className="field">
            <label htmlFor="f-assignee">Técnico</label>
            <select id="f-assignee" className="select" value={assignee} onChange={change(setAssignee)}>
              <option value="">Todos</option>
              <option value="none">Sem técnico</option>
              {technicians.map((u) => <option key={u.id} value={u.id}>{u.name}</option>)}
            </select>
          </div>
          <div className="field">
            <label htmlFor="f-sla">SLA</label>
            <select id="f-sla" className="select" value={sla} onChange={change(setSla)}>
              <option value="">Todos</option>
              <option value="ESTOURADO">Estourado</option>
              <option value="DENTRO_DO_PRAZO">No prazo</option>
            </select>
          </div>
        </div>
      </section>

      <section className="card" aria-label="Lista de chamados" style={{ opacity: tickets.isFetching && data ? 0.6 : 1 }}>
        {tickets.isError ? (
          <p role="alert" className="form-error">Não foi possível carregar os chamados: {tickets.error.message}</p>
        ) : data && data.totalElements === 0 && !hasFilters ? (
          <div className="empty">
            <p>Nenhum chamado ainda.</p>
            <p style={{ marginTop: 12 }}><a className="btn btn-primary" href={paths.new}>Abrir o primeiro chamado</a></p>
          </div>
        ) : (
          <>
            <DataTable
              caption="Chamados"
              rows={data?.content ?? []}
              rowKey={(t) => t.id}
              empty={data ? 'Nenhum chamado com esses filtros.' : 'Carregando…'}
              columns={[
                { header: 'Nº', align: 'left', cell: (t) => <a className="link" href={paths.ticket(t.id)}>#{t.id}</a> },
                { header: 'Título', align: 'left', cell: (t) => <a className="link" href={paths.ticket(t.id)}>{t.title}</a> },
                { header: 'Categoria', align: 'left', cell: (t) => t.categoryName },
                { header: 'Prioridade', align: 'left', cell: (t) => <PriorityBadge priority={t.priority} /> },
                { header: 'Status', align: 'left', cell: (t) => <StatusBadge status={t.status} /> },
                { header: 'Técnico', align: 'left', cell: (t) => t.assigneeName ?? '—' },
                { header: 'Prazo do SLA', align: 'left', cell: (t) => <>{formatDateTime(t.slaDueAt)} <SlaBadge status={t.slaStatus} /></> },
                { header: 'Aberto em', align: 'left', cell: (t) => formatDateTime(t.createdAt) },
              ]}
            />
            {data && data.totalElements > 0 && (
              <div className="pagination">
                <span>{formatInt(data.totalElements)} chamado{data.totalElements === 1 ? '' : 's'} · página {data.page + 1} de {data.totalPages}</span>
                <span className="actions">
                  <button className="btn" type="button" disabled={data.page === 0} onClick={() => setPage(page - 1)}>Anterior</button>
                  <button className="btn" type="button" disabled={data.page + 1 >= data.totalPages} onClick={() => setPage(page + 1)}>Próxima</button>
                </span>
              </div>
            )}
          </>
        )}
      </section>
    </div>
  )
}
