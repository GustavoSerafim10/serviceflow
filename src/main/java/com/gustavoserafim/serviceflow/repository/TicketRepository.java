package com.gustavoserafim.serviceflow.repository;

import com.gustavoserafim.serviceflow.entity.Priority;
import com.gustavoserafim.serviceflow.entity.Ticket;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

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

    /**
     * Chamados rotulados para retreino do modelo (exceto cancelados). Projeção de
     * interface: traz só as 4 colunas necessárias, já com o NOME da categoria (JOIN),
     * sem carregar entidades inteiras.
     */
    @Query("""
            select t.title as title, t.description as description,
                   t.category.name as category, t.priority as priority
            from Ticket t
            where t.status <> com.gustavoserafim.serviceflow.entity.TicketStatus.CANCELADO
            order by t.id
            """)
    List<TrainingRow> findTrainingRows();

    interface TrainingRow {
        String getTitle();

        String getDescription();

        String getCategory();

        Priority getPriority();
    }
}
