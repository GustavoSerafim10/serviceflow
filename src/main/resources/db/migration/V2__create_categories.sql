-- V2__create_categories.sql
--
-- Etapa 2: primeira tabela de domínio. Categoria classifica um chamado
-- (ex: "Rede", "Hardware", "Acesso/Senha"). Mais adiante, cada categoria
-- poderá ter regras de SLA próprias.
--
-- IMPORTANTE: nunca edite uma migration já aplicada (o Flyway guarda o
-- checksum e vai recusar subir). Para mudar algo, crie uma V3, V4...

CREATE TABLE categories (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

-- Unicidade sem diferenciar maiúsculas/minúsculas: "Rede" e "rede" colidem.
-- A checagem no Service dá uma mensagem amigável; este índice é a garantia
-- final no banco (protege contra duas requisições simultâneas).
CREATE UNIQUE INDEX uk_categories_name_lower ON categories (lower(name));
