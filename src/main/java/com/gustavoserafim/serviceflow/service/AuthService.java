package com.gustavoserafim.serviceflow.service;

import com.gustavoserafim.serviceflow.dto.LoginRequest;
import com.gustavoserafim.serviceflow.dto.RefreshRequest;
import com.gustavoserafim.serviceflow.dto.TokenResponse;
import com.gustavoserafim.serviceflow.entity.User;
import com.gustavoserafim.serviceflow.repository.UserRepository;
import com.gustavoserafim.serviceflow.security.JwtService;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso de autenticação: login, renovação (refresh) e logout.
 *
 * O par de tokens: o access token (JWT, 15 min) autentica cada requisição; o
 * refresh token (opaco, 7 dias) só serve para obter um novo par. Assim, um
 * access token vazado vale pouco tempo, e o usuário não precisa digitar a
 * senha a cada 15 minutos.
 */
@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final UserRepository userRepository;

    public AuthService(AuthenticationManager authenticationManager,
                       JwtService jwtService,
                       RefreshTokenService refreshTokenService,
                       UserRepository userRepository) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.userRepository = userRepository;
    }

    /**
     * Delega a conferência de e-mail/senha ao AuthenticationManager (que usa
     * CustomUserDetailsService + BCrypt). Credenciais erradas ou usuário
     * desativado lançam AuthenticationException -> 401.
     */
    @Transactional
    public TokenResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email().trim(), request.password()));

        User user = userRepository.findByEmailIgnoreCase(authentication.getName())
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException("Usuário não encontrado"));
        return issueTokens(user);
    }

    /**
     * Troca um refresh token válido por um NOVO par (rotação). noRollbackFor:
     * quando o reuso de um token é detectado, a revogação das sessões precisa
     * ser gravada mesmo que a resposta seja 401 (ver RefreshTokenService.consume).
     */
    @Transactional(noRollbackFor = AuthenticationException.class)
    public TokenResponse refresh(RefreshRequest request) {
        User user = refreshTokenService.consume(request.refreshToken());
        return issueTokens(user);
    }

    public void logout(RefreshRequest request) {
        refreshTokenService.revoke(request.refreshToken());
    }

    private TokenResponse issueTokens(User user) {
        String accessToken = jwtService.generateToken(user.getEmail(), user.getTokenVersion());
        String refreshToken = refreshTokenService.issue(user);
        return new TokenResponse(accessToken, refreshToken, "Bearer", jwtService.getExpirationSeconds());
    }
}
