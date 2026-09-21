package com.gustavoserafim.serviceflow.security;

import com.gustavoserafim.serviceflow.entity.User;
import com.gustavoserafim.serviceflow.repository.UserRepository;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Responde "quem é o usuário logado nesta requisição?" devolvendo a entidade
 * User completa (com id e role), que as regras de negócio dos chamados usam.
 * Lê o SecurityContext preenchido pelo JwtAuthenticationFilter.
 * Isolado numa classe própria para os services não dependerem de código
 * estático do Spring Security (e poderem ser testados com um mock).
 */
@Component
public class CurrentUserProvider {

    private final UserRepository userRepository;

    public CurrentUserProvider(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User get() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AuthenticationCredentialsNotFoundException("Nenhum usuário autenticado");
        }
        return userRepository.findByEmailIgnoreCase(authentication.getName())
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException("Usuário autenticado não encontrado"));
    }
}
