package com.gustavoserafim.serviceflow.repository;

import com.gustavoserafim.serviceflow.entity.Ticket;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * JpaSpecificationExecutor adiciona findAll(Specification, Pageable): consultas
 * montadas dinamicamente a partir de filtros opcionais (ver TicketSpecifications).
 */
public interface TicketRepository extends JpaRepository<Ticket, Long>, JpaSpecificationExecutor<Ticket> {

    /**
     * Sobrescreve o método padrão só para acrescentar o @EntityGraph: busca
     * categoria, solicitante e técnico no MESMO SELECT (JOIN). Sem isso,
     * montar 20 TicketResponse dispararia ~60 consultas extras (o famoso
     * problema "N+1", por causa do carregamento LAZY).
     */
    @Override
    @EntityGraph(attributePaths = {"category", "requester", "assignee"})
    Page<Ticket> findAll(Specification<Ticket> spec, Pageable pageable);
}
