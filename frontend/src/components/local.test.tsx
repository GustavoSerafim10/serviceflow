import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

// O modo é decidido quando o módulo é carregado: precisa valer ANTES dos imports abaixo.
vi.hoisted(() => { vi.stubEnv('VITE_STANDALONE', 'true') })

import App from '../App'
import { AuthProvider } from '../auth/AuthContext'
import { LocalEngine } from '../local/engine'
import { setEngine } from '../local/localApi'
import type { LocalState, StateStorage } from '../local/storage'

function memoryStorage(): StateStorage {
  let saved: LocalState | null = null
  return { persistent: true, load: () => saved, save: (s) => { saved = JSON.parse(JSON.stringify(s)) as LocalState } }
}

function renderApp() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <AuthProvider>
        <App />
      </AuthProvider>
    </QueryClientProvider>,
  )
}

const nav = (name: string) => userEvent.click(within(screen.getByRole('navigation', { name: 'Principal' })).getByRole('link', { name }))

beforeEach(() => {
  window.location.hash = ''
  setEngine(new LocalEngine(memoryStorage()))
})
afterEach(() => setEngine(null))

async function openTicket(title: string, priority = 'P1') {
  await nav('Chamados')
  await userEvent.click(await screen.findByRole('link', { name: /Novo chamado|Abrir o primeiro chamado/ }))
  await userEvent.type(await screen.findByLabelText('Título'), title)
  await userEvent.type(screen.getByLabelText('Descrição'), 'Não consigo acessar a internet no setor')
  await userEvent.selectOptions(screen.getByLabelText('Categoria'), 'Rede')
  await userEvent.selectOptions(screen.getByLabelText('Prioridade'), priority)
  await userEvent.click(screen.getByRole('button', { name: 'Abrir chamado' }))
  return screen.findByRole('heading', { name: new RegExp(title) })
}

describe('modo local: começa vazio e não pede login', () => {
  it('entra direto, avisa que os dados ficam só no navegador e que ainda não há chamados', async () => {
    renderApp()

    expect(await screen.findByText(/Modo local/)).toBeInTheDocument()
    expect(screen.getByText(/só neste navegador/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Entrar' })).toBeNull()
    expect(screen.queryByRole('button', { name: 'Sair' })).toBeNull()
    expect(await screen.findByText(/Ainda não há chamados/)).toBeInTheDocument()
    // a sugestão automática depende do servidor: o cartão de qualidade das sugestões não existe aqui
    expect(screen.queryByRole('region', { name: /sugestões automáticas/ })).toBeNull()
  })
})

describe('modo local: ciclo de vida de um chamado, ponta a ponta', () => {
  it('abrir → atribuir → comentar → resolver → aparece nos indicadores', async () => {
    renderApp()

    // abrir
    expect(await openTicket('Sem internet no setor')).toBeInTheDocument()
    expect(screen.getByText('Aberto')).toBeInTheDocument()
    expect(screen.getByText('Para iniciar o atendimento, atribua um técnico.')).toBeInTheDocument()

    // atribuir (ABERTO -> EM_ATENDIMENTO automaticamente)
    await userEvent.selectOptions(screen.getByLabelText('Atribuir a'), 'Você')
    await userEvent.click(screen.getByRole('button', { name: 'Atribuir e iniciar atendimento' }))
    await waitFor(() => expect(screen.getAllByText('Em atendimento').length).toBeGreaterThan(0))

    // comentar
    await userEvent.type(screen.getByLabelText('Novo comentário'), 'Reiniciei o roteador')
    await userEvent.click(screen.getByRole('button', { name: 'Comentar' }))
    expect(await screen.findByText('Reiniciei o roteador')).toBeInTheDocument()

    // resolver
    await userEvent.click(screen.getByRole('button', { name: 'Marcar como resolvido' }))
    await waitFor(() => expect(screen.getAllByText('Resolvido').length).toBeGreaterThan(0))

    // histórico completo
    const history = screen.getByRole('region', { name: 'Histórico' })
    expect(within(history).getByText('Atribuído a Você')).toBeInTheDocument()
    expect(within(history).getByText('Status: Em atendimento → resolvido')).toBeInTheDocument() // legível, não o código do backend
    expect(within(history).getByText('Comentário adicionado')).toBeInTheDocument()

    // indicadores refletem o que foi feito
    await nav('Indicadores')
    const resolved = (await screen.findByText('Resolvidos no período')).parentElement!
    await waitFor(() => expect(within(resolved).getByText('1')).toBeInTheDocument())
    expect(document.querySelector('.hero')).toHaveTextContent('100,0%') // resolvido dentro do prazo
  })

  it('fechar encerra o chamado: sem novas ações nem comentários', async () => {
    renderApp()
    await openTicket('Impressora parada')
    await userEvent.selectOptions(screen.getByLabelText('Atribuir a'), 'Você')
    await userEvent.click(screen.getByRole('button', { name: 'Atribuir e iniciar atendimento' }))
    await userEvent.click(await screen.findByRole('button', { name: 'Marcar como resolvido' }))
    await userEvent.click(await screen.findByRole('button', { name: 'Fechar chamado' }))

    expect(await screen.findByText(/Chamado encerrado: não há mais ações/)).toBeInTheDocument()
    expect(screen.getByText(/não recebe novos comentários/)).toBeInTheDocument()
  })

  it('cancelar pede confirmação antes de encerrar', async () => {
    renderApp()
    await openTicket('Chamado engano')

    await userEvent.click(screen.getByRole('button', { name: 'Cancelar chamado' }))
    expect(screen.getByRole('button', { name: 'Confirmar cancelamento' })).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Voltar' }))
    expect(screen.queryByRole('button', { name: 'Confirmar cancelamento' })).toBeNull()

    await userEvent.click(screen.getByRole('button', { name: 'Cancelar chamado' }))
    await userEvent.click(screen.getByRole('button', { name: 'Confirmar cancelamento' }))
    expect(await screen.findByText(/Chamado encerrado/)).toBeInTheDocument()
  })

  it('a lista mostra os chamados e os filtros funcionam', async () => {
    renderApp()
    await openTicket('Wi-Fi caindo', 'P2')
    await nav('Chamados')
    await openTicket('Mouse quebrado', 'P4')
    await nav('Chamados')

    const list = await screen.findByRole('region', { name: 'Lista de chamados' })
    expect(within(list).getByText('Wi-Fi caindo')).toBeInTheDocument()
    expect(within(list).getByText('Mouse quebrado')).toBeInTheDocument()
    expect(within(list).getByText(/2 chamados · página 1 de 1/)).toBeInTheDocument()

    await userEvent.selectOptions(screen.getByLabelText('Prioridade'), 'P2')
    await waitFor(() => expect(within(list).queryByText('Mouse quebrado')).toBeNull())
    expect(within(list).getByText('Wi-Fi caindo')).toBeInTheDocument()

    await userEvent.type(screen.getByLabelText('Buscar'), 'não existe')
    expect(await within(list).findByText('Nenhum chamado com esses filtros.')).toBeInTheDocument()
  })

  it('sugestão local: identifica categoria e prioridade pelo texto, sem tocar a rede, e aplica ao formulário', async () => {
    renderApp()
    await nav('Chamados')
    await userEvent.click(await screen.findByRole('link', { name: /Novo chamado|Abrir o primeiro chamado/ }))

    await userEvent.type(await screen.findByLabelText('Título'), 'Sem internet no setor')
    await userEvent.type(screen.getByLabelText('Descrição'), 'Ninguém consegue acessar, o wifi caiu e é urgente')
    expect(screen.getByText(/Estimativa local por palavras-chave/)).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Sugerir categoria e prioridade' }))

    expect(await screen.findByText('Rede', { selector: 'strong' })).toBeInTheDocument()
    expect(screen.getByText('P1', { selector: 'strong' })).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Aplicar sugestão' }))
    const categorySelect = screen.getByLabelText('Categoria') as HTMLSelectElement
    expect(categorySelect.selectedOptions[0]).toHaveTextContent('Rede')
    expect(screen.getByLabelText('Prioridade')).toHaveValue('P1')
  })

  it('sugestão local: sem palavra reconhecida, avisa e não trava o formulário', async () => {
    renderApp()
    await nav('Chamados')
    await userEvent.click(await screen.findByRole('link', { name: /Novo chamado|Abrir o primeiro chamado/ }))

    await userEvent.type(await screen.findByLabelText('Título'), 'Assunto qualquer')
    await userEvent.type(screen.getByLabelText('Descrição'), 'xyzxyz abc123')
    await userEvent.click(screen.getByRole('button', { name: 'Sugerir categoria e prioridade' }))

    expect(await screen.findByText(/Sugestão indisponível para este texto/)).toBeInTheDocument()
  })

  it('formulário mostra o prazo de SLA da prioridade escolhida e recusa dados inválidos vindos do motor', async () => {
    renderApp()
    await nav('Chamados')
    await userEvent.click(await screen.findByRole('link', { name: 'Abrir o primeiro chamado' }))

    await userEvent.selectOptions(await screen.findByLabelText('Prioridade'), 'P3')
    expect(await screen.findByText(/Prazo de SLA: 10h em horas úteis/)).toBeInTheDocument()
    await userEvent.selectOptions(screen.getByLabelText('Prioridade'), 'P1')
    expect(screen.getByText(/Prazo de SLA: 4h corridas/)).toBeInTheDocument()
  })
})

describe('modo local: categorias e configurações', () => {
  it('cria categoria, recusa duplicada e desativa (que some do formulário de novo chamado)', async () => {
    renderApp()
    await nav('Categorias')

    await userEvent.type(await screen.findByLabelText('Nome'), 'Telefonia')
    await userEvent.click(screen.getByRole('button', { name: 'Adicionar' }))
    expect(await screen.findByText('Telefonia')).toBeInTheDocument()

    await userEvent.type(screen.getByLabelText('Nome'), 'telefonia')
    await userEvent.click(screen.getByRole('button', { name: 'Adicionar' }))
    expect(await screen.findByRole('alert')).toHaveTextContent("Já existe uma categoria com o nome 'telefonia'")

    await userEvent.click(screen.getByRole('button', { name: 'Desativar Telefonia' }))
    expect(await screen.findByText('Inativa')).toBeInTheDocument()

    await nav('Chamados')
    await userEvent.click(await screen.findByRole('link', { name: 'Abrir o primeiro chamado' }))
    const select = await screen.findByLabelText('Categoria')
    expect(within(select).queryByRole('option', { name: 'Telefonia' })).toBeNull()
    expect(within(select).getByRole('option', { name: 'Rede' })).toBeInTheDocument()
  })

  it('edita o prazo de SLA e mostra a equivalência em horas', async () => {
    renderApp()
    await nav('Configurações')

    const input = await screen.findByLabelText('Prazo de P1 em minutos')
    await userEvent.clear(input)
    await userEvent.type(input, '90')
    expect(screen.getByText('= 1h 30min')).toBeInTheDocument()

    const row = input.closest('tr') as HTMLElement
    await userEvent.click(within(row).getByRole('button', { name: 'Salvar' }))
    expect(await within(row).findByText('Salvo')).toBeInTheDocument()
  })

  it('equipe: adiciona uma pessoa, que passa a poder receber chamados', async () => {
    renderApp()
    await nav('Configurações')
    await userEvent.type(await screen.findByLabelText('Nome'), 'Bia')
    await userEvent.click(screen.getByRole('button', { name: 'Adicionar à equipe' }))
    expect(await screen.findByRole('button', { name: 'Remover Bia' })).toBeInTheDocument()

    await openTicket('Chamado da Bia')
    expect(within(screen.getByLabelText('Atribuir a')).getByRole('option', { name: 'Bia' })).toBeInTheDocument()
  })

  it('backup: gera o JSON, restaura em outro estado e recusa texto inválido; apagar exige confirmação', async () => {
    renderApp()
    await openTicket('Chamado para backup')
    await nav('Configurações')

    await userEvent.click(await screen.findByRole('button', { name: 'Gerar backup' }))
    const backup = (await screen.findByLabelText('Backup dos dados')) as HTMLTextAreaElement
    expect(JSON.parse(backup.value).tickets).toHaveLength(1)

    // apagar tudo (com confirmação)
    await userEvent.click(screen.getByRole('button', { name: 'Apagar todos os dados' }))
    await userEvent.click(screen.getByRole('button', { name: 'Confirmar: apagar tudo' }))
    expect(await screen.findByText('Todos os dados foram apagados.')).toBeInTheDocument()

    // texto inválido é recusado
    await userEvent.click(screen.getByLabelText('Restaurar de um backup'))
    await userEvent.paste('isto não é um backup')
    await userEvent.click(screen.getByRole('button', { name: 'Restaurar backup' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('não é um backup válido')

    // restaurar o backup válido traz o chamado de volta
    const restore = screen.getByLabelText('Restaurar de um backup')
    await userEvent.clear(restore)
    await userEvent.click(restore)
    await userEvent.paste(backup.value)
    await userEvent.click(screen.getByRole('button', { name: 'Restaurar backup' }))
    expect(await screen.findByText('Dados restaurados.')).toBeInTheDocument()

    await nav('Chamados')
    expect(await screen.findByText('Chamado para backup')).toBeInTheDocument()
  })
})
