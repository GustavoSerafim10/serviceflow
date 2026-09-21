-- V6__add_token_version.sql
--
-- JWT é stateless: uma vez emitido, vale até expirar, mesmo que o usuário troque
-- a senha. Para poder INVALIDAR tokens antigos, cada usuário ganha uma "versão
-- de token". O JWT carrega a versão vigente na emissão; se a versão do usuário
-- aumentar (troca de senha, reset pelo ADMIN, detecção de reuso de refresh
-- token), todos os tokens anteriores passam a ser rejeitados.

ALTER TABLE users ADD COLUMN token_version INTEGER NOT NULL DEFAULT 0;
