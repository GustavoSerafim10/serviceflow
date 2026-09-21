package com.gustavoserafim.serviceflow.dto;

import com.gustavoserafim.serviceflow.entity.Priority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Corpo do POST /api/tickets. Repare no que NÃO está aqui: status, solicitante
 * e prazo de SLA. O cliente não decide isso — o servidor define (status ABERTO,
 * solicitante = usuário logado, prazo = calculado pela regra de SLA).
 */
public record TicketCreateRequest(

        @NotBlank(message = "O título é obrigatório")
        @Size(max = 150, message = "O título deve ter no máximo 150 caracteres")
        String title,

        @NotBlank(message = "A descrição é obrigatória")
        @Size(max = 4000, message = "A descrição deve ter no máximo 4000 caracteres")
        String description,

        @NotNull(message = "A categoria é obrigatória")
        Long categoryId,

        @NotNull(message = "A prioridade é obrigatória")
        Priority priority,

        // Opcional: id devolvido por POST /api/tickets/suggestions. Permite ao servidor registrar se o
        // usuário aceitou ou trocou a sugestão. Inválido ou de outro usuário é ignorado (nunca falha a abertura).
        Long suggestionId
) {
}
