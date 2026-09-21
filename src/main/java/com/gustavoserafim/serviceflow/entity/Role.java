package com.gustavoserafim.serviceflow.entity;

/**
 * Papéis (roles) do sistema. É um enum: conjunto fechado de valores, então
 * o compilador impede um "ADMN" digitado errado.
 *
 * No banco fica como texto (@Enumerated(STRING) na entidade User). Nunca use
 * EnumType.ORDINAL: ele grava a posição (0, 1, 2) e reordenar o enum
 * corromperia os dados.
 */
public enum Role {
    ADMIN,
    TECNICO,
    SOLICITANTE
}
