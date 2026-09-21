package com.gustavoserafim.serviceflow.repository;

import com.gustavoserafim.serviceflow.entity.TicketComment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TicketCommentRepository extends JpaRepository<TicketComment, Long> {

    // "TicketId": o Spring navega comment.ticket.id. Ordena do mais antigo ao mais novo.
    List<TicketComment> findByTicketIdOrderByIdAsc(Long ticketId);
}
