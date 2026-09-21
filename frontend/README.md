# ServiceFlow — front-end (painel de indicadores)

React 19 + TypeScript + Vite. Consome os endpoints `/api/analytics/*` da API Java. Documentação geral do projeto no [README da raiz](../README.md).

```bash
npm install
npm run dev        # http://localhost:5173 (o Vite encaminha /api para http://localhost:8080)
npm test           # Vitest + Testing Library
npm run build      # checagem de tipos (tsc) + build de produção em dist/
npm run lint
```

Para ver dados sem cadastrar chamados, suba a API com `DEMO_DATA=true` (ver README da raiz) e entre com
`admin@serviceflow.local` / `Admin@12345`.

## Estrutura

```
src/
├── api/            client.ts (fetch + renovação de sessão), endpoints.ts, types.ts
├── auth/           tokenStore.ts (onde os tokens vivem), AuthContext.tsx (estado da sessão)
├── components/     Dashboard, Kpis, PeriodFilter, ChartCard, DataTable, Login, Header
│   └── charts/     LineChart e BarChart (SVG/HTML feitos à mão), Tooltip
├── lib/            format, period, fold, scale: funções puras e testadas
└── styles.css      tokens de design (claro/escuro) + estilos
```

## Decisões

- **Sem biblioteca de gráficos.** Dois gráficos simples feitos à mão (SVG/HTML) seguem exatamente as especificações
  de visualização adotadas: um eixo só, linhas de 2px, barras de até 24px com ponta arredondada, grade discreta,
  crosshair, tooltip que lista todas as séries e o mesmo comportamento por **teclado**. Todo gráfico tem uma
  **tabela-gêmea** (botão "Ver tabela"): nada fica acessível só pelo tooltip.
- **Cores por função, validadas.** Duas cores categóricas para abertos/resolvidos; prioridade (P1–P4) é uma escala
  *ordinal*, então usa uma rampa de um matiz só (no tema escuro a âncora inverte: o mais urgente fica o mais claro);
  categorias nominais usam uma cor só. Status (SLA estourado) sempre com ícone + texto. O tema escuro é um conjunto
  próprio de passos, não uma inversão automática.
- **Renovação de sessão single-flight.** O refresh token da API é de uso único e a reapresentação de um token já
  usado encerra todas as sessões. Por isso `client.ts` garante **uma única renovação por vez** (várias requisições com
  401, o `StrictMode` do React e várias abas do navegador via Web Locks). Há testes para esses cenários.
- **Tokens.** O access token vive só em memória; o refresh token, em `localStorage` (trade-off documentado em
  `tokenStore.ts`: um cookie HttpOnly seria melhor, mas exigiria mudar a API). Em produção, o nginx envia uma
  Content-Security-Policy restritiva para reduzir o impacto de um XSS.
- **Mesma origem, sem CORS.** Em desenvolvimento o Vite faz proxy de `/api`; em produção, o nginx.
- **Refetch mantém o quadro.** Ao trocar o período, os cartões ficam esmaecidos até os dados novos chegarem — sem
  esqueleto nem salto de layout.
- **Papéis.** ADMIN vê tudo (inclusive a qualidade das sugestões); TECNICO vê os indicadores; SOLICITANTE recebe um
  aviso (a API também recusa com 403).
