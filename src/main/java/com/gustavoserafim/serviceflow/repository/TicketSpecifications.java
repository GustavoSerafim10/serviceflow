package com.gustavoserafim.serviceflow.repository;

import com.gustavoserafim.serviceflow.dto.TicketFilter;
import com.gustavoserafim.serviceflow.entity.SlaStatus;
import com.gustavoserafim.serviceflow.entity.Ticket;
import com.gustavoserafim.serviceflow.entity.TicketStatus;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Peças de consulta reutilizáveis. Uma Specification é uma função
 * "(tabela, construtor de critérios) -> condição do WHERE". O Spring Data
 * combina várias com AND. Assim, 8 filtros opcionais não viram 256 métodos
 * de repository: cada filtro presente adiciona sua condição, e só isso.
 */
public final class TicketSpecifications {

    private TicketSpecifications() {
    }

    /** Converte o TicketFilter numa lista de condições (só as dos filtros preenchidos). */
    public static List<Specification<Ticket>> from(TicketFilter filter, Instant now) {
        List<Specification<Ticket>> specs = new ArrayList<>();

        if (filter.status() != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("status"), filter.status()));
        }
        if (filter.priority() != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("priority"), filter.priority()));
        }
        if (filter.categoryId() != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("category").get("id"), filter.categoryId()));
        }
        if (filter.assigneeId() != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("assignee").get("id"), filter.assigneeId()));
        }
        if (Boolean.TRUE.equals(filter.unassigned())) {
            specs.add((root, query, cb) -> cb.isNull(root.get("assignee")));
        }
        if (filter.requesterId() != null) {
            specs.add(requesterIs(filter.requesterId()));
        }
        if (filter.slaStatus() != null) {
            specs.add(slaStatus(filter.slaStatus(), now));
        }
        if (filter.q() != null && !filter.q().isBlank()) {
            specs.add(textContains(filter.q().trim()));
        }
        return specs;
    }

    public static Specification<Ticket> requesterIs(Long requesterId) {
        return (root, query, cb) -> cb.equal(root.get("requester").get("id"), requesterId);
    }

    /**
     * Espelha em SQL a regra do SlaCalculator.evaluate (as duas precisam
     * concordar):
     *  - ESTOURADO: não cancelado E ( resolvido depois do prazo  OU  não resolvido e prazo já passou )
     *  - DENTRO:    não cancelado E ( resolvido até o prazo      OU  não resolvido e prazo ainda não passou )
     * Cancelados nunca aparecem em nenhum dos dois (têm slaStatus nulo).
     */
    static Specification<Ticket> slaStatus(SlaStatus target, Instant now) {
        return (root, query, cb) -> {
            var notCancelled = cb.notEqual(root.get("status"), TicketStatus.CANCELADO);
            var resolved = cb.isNotNull(root.get("resolvedAt"));
            var open = cb.isNull(root.get("resolvedAt"));

            if (target == SlaStatus.ESTOURADO) {
                var resolvedLate = cb.and(resolved,
                        cb.greaterThan(root.<Instant>get("resolvedAt"), root.<Instant>get("slaDueAt")));
                var openOverdue = cb.and(open, cb.lessThan(root.<Instant>get("slaDueAt"), now));
                return cb.and(notCancelled, cb.or(resolvedLate, openOverdue));
            }
            var resolvedOnTime = cb.and(resolved,
                    cb.lessThanOrEqualTo(root.<Instant>get("resolvedAt"), root.<Instant>get("slaDueAt")));
            var openOnTime = cb.and(open, cb.greaterThanOrEqualTo(root.<Instant>get("slaDueAt"), now));
            return cb.and(notCancelled, cb.or(resolvedOnTime, openOnTime));
        };
    }

    // Busca "contém" sem diferenciar maiúsculas, no título OU na descrição.
    // Os caracteres curinga do LIKE (% e _) digitados pelo usuário são escapados
    // para serem tratados como texto comum.
    static Specification<Ticket> textContains(String text) {
        String pattern = "%" + text.toLowerCase()
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_") + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("title")), pattern, '\\'),
                cb.like(cb.lower(root.get("description")), pattern, '\\'));
    }
}
