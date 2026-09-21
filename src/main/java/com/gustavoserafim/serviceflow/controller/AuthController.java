package com.gustavoserafim.serviceflow.controller;

import com.gustavoserafim.serviceflow.dto.LoginRequest;
import com.gustavoserafim.serviceflow.dto.RefreshRequest;
import com.gustavoserafim.serviceflow.dto.TokenResponse;
import com.gustavoserafim.serviceflow.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Rotas públicas da API (liberadas no SecurityConfig): não exigem access token. */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Autenticação", description = "Login, renovação de sessão e logout")
@SecurityRequirements // remove o cadeado no Swagger UI para todas as rotas desta classe
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    @Operation(summary = "Autentica com e-mail e senha e devolve o par de tokens")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Troca um refresh token por um novo par de tokens (rotação)",
            description = "Cada refresh token só pode ser usado uma vez. Reutilizar um token já usado "
                    + "encerra todas as sessões do usuário.")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request);
    }

    @PostMapping("/logout")
    @Operation(summary = "Encerra a sessão revogando o refresh token informado")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request);
        return ResponseEntity.noContent().build();
    }
}
