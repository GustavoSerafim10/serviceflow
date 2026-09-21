package com.gustavoserafim.serviceflow.controller;

import com.gustavoserafim.serviceflow.dto.LoginRequest;
import com.gustavoserafim.serviceflow.dto.LoginResponse;
import com.gustavoserafim.serviceflow.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Única rota pública da API (liberada no SecurityConfig). */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Autenticação", description = "Login e emissão do token JWT")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    @Operation(summary = "Autentica com e-mail e senha e devolve o token JWT")
    @SecurityRequirements // rota pública: remove o cadeado no Swagger UI
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }
}
