import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { updateSlaRule } from '../../api/endpoints'
import type { SlaRuleResponse } from '../../api/types'
import { formatMinutes } from '../../lib/format'
import { PRIORITY_INFO } from '../../lib/labels'

/**
 * Linha editável de uma regra de SLA. Enquanto você edita existe um RASCUNHO; sem rascunho, os campos mostram
 * a regra vinda dos dados (assim acompanham mudanças externas, como um backup restaurado, sem efeitos colaterais).
 */
export function SlaRow({ rule }: { rule: SlaRuleResponse }) {
  const client = useQueryClient()
  const [draft, setDraft] = useState<{ minutes: string; business: boolean } | null>(null)
  const [message, setMessage] = useState<{ ok: boolean; text: string } | null>(null)

  const minutes = draft?.minutes ?? String(rule.resolutionMinutes)
  const business = draft?.business ?? rule.businessHours
  const dirty = Number(minutes) !== rule.resolutionMinutes || business !== rule.businessHours

  const save = useMutation({
    mutationFn: () => updateSlaRule(rule.priority, Number(minutes), business),
    onSuccess: async () => {
      setMessage({ ok: true, text: 'Salvo' })
      await client.invalidateQueries({ queryKey: ['sla-rules'] })
      setDraft(null)
    },
    onError: (e: Error) => setMessage({ ok: false, text: e.message }),
  })

  return (
    <tr>
      <td className="left">{PRIORITY_INFO[rule.priority].label}</td>
      <td>
        <input aria-label={`Prazo de ${rule.priority} em minutos`} className="input" style={{ width: 110 }} type="number" min={1} max={43200}
          value={minutes} onChange={(e) => { setDraft({ minutes: e.target.value, business }); setMessage(null) }} />
      </td>
      <td className="left">{Number(minutes) > 0 ? `= ${formatMinutes(Number(minutes))}` : ''}</td>
      <td>
        <label className="actions">
          <input type="checkbox" checked={business} onChange={(e) => { setDraft({ minutes, business: e.target.checked }); setMessage(null) }} />
          Só horas úteis
        </label>
      </td>
      <td className="left">
        <span className="actions">
          <button className="btn" type="button" disabled={!dirty || save.isPending} onClick={() => save.mutate()}>Salvar</button>
          {message && <span className={message.ok ? 'status-good status' : 'form-error'} role={message.ok ? 'status' : 'alert'}>{message.text}</span>}
        </span>
      </td>
    </tr>
  )
}
