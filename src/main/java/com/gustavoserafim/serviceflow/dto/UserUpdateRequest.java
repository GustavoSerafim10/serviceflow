package com.gustavoserafim.serviceflow.dto;

import com.gustavoserafim.serviceflow.entity.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Corpo do PUT /api/users/{id}. Não inclui e-mail (é o login, imutável por
 * enquanto) nem senha (troca de senha é um fluxo próprio, fica para depois).
 */
public record UserUpdateRequest(

        @NotBlank(message = "O nome é obrigatório")
        @Size(max = 100, message = "O nome deve ter no máximo 100 caracteres")
        String name,

        @NotNull(message = "A role é obrigatória")
        Role role
) {
}
