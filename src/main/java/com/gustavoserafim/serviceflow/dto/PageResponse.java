package com.gustavoserafim.serviceflow.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Envelope de paginação com formato estável e próprio. Não devolvemos o
 * Page do Spring Data direto porque sua estrutura JSON não é um contrato
 * garantido (o Spring Boot 3.3 até avisa disso no log).
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }
}
