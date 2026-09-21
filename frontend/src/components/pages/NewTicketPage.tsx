import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { createTicket, getCategories, getSlaRules, suggest } from '../../api/endpoints'
import type { Priority, SuggestionResponse } from '../../api/types'
import { formatMinutes, formatRate } from '../../lib/format'
import { PRIORITIES, PRIORITY_INFO } from '../../lib/labels'
import { navigate, paths } from '../../lib/router'
import { STANDALONE } from '../../local/mode'

export function NewTicketPage() {
  const client = useQueryClient()
  const categories = useQuery({ queryKey: ['categories'], queryFn: getCategories })
  const rules = useQuery({ queryKey: ['sla-rules'], queryFn: getSlaRules })

  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [categoryId, setCategoryId] = useState('')
  const [priority, setPriority] = useState<Priority>('P3')
  const [suggestion, setSuggestion] = useState<SuggestionResponse | null>(null)
  const [suggesting, setSuggesting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const activeCategories = (categories.data ?? []).filter((c) => c.active)
  const rule = rules.data?.find((r) => r.priority === priority)

  const create = useMutation({
    mutationFn: () => createTicket({
      title, description, categoryId: Number(categoryId), priority,
      suggestionId: suggestion?.available ? (suggestion.suggestionId ?? undefined) : undefined,
    }),
    onSuccess: async (ticket) => {
      await client.invalidateQueries({ queryKey: ['tickets'] })
      await client.invalidateQueries({ queryKey: ['summary'] })
      navigate(paths.ticket(ticket.id))
    },
    onError: (e: Error) => setError(e.message),
  })

  async function askSuggestion() {
    setSuggesting(true)
    try {
      setSuggestion(await suggest(title, description))
    } catch {
      setSuggestion({ available: false, suggestionId: null, modelVersion: null, category: null, priority: null })
    } finally {
      setSuggesting(false)
    }
  }

  function apply() {
    if (suggestion?.category) setCategoryId(String(suggestion.category.id))
    if (suggestion?.priority) setPriority(suggestion.priority.priority)
  }

  function onSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    create.mutate()
  }

  return (
    <div className="stack">
      <div className="page-head"><h1 className="page-title">Novo chamado</h1></div>
      <form className="card form-grid" onSubmit={onSubmit}>
        <div className="field">
          <label htmlFor="t-title">Título</label>
          <input id="t-title" className="input" required maxLength={150} value={title} onChange={(e) => setTitle(e.target.value)} />
        </div>
        <div className="field">
          <label htmlFor="t-desc">Descrição</label>
          <textarea id="t-desc" className="textarea" required maxLength={4000} value={description} onChange={(e) => setDescription(e.target.value)} />
        </div>

        {/* A sugestão automática depende do serviço Python (servidor): não existe no modo local. */}
        {!STANDALONE && (
          <div className="suggestion">
            <div className="actions">
              <button type="button" className="btn" disabled={!title.trim() || !description.trim() || suggesting} onClick={() => void askSuggestion()}>
                {suggesting ? 'Consultando…' : 'Sugerir categoria e prioridade'}
              </button>
            </div>
            {suggestion && !suggestion.available && <span className="field-hint">Sugestão indisponível no momento. Preencha manualmente.</span>}
            {suggestion?.available && (
              <>
                <span>
                  {suggestion.category ? <>Categoria <strong>{suggestion.category.name}</strong> ({formatRate(suggestion.category.confidence)}) </> : null}
                  {suggestion.priority ? <>· Prioridade <strong>{suggestion.priority.priority}</strong> ({formatRate(suggestion.priority.confidence)})</> : null}
                </span>
                <span className="actions"><button type="button" className="btn" onClick={apply}>Aplicar sugestão</button></span>
              </>
            )}
          </div>
        )}

        <div className="form-row">
          <div className="field">
            <label htmlFor="t-cat">Categoria</label>
            <select id="t-cat" className="select" required value={categoryId} onChange={(e) => setCategoryId(e.target.value)}>
              <option value="" disabled>Selecione…</option>
              {activeCategories.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
            </select>
          </div>
          <div className="field">
            <label htmlFor="t-prio">Prioridade</label>
            <select id="t-prio" className="select" value={priority} onChange={(e) => setPriority(e.target.value as Priority)}>
              {PRIORITIES.map((p) => <option key={p} value={p}>{PRIORITY_INFO[p].label}</option>)}
            </select>
            {rule && (
              <span className="field-hint">
                Prazo de SLA: {formatMinutes(rule.resolutionMinutes)} {rule.businessHours ? 'em horas úteis' : 'corridas'}
              </span>
            )}
          </div>
        </div>

        {error && <p className="form-error" role="alert">{error}</p>}
        <div className="actions">
          <button className="btn btn-primary" type="submit" disabled={create.isPending}>{create.isPending ? 'Abrindo…' : 'Abrir chamado'}</button>
          <a className="btn" href={paths.tickets}>Cancelar</a>
        </div>
      </form>
    </div>
  )
}
