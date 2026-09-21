import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { addTechnician, deactivateUser, getSlaRules, getUsers } from '../../api/endpoints'
import { getEngine } from '../../local/localApi'
import { STANDALONE } from '../../local/mode'
import { SlaRow } from './SlaRow'

function SlaSection() {
  const rules = useQuery({ queryKey: ['sla-rules'], queryFn: getSlaRules })
  return (
    <section className="card" aria-label="Regras de SLA">
      <div className="card-head"><div>
        <h2 className="card-title">Prazos de SLA</h2>
        <p className="card-sub">Tempo para resolver cada prioridade. O prazo é gravado na abertura do chamado: mudar a regra depois não altera chamados já abertos.</p>
      </div></div>
      <div className="table-wrap">
        <table className="data">
          <caption className="sr-only">Regras de SLA por prioridade</caption>
          <thead><tr><th className="left">Prioridade</th><th>Minutos</th><th className="left">Equivale a</th><th>Contagem</th><th /></tr></thead>
          <tbody>{(rules.data ?? []).map((r) => <SlaRow key={r.priority} rule={r} />)}</tbody>
        </table>
      </div>
      <p className="card-note">Horas úteis: segunda a sexta, das 08:00 às 18:00, no fuso do seu navegador.</p>
    </section>
  )
}

/** Equipe e dados: só existem no modo local (no servidor, usuários são cadastrados com e-mail e senha pelo ADMIN). */
function TeamSection() {
  const client = useQueryClient()
  const users = useQuery({ queryKey: ['users'], queryFn: getUsers })
  const [name, setName] = useState('')
  const [error, setError] = useState<string | null>(null)
  const refresh = () => client.invalidateQueries({ queryKey: ['users'] })

  const add = useMutation({
    mutationFn: () => addTechnician(name),
    onSuccess: async () => { setName(''); setError(null); await refresh() },
    onError: (e: Error) => setError(e.message),
  })
  const remove = useMutation({
    mutationFn: (id: number) => deactivateUser(id),
    onSuccess: async () => { setError(null); await refresh() },
    onError: (e: Error) => setError(e.message),
  })
  const technicians = (users.data ?? []).filter((u) => u.role === 'TECNICO' && u.active)

  function onSubmit(e: FormEvent) {
    e.preventDefault()
    add.mutate()
  }

  return (
    <section className="card" aria-label="Equipe">
      <div className="card-head"><div>
        <h2 className="card-title">Equipe</h2>
        <p className="card-sub">Pessoas a quem os chamados podem ser atribuídos.</p>
      </div></div>
      <ul className="comments">
        {technicians.map((u) => (
          <li key={u.id} className="actions">
            <strong>{u.name}</strong>
            {u.id !== 1 && <button className="btn" type="button" onClick={() => remove.mutate(u.id)} aria-label={`Remover ${u.name}`}>Remover</button>}
          </li>
        ))}
      </ul>
      <form onSubmit={onSubmit} className="actions">
        <label className="sr-only" htmlFor="tech-name">Nome</label>
        <input id="tech-name" className="input" style={{ maxWidth: 260 }} placeholder="Nome da pessoa" required maxLength={100} value={name} onChange={(e) => setName(e.target.value)} />
        <button className="btn btn-primary" type="submit" disabled={add.isPending}>Adicionar à equipe</button>
      </form>
      {error && <p className="form-error" role="alert" style={{ marginTop: 8 }}>{error}</p>}
    </section>
  )
}

function DataSection() {
  const client = useQueryClient()
  const engine = getEngine()
  const [backup, setBackup] = useState('')
  const [pasted, setPasted] = useState('')
  const [message, setMessage] = useState<{ ok: boolean; text: string } | null>(null)
  const [confirmReset, setConfirmReset] = useState(false)

  const reloadEverything = () => client.invalidateQueries()

  async function copy() {
    const json = engine.exportJson()
    setBackup(json)
    try {
      await navigator.clipboard.writeText(json)
      setMessage({ ok: true, text: 'Backup copiado. Cole em um arquivo de texto e guarde.' })
    } catch {
      setMessage({ ok: true, text: 'Backup gerado abaixo: selecione tudo e copie.' })
    }
  }

  async function restore() {
    if (engine.importJson(pasted)) {
      setPasted('')
      setMessage({ ok: true, text: 'Dados restaurados.' })
      await reloadEverything()
    } else {
      setMessage({ ok: false, text: 'O texto colado não é um backup válido do ServiceFlow.' })
    }
  }

  async function reset() {
    engine.reset()
    setConfirmReset(false)
    setBackup('')
    setMessage({ ok: true, text: 'Todos os dados foram apagados.' })
    await reloadEverything()
  }

  return (
    <section className="card" aria-label="Seus dados">
      <div className="card-head"><div>
        <h2 className="card-title">Seus dados</h2>
        <p className="card-sub">
          {engine.persistent
            ? 'Ficam salvos neste navegador, neste computador. Nada é enviado a nenhum servidor. Outros navegadores ou computadores começam vazios.'
            : 'Este navegador bloqueou o armazenamento: os dados só duram até você fechar a página. Faça backup antes de sair.'}
        </p>
      </div></div>

      <div className="form-grid">
        <div className="actions">
          <button className="btn" type="button" onClick={() => void copy()}>Gerar backup</button>
        </div>
        {backup && <textarea className="textarea backup" readOnly aria-label="Backup dos dados" value={backup} onFocus={(e) => e.currentTarget.select()} />}

        <div className="field">
          <label htmlFor="restore">Restaurar de um backup</label>
          <textarea id="restore" className="textarea backup" placeholder="Cole aqui o backup (JSON)" value={pasted} onChange={(e) => setPasted(e.target.value)} />
          <span className="field-hint">Atenção: restaurar substitui todos os dados atuais.</span>
        </div>
        <div className="actions">
          <button className="btn" type="button" disabled={!pasted.trim()} onClick={() => void restore()}>Restaurar backup</button>
          {confirmReset ? (
            <>
              <button className="btn btn-danger" type="button" onClick={() => void reset()}>Confirmar: apagar tudo</button>
              <button className="btn" type="button" onClick={() => setConfirmReset(false)}>Voltar</button>
            </>
          ) : (
            <button className="btn btn-danger" type="button" onClick={() => setConfirmReset(true)}>Apagar todos os dados</button>
          )}
        </div>
        {message && <p className={message.ok ? 'status status-good' : 'form-error'} role={message.ok ? 'status' : 'alert'}>{message.text}</p>}
      </div>
    </section>
  )
}

export function SettingsPage() {
  return (
    <div className="stack">
      <div className="page-head"><h1 className="page-title">Configurações</h1></div>
      <SlaSection />
      {STANDALONE && <TeamSection />}
      {STANDALONE && <DataSection />}
    </div>
  )
}
