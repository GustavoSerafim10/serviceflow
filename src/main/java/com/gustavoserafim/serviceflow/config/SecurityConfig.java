package com.gustavoserafim.serviceflow.config;

import com.gustavoserafim.serviceflow.security.JwtAuthenticationFilter;
import com.gustavoserafim.serviceflow.security.JwtService;
import com.gustavoserafim.serviceflow.security.RestSecurityHandlers;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Configuração central de segurança.
 *
 * - @Configuration: classe que declara beans com métodos @Bean.
 * - @EnableMethodSecurity: liga o @PreAuthorize nos controllers/services.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    /**
     * A "cadeia de filtros": define regras de acesso por URL e a ordem dos filtros.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtService jwtService,
                                                   UserDetailsService userDetailsService,
                                                   RestSecurityHandlers securityHandlers) throws Exception {
        http
                // CSRF protege sessões baseadas em cookie. Nossa API é stateless e
                // autentica por header Authorization (o navegador não o envia
                // sozinho), então CSRF não se aplica.
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/login", "/error").permitAll()
                        // Documentação pública (só leitura do contrato; as rotas em si seguem protegidas).
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(securityHandlers)   // 401
                        .accessDeniedHandler(securityHandlers))       // 403
                // Nosso filtro JWT entra ANTES do filtro padrão de login por formulário.
                .addFilterBefore(new JwtAuthenticationFilter(jwtService, userDetailsService),
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /** BCrypt: algoritmo de hash de senha, deliberadamente lento (dificulta força bruta). */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Quem efetivamente confere e-mail+senha no login. O Spring monta o
     * provider sozinho a partir dos beans UserDetailsService + PasswordEncoder.
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }
}
