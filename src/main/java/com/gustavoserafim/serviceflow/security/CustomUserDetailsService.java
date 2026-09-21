package com.gustavoserafim.serviceflow.security;

import com.gustavoserafim.serviceflow.entity.User;
import com.gustavoserafim.serviceflow.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Ponte entre o NOSSO banco e o Spring Security.
 *
 * O Security não conhece a entidade User; ele só entende UserDetails
 * (username, senha/hash, authorities, habilitado?). Este serviço busca o
 * usuário pelo e-mail e traduz. Ele é usado em dois lugares:
 *  1. no login (o AuthenticationManager compara a senha enviada com o hash);
 *  2. a cada requisição com token (o JwtAuthenticationFilter recarrega o
 *     usuário, então desativar alguém ou mudar a role vale imediatamente).
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new UsernameNotFoundException("Usuário não encontrado"));

        // Nome totalmente qualificado porque nossa entidade também se chama User.
        return org.springframework.security.core.userdetails.User
                .withUsername(user.getEmail())
                .password(user.getPasswordHash())
                // roles("ADMIN") vira a authority "ROLE_ADMIN"; é esse prefixo
                // que faz hasRole('ADMIN') funcionar nas anotações @PreAuthorize.
                .roles(user.getRole().name())
                .disabled(!user.isActive())
                .build();
    }
}
