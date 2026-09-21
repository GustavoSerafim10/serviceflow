# ServiceFlow

API REST de **gestão de chamados de TI (ITSM)** com controle de SLA, construída com Java 21 e Spring Boot 3.
Projeto de portfólio desenvolvido em etapas, com foco em arquitetura em camadas, segurança e regras de negócio reais.

## Funcionalidades

- **Autenticação JWT** com **refresh token** (rotação e detecção de reuso) e autorização por roles: `ADMIN`, `TECNICO`, `SOLICITANTE`
- **Usuários** com senha protegida por BCrypt, **troca de senha** (própria ou redefinição pelo ADMIN) que encerra as sessões anteriores
- **Categorias** de chamados (desativação em vez de exclusão)
- **Regras de SLA configuráveis** por prioridade (P1–P4), em **tempo corrido ou horário comercial** (fuso, expediente, dias úteis e feriados configuráveis)
- **Abertura de chamados** com **prazo de SLA calculado automaticamente**
- **Atribuição de técnico** e **fluxo de status** com permissões por papel
- **Indicador de SLA** por chamado: `DENTRO_DO_PRAZO` ou `ESTOURADO`
- **Comentários** e **histórico completo** (auditoria) de cada chamado
- **Busca com filtros combináveis e paginação**
- **Swagger UI**, tratamento global de erros (RFC 7807), migrations versionadas
- **Health checks** (Actuator: liveness/readiness) e healthcheck no Docker
- **Sugestão automática de categoria e prioridade** (serviço Python) durante o preenchimento do chamado, com degradação elegante se o serviço estiver fora do ar
- **Categorias padrão** já cadastradas (Rede, Hardware, Software, Acesso e Senha, E-mail, Impressora)

## Stack

| Área | Tecnologia |
|------|-----------|
| Linguagem / framework | Java 21, Spring Boot 3.3 |
| Persistência | Spring Data JPA (Hibernate), PostgreSQL 16, Flyway |
| Operação | Spring Boot Actuator (health), Docker healthcheck |
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

- Cada prioridade tem um prazo de resolução em minutos e um flag de **horário comercial**, editáveis pelo ADMIN.
  Padrões: **P1 = 4h** e **P2 = 8h** em tempo corrido (24x7); **P3 = 1 dia útil** (600 min) e **P4 = 3 dias úteis** (1800 min) contando apenas o expediente.
- O expediente (padrão: 08:00–18:00, segunda a sexta, fuso `America/Sao_Paulo`) e os feriados vêm da configuração `app.sla.business-hours`. Exemplo: um chamado P3 aberto na sexta às 16:00 vence na segunda às 16:00 (2h na sexta + 8h na segunda).
- Na abertura, o prazo é calculado pela regra da prioridade e **gravado** (mudar a regra depois não altera chamados já abertos).
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
- Faça login em `POST /api/auth/login`, copie o `accessToken` e clique em **Authorize** no Swagger.

```bash
curl -s -X POST localhost:8080/api/auth/login -H "Content-Type: application/json" \
  -d '{"email":"admin@serviceflow.local","password":"Admin@12345"}'
```

### Sessão: access token e refresh token

O login devolve um par de tokens. O **access token** (JWT, 15 min) autentica as requisições; o **refresh token** (aleatório, 7 dias, guardado no banco apenas como hash SHA-256) serve só para obter um novo par em `POST /api/auth/refresh`.
Cada refresh token é de **uso único** (rotação). Se um token já usado for apresentado de novo — sinal de vazamento — **todas as sessões do usuário são encerradas**. Trocar a senha também encerra todas as sessões.

### Health

`GET /actuator/health` (geral), `/actuator/health/liveness` e `/actuator/health/readiness` (inclui o banco). Nenhum outro endpoint do Actuator é exposto.

## Configuração

Todas as variáveis têm padrão para desenvolvimento local. **Em qualquer ambiente real, defina as suas** — os padrões são públicos.

| Variável | Descrição | Padrão (dev) |
|----------|-----------|--------------|
| `JWT_SECRET` | Chave HMAC em Base64 (mín. 256 bits) para assinar tokens | chave de exemplo |
| `ADMIN_EMAIL` / `ADMIN_PASSWORD` | ADMIN criado quando não há usuários | `admin@serviceflow.local` / `Admin@12345` |
| `DB_URL` / `DB_USER` / `DB_PASSWORD` | Conexão com o PostgreSQL | `localhost:5432/serviceflow` |

Ajustes finos (no `application.yml`): `app.jwt.expiration-minutes` (access token, 15), `app.jwt.refresh-expiration-days` (7) e `app.sla.business-hours.*` (fuso, expediente, dias úteis, feriados).

## API

| Método | Rota | Acesso |
|--------|------|--------|
| POST | `/api/auth/login` · `/api/auth/refresh` · `/api/auth/logout` | público |
| GET | `/api/users/me` | autenticado |
| POST | `/api/users/me/password` (troca a própria senha) | autenticado |
| PUT | `/api/users/{id}/password` (redefine a senha) | ADMIN |
| GET · POST · PUT · DELETE | `/api/users[/{id}]` | ADMIN |
| GET | `/api/categories[/{id}]` | autenticado |
| POST · PUT · DELETE | `/api/categories[/{id}]` | ADMIN |
| GET | `/api/sla-rules` | autenticado |
| PUT | `/api/sla-rules/{priority}` (`resolutionMinutes`, `businessHours`) | ADMIN |
| GET | `/actuator/health[/liveness\|/readiness]` | público |
| POST · GET | `/api/tickets` | autenticado (GET filtra por visibilidade) |
| POST | `/api/tickets/suggestions` (sugere categoria e prioridade) | autenticado |
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

- **Unitários** (JUnit 5 + Mockito): regras de negócio dos services, cálculo de SLA e do calendário comercial com relógio fixo, máquina de estados, JWT, rotação e reuso de refresh token.
- **Integração** (`ServiceFlowApiIntegrationTest`): aplicação completa com MockMvc, segurança e migrations reais contra um **PostgreSQL efêmero via Testcontainers** — ciclo de vida do chamado, autorização, visibilidade, validação, refresh token e troca de senha. Requerem Docker em execução; sem Docker, são **pulados** (aparecem como *skipped*).

## Decisões de projeto

- **DTOs separados das entidades**: o contrato da API não depende do modelo do banco, e o hash de senha nunca sai por descuido.
- **Flyway manda no schema** (`ddl-auto: validate`): o Hibernate só confere; toda mudança é uma migration versionada.
- **Autorização em duas camadas**: `@PreAuthorize` para regras só de role; regras que dependem dos dados do chamado (dono, responsável) ficam no service.
- **Visibilidade no servidor**: solicitantes só consultam os próprios chamados; chamado alheio responde `404` para não confirmar sua existência.
- **`Clock` injetável**: o tempo é dependência, o que torna os prazos de SLA testáveis de forma determinística.
- **Ordenação não configurável pelo cliente** na listagem, evitando inferência de dados por ordenação em campos aninhados.
- **Histórico só-inserção**, gravado na mesma transação da ação que o originou.

## Intelligence — V2 (em andamento)

Microsserviço **Python (FastAPI + scikit-learn)** em [`intelligence/`](intelligence/) que sugere **categoria e prioridade** de um chamado a partir do título e da descrição. A API Java o consome em `POST /api/tickets/suggestions`.

**Como a integração funciona**
- O formulário chama a sugestão **enquanto o usuário preenche** título e descrição; o usuário aceita ou ignora. Abrir o chamado (`POST /api/tickets`) **não depende** do serviço Python.
- O serviço Python devolve *nomes* de categoria; a API os casa com as **categorias ativas** do sistema (ignorando maiúsculas/minúsculas) e devolve o `id` pronto para uso. Uma categoria desativada pelo ADMIN nunca é sugerida.
- Sugestões principais com confiança abaixo de `app.intelligence.min-confidence` (padrão 30%) não são oferecidas.
- **Degradação elegante:** serviço desligado, fora do ar, lento (timeouts de 0,5 s/2 s) ou com resposta inválida → `200` com `"available": false`. Nada quebra e a API segue saudável.
- Configuração: `INTELLIGENCE_URL` (padrão `http://localhost:8000`; no Compose, `http://intelligence:8000`) e `app.intelligence.enabled`.

```
POST /api/tickets/suggestions   {"title": "...", "description": "..."}
→ { "available": true, "modelVersion": "v1-ad34633d",
    "category": {"id": 4, "name": "Impressora", "confidence": 0.91, "alternatives": [...]},
    "priority": {"priority": "P3", "confidence": 0.66, "alternatives": [...]} }
```

**Serviço Python isolado**

```bash
cd intelligence
python -m venv .venv && .venv\Scripts\activate      # Linux/macOS: source .venv/bin/activate
pip install -r requirements-dev.txt
python -m app.train                                  # treina e mostra as métricas de validação cruzada
uvicorn app.main:app --reload                        # http://localhost:8000/docs
pytest                                               # testes
```

Ou pelo Docker (o modelo é treinado durante o build): `docker compose --profile app up --build`.

```
POST /v1/suggestions   {"title": "...", "description": "..."}      (contrato interno do serviço Python)
→ { "category": {"label": "Impressora", "confidence": 0.91, "alternatives": [...]},
    "priority": {"label": "P3", "confidence": 0.66, "alternatives": [...]},
    "modelVersion": "v1-ad34633d" }
GET  /health
```

**Como funciona:** TF-IDF sobre n-gramas de caracteres + regressão logística, um modelo para categoria e outro para prioridade; a confiança é a probabilidade do rótulo. O treino (`app/train.py`) avalia com validação cruzada em dados não vistos e salva o modelo versionado pelo hash do dataset.

**Limites conhecidos (importante):**
- O dataset inicial (`data/tickets.csv`) tem **108 chamados escritos à mão**. Em validação cruzada: **~73% de acurácia na categoria** (acaso ≈ 17%) e **~57–60% na prioridade** (chute "sempre P4" ≈ 32%). É suficiente para demonstrar o pipeline, **não para produção**: o ganho real virá de chamados históricos reais.
- A prioridade é inerentemente mais difícil de inferir só pelo texto (depende de impacto, quantidade de usuários afetados, contexto). Trate-a como **sugestão** com a confiança exibida, nunca como decisão automática.
- O serviço ainda não tem autenticação: é interno e não deve ser exposto publicamente.

## Roadmap

- **V1 (esta versão)**: API completa com SLA (corrido e comercial), segurança (JWT + refresh token), documentação e health checks.
- **V2 — Python / Intelligence** *(em andamento)*: ✅ serviço FastAPI de sugestão · ✅ integração com a API Java · próximos: registrar se o usuário aceitou a sugestão (feedback para melhorar o modelo), retreino com chamados reais, futuramente LLM.
- **V3 — Front-end / Analytics**: React, dashboard, indicadores de SLA, MTTR, volume por categoria, chamados semelhantes.
