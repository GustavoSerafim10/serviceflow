package com.gustavoserafim.serviceflow.dto;

import com.gustavoserafim.serviceflow.entity.Priority;

import java.util.List;

/**
 * Sugestão de categoria e prioridade para um chamado ainda não aberto.
 *
 * available = false significa "sem sugestão agora" (serviço de IA desligado,
 * fora do ar ou sem candidato utilizável). Não é erro: o cliente simplesmente
 * não exibe a sugestão e o usuário preenche os campos como sempre.
 *
 * Diferente do serviço Python (que devolve NOMES), aqui a categoria já vem
 * resolvida para uma categoria REAL e ATIVA do sistema, com o id pronto para
 * ser usado em POST /api/tickets.
 */
public record SuggestionResponse(
        boolean available,
        String modelVersion,
        CategorySuggestion category,
        PrioritySuggestion priority
) {

    public record CategorySuggestion(Long id, String name, double confidence, List<CategoryOption> alternatives) {
    }

    public record CategoryOption(Long id, String name, double confidence) {
    }

    public record PrioritySuggestion(Priority priority, double confidence, List<PriorityOption> alternatives) {
    }

    public record PriorityOption(Priority priority, double confidence) {
    }

    public static SuggestionResponse unavailable() {
        return new SuggestionResponse(false, null, null, null);
    }
}
