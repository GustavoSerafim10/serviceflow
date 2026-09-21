package com.gustavoserafim.serviceflow.dto;

import jakarta.validation.constraints.NotBlank;

/** Corpo do POST /api/auth/login. */
public record LoginRequest(

        @NotBlank(message = "O e-mail é obrigatório")
        String email,

        @NotBlank(message = "A senha é obrigatória")
        String password
) {
}
