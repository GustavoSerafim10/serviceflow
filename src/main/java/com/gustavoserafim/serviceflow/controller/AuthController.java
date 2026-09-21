package com.gustavoserafim.serviceflow.controller;

import com.gustavoserafim.serviceflow.dto.LoginRequest;
import com.gustavoserafim.serviceflow.dto.LoginResponse;
import com.gustavoserafim.serviceflow.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Única rota pública da API (liberada no SecurityConfig). */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }
}
