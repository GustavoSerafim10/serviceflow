package com.gustavoserafim.serviceflow.dto;

import com.gustavoserafim.serviceflow.entity.Priority;
import com.gustavoserafim.serviceflow.entity.SlaStatus;
import com.gustavoserafim.serviceflow.entity.Ticket;
import com.gustavoserafim.serviceflow.entity.TicketStatus;
import com.gustavoserafim.serviceflow.entity.User;
import com.gustavoserafim.serviceflow.service.SlaCalculator;

import java.time.Instant;

/**
 * Chamado como a API o devolve. slaStatus é calculado no momento da
 * consulta (por isso o "now" no from): o mesmo chamado pode ser
 * DENTRO_DO_PRAZO agora e ESTOURADO daqui a uma hora, sem nenhuma escrita
 * no banco. slaStatus é null para chamados cancelados.
 */
public record TicketResponse(
        Long id,
        String title,
        String description,
        Long categoryId,
        String categoryName,
        Priority priority,
        TicketStatus status,
        Long requesterId,
        String requesterName,
        Long assigneeId,
        String assigneeName,
        Instant createdAt,
        Instant updatedAt,
        Instant slaDueAt,
        Instant resolvedAt,
        SlaStatus slaStatus
) {

    public static TicketResponse from(Ticket ticket, Instant now) {
        User assignee = ticket.getAssignee();
        return new TicketResponse(
                ticket.getId(),
                ticket.getTitle(),
                ticket.getDescription(),
                ticket.getCategory().getId(),
                ticket.getCategory().getName(),
                ticket.getPriority(),
                ticket.getStatus(),
                ticket.getRequester().getId(),
                ticket.getRequester().getName(),
                assignee != null ? assignee.getId() : null,
                assignee != null ? assignee.getName() : null,
                ticket.getCreatedAt(),
                ticket.getUpdatedAt(),
                ticket.getSlaDueAt(),
                ticket.getResolvedAt(),
                SlaCalculator.evaluate(ticket.getSlaDueAt(), ticket.getResolvedAt(), ticket.getStatus(), now)
        );
    }
}
