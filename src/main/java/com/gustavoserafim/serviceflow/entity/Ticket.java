package com.gustavoserafim.serviceflow.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Chamado. Primeira entidade com RELACIONAMENTOS: cada @ManyToOne vira uma
 * chave estrangeira (category_id, requester_id, assignee_id).
 *
 * fetch = LAZY: o Hibernate NÃO carrega a categoria/usuário junto com o
 * chamado; só busca no banco quando alguém chama getCategory().getName().
 * (O padrão do @ManyToOne é EAGER, que causa consultas desnecessárias.)
 * Consequência: acessar esses campos só funciona dentro de uma transação
 * (@Transactional no service), por isso convertemos para DTO lá dentro.
 */
@Entity
@Table(name = "tickets")
@Getter
@Setter
@NoArgsConstructor
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(nullable = false, length = 4000)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requester_id", nullable = false)
    private User requester;

    // Nulo até algum técnico assumir o chamado.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id")
    private User assignee;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 2)
    private Priority priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TicketStatus status;

    // Prazo de SLA gravado na abertura (snapshot, ver migration V5).
    @Column(name = "sla_due_at", nullable = false)
    private Instant slaDueAt;

    // Preenchido ao entrar em RESOLVIDO; volta a nulo se o chamado for reaberto.
    @Column(name = "resolved_at")
    private Instant resolvedAt;

    // O service define createdAt (com o Clock injetável) para que o prazo de SLA
    // e a data de abertura usem o MESMO instante; o fallback cobre outros usos.
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
