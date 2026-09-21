# ServiceFlow

Sistema de Gestão de Chamados de TI (ITSM) — projeto de portfólio construído
em etapas, aprendendo Java/Spring Boot na prática.

## Status atual: Etapa 1 — Esqueleto conectado ao PostgreSQL

Nesta etapa a aplicação ainda não tem regra de negócio nenhuma. O único
objetivo é provar que:

1. o projeto Maven compila e sobe com Spring Boot;
2. a aplicação consegue conectar no PostgreSQL (via Docker Compose);
3. o Flyway roda a primeira migration com sucesso.

## Stack (Fase 1 — só Java)

- Java 21
- Spring Boot 3.3
- Spring Data JPA
- PostgreSQL
- Flyway
- Bean Validation
- Lombok (a partir da Etapa 2)
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
