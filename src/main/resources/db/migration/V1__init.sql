-- V1__init.sql
--
-- Migração inicial da Etapa 1. Ainda não existem entidades de domínio
-- (usuários, categorias, chamados) — isso entra na Etapa 2.
--
-- O único objetivo desta migration é provar que o pipeline do Flyway está
-- funcionando: ao rodar a aplicação, esta tabela deve aparecer no banco e
-- uma linha em "flyway_schema_history" deve registrar que V1 foi aplicada.

CREATE TABLE IF NOT EXISTS flyway_healthcheck (
    id SERIAL PRIMARY KEY,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);
