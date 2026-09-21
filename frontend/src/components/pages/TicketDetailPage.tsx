import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { addComment, assignTicket, changeTicketStatus, getComments, getHistory, getTicket, getUsers } from '../../api/endpoints'
import type { TicketStatus } from '../../api/types'
import { STATUS_ACTIONS, formatDateTime, isTerminal, prettifyHistory } from '../../lib/labels'
import { paths } from '../../lib/router'
import { PriorityBadge, SlaBadge, StatusBadge } from '../Badges'

export function TicketDetailPage({ id }: { id: number }) {
  const client = useQueryClient()
  const ticket = useQuery({ queryKey: ['ticket', id], queryFn: () => getTicket(id), retry: false })
  const comments = useQuery({ queryKey: ['comments', id], queryFn: () => getComments(id), enabled: ticket.isSuccess })
  const history = useQuery({ queryKey: ['history', id], queryFn: () => getHistory(id), enabled: ticket.isSuccess })
  const users = useQuery({ queryKey: ['users'], queryFn: getUsers, retry: false })

  const [technician, setTechnician] = useState('')
  const [text, setText] = useState('')
  const [confirmCancel, setConfirmCancel] = useState(false)
  const [error, setError] = useState<string | null>(null)

  // Depois de qualquer mudança, o chamado, o histórico, a lista e os indicadores precisam refletir o novo estado.
  const refresh = async () => {
    setError(null)
    await Promise.all(['ticket', 'comments', 'history', 'tickets', 'summary', 'timeline', 'by-priority', 'by-category', 'by-technician']
      .map((key) => client.invalidateQueries({ queryKey: [key] })))
  }
  const onError = (e: Error) => setError(e.message)

  const assign = useMutation({ mutationFn: () => assignTicket(id, Number(technician)), onSuccess: refresh, onError })
  const status = useMutation({
    mutationFn: (to: TicketStatus) => changeTicketStatus(id, to),
    onSuccess: async () => { setConfirmCancel(false); await refresh() },
    onError,
  })
  const comment = useMutation({
    mutationFn: () => addComment(id, text),
    onSuccess: async () => { setText(''); await refresh() },
    onError,
  })

  if (ticket.isError) {
    return (
      <div className="notice">
        <h2>Chamado não encontrado</h2>
        <p>{ticket.error.message}</p>
        <p style={{ marginTop: 12 }}><a className="btn" href={paths.tickets}>Voltar para a lista</a></p>
      </div>
    )
  }
  if (!ticket.data) return <p className="empty" role="status">Carregando…</p>

  const t = ticket.data
  const technicians = (users.data ?? []).filter((u) => u.role === 'TECNICO' && u.active)
  const canAssign = (t.status === 'ABERTO' || t.status === 'EM_ATENDIMENTO') && technicians.length > 0
  const selectable = technicians.filter((u) => u.id !== t.assigneeId)

  function onComment(e: FormEvent) {
    e.preventDefault()
    if (text.trim()) comment.mutate()
  }

  return (
    <div className="stack">
      <div className="page-head">
        <div>
          <p className="card-sub"><a className="link" href={paths.tickets}>← Chamados</a></p>
          <h1 className="page-title">#{t.id} · {t.title}</h1>
        </div>
        <span className="actions"><PriorityBadge priority={t.priority} /><StatusBadge status={t.status} /></span>
      </div>

      {error && <p className="form-error" role="alert">{error}</p>}

      <div className="detail-grid">
        <div className="stack">
          <section className="card" aria-label="Dados do chamado">
            <dl className="facts">
              <div><dt>Categoria</dt><dd>{t.categoryName}</dd></div>
              <div><dt>Solicitante</dt><dd>{t.requesterName}</dd></div>
              <div><dt>Técnico</dt><dd>{t.assigneeName ?? 'Sem técnico'}</dd></div>
              <div><dt>Aberto em</dt><dd>{formatDateTime(t.createdAt)}</dd></div>
              <div><dt>Prazo do SLA</dt><dd>{formatDateTime(t.slaDueAt)} <SlaBadge status={t.slaStatus} /></dd></div>
              <div><dt>Resolvido em</dt><dd>{t.resolvedAt ? formatDateTime(t.resolvedAt) : '—'}</dd></div>
            </dl>
            <p className="description">{t.description}</p>
          </section>

          <section className="card" aria-label="Comentários">
            <h2 className="card-title" style={{ marginBottom: 12 }}>Comentários</h2>
            {comments.data && comments.data.length > 0 ? (
              <ul className="comments">
                {comments.data.map((c) => (
                  <li key={c.id} className="comment">
                    <div className="comment-meta">{c.authorName} · {formatDateTime(c.createdAt)}</div>
                    <div className="comment-body">{c.body}</div>
                  </li>
                ))}
              </ul>
            ) : <p className="card-sub" style={{ marginBottom: 12 }}>Nenhum comentário ainda.</p>}

            {isTerminal(t.status) ? (
              <p className="field-hint">Chamado {t.status === 'FECHADO' ? 'fechado' : 'cancelado'}: não recebe novos comentários.</p>
            ) : (
              <form onSubmit={onComment} className="form-grid">
                <div className="field">
                  <label htmlFor="c-body">Novo comentário</label>
                  <textarea id="c-body" className="textarea" maxLength={2000} value={text} onChange={(e) => setText(e.target.value)} />
                </div>
                <div className="actions">
                  <button className="btn btn-primary" type="submit" disabled={!text.trim() || comment.isPending}>Comentar</button>
                </div>
              </form>
            )}
          </section>
        </div>

        <div className="stack">
          <section className="card" aria-label="Ações">
            <h2 className="card-title" style={{ marginBottom: 12 }}>Ações</h2>

            {canAssign && (
              <div className="form-grid" style={{ marginBottom: 16 }}>
                <div className="field">
                  <label htmlFor="a-tech">{t.assigneeId ? 'Reatribuir a' : 'Atribuir a'}</label>
                  <select id="a-tech" className="select" value={technician} onChange={(e) => setTechnician(e.target.value)}>
                    <option value="">Selecione o técnico…</option>
                    {selectable.map((u) => <option key={u.id} value={u.id}>{u.name}</option>)}
                  </select>
                </div>
                <div className="actions">
                  <button className="btn" type="button" disabled={!technician || assign.isPending} onClick={() => assign.mutate()}>
                    {t.assigneeId ? 'Reatribuir' : 'Atribuir e iniciar atendimento'}
                  </button>
                </div>
              </div>
            )}

            {STATUS_ACTIONS[t.status].length === 0 ? (
              <p className="field-hint">Chamado encerrado: não há mais ações.</p>
            ) : (
              <div className="actions">
                {STATUS_ACTIONS[t.status].map((a) => a.danger ? (
                  confirmCancel ? (
                    <span key={a.to} className="actions">
                      <button className="btn btn-danger" type="button" disabled={status.isPending} onClick={() => status.mutate(a.to)}>Confirmar cancelamento</button>
                      <button className="btn" type="button" onClick={() => setConfirmCancel(false)}>Voltar</button>
                    </span>
                  ) : (
                    <button key={a.to} className="btn btn-danger" type="button" onClick={() => setConfirmCancel(true)}>{a.label}</button>
                  )
                ) : (
                  <button key={a.to} className="btn btn-primary" type="button" disabled={status.isPending} onClick={() => status.mutate(a.to)}>{a.label}</button>
                ))}
              </div>
            )}
            {t.status === 'ABERTO' && <p className="field-hint" style={{ marginTop: 10 }}>Para iniciar o atendimento, atribua um técnico.</p>}
          </section>

          <section className="card" aria-label="Histórico">
            <h2 className="card-title" style={{ marginBottom: 12 }}>Histórico</h2>
            <ol className="timeline">
              {(history.data ?? []).map((h) => (
                <li key={h.id}>
                  <span>{prettifyHistory(h.details)}</span>
                  <span className="who">{h.actorName}</span>
                  <span className="when">{formatDateTime(h.createdAt)}</span>
                </li>
              ))}
            </ol>
          </section>
        </div>
      </div>
    </div>
  )
}
