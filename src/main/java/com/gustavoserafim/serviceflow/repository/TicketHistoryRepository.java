package com.gustavoserafim.serviceflow.repository;

import com.gustavoserafim.serviceflow.entity.TicketHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TicketHistoryRepository extends JpaRepository<TicketHistory, Long> {

    List<TicketHistory> findByTicketIdOrderByIdAsc(Long ticketId);
}
