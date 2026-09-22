import { describe, expect, it } from 'vitest'
import { suggestLocally } from './suggest'

describe('suggestLocally', () => {
  it.each([
    ['Sem internet no setor', 'Ninguém consegue acessar a internet, o wifi caiu de vez', 'Rede'],
    ['Computador não liga', 'A fonte parece ter queimado, monitor sem imagem', 'Hardware'],
    ['Erro no sistema', 'O programa trava toda vez que eu abro a planilha do Excel', 'Software'],
    ['Esqueci minha senha', 'Não consigo fazer login, minha conta está bloqueada', 'Acesso e Senha'],
    ['Não recebo e-mails', 'A caixa de entrada do Outlook parou de sincronizar', 'E-mail'],
    ['Impressora sem toner', 'Preciso imprimir um relatório mas o cartucho acabou', 'Impressora'],
  ])('identifica a categoria pelo texto: %s', (title, description, expected) => {
    expect(suggestLocally(title, description).category?.label).toBe(expected)
  })

  it('funciona sem acento no texto do usuário (mesmo dicionário, comparação normalizada)', () => {
    // "não", "está" sem acento — não pode fazer a palavra-chave "não consigo" deixar de bater
    expect(suggestLocally('Sem acesso', 'nao consigo entrar, minha senha esta bloqueada').category?.label)
      .toBe('Acesso e Senha')
  })

  it('sem nenhuma palavra reconhecida, não inventa categoria nem prioridade', () => {
    const result = suggestLocally('xyzxyz', 'abc123 qwe')
    expect(result.category).toBeNull()
    expect(result.priority).toBeNull()
  })

  it.each([
    ['Sistema fora do ar', 'Ninguém da empresa consegue trabalhar, é urgente', 'P1'],
    ['Não consigo emitir nota', 'O sistema bloqueando o fechamento do caixa, é importante hoje', 'P2'],
    ['Rede lenta', 'De vez em quando a conexão fica intermitente', 'P3'],
    ['Dúvida sobre o sistema', 'Gostaria de saber como exportar um relatório, sem pressa', 'P4'],
  ])('identifica a prioridade pelo texto: %s', (title, description, expected) => {
    expect(suggestLocally(title, description).priority?.priority).toBe(expected)
  })

  it('em caso de sinais de mais de um nível, o mais urgente decide o empate', () => {
    // "urgente" (P1) e "duvida" (P4) no mesmo texto: P1 vence, por ser o mais crítico dos dois
    const result = suggestLocally('x', 'tenho uma duvida urgente sobre o sistema')
    expect(result.priority?.priority).toBe('P1')
  })

  it('a confiança cresce com mais palavras batendo, sem nunca soar "certeza absoluta"', () => {
    const weak = suggestLocally('Rede', 'internet lenta')
    const strong = suggestLocally('Sem internet, wifi e vpn fora do ar', 'firewall bloqueando a conexao, sinal ruim')

    expect(weak.category!.score).toBeLessThan(strong.category!.score)
    expect(strong.category!.score).toBeLessThanOrEqual(0.92)
  })

  it('categoria e prioridade são independentes: uma pode faltar sem afetar a outra', () => {
    const result = suggestLocally('Chamado', 'e urgente')
    expect(result.category).toBeNull()
    expect(result.priority?.priority).toBe('P1')
  })
})
