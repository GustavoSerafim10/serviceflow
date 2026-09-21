package com.gustavoserafim.serviceflow.repository;

import com.gustavoserafim.serviceflow.entity.TicketSuggestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface TicketSuggestionRepository extends JpaRepository<TicketSuggestion, Long> {

    /**
     * Agregados por versão do modelo, calculados no banco em uma única consulta.
     * "sum(case when ...)" é a forma de contar linhas que satisfazem uma condição.
     * Resultado mapeado para uma interface de projeção: o Spring implementa os
     * getters a partir dos apelidos (as ...) do select.
     */
    @Query("""
            select s.modelVersion as modelVersion,
                   count(s) as offered,
                   count(s.ticket) as usedInTickets,
                   sum(case when s.categoryAccepted is not null then 1 else 0 end) as categoryEvaluated,
                   sum(case when s.categoryAccepted = true then 1 else 0 end) as categoryAccepted,
                   sum(case when s.priorityAccepted is not null then 1 else 0 end) as priorityEvaluated,
                   sum(case when s.priorityAccepted = true then 1 else 0 end) as priorityAccepted
            from TicketSuggestion s
            group by s.modelVersion
            order by s.modelVersion
            """)
    List<ModelStats> statsByModel();

    // Limpeza: sugestões que nunca viraram chamado (o usuário desistiu).
    @Modifying
    @Query("delete from TicketSuggestion s where s.ticket is null and s.createdAt < :before")
    int deleteUnlinkedOlderThan(@Param("before") Instant before);

    interface ModelStats {
        String getModelVersion();

        Long getOffered();

        Long getUsedInTickets();

        Long getCategoryEvaluated();

        Long getCategoryAccepted();

        Long getPriorityEvaluated();

        Long getPriorityAccepted();
    }
}
