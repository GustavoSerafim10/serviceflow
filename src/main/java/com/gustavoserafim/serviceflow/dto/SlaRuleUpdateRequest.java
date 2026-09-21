package com.gustavoserafim.serviceflow.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Corpo do PUT /api/sla-rules/{priority}. Usamos Integer (objeto) e não int
 * (primitivo) para que o campo ausente vire null e o @NotNull o capture;
 * com int, um campo faltando viraria 0 silenciosamente.
 */
public record SlaRuleUpdateRequest(

        @NotNull(message = "O prazo de resolução é obrigatório")
        @Min(value = 1, message = "O prazo deve ser de pelo menos 1 minuto")
        @Max(value = 43200, message = "O prazo deve ser de no máximo 43200 minutos (30 dias)")
        Integer resolutionMinutes
) {
}
