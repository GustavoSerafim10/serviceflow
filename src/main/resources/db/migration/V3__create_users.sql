-- V3__create_users.sql
--
-- Etapa 3: usuários do sistema. Cada usuário tem exatamente uma role:
--   ADMIN        -> gerencia usuários, categorias e regras de SLA
--   TECNICO      -> atende chamados
--   SOLICITANTE  -> abre e acompanha os próprios chamados
--
-- Nome da tabela no plural ("users"): "user" é palavra reservada no PostgreSQL.
-- A senha NUNCA é guardada em texto puro: password_hash recebe o hash BCrypt
-- (60 caracteres; VARCHAR(100) dá folga).

CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    name          VARCHAR(100) NOT NULL,
    email         VARCHAR(150) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    role          VARCHAR(20)  NOT NULL,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT ck_users_role CHECK (role IN ('ADMIN', 'TECNICO', 'SOLICITANTE'))
);

-- E-mail é o "login": único, sem diferenciar maiúsculas/minúsculas.
CREATE UNIQUE INDEX uk_users_email_lower ON users (lower(email));
