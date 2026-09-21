package com.gustavoserafim.serviceflow.entity;

/**
 * Prioridade de um chamado, da mais urgente (P1) à menos urgente (P4).
 * Cada prioridade tem seu prazo de SLA na tabela sla_rules.
 */
public enum Priority {
    P1, // crítico: serviço parado
    P2, // alto: impacto grande
    P3, // médio: impacto moderado
    P4  // baixo: dúvida, melhoria
}
