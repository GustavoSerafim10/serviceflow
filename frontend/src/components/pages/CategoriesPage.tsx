import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { createCategory, deactivateCategory, getCategories } from '../../api/endpoints'
import { DataTable } from '../DataTable'

export function CategoriesPage() {
  const client = useQueryClient()
  const categories = useQuery({ queryKey: ['categories'], queryFn: getCategories })
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [error, setError] = useState<string | null>(null)

  const refresh = () => client.invalidateQueries({ queryKey: ['categories'] })
  const add = useMutation({
    mutationFn: () => createCategory(name, description),
    onSuccess: async () => { setName(''); setDescription(''); setError(null); await refresh() },
    onError: (e: Error) => setError(e.message),
  })
  const remove = useMutation({
    mutationFn: (id: number) => deactivateCategory(id),
    onSuccess: async () => { setError(null); await refresh() },
    onError: (e: Error) => setError(e.message),
  })

  function onSubmit(e: FormEvent) {
    e.preventDefault()
    add.mutate()
  }

  return (
    <div className="stack">
      <div className="page-head"><h1 className="page-title">Categorias</h1></div>

      <form className="card form-grid" onSubmit={onSubmit}>
        <h2 className="card-title">Nova categoria</h2>
        <div className="form-row">
          <div className="field">
            <label htmlFor="cat-name">Nome</label>
            <input id="cat-name" className="input" required maxLength={100} value={name} onChange={(e) => setName(e.target.value)} />
          </div>
          <div className="field">
            <label htmlFor="cat-desc">Descrição (opcional)</label>
            <input id="cat-desc" className="input" maxLength={255} value={description} onChange={(e) => setDescription(e.target.value)} />
          </div>
        </div>
        {error && <p className="form-error" role="alert">{error}</p>}
        <div className="actions"><button className="btn btn-primary" type="submit" disabled={add.isPending}>Adicionar</button></div>
      </form>

      <section className="card" aria-label="Lista de categorias">
        <DataTable
          caption="Categorias"
          rows={categories.data ?? []}
          rowKey={(c) => c.id}
          empty={categories.data ? 'Nenhuma categoria.' : 'Carregando…'}
          columns={[
            { header: 'Nome', cell: (c) => c.name },
            { header: 'Descrição', cell: (c) => <span className="wrap">{c.description ?? '—'}</span> },
            { header: 'Situação', cell: (c) => (c.active ? 'Ativa' : 'Inativa') },
            {
              header: '',
              cell: (c) => c.active
                ? <button className="btn" type="button" disabled={remove.isPending} onClick={() => remove.mutate(c.id)} aria-label={`Desativar ${c.name}`}>Desativar</button>
                : null,
            },
          ]}
        />
        <p className="card-note">Categorias desativadas deixam de aparecer para novos chamados, mas os chamados antigos continuam apontando para elas.</p>
      </section>
    </div>
  )
}
