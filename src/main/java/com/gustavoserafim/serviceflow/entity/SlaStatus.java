package com.gustavoserafim.serviceflow.entity;

/** Situação do chamado em relação ao prazo de SLA. Calculado, nunca gravado. */
public enum SlaStatus {
    DENTRO_DO_PRAZO,
    ESTOURADO
}
