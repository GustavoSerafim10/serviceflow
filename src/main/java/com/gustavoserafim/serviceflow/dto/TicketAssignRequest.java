package com.gustavoserafim.serviceflow.dto;

import jakarta.validation.constraints.NotNull;

public record TicketAssignRequest(

        @NotNull(message = "O técnico é obrigatório")
        Long technicianId
) {
}
