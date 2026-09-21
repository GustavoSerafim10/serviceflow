package com.gustavoserafim.serviceflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Corpo do POST /api/tickets/suggestions: o que o usuário já digitou no formulário. */
public record SuggestionRequest(

        @NotBlank(message = "O título é obrigatório")
        @Size(max = 150, message = "O título deve ter no máximo 150 caracteres")
        String title,

        @NotBlank(message = "A descrição é obrigatória")
        @Size(max = 4000, message = "A descrição deve ter no máximo 4000 caracteres")
        String description
) {
}
