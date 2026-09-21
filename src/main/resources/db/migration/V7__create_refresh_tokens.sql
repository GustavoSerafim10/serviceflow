-- V7__create_refresh_tokens.sql
--
-- Refresh tokens: credenciais de longa duração (7 dias) usadas apenas para obter
-- novos access tokens (JWT de 15 min) sem pedir a senha de novo.
--
-- Segurança:
--  * O valor do token NUNCA é gravado: só o hash SHA-256 (token_hash). Se o
--    banco vazar, os hashes não servem como credencial. SHA-256 (rápido) basta
--    aqui porque o token é aleatório de 256 bits, diferente de uma senha humana.
--  * Rotação: cada uso revoga o token (revoked_at) e emite outro. Apresentar
--    um token JÁ revogado indica reuso/roubo e derruba todas as sessões do usuário.

CREATE TABLE refresh_tokens (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES users (id),
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uk_refresh_tokens_hash ON refresh_tokens (token_hash);
CREATE INDEX idx_refresh_tokens_user    ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_expires ON refresh_tokens (expires_at);
