package com.gustavoserafim.serviceflow.dto;

import com.gustavoserafim.serviceflow.entity.Category;

import java.time.Instant;

/**
 * DTO de SAÍDA: o que a API devolve. Só expõe o que queremos expor.
 *
 * O método estático from(...) converte Entidade -> DTO. Manter essa
 * conversão aqui (em vez de espalhar pelo projeto) deixa um único lugar
 * para mudar quando o contrato da API evoluir.
 */
public record CategoryResponse(
        Long id,
        String name,
        String description,
        boolean active,
        Instant createdAt
) {

    public static CategoryResponse from(Category category) {
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getDescription(),
                category.isActive(),
                category.getCreatedAt()
        );
    }
}
