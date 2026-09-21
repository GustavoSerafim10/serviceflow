package com.gustavoserafim.serviceflow.dto;

import com.gustavoserafim.serviceflow.entity.Role;
import com.gustavoserafim.serviceflow.entity.User;

import java.time.Instant;

/**
 * Saída de usuário. Repare que NÃO existe campo de senha/hash: este é
 * exatamente o motivo de separar entidade e DTO — não há como vazar o hash
 * por descuido, porque o campo nem existe aqui.
 */
public record UserResponse(
        Long id,
        String name,
        String email,
        Role role,
        boolean active,
        Instant createdAt
) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole(),
                user.isActive(),
                user.getCreatedAt()
        );
    }
}
