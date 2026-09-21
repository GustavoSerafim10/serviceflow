package com.gustavoserafim.serviceflow.service;

import com.gustavoserafim.serviceflow.dto.CategoryRequest;
import com.gustavoserafim.serviceflow.dto.CategoryResponse;
import com.gustavoserafim.serviceflow.entity.Category;
import com.gustavoserafim.serviceflow.exception.ConflictException;
import com.gustavoserafim.serviceflow.exception.ResourceNotFoundException;
import com.gustavoserafim.serviceflow.repository.CategoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Teste UNITÁRIO do CategoryService: sem Spring e sem banco. O repository é
 * um "mock" (dublê) que nós programamos, então o teste roda em milissegundos
 * e verifica apenas a lógica do service.
 *
 * Padrão de cada teste: Arrange (prepara) / Act (executa) / Assert (confere).
 */
@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private CategoryService categoryService;

    @Test
    void create_withNewName_savesTrimmedNameAndReturnsResponse() {
        when(categoryRepository.existsByNameIgnoreCase("Rede")).thenReturn(false);
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        CategoryResponse response = categoryService.create(new CategoryRequest("  Rede  ", "Problemas de rede"));

        assertThat(response.name()).isEqualTo("Rede");
        assertThat(response.description()).isEqualTo("Problemas de rede");
        assertThat(response.active()).isTrue();
    }

    @Test
    void create_withDuplicatedName_throwsConflictAndDoesNotSave() {
        when(categoryRepository.existsByNameIgnoreCase("Rede")).thenReturn(true);

        assertThatThrownBy(() -> categoryService.create(new CategoryRequest("Rede", null)))
                .isInstanceOf(ConflictException.class);

        verify(categoryRepository, never()).save(any());
    }

    @Test
    void findById_whenMissing_throwsResourceNotFound() {
        when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.findById(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deactivate_marksCategoryAsInactive() {
        Category category = new Category();
        category.setName("Rede");
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));

        categoryService.deactivate(1L);

        assertThat(category.isActive()).isFalse();
    }
}
