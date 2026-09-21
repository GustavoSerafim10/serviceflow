package com.gustavoserafim.serviceflow.dto;

import com.gustavoserafim.serviceflow.entity.Priority;
import com.gustavoserafim.serviceflow.entity.TicketStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Formatos de resposta da API de analytics (agrupados aqui por serem todos
 * "linhas de relatório"). Convenções:
 *  - tempos em MINUTOS (número de fácil leitura em gráficos);
 *  - taxas de 0 a 1 (0.75 = 75%); null quando não há base de cálculo (divisão por zero),
 *    para o painel mostrar "—" em vez de um enganoso 0%;
 *  - datas no fuso da empresa (o mesmo do calendário comercial).
 */
public final class AnalyticsResponses {

    private AnalyticsResponses() {
    }

    /** Período analisado, inclusivo nas duas pontas. */
    public record Period(LocalDate from, LocalDate to) {
    }

    /** Relatório genérico: o período efetivamente usado (útil quando o cliente não o informou) + as linhas. */
    public record Report<T>(Period period, List<T> items) {
    }

    /**
     * Visão geral. Os totais do período usam duas réguas:
     *  opened   = chamados abertos no período;
     *  resolved = chamados resolvidos no período (base do MTTR e da conformidade de SLA).
     */
    public record Summary(
            Period period,
            long opened,
            long resolved,
            long resolvedWithinSla,
            Double slaComplianceRate,
            Double avgResolutionMinutes,     // MTTR (tempo médio de resolução)
            Double medianResolutionMinutes,  // mediana: menos sensível a casos extremos que a média
            Current current
    ) {
    }

    /** Retrato de AGORA, independente do período. */
    public record Current(long total, long breachedOpen, Map<TicketStatus, Long> byStatus) {
    }

    public record CategoryStat(
            Long categoryId, String categoryName,
            long opened, long resolved, long resolvedWithinSla,
            Double slaComplianceRate, Double avgResolutionMinutes
    ) {
    }

    public record PriorityStat(
            Priority priority,
            long opened, long resolved, long resolvedWithinSla,
            Double slaComplianceRate, Double avgResolutionMinutes
    ) {
    }

    public record TechnicianStat(
            Long technicianId, String technicianName,
            long resolved, long resolvedWithinSla,
            Double slaComplianceRate, Double avgResolutionMinutes,
            long inProgressNow
    ) {
    }

    public record TimelinePoint(LocalDate day, long opened, long resolved) {
    }
}
