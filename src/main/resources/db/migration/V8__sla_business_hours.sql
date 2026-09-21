-- V8__sla_business_hours.sql
--
-- SLA em horário comercial. Cada regra passa a dizer se o prazo conta em tempo
-- corrido (24x7, como P1/P2, incidentes críticos) ou apenas em horas úteis
-- (P3/P4, demandas). O expediente, os dias úteis, o fuso e os feriados vêm da
-- configuração da aplicação (app.sla.business-hours.*).

ALTER TABLE sla_rules ADD COLUMN business_hours BOOLEAN NOT NULL DEFAULT FALSE;

-- P3 e P4 passam a contar em horas úteis. Os valores em minutos são ajustados
-- para a nova unidade (expediente de 10h): P3 = 1 dia útil, P4 = 3 dias úteis.
-- A condição "resolution_minutes = valor padrão" preserva qualquer valor que o
-- ADMIN já tenha personalizado.
UPDATE sla_rules SET business_hours = TRUE, resolution_minutes = 600  WHERE priority = 'P3' AND resolution_minutes = 1440;
UPDATE sla_rules SET business_hours = TRUE, resolution_minutes = 1800 WHERE priority = 'P4' AND resolution_minutes = 4320;
