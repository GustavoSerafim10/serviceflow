package com.gustavoserafim.serviceflow.service;

import com.gustavoserafim.serviceflow.dto.LoginRequest;
import com.gustavoserafim.serviceflow.dto.LoginResponse;
import com.gustavoserafim.serviceflow.security.AppUserDetails;
import com.gustavoserafim.serviceflow.security.JwtService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

/**
 * Caso de uso "login". Delega a conferência de e-mail/senha ao
 * AuthenticationManager (que usa CustomUserDetailsService + BCrypt). Se as
 * credenciais estiverem erradas ou o usuário estiver desativado, ele lança
 * AuthenticationException, que o GlobalExceptionHandler converte em 401.
 * Se der certo, emitimos o token.
 */
@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthService(AuthenticationManager authenticationManager, JwtService jwtService) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    public LoginResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email().trim(), request.password()));

        AppUserDetails principal = (AppUserDetails) authentication.getPrincipal();
        String token = jwtService.generateToken(principal.getUsername(), principal.getTokenVersion());
        return new LoginResponse(token, "Bearer", jwtService.getExpirationSeconds());
    }
}
