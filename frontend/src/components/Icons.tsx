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

/** Indicadores: três barras — o mesmo motivo usado na marca (ver LogoMark), para o menu e o produto lerem como um sistema só. */
export function ChartIcon({ className }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M3 13V8.5" />
      <path d="M8 13V3.5" />
      <path d="M13 13V6.5" />
      <path d="M1.5 13.5h13" />
    </svg>
  )
}

/** Chamados: lista/ficha. */
export function TicketIcon({ className }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <rect x="2.5" y="2" width="11" height="12" rx="2" />
      <path d="M5.5 6h5" />
      <path d="M5.5 8.5h5" />
      <path d="M5.5 11h3" />
    </svg>
  )
}

/** Categorias: etiqueta. */
export function TagIcon({ className }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M8.5 2H13a1 1 0 011 1v4.5a1 1 0 01-.3.7l-6 6a1 1 0 01-1.4 0l-5-5a1 1 0 010-1.4l6-6a1 1 0 01.7-.3z" />
      <circle cx="10.6" cy="5.4" r="1" fill="currentColor" stroke="none" />
    </svg>
  )
}

/** Configurações: engrenagem. */
export function GearIcon({ className }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <circle cx="8" cy="8" r="2.2" />
      <path d="M8 1.5v1.6M8 12.9v1.6M14.5 8h-1.6M3.1 8H1.5M12.4 3.6l-1.1 1.1M4.7 11.3l-1.1 1.1M12.4 12.4l-1.1-1.1M4.7 4.7 3.6 3.6" />
    </svg>
  )
}

/**
 * Marca do produto: um quadrado arredondado com três barras crescentes (métricas/fluxo). O mesmo motivo
 * do ChartIcon, só que como selo preenchido — dá uma identidade consistente ao app, não dois desenhos
 * soltos. `fill="currentColor"` no quadrado e `stroke="var(--surface)"` nas barras: a cor da marca (o
 * quadrado) vem de fora via `color` no elemento pai; as barras usam a cor de fundo do app para contrastar
 * com a marca em qualquer tema, sem precisar de tokens próprios.
 */
export function LogoMark({ className }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 28 28" fill="none" aria-hidden="true">
      <rect x="1" y="1" width="26" height="26" rx="7" fill="currentColor" />
      <path d="M9 18.5V14" stroke="var(--surface)" strokeWidth="2.2" strokeLinecap="round" />
      <path d="M14 18.5V9.5" stroke="var(--surface)" strokeWidth="2.2" strokeLinecap="round" />
      <path d="M19 18.5V12" stroke="var(--surface)" strokeWidth="2.2" strokeLinecap="round" />
    </svg>
  )
}
