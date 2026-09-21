package com.gustavoserafim.serviceflow.dto;

import com.gustavoserafim.serviceflow.repository.TicketSuggestionRepository.ModelStats;

import java.util.List;

/**
 * Qualidade REAL do modelo, medida pelo que os usuários fazem com as sugestões.
 * "Taxa de aceitação" = sugestões aceitas / sugestões avaliadas (as que chegaram
 * a virar chamado). É null enquanto não houver nenhuma avaliada (divisão por zero).
 */
public record SuggestionMetricsResponse(List<ModelMetrics> models) {

    public record ModelMetrics(
            String modelVersion,
            long offered,
            long usedInTickets,
            long categoryEvaluated,
            long categoryAccepted,
            Double categoryAcceptanceRate,
            long priorityEvaluated,
            long priorityAccepted,
            Double priorityAcceptanceRate
    ) {

        public static ModelMetrics from(ModelStats stats) {
            long categoryEvaluated = value(stats.getCategoryEvaluated());
            long categoryAccepted = value(stats.getCategoryAccepted());
            long priorityEvaluated = value(stats.getPriorityEvaluated());
            long priorityAccepted = value(stats.getPriorityAccepted());
            return new ModelMetrics(
                    stats.getModelVersion(),
                    value(stats.getOffered()),
                    value(stats.getUsedInTickets()),
                    categoryEvaluated, categoryAccepted, rate(categoryAccepted, categoryEvaluated),
                    priorityEvaluated, priorityAccepted, rate(priorityAccepted, priorityEvaluated));
        }

        private static long value(Long number) {
            return number == null ? 0 : number;
        }

        private static Double rate(long accepted, long evaluated) {
            return evaluated == 0 ? null : Math.round(10000.0 * accepted / evaluated) / 10000.0;
        }
    }
}
