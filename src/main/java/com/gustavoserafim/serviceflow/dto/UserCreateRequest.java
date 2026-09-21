package com.gustavoserafim.serviceflow.dto;

import com.gustavoserafim.serviceflow.entity.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Corpo do POST /api/users (somente ADMIN cria usuários). */
public record UserCreateRequest(

        @NotBlank(message = "O nome é obrigatório")
        @Size(max = 100, message = "O nome deve ter no máximo 100 caracteres")
        String name,

        @NotBlank(message = "O e-mail é obrigatório")
        @Email(message = "E-mail inválido")
        @Size(max = 150, message = "O e-mail deve ter no máximo 150 caracteres")
        String email,

        // O limite de 72 existe porque o BCrypt só considera os 72 primeiros bytes.
        @NotBlank(message = "A senha é obrigatória")
        @Size(min = 8, max = 72, message = "A senha deve ter entre 8 e 72 caracteres")
        String password,

        @NotNull(message = "A role é obrigatória")
        Role role
) {
}
