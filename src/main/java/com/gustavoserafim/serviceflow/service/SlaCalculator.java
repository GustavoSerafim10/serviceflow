package com.gustavoserafim.serviceflow.service;

import com.gustavoserafim.serviceflow.entity.SlaStatus;
import com.gustavoserafim.serviceflow.entity.TicketStatus;

import java.time.Duration;
import java.time.Instant;

/**
 * Cálculo de SLA em funções puras (sem banco, sem Spring, sem relógio
 * escondido): recebem tudo por parâmetro, então são triviais de testar.
 * O "agora" entra como argumento justamente para o teste poder simular
 * qualquer momento.
 */
public final class SlaCalculator {

    private SlaCalculator() {
    }

    /** Prazo final = instante de abertura + minutos da regra de SLA. */
    public static Instant dueAt(Instant openedAt, int resolutionMinutes) {
        return openedAt.plus(Duration.ofMinutes(resolutionMinutes));
    }

    /**
     * Situação do SLA:
     *  - CANCELADO: não se aplica (null);
     *  - já resolvido: compara o momento da resolução com o prazo;
     *  - ainda em aberto: compara "agora" com o prazo.
     * Resolver exatamente no instante do prazo conta como DENTRO do prazo.
     */
    public static SlaStatus evaluate(Instant dueAt, Instant resolvedAt, TicketStatus status, Instant now) {
        if (status == TicketStatus.CANCELADO) {
            return null;
        }
        Instant reference = (resolvedAt != null) ? resolvedAt : now;
        return reference.isAfter(dueAt) ? SlaStatus.ESTOURADO : SlaStatus.DENTRO_DO_PRAZO;
    }
}
