# ServiceFlow

API REST de **gestão de chamados de TI (ITSM)** com controle de SLA, construída com Java 21 e Spring Boot 3.
Projeto de portfólio desenvolvido em etapas, com foco em arquitetura em camadas, segurança e regras de negócio reais.

## Funcionalidades

- **Autenticação JWT** e autorização por roles: `ADMIN`, `TECNICO`, `SOLICITANTE`
- **Usuários** com senha protegida por BCrypt (gestão pelo ADMIN)
- **Categorias** de chamados (desativação em vez de exclusão)
- **Regras de SLA configuráveis** por prioridade (P1–P4)
- **Abertura de chamados** com **prazo de SLA calculado automaticamente**
- **Atribuição de técnico** e **fluxo de status** com permissões por papel
- **Indicador de SLA** por chamado: `DENTRO_DO_PRAZO` ou `ESTOURADO`
- **Comentários** e **histórico completo** (auditoria) de cada chamado
- **Busca com filtros combináveis e paginação**
- **Swagger UI**, tratamento global de erros (RFC 7807), migrations versionadas

## Stack

| Área | Tecnologia |
|------|-----------|
| Linguagem / framework | Java 21, Spring Boot 3.3 |
| Persistência | Spring Data JPA (Hibernate), PostgreSQL 16, Flyway |
| Segurança | Spring Security, JWT (JJWT), BCrypt |
| Validação / docs | Bean Validation, springdoc-openapi (Swagger UI) |
| Testes | JUnit 5, Mockito, AssertJ, MockMvc, Testcontainers |
| Infra | Docker, Docker Compose, Maven |

## Arquitetura

```
Requisição HTTP
   │
   ▼
JwtAuthenticationFilter ── valida o token e identifica o usuário
   │
   ▼
Controller ──► Service ──► Repository ──► PostgreSQL
 (HTTP, DTOs)   (regras)    (Spring Data)   (schema via Flyway)
```

```
src/main/java/com/gustavoserafim/serviceflow/
├── config/       SecurityConfig, OpenApiConfig, AppConfig (Clock), AdminBootstrap
├── security/     JwtService, JwtAuthenticationFilter, CustomUserDetailsService, CurrentUserProvider
├── controller/   camada web: rotas e códigos HTTP (sem regra de negócio)
├── service/      regras de negócio (TicketService, SlaCalculator, ...)
├── repository/   acesso a dados (+ TicketSpecifications: filtros dinâmicos)
├── entity/       modelo JPA (Ticket, User, SlaRule, enums de domínio)
├── dto/          contratos da API (records) — entidades nunca saem da API
└── exception/    exceções de domínio + GlobalExceptionHandler

src/main/resources/db/migration/   V1..V5: schema versionado (Flyway)
```

## Regras de negócio

### Permissões

| Ação | ADMIN | TECNICO | SOLICITANTE |
|------|:----:|:-------:|:-----------:|
| Gerenciar usuários e regras de SLA, escrever categorias | ✅ | — | — |
| Abrir chamado | ✅ | ✅ | ✅ |
| Ver chamados | todos | todos | **só os próprios** |
| Atribuir técnico | qualquer técnico | **só a si mesmo** | — |
| Resolver chamado | ✅ | se for o responsável | — |
| Cancelar (ainda `ABERTO`), fechar ou reabrir | ✅ | — | se for o dono |

### Fluxo de status

```
ABERTO ──(atribuir técnico)──► EM_ATENDIMENTO ──► RESOLVIDO ──► FECHADO
   │                                 │               │
   └────────► CANCELADO ◄────────────┘               └──(reabrir)──► EM_ATENDIMENTO
```

`ABERTO → EM_ATENDIMENTO` só ocorre pela atribuição de um técnico. `FECHADO` e `CANCELADO` são estados finais.

### SLA

- Cada prioridade tem um prazo de resolução em minutos (padrão: P1 = 4h, P2 = 8h, P3 = 24h, P4 = 72h), editável pelo ADMIN.
- Na abertura, `prazo = abertura + minutos da prioridade` e o valor é **gravado** (mudar a regra depois não altera chamados já abertos).
- O status do SLA é **calculado na consulta**: chamado em aberto compara "agora" com o prazo; chamado resolvido compara a data de resolução com o prazo. Chamados cancelados não têm SLA. Reabrir um chamado reinicia a contagem em aberto.

## Executando

Pré-requisitos: **Java 21**, **Maven 3.9+** e **Docker**.

### Opção A — desenvolvimento (banco no Docker, API pelo Maven)

```bash
docker compose up -d        # sobe apenas o PostgreSQL
mvn spring-boot:run         # sobe a API em http://localhost:8080
```

### Opção B — tudo no Docker

```bash
docker compose --profile app up --build
```

### Primeiro acesso

- Swagger UI: <http://localhost:8080/swagger-ui.html>
- Na primeira execução (banco vazio) é criado um ADMIN:
  `admin@serviceflow.local` / `Admin@12345`
- Faça login em `POST /api/auth/login`, copie o `token` e clique em **Authorize** no Swagger.

```bash
curl -s -X POST localhost:8080/api/auth/login -H "Content-Type: application/json" \
  -d '{"email":"admin@serviceflow.local","password":"Admin@12345"}'
```

## Configuração

Todas as variáveis têm padrão para desenvolvimento local. **Em qualquer ambiente real, defina as suas** — os padrões são públicos.

| Variável | Descrição | Padrão (dev) |
|----------|-----------|--------------|
| `JWT_SECRET` | Chave HMAC em Base64 (mín. 256 bits) para assinar tokens | chave de exemplo |
| `ADMIN_EMAIL` / `ADMIN_PASSWORD` | ADMIN criado quando não há usuários | `admin@serviceflow.local` / `Admin@12345` |
| `DB_URL` / `DB_USER` / `DB_PASSWORD` | Conexão com o PostgreSQL | `localhost:5432/serviceflow` |

O token expira em 60 minutos (`app.jwt.expiration-minutes`).

## API

| Método | Rota | Acesso |
|--------|------|--------|
| POST | `/api/auth/login` | público |
| GET | `/api/users/me` | autenticado |
| GET · POST · PUT · DELETE | `/api/users[/{id}]` | ADMIN |
| GET | `/api/categories[/{id}]` | autenticado |
| POST · PUT · DELETE | `/api/categories[/{id}]` | ADMIN |
| GET | `/api/sla-rules` | autenticado |
| PUT | `/api/sla-rules/{priority}` | ADMIN |
| POST · GET | `/api/tickets` | autenticado (GET filtra por visibilidade) |
| GET | `/api/tickets/{id}` | dono, técnicos e admin |
| PUT | `/api/tickets/{id}/assignment` | ADMIN, TECNICO |
| PATCH | `/api/tickets/{id}/status` | conforme a matriz de permissões |
| GET · POST | `/api/tickets/{id}/comments` | quem enxerga o chamado |
| GET | `/api/tickets/{id}/history` | quem enxerga o chamado |

**Filtros** em `GET /api/tickets` (todos opcionais, combinados com E):
`status`, `priority`, `categoryId`, `assigneeId`, `unassigned`, `requesterId`, `slaStatus`, `q` (título/descrição), `page`, `size` (máx. 100).
Ordenação fixa: mais recentes primeiro.

Exemplo — fila de chamados críticos estourados e sem responsável:

```
GET /api/tickets?priority=P1&slaStatus=ESTOURADO&unassigned=true
```

Erros seguem o formato **ProblemDetail (RFC 7807)**: `400` validação/JSON inválido · `401` sem autenticação · `403` sem permissão · `404` não encontrado · `409` conflito (duplicidade) · `422` regra de negócio violada.

## Testes

```bash
mvn test
```

- **Unitários** (JUnit 5 + Mockito): regras de negócio dos services, cálculo de SLA com relógio fixo, máquina de estados, geração/validação de JWT.
- **Integração** (`ServiceFlowApiIntegrationTest`): aplicação completa com MockMvc, segurança e migrations reais contra um **PostgreSQL efêmero via Testcontainers** — ciclo de vida do chamado, autorização, visibilidade e validação. Requerem Docker em execução; sem Docker, são **pulados** (aparecem como *skipped*).

## Decisões de projeto

- **DTOs separados das entidades**: o contrato da API não depende do modelo do banco, e o hash de senha nunca sai por descuido.
- **Flyway manda no schema** (`ddl-auto: validate`): o Hibernate só confere; toda mudança é uma migration versionada.
- **Autorização em duas camadas**: `@PreAuthorize` para regras só de role; regras que dependem dos dados do chamado (dono, responsável) ficam no service.
- **Visibilidade no servidor**: solicitantes só consultam os próprios chamados; chamado alheio responde `404` para não confirmar sua existência.
- **`Clock` injetável**: o tempo é dependência, o que torna os prazos de SLA testáveis de forma determinística.
- **Ordenação não configurável pelo cliente** na listagem, evitando inferência de dados por ordenação em campos aninhados.
- **Histórico só-inserção**, gravado na mesma transação da ação que o originou.

## Roadmap

- **V1 (esta versão)**: API completa com SLA, segurança e documentação.
  Próximos refinamentos: troca de senha, refresh token, SLA em horário comercial, Actuator/health.
- **V2 — Python / Intelligence**: FastAPI, sugestão de categoria e prioridade, classificação de chamados.
- **V3 — Front-end / Analytics**: React, dashboard, indicadores de SLA, MTTR, volume por categoria, chamados semelhantes.
