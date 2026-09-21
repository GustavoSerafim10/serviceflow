/** Ícones inline (sem dependência). Sempre acompanhados de texto: status nunca é só cor. */

export function CheckIcon({ className }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M3 8.5l3.2 3.2L13 4.8" />
    </svg>
  )
}

export function AlertIcon({ className }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M8 2.2L14.2 13H1.8L8 2.2z" />
      <path d="M8 6.6v3" />
      <path d="M8 11.6h.01" />
    </svg>
  )
}
