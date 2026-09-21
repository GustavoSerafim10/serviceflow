package com.gustavoserafim.serviceflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO de ENTRADA: o que o cliente envia no corpo do POST/PUT.
 *
 * É um "record" (Java 16+): classe imutável enxuta. O compilador gera
 * construtor, getters (name(), description()), equals, hashCode e toString.
 *
 * As anotações são Bean Validation. Só são checadas quando o controller
 * usa @Valid no parâmetro; se falharem, o Spring lança
 * MethodArgumentNotValidException, que o GlobalExceptionHandler traduz em 400.
 */
public record CategoryRequest(

        @NotBlank(message = "O nome é obrigatório")
        @Size(max = 100, message = "O nome deve ter no máximo 100 caracteres")
        String name,

        @Size(max = 255, message = "A descrição deve ter no máximo 255 caracteres")
        String description
) {
}
