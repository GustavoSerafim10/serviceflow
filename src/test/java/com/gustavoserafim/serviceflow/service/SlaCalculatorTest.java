package com.gustavoserafim.serviceflow.service;

import com.gustavoserafim.serviceflow.entity.SlaStatus;
import com.gustavoserafim.serviceflow.entity.TicketStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes do cálculo de SLA. Como SlaCalculator é puro, o "agora" é só um
 * parâmetro: simulamos qualquer momento sem esperar nem mockar nada.
 */
class SlaCalculatorTest {

    private static final Instant OPENED = Instant.parse("2026-01-10T10:00:00Z");
    private static final Instant DUE = Instant.parse("2026-01-10T14:00:00Z"); // 4h depois

    @Test
    void dueAt_addsResolutionMinutesToOpeningTime() {
        assertThat(SlaCalculator.dueAt(OPENED, 240)).isEqualTo(DUE);
    }

    @Test
    void evaluate_openTicketBeforeDeadline_isWithinSla() {
        Instant now = Instant.parse("2026-01-10T13:59:59Z");

        assertThat(SlaCalculator.evaluate(DUE, null, TicketStatus.EM_ATENDIMENTO, now))
                .isEqualTo(SlaStatus.DENTRO_DO_PRAZO);
    }

    @Test
    void evaluate_openTicketAfterDeadline_isBreached() {
        Instant now = Instant.parse("2026-01-10T14:00:01Z");

        assertThat(SlaCalculator.evaluate(DUE, null, TicketStatus.ABERTO, now))
                .isEqualTo(SlaStatus.ESTOURADO);
    }

    @Test
    void evaluate_resolvedExactlyAtDeadline_isWithinSla() {
        Instant later = Instant.parse("2026-02-01T00:00:00Z"); // "agora" não importa após resolver

        assertThat(SlaCalculator.evaluate(DUE, DUE, TicketStatus.RESOLVIDO, later))
                .isEqualTo(SlaStatus.DENTRO_DO_PRAZO);
    }

    @Test
    void evaluate_resolvedAfterDeadline_staysBreachedForever() {
        Instant resolvedAt = Instant.parse("2026-01-10T14:30:00Z");
        Instant later = Instant.parse("2026-03-01T00:00:00Z");

        assertThat(SlaCalculator.evaluate(DUE, resolvedAt, TicketStatus.FECHADO, later))
                .isEqualTo(SlaStatus.ESTOURADO);
    }

    @Test
    void evaluate_cancelledTicket_hasNoSlaStatus() {
        Instant now = Instant.parse("2026-05-01T00:00:00Z");

        assertThat(SlaCalculator.evaluate(DUE, null, TicketStatus.CANCELADO, now)).isNull();
    }
}
