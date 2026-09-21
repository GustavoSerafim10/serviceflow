# ServiceFlow

Sistema de Gestão de Chamados de TI (ITSM) — projeto de portfólio construído
em etapas, aprendendo Java/Spring Boot na prática.

## Status atual

| Etapa | Entrega | Situação |
|-------|---------|----------|
| 1 | Esqueleto Spring Boot + PostgreSQL (Docker) + Flyway | ✅ |
| 2 | CRUD de categorias (DTOs, validação, tratamento global de erros) | ✅ |
| 3 | Usuários, autenticação JWT e autorização por roles | 🚧 em validação |

### Autenticação e autorização (Etapa 3)

- Login em `POST /api/auth/login` devolve um JWT; as demais rotas exigem
  `Authorization: Bearer <token>`.
- Roles: `ADMIN`, `TECNICO`, `SOLICITANTE`. Senhas guardadas com BCrypt.
- `ADMIN`: gerencia usuários (`/api/users`) e escreve em categorias.
  Qualquer usuário autenticado lê categorias e consulta `/api/users/me`.
- No primeiro boot, com a tabela vazia, é criado um `ADMIN` inicial
  (`admin@serviceflow.local` / `Admin@12345` por padrão).
  **Em qualquer ambiente real, defina `ADMIN_EMAIL`, `ADMIN_PASSWORD` e
  `JWT_SECRET` como variáveis de ambiente.**

## Stack (Fase 1 — só Java)

- Java 21
- Spring Boot 3.3
- Spring Data JPA
- PostgreSQL
- Flyway
- Bean Validation
- Spring Security + JWT (JJWT)
- Lombok
- JUnit 5 + Mockito
- Docker / Docker Compose

## Como rodar

Pré-requisitos: Java 21, Maven (ou usar o `mvnw` quando gerado via Initializr),
Docker e Docker Compose.

```bash
# 1. sobe o banco Postgres em container
docker compose up -d

# 2. roda a aplicação
./mvnw spring-boot:run
# (ou, se não tiver o wrapper: mvn spring-boot:run)
```

Se tudo estiver certo, o log mostra o Flyway aplicando `V1__init.sql` e o
Tomcat subindo na porta 8080, sem erros de conexão com o banco.

## Como validar

```bash
# entra no container do banco
docker exec -it serviceflow-db psql -U serviceflow -d serviceflow

# dentro do psql:
\dt                     -- deve listar flyway_healthcheck e flyway_schema_history
SELECT * FROM flyway_schema_history;   -- deve mostrar a migration V1 aplicada
```

## Roadmap

- **Fase 1 (Java/Spring Boot)** — autenticação JWT, usuários, categorias,
  regras de SLA, chamados, prioridade, atribuição, status, comentários,
  histórico, filtros, Swagger, testes.
- **Fase 2 (Python/FastAPI)** — microsserviço de sugestão de categoria e
  prioridade, classificação de chamados.
- **Fase 3 (React)** — dashboard, indicadores de SLA, MTTR, chamados
  semelhantes.
