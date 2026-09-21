-- V5__create_tickets.sql
--
-- Etapa 5: chamados, comentários e histórico.
--
-- Decisões:
--  * sla_due_at é um SNAPSHOT: calculado na abertura (abertura + minutos da
--    regra de SLA da prioridade) e gravado. Se o ADMIN alterar a regra depois,
--    chamados já abertos mantêm o prazo com que nasceram.
--  * O status do SLA (dentro/estourado) NÃO é coluna: depende de "agora" e é
--    calculado na leitura, a partir de sla_due_at, resolved_at e status.
--  * ticket_history é só-INSERT: é o registro de auditoria, nunca se altera.

CREATE TABLE tickets (
    id           BIGSERIAL PRIMARY KEY,
    title        VARCHAR(150)  NOT NULL,
    description  VARCHAR(4000) NOT NULL,
    category_id  BIGINT        NOT NULL REFERENCES categories (id),
    requester_id BIGINT        NOT NULL REFERENCES users (id),
    assignee_id  BIGINT        REFERENCES users (id),
    priority     VARCHAR(2)    NOT NULL,
    status       VARCHAR(20)   NOT NULL,
    sla_due_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    resolved_at  TIMESTAMP WITH TIME ZONE,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT ck_tickets_priority CHECK (priority IN ('P1', 'P2', 'P3', 'P4')),
    CONSTRAINT ck_tickets_status   CHECK (status IN ('ABERTO', 'EM_ATENDIMENTO', 'RESOLVIDO', 'FECHADO', 'CANCELADO'))
);

-- Índices nas colunas usadas em filtros (Etapa 6) e nas chaves estrangeiras.
CREATE INDEX idx_tickets_status     ON tickets (status);
CREATE INDEX idx_tickets_priority   ON tickets (priority);
CREATE INDEX idx_tickets_category   ON tickets (category_id);
CREATE INDEX idx_tickets_requester  ON tickets (requester_id);
CREATE INDEX idx_tickets_assignee   ON tickets (assignee_id);
CREATE INDEX idx_tickets_sla_due_at ON tickets (sla_due_at);

CREATE TABLE ticket_comments (
    id         BIGSERIAL PRIMARY KEY,
    ticket_id  BIGINT        NOT NULL REFERENCES tickets (id),
    author_id  BIGINT        NOT NULL REFERENCES users (id),
    body       VARCHAR(2000) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX idx_ticket_comments_ticket ON ticket_comments (ticket_id);

CREATE TABLE ticket_history (
    id         BIGSERIAL PRIMARY KEY,
    ticket_id  BIGINT       NOT NULL REFERENCES tickets (id),
    actor_id   BIGINT       NOT NULL REFERENCES users (id),
    action     VARCHAR(20)  NOT NULL,
    details    VARCHAR(500) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT ck_ticket_history_action CHECK (action IN ('CREATED', 'ASSIGNED', 'STATUS_CHANGED', 'COMMENT_ADDED'))
);

CREATE INDEX idx_ticket_history_ticket ON ticket_history (ticket_id);
