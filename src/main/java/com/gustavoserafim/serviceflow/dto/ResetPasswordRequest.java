package com.gustavoserafim.serviceflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Corpo do PUT /api/users/{id}/password: o ADMIN define uma nova senha (ex: usuário esqueceu). */
public record ResetPasswordRequest(

        @NotBlank(message = "A nova senha é obrigatória")
        @Size(min = 8, max = 72, message = "A nova senha deve ter entre 8 e 72 caracteres")
        String newPassword
) {
}
