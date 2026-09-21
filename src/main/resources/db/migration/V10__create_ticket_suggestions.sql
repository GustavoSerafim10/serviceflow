-- V10__create_ticket_suggestions.sql
--
-- Ciclo de feedback da sugestão automática. Cada sugestão OFERECIDA ao usuário
-- é gravada aqui. Quando o usuário abre o chamado informando o id da sugestão,
-- o servidor a vincula ao chamado e registra se o usuário ACEITOU ou TROCOU a
-- categoria e a prioridade sugeridas. Esses dados medem a qualidade do modelo
-- (taxa de aceitação por versão) e orientam o retreino.
--
-- Não guardamos o texto do chamado aqui: ele já existe na tabela tickets.
-- Sugestões nunca vinculadas a um chamado são descartadas por uma rotina de
-- limpeza (o usuário digitou, viu a sugestão e desistiu).

CREATE TABLE ticket_suggestions (
    id                    BIGSERIAL PRIMARY KEY,
    user_id               BIGINT       NOT NULL REFERENCES users (id),
    model_version         VARCHAR(50)  NOT NULL,
    suggested_category_id BIGINT       REFERENCES categories (id),
    category_confidence   DOUBLE PRECISION,
    suggested_priority    VARCHAR(2),
    priority_confidence   DOUBLE PRECISION,
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),

    -- Preenchidos quando o chamado é aberto usando esta sugestão:
    ticket_id             BIGINT       REFERENCES tickets (id),
    linked_at             TIMESTAMP WITH TIME ZONE,
    category_accepted     BOOLEAN,      -- NULL = não havia sugestão de categoria (ou ainda sem chamado)
    priority_accepted     BOOLEAN,      -- NULL = não havia sugestão de prioridade (ou ainda sem chamado)

    CONSTRAINT ck_ticket_suggestions_priority
        CHECK (suggested_priority IS NULL OR suggested_priority IN ('P1', 'P2', 'P3', 'P4'))
);

-- Um chamado tem no máximo UMA sugestão vinculada (índice parcial: ignora as não vinculadas).
CREATE UNIQUE INDEX uk_ticket_suggestions_ticket ON ticket_suggestions (ticket_id) WHERE ticket_id IS NOT NULL;
CREATE INDEX idx_ticket_suggestions_user    ON ticket_suggestions (user_id);
CREATE INDEX idx_ticket_suggestions_created ON ticket_suggestions (created_at);
