package com.gustavoserafim.serviceflow.dto;

/**
 * Resposta do login. O cliente guarda o token e o envia nas próximas
 * requisições no header:  Authorization: Bearer <token>
 */
public record LoginResponse(
        String token,
        String tokenType,
        long expiresInSeconds
) {
}
