package com.gustavoserafim.serviceflow.dto;

import com.gustavoserafim.serviceflow.entity.HistoryAction;
import com.gustavoserafim.serviceflow.entity.TicketHistory;

import java.time.Instant;

public record HistoryResponse(
        Long id,
        HistoryAction action,
        String details,
        Long actorId,
        String actorName,
        Instant createdAt
) {

    public static HistoryResponse from(TicketHistory history) {
        return new HistoryResponse(
                history.getId(),
                history.getAction(),
                history.getDetails(),
                history.getActor().getId(),
                history.getActor().getName(),
                history.getCreatedAt()
        );
    }
}
