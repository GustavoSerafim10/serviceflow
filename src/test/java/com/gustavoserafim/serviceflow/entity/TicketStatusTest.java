package com.gustavoserafim.serviceflow.entity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class TicketStatusTest {

    // @ParameterizedTest roda o mesmo teste para cada linha do CsvSource.
    @ParameterizedTest
    @CsvSource({
            "ABERTO, CANCELADO",
            "EM_ATENDIMENTO, RESOLVIDO",
            "EM_ATENDIMENTO, CANCELADO",
            "RESOLVIDO, FECHADO",
            "RESOLVIDO, EM_ATENDIMENTO"
    })
    void allowedTransitions(TicketStatus from, TicketStatus to) {
        assertThat(from.canTransitionTo(to)).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
            "ABERTO, EM_ATENDIMENTO",   // só via atribuição de técnico
            "ABERTO, RESOLVIDO",
            "ABERTO, FECHADO",
            "EM_ATENDIMENTO, ABERTO",
            "EM_ATENDIMENTO, FECHADO",
            "RESOLVIDO, CANCELADO",
            "FECHADO, EM_ATENDIMENTO",
            "CANCELADO, ABERTO"
    })
    void forbiddenTransitions(TicketStatus from, TicketStatus to) {
        assertThat(from.canTransitionTo(to)).isFalse();
    }

    @Test
    void terminalStates() {
        assertThat(TicketStatus.FECHADO.isTerminal()).isTrue();
        assertThat(TicketStatus.CANCELADO.isTerminal()).isTrue();
        assertThat(TicketStatus.RESOLVIDO.isTerminal()).isFalse();
    }
}
