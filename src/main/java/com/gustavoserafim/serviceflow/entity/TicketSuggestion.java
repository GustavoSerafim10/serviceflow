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
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Registro de uma sugestão automática oferecida a um usuário e do que ele fez
 * com ela. Boolean (objeto) nos campos "accepted" porque há três estados:
 * true = aceitou, false = trocou, null = não se aplica / ainda sem chamado.
 */
@Entity
@Table(name = "ticket_suggestions")
@Getter
@Setter
@NoArgsConstructor
public class TicketSuggestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "model_version", nullable = false, length = 50)
    private String modelVersion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "suggested_category_id")
    private Category suggestedCategory;

    @Column(name = "category_confidence")
    private Double categoryConfidence;

    @Enumerated(EnumType.STRING)
    @Column(name = "suggested_priority", length = 2)
    private Priority suggestedPriority;

    @Column(name = "priority_confidence")
    private Double priorityConfidence;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id")
    private Ticket ticket;

    @Column(name = "linked_at")
    private Instant linkedAt;

    @Column(name = "category_accepted")
    private Boolean categoryAccepted;

    @Column(name = "priority_accepted")
    private Boolean priorityAccepted;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
