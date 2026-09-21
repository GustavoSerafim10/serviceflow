package com.gustavoserafim.serviceflow.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * Entidade JPA: a representação Java de uma linha da tabela "categories".
 *
 * Conceitos:
 *  - @Entity: diz ao Hibernate "esta classe é mapeada para uma tabela".
 *  - @Table: nome da tabela (o mesmo criado na migration V2).
 *  - @Id + @GeneratedValue(IDENTITY): a chave primária é gerada pelo próprio
 *    banco (BIGSERIAL). O Hibernate faz o INSERT e lê o id gerado.
 *  - Lombok (@Getter/@Setter/@NoArgsConstructor): gera em tempo de compilação
 *    os getters, setters e o construtor vazio. O JPA EXIGE um construtor sem
 *    argumentos para conseguir instanciar a entidade ao ler do banco.
 *
 * Por que esta classe NÃO é devolvida direto pela API? Porque ela é o modelo
 * do banco. A API expõe DTOs (CategoryResponse), assim podemos mudar o banco
 * sem quebrar quem consome a API — e nunca vazamos campos internos.
 */
@Entity
@Table(name = "categories")
@Getter
@Setter
@NoArgsConstructor
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 255)
    private String description;

    // Em vez de DELETE, desativamos a categoria (soft delete): chamados
    // antigos continuam apontando para ela sem quebrar o histórico.
    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // Callback do JPA: roda logo antes do primeiro INSERT.
    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }
}
