package com.gustavoserafim.serviceflow.controller;

import com.gustavoserafim.serviceflow.dto.CategoryRequest;
import com.gustavoserafim.serviceflow.dto.CategoryResponse;
import com.gustavoserafim.serviceflow.service.CategoryService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

/**
 * Camada web: traduz HTTP <-> chamadas ao Service. Nenhuma regra de negócio
 * aqui. Faz três coisas: recebe a requisição, chama o service, devolve o
 * status HTTP correto.
 *
 * Conceitos:
 *  - @RestController: @Controller + @ResponseBody. O retorno dos métodos é
 *    serializado para JSON (pelo Jackson) em vez de procurar uma página HTML.
 *  - @RequestMapping("/api/categories"): prefixo de todas as rotas da classe.
 *  - @PathVariable: pega o {id} da URL. @RequestBody: converte o JSON do
 *    corpo no DTO. @Valid: dispara a Bean Validation do DTO.
 *  - @PreAuthorize("hasRole('ADMIN')"): (Etapa 3) só ADMIN escreve. Leitura
 *    (GET) fica liberada a qualquer usuário autenticado.
 */
@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    public List<CategoryResponse> list() {
        return categoryService.findAll();
    }

    @GetMapping("/{id}")
    public CategoryResponse get(@PathVariable Long id) {
        return categoryService.findById(id);
    }

    // 201 Created + header Location apontando para o novo recurso (boa prática REST).
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CategoryResponse> create(@Valid @RequestBody CategoryRequest request) {
        CategoryResponse created = categoryService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public CategoryResponse update(@PathVariable Long id, @Valid @RequestBody CategoryRequest request) {
        return categoryService.update(id, request);
    }

    // 204 No Content: deu certo e não há corpo a devolver.
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        categoryService.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
