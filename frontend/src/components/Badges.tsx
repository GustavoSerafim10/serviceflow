import type { Priority, SlaStatus, TicketStatus } from '../api/types'
import { PRIORITY_INFO, STATUS_LABEL } from '../lib/labels'
import { AlertIcon, CheckIcon } from './Icons'

/** Prioridade: ponto na rampa ordinal + texto (o texto é quem carrega a informação; a cor só reforça). */
export function PriorityBadge({ priority }: { priority: Priority }) {
  return (
    <span className="badge">
      <span className="dot" style={{ background: PRIORITY_INFO[priority].color }} aria-hidden="true" />
      {PRIORITY_INFO[priority].label}
    </span>
  )
}

export function StatusBadge({ status }: { status: TicketStatus }) {
  return <span className="badge badge-status" data-status={status}>{STATUS_LABEL[status]}</span>
}

/** Situação do SLA: cores semânticas SEMPRE com ícone + texto. Cancelado não tem SLA. */
export function SlaBadge({ status }: { status: SlaStatus | null }) {
  if (status === null) return <span className="badge-muted">—</span>
  return status === 'ESTOURADO'
    ? <span className="status status-critical"><AlertIcon /> Estourado</span>
    : <span className="status status-good"><CheckIcon /> No prazo</span>
}
