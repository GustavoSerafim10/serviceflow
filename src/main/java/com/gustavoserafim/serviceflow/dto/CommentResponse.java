package com.gustavoserafim.serviceflow.dto;

import com.gustavoserafim.serviceflow.entity.TicketComment;

import java.time.Instant;

public record CommentResponse(
        Long id,
        Long ticketId,
        Long authorId,
        String authorName,
        String body,
        Instant createdAt
) {

    public static CommentResponse from(TicketComment comment) {
        return new CommentResponse(
                comment.getId(),
                comment.getTicket().getId(),
                comment.getAuthor().getId(),
                comment.getAuthor().getName(),
                comment.getBody(),
                comment.getCreatedAt()
        );
    }
}
