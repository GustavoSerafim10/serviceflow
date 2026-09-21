package com.gustavoserafim.serviceflow.dto;

/**
 * Resposta de login e de refresh.
 *  - accessToken: JWT de vida curta; vai em "Authorization: Bearer <accessToken>".
 *  - refreshToken: guardado pelo cliente e usado só em POST /api/auth/refresh
 *    para obter um novo par quando o accessToken expirar.
 */
public record TokenResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresInSeconds
) {
}
