package com.gustavoserafim.serviceflow.entity;

/**
 * Ciclo de vida de um chamado (máquina de estados):
 *
 *   ABERTO ──(atribuir técnico)──> EM_ATENDIMENTO ──> RESOLVIDO ──> FECHADO
 *      │                                 │               │
 *      └──────────> CANCELADO <──────────┘               └──(reabrir)──> EM_ATENDIMENTO
 *
 * Este enum guarda só quais transições são VÁLIDAS. Quem pode executar cada
 * uma (solicitante, técnico, admin) é regra do TicketService.
 *
 * ABERTO -> EM_ATENDIMENTO não está aqui de propósito: só acontece pela
 * atribuição de um técnico (não existe "em atendimento" sem responsável).
 */
public enum TicketStatus {
    ABERTO,
    EM_ATENDIMENTO,
    RESOLVIDO,
    FECHADO,
    CANCELADO;

    public boolean canTransitionTo(TicketStatus target) {
        return switch (this) {
            case ABERTO -> target == CANCELADO;
            case EM_ATENDIMENTO -> target == RESOLVIDO || target == CANCELADO;
            case RESOLVIDO -> target == FECHADO || target == EM_ATENDIMENTO;
            case FECHADO, CANCELADO -> false;
        };
    }

    /** Estados finais: o chamado não muda mais (nem recebe comentários). */
    public boolean isTerminal() {
        return this == FECHADO || this == CANCELADO;
    }
}
