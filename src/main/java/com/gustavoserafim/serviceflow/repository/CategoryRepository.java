package com.gustavoserafim.serviceflow.repository;

import com.gustavoserafim.serviceflow.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Camada de acesso a dados. Repare que é uma INTERFACE, sem implementação:
 * o Spring Data JPA gera a implementação em tempo de execução.
 *
 * Ao estender JpaRepository<Category, Long> já ganhamos save, findById,
 * findAll, deleteById, count... (Long é o tipo do @Id).
 *
 * Os métodos abaixo são "derived queries": o Spring lê o NOME do método e
 * monta o SQL sozinho.
 *   existsByNameIgnoreCase -> SELECT ... WHERE lower(name) = lower(?)
 */
public interface CategoryRepository extends JpaRepository<Category, Long> {

    boolean existsByNameIgnoreCase(String name);

    // Usado na edição: outro registro (id diferente) já tem esse nome?
    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
}
