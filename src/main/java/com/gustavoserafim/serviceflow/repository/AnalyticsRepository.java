package com.gustavoserafim.serviceflow.repository;

import com.gustavoserafim.serviceflow.entity.Ticket;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Consultas analíticas (agregações) sobre chamados.
 *
 * Por que SQL nativo e não JPQL? Estas consultas usam recursos do PostgreSQL que
 * o JPQL não expressa: COUNT(*) FILTER (WHERE ...) (várias contagens condicionais
 * em UMA passada pela tabela), percentile_cont (mediana), aritmética de intervalos
 * e generate_series. A agregação acontece NO BANCO, que devolve poucas linhas —
 * trazer milhares de chamados para somar em Java seria lento e desperdiçaria memória.
 *
 * Estende Repository (a interface "vazia" do Spring Data) em vez de JpaRepository:
 * não queremos save/delete aqui, só consultas de leitura.
 *
 * Regras de medição (comuns a todas):
 *  - chamados CANCELADOS nunca entram;
 *  - "aberto no período"   = created_at  dentro de [from, to);
 *  - "resolvido no período" = resolved_at dentro de [from, to). MTTR e conformidade
 *    de SLA usam ESTA data: só faz sentido medir o tempo de resolução de quem foi resolvido;
 *  - "dentro do SLA" = resolved_at <= sla_due_at.
 * Os apelidos entre aspas duplas preservam o camelCase (o PostgreSQL passaria tudo para minúsculas).
 */
public interface AnalyticsRepository extends Repository<Ticket, Long> {

    @Query(nativeQuery = true, value = """
            SELECT
                count(*) FILTER (WHERE t.created_at >= :from AND t.created_at < :to) AS "opened",
                count(*) FILTER (WHERE t.resolved_at >= :from AND t.resolved_at < :to) AS "resolved",
                count(*) FILTER (WHERE t.resolved_at >= :from AND t.resolved_at < :to
                                   AND t.resolved_at <= t.sla_due_at) AS "resolvedWithinSla",
                CAST(avg(EXTRACT(EPOCH FROM (t.resolved_at - t.created_at)))
                     FILTER (WHERE t.resolved_at >= :from AND t.resolved_at < :to) AS double precision)
                     AS "avgResolutionSeconds",
                CAST(percentile_cont(0.5) WITHIN GROUP (ORDER BY EXTRACT(EPOCH FROM (t.resolved_at - t.created_at)))
                     FILTER (WHERE t.resolved_at >= :from AND t.resolved_at < :to) AS double precision)
                     AS "medianResolutionSeconds"
            FROM tickets t
            WHERE t.status <> 'CANCELADO'
              AND ((t.created_at >= :from AND t.created_at < :to)
                OR (t.resolved_at >= :from AND t.resolved_at < :to))
            """)
    SummaryRow summary(@Param("from") Instant from, @Param("to") Instant to);

    /** Retrato de AGORA (independe do período): chamados por status e quantos estão com o SLA já estourado. */
    @Query(nativeQuery = true, value = """
            SELECT t.status AS "status",
                   count(*) AS "total",
                   count(*) FILTER (WHERE t.resolved_at IS NULL AND t.sla_due_at < :now) AS "breached"
            FROM tickets t
            WHERE t.status <> 'CANCELADO'
            GROUP BY t.status
            """)
    List<StatusRow> currentByStatus(@Param("now") Instant now);

    @Query(nativeQuery = true, value = """
            SELECT c.id AS "categoryId", c.name AS "categoryName",
                   count(*) FILTER (WHERE t.created_at >= :from AND t.created_at < :to) AS "opened",
                   count(*) FILTER (WHERE t.resolved_at >= :from AND t.resolved_at < :to) AS "resolved",
                   count(*) FILTER (WHERE t.resolved_at >= :from AND t.resolved_at < :to
                                      AND t.resolved_at <= t.sla_due_at) AS "resolvedWithinSla",
                   CAST(avg(EXTRACT(EPOCH FROM (t.resolved_at - t.created_at)))
                        FILTER (WHERE t.resolved_at >= :from AND t.resolved_at < :to) AS double precision)
                        AS "avgResolutionSeconds"
            FROM tickets t
            JOIN categories c ON c.id = t.category_id
            WHERE t.status <> 'CANCELADO'
              AND ((t.created_at >= :from AND t.created_at < :to)
                OR (t.resolved_at >= :from AND t.resolved_at < :to))
            GROUP BY c.id, c.name
            ORDER BY count(*) FILTER (WHERE t.created_at >= :from AND t.created_at < :to) DESC, c.name
            """)
    List<CategoryRow> byCategory(@Param("from") Instant from, @Param("to") Instant to);

    @Query(nativeQuery = true, value = """
            SELECT t.priority AS "priority",
                   count(*) FILTER (WHERE t.created_at >= :from AND t.created_at < :to) AS "opened",
                   count(*) FILTER (WHERE t.resolved_at >= :from AND t.resolved_at < :to) AS "resolved",
                   count(*) FILTER (WHERE t.resolved_at >= :from AND t.resolved_at < :to
                                      AND t.resolved_at <= t.sla_due_at) AS "resolvedWithinSla",
                   CAST(avg(EXTRACT(EPOCH FROM (t.resolved_at - t.created_at)))
                        FILTER (WHERE t.resolved_at >= :from AND t.resolved_at < :to) AS double precision)
                        AS "avgResolutionSeconds"
            FROM tickets t
            WHERE t.status <> 'CANCELADO'
              AND ((t.created_at >= :from AND t.created_at < :to)
                OR (t.resolved_at >= :from AND t.resolved_at < :to))
            GROUP BY t.priority
            """)
    List<PriorityRow> byPriority(@Param("from") Instant from, @Param("to") Instant to);

    /** Desempenho por técnico ATUALMENTE responsável (uma reatribuição transfere o histórico ao novo responsável). */
    @Query(nativeQuery = true, value = """
            SELECT u.id AS "technicianId", u.name AS "technicianName",
                   count(*) FILTER (WHERE t.resolved_at >= :from AND t.resolved_at < :to) AS "resolved",
                   count(*) FILTER (WHERE t.resolved_at >= :from AND t.resolved_at < :to
                                      AND t.resolved_at <= t.sla_due_at) AS "resolvedWithinSla",
                   CAST(avg(EXTRACT(EPOCH FROM (t.resolved_at - t.created_at)))
                        FILTER (WHERE t.resolved_at >= :from AND t.resolved_at < :to) AS double precision)
                        AS "avgResolutionSeconds",
                   count(*) FILTER (WHERE t.status = 'EM_ATENDIMENTO') AS "inProgressNow"
            FROM tickets t
            JOIN users u ON u.id = t.assignee_id
            WHERE t.status <> 'CANCELADO'
            GROUP BY u.id, u.name
            HAVING count(*) FILTER (WHERE t.resolved_at >= :from AND t.resolved_at < :to) > 0
                OR count(*) FILTER (WHERE t.status = 'EM_ATENDIMENTO') > 0
            ORDER BY count(*) FILTER (WHERE t.resolved_at >= :from AND t.resolved_at < :to) DESC, u.name
            """)
    List<TechnicianRow> byTechnician(@Param("from") Instant from, @Param("to") Instant to);

    /**
     * Série diária (abertos e resolvidos por dia), com os dias sem movimento presentes como zero
     * (generate_series): um gráfico precisa de todos os dias do eixo. O "dia" é o do fuso da empresa.
     */
    @Query(nativeQuery = true, value = """
            SELECT to_char(d.day, 'YYYY-MM-DD') AS "day",
                   (SELECT count(*) FROM tickets t
                     WHERE t.status <> 'CANCELADO'
                       AND t.created_at >= :from AND t.created_at < :to
                       AND CAST(t.created_at AT TIME ZONE CAST(:zone AS text) AS date) = d.day) AS "opened",
                   (SELECT count(*) FROM tickets t
                     WHERE t.status <> 'CANCELADO'
                       AND t.resolved_at >= :from AND t.resolved_at < :to
                       AND CAST(t.resolved_at AT TIME ZONE CAST(:zone AS text) AS date) = d.day) AS "resolved"
            FROM (SELECT CAST(g AS date) AS day
                    FROM generate_series(CAST(:fromDate AS date), CAST(:toDate AS date), interval '1 day') g) d
            ORDER BY d.day
            """)
    List<TimelineRow> timeline(@Param("from") Instant from, @Param("to") Instant to,
                               @Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate,
                               @Param("zone") String zone);

    // ------------------------------------------------------------------ projeções

    interface SummaryRow {
        Long getOpened();

        Long getResolved();

        Long getResolvedWithinSla();

        Double getAvgResolutionSeconds();

        Double getMedianResolutionSeconds();
    }

    interface StatusRow {
        String getStatus();

        Long getTotal();

        Long getBreached();
    }

    interface CategoryRow {
        Long getCategoryId();

        String getCategoryName();

        Long getOpened();

        Long getResolved();

        Long getResolvedWithinSla();

        Double getAvgResolutionSeconds();
    }

    interface PriorityRow {
        String getPriority();

        Long getOpened();

        Long getResolved();

        Long getResolvedWithinSla();

        Double getAvgResolutionSeconds();
    }

    interface TechnicianRow {
        Long getTechnicianId();

        String getTechnicianName();

        Long getResolved();

        Long getResolvedWithinSla();

        Double getAvgResolutionSeconds();

        Long getInProgressNow();
    }

    interface TimelineRow {
        String getDay();

        Long getOpened();

        Long getResolved();
    }
}
