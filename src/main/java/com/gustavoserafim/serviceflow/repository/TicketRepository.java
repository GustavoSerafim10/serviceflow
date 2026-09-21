package com.gustavoserafim.serviceflow.repository;

import com.gustavoserafim.serviceflow.entity.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketRepository extends JpaRepository<Ticket, Long> {
}
