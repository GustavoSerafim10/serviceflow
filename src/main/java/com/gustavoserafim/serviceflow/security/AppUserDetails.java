package com.gustavoserafim.serviceflow.security;

import com.gustavoserafim.serviceflow.entity.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Nossa implementação de UserDetails: o "retrato" do usuário que o Spring
 * Security enxerga. Além do padrão (login, hash, autoridades, habilitado),
 * carrega a versão do token, que o JwtAuthenticationFilter compara com a do JWT.
 *
 * É um snapshot imutável criado a partir da entidade User; não é a entidade
 * (a entidade continua sem conhecer o Spring Security).
 */
public class AppUserDetails implements UserDetails {

    private final String email;
    private final String passwordHash;
    private final boolean active;
    private final int tokenVersion;
    private final List<GrantedAuthority> authorities;

    public AppUserDetails(User user) {
        this.email = user.getEmail();
        this.passwordHash = user.getPasswordHash();
        this.active = user.isActive();
        this.tokenVersion = user.getTokenVersion();
        // O prefixo ROLE_ é o que faz hasRole('ADMIN') funcionar nos @PreAuthorize.
        this.authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
    }

    public int getTokenVersion() {
        return tokenVersion;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }
}
