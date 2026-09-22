import type { Priority } from '../api/types'

/**
 * Sugestão de categoria e prioridade SEM servidor: um classificador simples por palavras-chave,
 * rodando inteiramente no navegador. Não é o modelo de aprendizado de máquina do serviço Python (que
 * exige o backend) — é um heurística mais simples, mas gratuita e imediata, para o modo local não ficar
 * sem nenhuma ajuda ao preencher o chamado.
 *
 * Como funciona: o texto do chamado é comparado contra listas de termos típicos de cada categoria/
 * prioridade; quem tiver mais palavras batendo, ganha. Sem nenhuma palavra batendo, devolve `null`
 * (não inventa uma sugestão sem base — mesma regra do serviço Python: melhor nenhuma sugestão do que
 * uma errada com cara de certeza).
 */

export interface LocalSuggestion {
  category: { label: string; score: number } | null
  priority: { priority: Priority; score: number } | null
}

// Termos SEM acento (o texto do usuário é normalizado antes de comparar) e em minúsculas. As chaves
// batem com os nomes das categorias padrão (ver local/storage.ts); uma categoria renomeada ou removida
// pelo usuário simplesmente não terá correspondência — quem resolve o nome para um id é o chamador.
const CATEGORY_KEYWORDS: Record<string, string[]> = {
  Rede: [
    'internet', 'rede', 'wifi', 'wi-fi', 'vpn', 'conexao', 'conectar', 'desconectando',
    'firewall', 'roteador', 'switch', 'cabo de rede', 'sinal', 'latencia', 'ping', 'dns',
  ],
  Hardware: [
    'computador', 'notebook', 'desktop', 'maquina', 'monitor', 'tela', 'teclado', 'mouse',
    'fonte', 'memoria', 'hd ', 'disco', 'placa', 'nobreak', 'bateria', 'nao liga', 'ligar',
    'webcam', 'fone', 'headset', 'superaquecendo', 'travando o computador',
  ],
  Software: [
    'sistema', 'programa', 'aplicativo', 'instalar', 'instalacao', 'atualizacao', 'licenca',
    'excel', 'word', 'planilha', 'erp', 'tela azul', 'trava', 'travando', 'bug', 'versao',
  ],
  'Acesso e Senha': [
    'senha', 'login', 'usuario', 'bloqueada', 'bloqueado', 'autenticacao', 'permissao',
    'acesso negado', 'cadastro de acesso', 'token', 'desbloquear', 'esqueci a senha', 'conta bloqueada',
  ],
  'E-mail': [
    'e-mail', 'email', 'outlook', 'caixa de entrada', 'spam', 'enviar mensagem', 'receber email', 'anexo',
  ],
  Impressora: [
    'impressora', 'imprimir', 'impressao', 'toner', 'scanner', 'digitalizar', 'papel atolado', 'cartucho',
  ],
}

// Ordem = prioridade de desempate: se o texto tiver sinais de dois níveis, o mais urgente decide.
const PRIORITY_KEYWORDS: [Priority, string[]][] = [
  ['P1', [
    'parou', 'parado', 'ninguem consegue', 'todos os usuarios', 'fora do ar', 'producao parada',
    'critico', 'urgente', 'emergencia', 'caiu', 'toda a empresa', 'todo o setor', 'perda de dados',
  ]],
  ['P2', [
    'nao consigo', 'nao consegue', 'impede', 'bloqueado', 'bloqueando', 'varios usuarios',
    'equipe inteira', 'atrapalhando', 'importante', 'hoje preciso',
  ]],
  ['P3', [
    'as vezes', 'de vez em quando', 'intermitente', 'lento', 'lentidao', 'demorando',
  ]],
  ['P4', [
    'duvida', 'sugestao', 'quando possivel', 'sem pressa', 'gostaria de saber', 'solicito', 'seria bom',
  ]],
]

/** minúsculas + sem acento, para "não consigo" bater com "nao consigo" no dicionário. */
function normalize(text: string): string {
  return text.toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '')
}

function bestMatch<T extends string>(text: string, entries: [T, string[]][]): { key: T; hits: number } | null {
  let best: { key: T; hits: number } | null = null
  for (const [key, keywords] of entries) {
    const hits = keywords.filter((k) => text.includes(k)).length
    if (hits > 0 && (best === null || hits > best.hits)) best = { key, hits }
  }
  return best
}

/** 0.45 na primeira palavra batendo, subindo com cada acerto extra, sem nunca soar "certo demais". */
const scoreFor = (hits: number) => Math.min(0.45 + hits * 0.12, 0.92)

export function suggestLocally(title: string, description: string): LocalSuggestion {
  const text = normalize(`${title} ${description}`)

  const category = bestMatch(text, Object.entries(CATEGORY_KEYWORDS) as [string, string[]][])
  const priority = bestMatch(text, PRIORITY_KEYWORDS)

  return {
    category: category && { label: category.key, score: scoreFor(category.hits) },
    priority: priority && { priority: priority.key, score: scoreFor(priority.hits) },
  }
}
