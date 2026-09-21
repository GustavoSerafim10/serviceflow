-- V4__create_sla_rules.sql
--
-- Etapa 4: regras de SLA configuráveis. Uma linha por prioridade (P1 a P4)
-- com o prazo, em minutos, para RESOLVER um chamado dessa prioridade.
-- Ao abrir um chamado, o sistema calcula: prazo = abertura + resolution_minutes.
--
-- Os 4 registros já nascem aqui (seed). O ADMIN só EDITA os minutos; não há
-- criar/apagar, pois o conjunto de prioridades é fixo (enum Priority).
-- Prazos em tempo corrido (24x7). Horário comercial fica como evolução futura.

CREATE TABLE sla_rules (
    id                 BIGSERIAL PRIMARY KEY,
    priority           VARCHAR(2) NOT NULL UNIQUE,
    resolution_minutes INTEGER    NOT NULL,
    updated_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT ck_sla_rules_priority CHECK (priority IN ('P1', 'P2', 'P3', 'P4')),
    CONSTRAINT ck_sla_rules_minutes  CHECK (resolution_minutes > 0)
);

INSERT INTO sla_rules (priority, resolution_minutes) VALUES
    ('P1', 240),    -- crítico: 4 horas
    ('P2', 480),    -- alto:    8 horas
    ('P3', 1440),   -- médio:   24 horas
    ('P4', 4320);   -- baixo:   72 horas
