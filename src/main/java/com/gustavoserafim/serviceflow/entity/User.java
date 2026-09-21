package com.gustavoserafim.serviceflow.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Usuário do sistema (tabela "users"). Mesma ideia da Category, com dois
 * detalhes novos:
 *  - passwordHash: guarda o HASH BCrypt, nunca a senha. Hash é de mão única:
 *    dá para conferir "esta senha gera este hash?", mas não recuperar a senha.
 *  - role: enum gravado como texto.
 *
 * Esta classe NÃO implementa UserDetails (interface do Spring Security) de
 * propósito: a entidade é modelo de banco; a tradução para o Spring Security
 * é responsabilidade do CustomUserDetailsService. Cada classe com um motivo
 * só para mudar.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 150)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }
}
