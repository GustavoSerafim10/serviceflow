import { useEffect, useRef, useState } from 'react'

/** Largura atual de um elemento (acompanha redimensionamento). Em ambientes sem ResizeObserver (jsdom), usa o padrão. */
export function useContainerWidth<T extends HTMLElement>(fallback = 640) {
  const ref = useRef<T>(null)
  const [width, setWidth] = useState(fallback)

  useEffect(() => {
    const el = ref.current
    if (!el || typeof ResizeObserver === 'undefined') return
    const observer = new ResizeObserver(([entry]) => {
      const w = Math.floor(entry.contentRect.width)
      if (w > 0) setWidth(w)
    })
    observer.observe(el)
    setWidth(Math.max(1, Math.floor(el.getBoundingClientRect().width)) || fallback)
    return () => observer.disconnect()
  }, [fallback])

  return { ref, width }
}
