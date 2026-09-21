import { describe, expect, it } from 'vitest'
import type { CategoryStat } from '../api/types'
import { foldCategories } from './fold'
import { formatDayLong, formatDayShort, formatMinutes, formatRate, toIsoDay } from './format'
import { periodForPreset, validatePeriod } from './period'
import { niceScale } from './scale'

describe('formatRate', () => {
  it('formata como percentual pt-BR com uma casa', () => {
    expect(formatRate(0.7692)).toBe('76,9%')
    expect(formatRate(1)).toBe('100,0%')
    expect(formatRate(0)).toBe('0,0%')
  })
  it('sem base de cálculo mostra traço, nunca um enganoso 0%', () => {
    expect(formatRate(null)).toBe('—')
    expect(formatRate(undefined)).toBe('—')
  })
})

describe('formatMinutes', () => {
  it.each([
    [0.4, '0 min'], [45, '45 min'], [60, '1h'], [61, '1h 01min'], [1141.5, '19h 02min'],
    [1440, '1d'], [1500, '1d 1h'], [3000, '2d 2h'],
  ])('%s min -> %s', (input, expected) => {
    expect(formatMinutes(input)).toBe(expected)
  })
  it('null -> traço', () => expect(formatMinutes(null)).toBe('—'))
})

describe('datas', () => {
  it('não deixa o fuso do navegador deslocar o dia', () => {
    // new Date("2026-01-05") em UTC-3 viraria 04/01; aqui deve permanecer 05/01
    expect(formatDayShort('2026-01-05')).toBe('05/01')
    expect(formatDayLong('2026-01-05')).toContain('05/01/2026')
  })
  it('toIsoDay usa a data local', () => {
    expect(toIsoDay(new Date(2026, 0, 5, 23, 59))).toBe('2026-01-05')
    expect(toIsoDay(new Date(2026, 11, 1, 0, 1))).toBe('2026-12-01')
  })
})

describe('períodos', () => {
  const today = new Date(2026, 8, 21) // 21/09/2026
  it('presets são inclusivos e terminam hoje', () => {
    expect(periodForPreset('7d', today)).toEqual({ from: '2026-09-15', to: '2026-09-21' })
    expect(periodForPreset('30d', today)).toEqual({ from: '2026-08-23', to: '2026-09-21' })
    expect(periodForPreset('90d', today)).toEqual({ from: '2026-06-24', to: '2026-09-21' })
    expect(periodForPreset('month', today)).toEqual({ from: '2026-09-01', to: '2026-09-21' })
  })
  it('valida como a API: ordem e limite de 366 dias', () => {
    expect(validatePeriod({ from: '2026-01-01', to: '2026-01-31' })).toBeNull()
    expect(validatePeriod({ from: '2026-02-01', to: '2026-01-01' })).toMatch(/posterior/)
    expect(validatePeriod({ from: '', to: '2026-01-01' })).toMatch(/duas datas/)
    expect(validatePeriod({ from: '2025-01-01', to: '2025-12-31' })).toBeNull()      // 365 dias
    expect(validatePeriod({ from: '2024-01-01', to: '2024-12-31' })).toBeNull()      // 366 (ano bissexto)
    expect(validatePeriod({ from: '2024-01-01', to: '2025-01-01' })).toMatch(/366/)  // 367
  })
})

describe('niceScale', () => {
  it('escolhe passos limpos começando em zero', () => {
    expect(niceScale(9)).toEqual({ max: 10, ticks: [0, 5, 10] })
    expect(niceScale(37).ticks).toEqual([0, 10, 20, 30, 40])
    expect(niceScale(3).ticks).toEqual([0, 1, 2, 3])
  })
  it('sem dados ainda desenha um eixo utilizável', () => {
    expect(niceScale(0).ticks).toEqual([0, 1, 2, 3, 4])
  })
})

describe('foldCategories', () => {
  const cat = (id: number, opened: number, resolved: number, within: number, avg: number | null): CategoryStat => ({
    categoryId: id, categoryName: `C${id}`, opened, resolved, resolvedWithinSla: within,
    slaComplianceRate: resolved ? within / resolved : null, avgResolutionMinutes: avg,
  })

  it('não dobra quando cabe (dobrar UMA categoria em "Outras" seria absurdo)', () => {
    const items = Array.from({ length: 8 }, (_, i) => cat(i + 1, 10, 5, 4, 100))
    expect(foldCategories(items, 7)).toHaveLength(8)
  })

  it('mantém as maiores e soma a cauda, com médias ponderadas pelos resolvidos', () => {
    const items = [
      ...Array.from({ length: 7 }, (_, i) => cat(i + 1, 50 - i, 10, 8, 200)),
      cat(8, 4, 2, 1, 100),
      cat(9, 3, 6, 6, 400),
      cat(10, 1, 0, 0, null),
    ]
    const folded = foldCategories(items, 7)

    expect(folded).toHaveLength(8)
    const other = folded[7]
    expect(other.categoryName).toBe('Outras')
    expect(other.opened).toBe(8)
    expect(other.resolved).toBe(8)
    expect(other.resolvedWithinSla).toBe(7)
    expect(other.slaComplianceRate).toBeCloseTo(7 / 8)
    expect(other.avgResolutionMinutes).toBeCloseTo((100 * 2 + 400 * 6) / 8) // ponderada: 325
  })

  it('cauda sem nenhum resolvido -> taxas nulas (não 0%)', () => {
    const items = [...Array.from({ length: 7 }, (_, i) => cat(i + 1, 10, 5, 4, 100)), cat(8, 2, 0, 0, null), cat(9, 1, 0, 0, null)]
    const other = foldCategories(items, 7)[7]
    expect(other.slaComplianceRate).toBeNull()
    expect(other.avgResolutionMinutes).toBeNull()
  })
})
