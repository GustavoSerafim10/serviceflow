package com.gustavoserafim.serviceflow.service;

import com.gustavoserafim.serviceflow.dto.CategoryRequest;
import com.gustavoserafim.serviceflow.dto.CategoryResponse;
import com.gustavoserafim.serviceflow.entity.Category;
import com.gustavoserafim.serviceflow.exception.ConflictException;
import com.gustavoserafim.serviceflow.exception.ResourceNotFoundException;
import com.gustavoserafim.serviceflow.repository.CategoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Camada de regras de negócio. O controller só cuida de HTTP; o repository só
 * cuida de banco; TODA regra ("nome não pode repetir", "só desativa, não
 * apaga") mora aqui.
 *
 * Conceitos:
 *  - @Service: registra a classe como bean do Spring.
 *  - Injeção de dependência por CONSTRUTOR: o Spring vê que precisamos de um
 *    CategoryRepository e entrega uma instância. É a forma recomendada (a
 *    dependência fica final e o teste unitário pode passar um mock).
 *  - @Transactional: o método roda dentro de uma transação. Se lançar uma
 *    RuntimeException, o Spring faz rollback. readOnly = true nas consultas
 *    é uma dica de otimização.
 */
@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> findAll() {
        return categoryRepository.findAll().stream()
                .map(CategoryResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public CategoryResponse findById(Long id) {
        return CategoryResponse.from(getOrThrow(id));
    }

    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        String name = request.name().trim();

        if (categoryRepository.existsByNameIgnoreCase(name)) {
            throw new ConflictException("Já existe uma categoria com o nome '" + name + "'");
        }

        Category category = new Category();
        category.setName(name);
        category.setDescription(request.description());

        return CategoryResponse.from(categoryRepository.save(category));
    }

    @Transactional
    public CategoryResponse update(Long id, CategoryRequest request) {
        Category category = getOrThrow(id);
        String name = request.name().trim();

        if (categoryRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new ConflictException("Já existe uma categoria com o nome '" + name + "'");
        }

        category.setName(name);
        category.setDescription(request.description());

        // Sem chamar save(): a entidade foi carregada dentro da transação e
        // está "gerenciada" pelo Hibernate. No commit ele detecta a mudança
        // (dirty checking) e emite o UPDATE sozinho.
        return CategoryResponse.from(category);
    }

    // Soft delete: marca como inativa em vez de apagar a linha.
    @Transactional
    public void deactivate(Long id) {
        getOrThrow(id).setActive(false);
    }

    private Category getOrThrow(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Categoria", id));
    }
}
