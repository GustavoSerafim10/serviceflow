import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../api/errors'
import * as analytics from './analytics'
import { addBusinessMinutes } from './calendar'
import { LocalEngine } from './engine'
import { localRequest, setEngine } from './localApi'
import { createBrowserStorage, emptyState, type LTicket, type LocalState, type StateStorage } from './storage'

/** Armazenamento em memória, para os testes não dependerem do navegador. */
function memoryStorage(initial: LocalState | null = null): StateStorage & { saved: LocalState | null } {
  const storage = {
    saved: initial,
    persistent: true,
    load: () => storage.saved,
    save: (s: LocalState) => { storage.saved = JSON.parse(JSON.stringify(s)) as LocalState },
  }
  return storage
}

const local = (y: number, mo: number, d: number, h = 0, mi = 0) => new Date(y, mo - 1, d, h, mi).getTime()

/** Motor com relógio controlável. */
function engineAt(start: number) {
  let now = start
  const storage = memoryStorage()
  const engine = new LocalEngine(storage, () => now)
  return { engine, storage, advance: (ms: number) => { now += ms }, set: (t: number) => { now = t } }
}

const ticket = { title: 'Sem rede', description: 'Sem conexão', categoryId: 1, priority: 'P2' as const }
const MIN = 60_000

describe('estado inicial', () => {
  it('começa SEM chamados, com categorias, regras de SLA e você como técnico', () => {
    const { engine } = engineAt(local(2026, 1, 5, 10))

    expect(engine.listTickets({}).totalElements).toBe(0)
    expect(engine.listCategories().map((c) => c.name)).toContain('Impressora')
    expect(engine.listSlaRules().map((r) => r.priority)).toEqual(['P1', 'P2', 'P3', 'P4'])
    expect(engine.me()).toMatchObject({ name: 'Você', role: 'ADMIN' })
    expect(engine.listUsers().filter((u) => u.role === 'TECNICO')).toHaveLength(1)
  })
})

describe('calendário comercial', () => {
  it.each([
    [local(2026, 1, 5, 10), 60, local(2026, 1, 5, 11)],        // mesmo expediente
    [local(2026, 1, 5, 17, 30), 60, local(2026, 1, 6, 8, 30)], // vaza para o dia seguinte
    [local(2026, 1, 9, 17), 120, local(2026, 1, 12, 9)],       // sexta 17h + 2h úteis = segunda 09h
    [local(2026, 1, 10, 12), 30, local(2026, 1, 12, 8, 30)],   // sábado
    [local(2026, 1, 5, 7), 30, local(2026, 1, 5, 8, 30)],      // antes do expediente
    [local(2026, 1, 5, 18), 30, local(2026, 1, 6, 8, 30)],     // exatamente no fim: já é fora do expediente
    [local(2026, 1, 5, 9), 1200, local(2026, 1, 7, 9)],        // 20h úteis = 2 dias
  ])('%#: soma minutos úteis', (from, minutes, expected) => {
    expect(addBusinessMinutes(from, minutes)).toBe(expected)
  })
})

describe('abertura de chamado', () => {
  it('cria ABERTO com o prazo de SLA calculado pela regra da prioridade (tempo corrido)', () => {
    const t0 = local(2026, 1, 5, 10)
    const { engine } = engineAt(t0)

    const created = engine.createTicket({ ...ticket, priority: 'P1' })

    expect(created).toMatchObject({ id: 1, status: 'ABERTO', assigneeId: null, slaStatus: 'DENTRO_DO_PRAZO', categoryName: 'Rede' })
    expect(Date.parse(created.slaDueAt)).toBe(t0 + 240 * MIN)
  })

  it('P3 conta só horas úteis: aberto na sexta 16h vence na segunda 16h', () => {
    const { engine } = engineAt(local(2026, 1, 9, 16))
    const created = engine.createTicket({ ...ticket, priority: 'P3' })
    expect(Date.parse(created.slaDueAt)).toBe(local(2026, 1, 12, 16))
  })

  it('o prazo é um SNAPSHOT: mudar a regra depois não altera chamados existentes', () => {
    const t0 = local(2026, 1, 5, 10)
    const { engine } = engineAt(t0)
    const created = engine.createTicket({ ...ticket, priority: 'P1' })

    engine.updateSlaRule('P1', { resolutionMinutes: 10, businessHours: false })

    expect(Date.parse(engine.getTicket(created.id).slaDueAt)).toBe(t0 + 240 * MIN)
    expect(Date.parse(engine.createTicket({ ...ticket, priority: 'P1' }).slaDueAt)).toBe(t0 + 10 * MIN)
  })

  it('valida os dados e a categoria', () => {
    const { engine } = engineAt(local(2026, 1, 5, 10))
    expect(() => engine.createTicket({ ...ticket, title: '  ' })).toThrowError(/título/)
    expect(() => engine.createTicket({ ...ticket, description: '' })).toThrowError(/descrição/)
    expect(() => engine.createTicket({ ...ticket, categoryId: 999 })).toThrowError(/Categoria não encontrada/)
    engine.deactivateCategory(1)
    expect(() => engine.createTicket(ticket)).toThrowError(/inativa/)
    expect(() => engine.createTicket({ ...ticket, priority: undefined })).toThrowError(/prioridade/)
  })
})

describe('fluxo de status', () => {
  it('atribuir coloca em atendimento; resolver grava a data; reabrir a apaga; tudo vai para o histórico', () => {
    const { engine, advance } = engineAt(local(2026, 1, 5, 10))
    const { id } = engine.createTicket(ticket)

    expect(engine.assign(id, 1)).toMatchObject({ status: 'EM_ATENDIMENTO', assigneeName: 'Você' })
    advance(30 * MIN)
    const resolved = engine.changeStatus(id, 'RESOLVIDO')
    expect(resolved.resolvedAt).not.toBeNull()

    expect(engine.changeStatus(id, 'EM_ATENDIMENTO').resolvedAt).toBeNull() // reaberto
    engine.changeStatus(id, 'RESOLVIDO')
    expect(engine.changeStatus(id, 'FECHADO').status).toBe('FECHADO')

    expect(engine.listHistory(id).map((h) => h.details)).toEqual([
      expect.stringContaining('Chamado aberto com prioridade P2'),
      'Atribuído a Você',
      'Status: ABERTO → EM_ATENDIMENTO',
      'Status: EM_ATENDIMENTO → RESOLVIDO',
      'Status: RESOLVIDO → EM_ATENDIMENTO',
      'Status: EM_ATENDIMENTO → RESOLVIDO',
      'Status: RESOLVIDO → FECHADO',
    ])
  })

  it('recusa transições inválidas (inclusive ABERTO → EM_ATENDIMENTO direto: só atribuindo)', () => {
    const { engine } = engineAt(local(2026, 1, 5, 10))
    const { id } = engine.createTicket(ticket)

    expect(() => engine.changeStatus(id, 'EM_ATENDIMENTO')).toThrowError(/inválida/)
    expect(() => engine.changeStatus(id, 'RESOLVIDO')).toThrowError(/inválida/)
    engine.changeStatus(id, 'CANCELADO')
    expect(() => engine.changeStatus(id, 'ABERTO')).toThrowError(/inválida/) // estado final
  })

  it('atribuição: só a técnico ativo, em chamado aberto/em atendimento, sem repetir', () => {
    const { engine } = engineAt(local(2026, 1, 5, 10))
    const { id } = engine.createTicket(ticket)
    const bia = engine.addTechnician('Bia')

    expect(() => engine.assign(id, 999)).toThrowError(/não encontrado/)
    engine.assign(id, 1)
    expect(() => engine.assign(id, 1)).toThrowError(/já está atribuído/)
    expect(engine.assign(id, bia.id).assigneeName).toBe('Bia')
    expect(engine.listHistory(id).at(-1)?.details).toBe('Reatribuído de Você para Bia')

    engine.deactivateUser(bia.id)
    engine.changeStatus(id, 'CANCELADO')
    expect(() => engine.assign(id, 1)).toThrowError(/abertos ou em atendimento/)
  })

  it('comentários: validados, listados em ordem, e bloqueados em chamado encerrado', () => {
    const { engine } = engineAt(local(2026, 1, 5, 10))
    const { id } = engine.createTicket(ticket)

    expect(() => engine.addComment(id, { body: '   ' })).toThrowError(/obrigatório/)
    engine.addComment(id, { body: 'Reiniciei o roteador' })
    engine.addComment(id, { body: 'Voltou' })
    expect(engine.listComments(id).map((c) => c.body)).toEqual(['Reiniciei o roteador', 'Voltou'])

    engine.changeStatus(id, 'CANCELADO')
    expect(() => engine.addComment(id, { body: 'tarde demais' })).toThrowError(/CANCELADO/)
  })

  it('o status do SLA é calculado na consulta: vira ESTOURADO quando o prazo passa; resolvido no prazo fica', () => {
    const { engine, advance } = engineAt(local(2026, 1, 5, 10))
    const late = engine.createTicket({ ...ticket, priority: 'P1' }) // prazo: +4h
    const onTime = engine.createTicket({ ...ticket, priority: 'P1' })
    engine.assign(onTime.id, 1)
    engine.changeStatus(onTime.id, 'RESOLVIDO')                       // resolvido agora, dentro do prazo

    advance(5 * 60 * MIN)

    expect(engine.getTicket(late.id).slaStatus).toBe('ESTOURADO')
    expect(engine.getTicket(onTime.id).slaStatus).toBe('DENTRO_DO_PRAZO') // resolvido no prazo continua no prazo
    engine.assign(late.id, 1)
    engine.changeStatus(late.id, 'CANCELADO')
    expect(engine.getTicket(late.id).slaStatus).toBeNull()               // cancelado não tem SLA
  })
})

describe('listagem e filtros', () => {
  function seeded() {
    const ctx = engineAt(local(2026, 1, 5, 10))
    const { engine, advance } = ctx
    for (let i = 1; i <= 25; i++) {
      engine.createTicket({ title: `Chamado ${i}`, description: i % 5 === 0 ? 'impressora quebrada' : 'outro', categoryId: i % 2 ? 1 : 2, priority: i % 3 ? 'P3' : 'P1' })
      advance(MIN)
    }
    return ctx
  }

  it('pagina com os mais recentes primeiro e limita o tamanho da página a 100', () => {
    const { engine } = seeded()
    const first = engine.listTickets({}, 0, 10)

    expect(first).toMatchObject({ page: 0, size: 10, totalElements: 25, totalPages: 3 })
    expect(first.content[0].title).toBe('Chamado 25')
    expect(engine.listTickets({}, 2, 10).content).toHaveLength(5)
    expect(engine.listTickets({}, 0, 1000).size).toBe(100)
  })

  it('combina filtros com E: prioridade, categoria, texto (título ou descrição, sem diferenciar maiúsculas)', () => {
    const { engine } = seeded()

    expect(engine.listTickets({ priority: 'P1' }).totalElements).toBe(8)                    // 3,6,...,24
    expect(engine.listTickets({ categoryId: 2 }).totalElements).toBe(12)
    expect(engine.listTickets({ q: 'IMPRESSORA' }).totalElements).toBe(5)                   // na descrição
    expect(engine.listTickets({ q: 'chamado 7' }).totalElements).toBe(1)
    expect(engine.listTickets({ priority: 'P1', categoryId: 2, q: 'impressora' }).totalElements).toBe(0)
  })

  it('filtra por técnico, sem técnico e situação do SLA', () => {
    const { engine, advance } = seeded()
    const some = engine.listTickets({}, 0, 3).content
    some.forEach((t) => engine.assign(t.id, 1))

    expect(engine.listTickets({ assigneeId: 1 }).totalElements).toBe(3)
    expect(engine.listTickets({ unassigned: true }).totalElements).toBe(22)
    expect(engine.listTickets({ slaStatus: 'ESTOURADO' }).totalElements).toBe(0)

    advance(5 * 60 * MIN) // os P1 (prazo de 4h) passam do prazo
    expect(engine.listTickets({ slaStatus: 'ESTOURADO' }).totalElements).toBeGreaterThanOrEqual(8)
  })
})

describe('persistência', () => {
  it('outro motor sobre o mesmo armazenamento enxerga os dados (é o que acontece ao recarregar a página)', () => {
    const storage = memoryStorage()
    const first = new LocalEngine(storage, () => local(2026, 1, 5, 10))
    first.createTicket(ticket)

    const reloaded = new LocalEngine(storage, () => local(2026, 1, 5, 11))
    expect(reloaded.listTickets({}).totalElements).toBe(1)
    expect(reloaded.createTicket(ticket).id).toBe(2) // a sequência de ids continua
  })

  it('backup: exportar e importar reproduz tudo; texto inválido é recusado sem estragar os dados', () => {
    const { engine } = engineAt(local(2026, 1, 5, 10))
    engine.createTicket(ticket)
    const backup = engine.exportJson()

    const other = engineAt(local(2026, 2, 1, 10)).engine
    expect(other.importJson(backup)).toBe(true)
    expect(other.listTickets({}).content[0].title).toBe('Sem rede')

    expect(other.importJson('isto não é json')).toBe(false)
    expect(other.importJson('{"version":2}')).toBe(false)
    expect(other.listTickets({}).totalElements).toBe(1)

    other.reset()
    expect(other.listTickets({}).totalElements).toBe(0)
  })

  it('sem localStorage (bloqueado), o app continua funcionando em memória e avisa que não persiste', () => {
    const blocked = vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => { throw new Error('bloqueado') })
    const storage = createBrowserStorage()
    blocked.mockRestore()

    expect(storage.persistent).toBe(false)
    const engine = new LocalEngine(storage, () => local(2026, 1, 5, 10))
    expect(engine.persistent).toBe(false)
    engine.createTicket(ticket)
    expect(engine.listTickets({}).totalElements).toBe(1)
  })

  it('dados corrompidos no armazenamento não quebram: começa do zero', () => {
    window.localStorage.setItem('sf.local.v1', '{quebrado')
    expect(new LocalEngine(createBrowserStorage()).listTickets({}).totalElements).toBe(0)
    window.localStorage.clear()
  })
})

describe('indicadores (mesmo cenário calculado à mão do teste de integração do backend)', () => {
  // Janeiro/2021. Horários LOCAIS (o fuso do navegador é quem manda, então o teste não depende do fuso da máquina).
  function scenario(): LocalState {
    const state = emptyState(local(2021, 1, 1))
    state.users.push({ id: 2, name: 't1x', role: 'TECNICO', active: true }, { id: 3, name: 't2x', role: 'TECNICO', active: true })
    let id = 0
    const add = (priority: LTicket['priority'], status: LTicket['status'], assigneeId: number | null,
                 created: number, resolved: number | null, due: number) => {
      state.tickets.push({ id: ++id, title: 't', description: 'd', categoryId: 1, priority, status, requesterId: 1, assigneeId, createdAt: created, updatedAt: created, resolvedAt: resolved, slaDueAt: due })
    }
    add('P1', 'RESOLVIDO', 2, local(2021, 1, 5, 7), local(2021, 1, 5, 7, 30), local(2021, 1, 5, 11))      // 30 min, no prazo
    add('P1', 'FECHADO', 2, local(2021, 1, 5, 9), local(2021, 1, 5, 15), local(2021, 1, 5, 13))          // 360 min, ESTOUROU
    add('P3', 'RESOLVIDO', 3, local(2021, 1, 6, 6), local(2021, 1, 6, 9), local(2021, 1, 7, 6))          // 180 min, no prazo
    add('P3', 'EM_ATENDIMENTO', 3, local(2021, 1, 20, 6), null, local(2021, 1, 21, 6))                   // aberto e já vencido
    add('P4', 'CANCELADO', null, local(2021, 1, 7, 6), null, local(2021, 1, 8, 6))                       // cancelado: nunca entra
    add('P2', 'RESOLVIDO', 3, local(2020, 12, 31, 20), local(2021, 1, 1, 3), local(2021, 1, 1, 21))      // aberto ANTES, resolvido no período: 420 min
    return state
  }
  const period = { from: '2021-01-01', to: '2021-01-31' }

  it('summary: abertos 4, resolvidos 4, dentro do SLA 3, MTTR 247,5 e mediana 270', () => {
    const s = analytics.summary(scenario(), period, local(2026, 1, 1))

    expect(s).toMatchObject({ opened: 4, resolved: 4, resolvedWithinSla: 3, slaComplianceRate: 0.75, avgResolutionMinutes: 247.5, medianResolutionMinutes: 270 })
    expect(s.current.breachedOpen).toBe(1)
    expect(s.current.byStatus).toMatchObject({ EM_ATENDIMENTO: 1, RESOLVIDO: 3, FECHADO: 1 })
  })

  it('por prioridade: sempre P1..P4, as sem movimento com taxa nula', () => {
    const items = analytics.byPriority(scenario(), period).items

    expect(items.map((i) => i.priority)).toEqual(['P1', 'P2', 'P3', 'P4'])
    expect(items.map((i) => i.opened)).toEqual([2, 0, 2, 0])
    expect(items.map((i) => i.resolved)).toEqual([2, 1, 1, 0])
    expect(items.map((i) => i.slaComplianceRate)).toEqual([0.5, 1, 1, null])
    expect(items.map((i) => i.avgResolutionMinutes)).toEqual([195, 420, 180, null])
  })

  it('por técnico e por categoria', () => {
    const tech = analytics.byTechnician(scenario(), period).items
    expect(tech.map((t) => [t.technicianName, t.resolved, t.avgResolutionMinutes, t.inProgressNow])).toEqual([['t1x', 2, 195, 0], ['t2x', 2, 300, 1]])
    const cat = analytics.byCategory(scenario(), period).items
    expect(cat).toHaveLength(1)
    expect(cat[0]).toMatchObject({ categoryName: 'Rede', opened: 4, slaComplianceRate: 0.75 })
  })

  it('série diária: 31 dias, com zeros; o dia é o do fuso local', () => {
    const items = analytics.timeline(scenario(), period).items

    expect(items).toHaveLength(31)
    expect(items[0].day).toBe('2021-01-01')
    expect(items.at(-1)?.day).toBe('2021-01-31')
    expect(items.find((i) => i.day === '2021-01-01')).toMatchObject({ opened: 0, resolved: 1 })
    expect(items.find((i) => i.day === '2021-01-05')).toMatchObject({ opened: 2, resolved: 2 })
    expect(items.find((i) => i.day === '2021-01-10')).toMatchObject({ opened: 0, resolved: 0 })
    expect(items.reduce((s, i) => s + i.opened, 0)).toBe(4)
  })

  it('sem nenhum chamado: números zerados e taxas nulas (nunca um enganoso 0%)', () => {
    const s = analytics.summary(emptyState(local(2026, 1, 1)), period, local(2026, 1, 1))
    expect(s).toMatchObject({ opened: 0, resolved: 0, slaComplianceRate: null, avgResolutionMinutes: null })
  })
})

describe('localRequest (o "servidor" do navegador)', () => {
  beforeEach(() => setEngine(new LocalEngine(memoryStorage(), () => local(2026, 1, 5, 10))))

  it('responde no mesmo formato da API e traduz filtros da query string', async () => {
    const created = await localRequest<{ id: number }>('/api/tickets', { method: 'POST', body: ticket })
    await localRequest('/api/tickets', { method: 'POST', body: { ...ticket, title: 'Outro', priority: 'P1' } })

    const page = await localRequest<{ totalElements: number; content: { title: string }[] }>('/api/tickets?priority=P1&page=0&size=5')
    expect(page.totalElements).toBe(1)
    expect(page.content[0].title).toBe('Outro')
    expect((await localRequest<{ id: number }>(`/api/tickets/${created.id}`)).id).toBe(created.id)
  })

  it('erros chegam como ApiError com o mesmo status do servidor (400 validação, 404, 422 regra de negócio)', async () => {
    await expect(localRequest('/api/tickets', { method: 'POST', body: { ...ticket, title: '' } })).rejects.toMatchObject({ status: 400 })
    await expect(localRequest('/api/tickets/999')).rejects.toMatchObject({ status: 404 })
    const { id } = await localRequest<{ id: number }>('/api/tickets', { method: 'POST', body: ticket })
    await expect(localRequest(`/api/tickets/${id}/status`, { method: 'PATCH', body: { status: 'FECHADO' } })).rejects.toBeInstanceOf(ApiError)
    await expect(localRequest(`/api/tickets/${id}/status`, { method: 'PATCH', body: { status: 'FECHADO' } })).rejects.toMatchObject({ status: 422 })
    await expect(localRequest('/api/rota-inexistente')).rejects.toMatchObject({ status: 404 })
  })

  it('a sugestão automática responde "indisponível" (o modelo Python não roda no navegador)', async () => {
    await expect(localRequest('/api/tickets/suggestions', { method: 'POST', body: {} })).resolves.toMatchObject({ available: false })
  })
})
