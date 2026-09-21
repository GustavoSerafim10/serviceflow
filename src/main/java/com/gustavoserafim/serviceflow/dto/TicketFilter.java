package com.gustavoserafim.serviceflow.dto;

import com.gustavoserafim.serviceflow.entity.Priority;
import com.gustavoserafim.serviceflow.entity.SlaStatus;
import com.gustavoserafim.serviceflow.entity.TicketStatus;

/**
 * Filtros aceitos em GET /api/tickets, todos opcionais (query string):
 *   ?status=ABERTO&priority=P1&categoryId=3&assigneeId=5&unassigned=true
 *   &requesterId=2&slaStatus=ESTOURADO&q=impressora&page=0&size=20
 * Filtro ausente = null = "não filtra por isso". Os filtros se combinam com E.
 *
 * Spring preenche este record automaticamente a partir dos parâmetros da URL.
 */
public record TicketFilter(
        TicketStatus status,
        Priority priority,
        Long categoryId,
        Long assigneeId,
        Boolean unassigned,   // true = só chamados sem técnico (a "fila" de triagem)
        Long requesterId,
        SlaStatus slaStatus,
        String q              // busca por texto no título ou na descrição
) {
}
