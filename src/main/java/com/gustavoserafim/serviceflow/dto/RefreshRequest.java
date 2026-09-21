package com.gustavoserafim.serviceflow.dto;

import jakarta.validation.constraints.NotBlank;

/** Corpo de POST /api/auth/refresh e POST /api/auth/logout. */
public record RefreshRequest(

        @NotBlank(message = "O refresh token é obrigatório")
        String refreshToken
) {
}
