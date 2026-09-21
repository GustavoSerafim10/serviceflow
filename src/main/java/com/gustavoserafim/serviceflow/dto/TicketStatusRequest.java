package com.gustavoserafim.serviceflow.dto;

import com.gustavoserafim.serviceflow.entity.TicketStatus;
import jakarta.validation.constraints.NotNull;

public record TicketStatusRequest(

        @NotNull(message = "O novo status é obrigatório")
        TicketStatus status
) {
}
