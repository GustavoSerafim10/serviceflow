package com.gustavoserafim.serviceflow.service;

import com.gustavoserafim.serviceflow.entity.RefreshToken;
import com.gustavoserafim.serviceflow.entity.User;
import com.gustavoserafim.serviceflow.repository.RefreshTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Ciclo de vida dos refresh tokens: emitir, consumir (com rotação e detecção
 * de reuso), revogar e limpar os expirados.
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepository refreshTokenRepository;
    private final Clock clock;
    private final Duration ttl;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository,
                               Clock clock,
                               @Value("${app.jwt.refresh-expiration-days}") long refreshExpirationDays) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.clock = clock;
        this.ttl = Duration.ofDays(refreshExpirationDays);
    }

    /** Cria um refresh token para o usuário e devolve o valor em texto (única vez que ele existe no servidor). */
    @Transactional
    public String issue(User user) {
        byte[] bytes = new byte[32]; // 256 bits de aleatoriedade criptográfica
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        Instant now = clock.instant();
        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setTokenHash(hash(raw));
        token.setCreatedAt(now);
        token.setExpiresAt(now.plus(ttl));
        refreshTokenRepository.save(token);
        return raw;
    }

    /**
     * Valida o token apresentado e o CONSOME (marca como revogado): cada refresh
     * token só pode ser usado uma vez (rotação). Devolve o dono para emitir o par novo.
     *
     * Se o token JÁ estava revogado, alguém o está reutilizando (o legítimo já
     * trocou por um novo) — possível roubo. Resposta: revogar todos os tokens do
     * usuário e subir a versão do access token, derrubando todas as sessões.
     *
     * noRollbackFor: essas mudanças precisam ser GRAVADAS mesmo com a exceção
     * (401) sendo lançada logo em seguida; por padrão, uma RuntimeException
     * desfaria a transação inteira, incluindo a revogação.
     */
    @Transactional(noRollbackFor = AuthenticationException.class)
    public User consume(String rawToken) {
        RefreshToken token = refreshTokenRepository.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new BadCredentialsException("Refresh token inválido"));
        User user = token.getUser();
        Instant now = clock.instant();

        if (token.getRevokedAt() != null) {
            log.warn("Reuso de refresh token detectado para o usuário {}: encerrando todas as sessões", user.getId());
            refreshTokenRepository.revokeAllActiveByUserId(user.getId(), now);
            user.setTokenVersion(user.getTokenVersion() + 1);
            throw new BadCredentialsException("Refresh token reutilizado");
        }
        if (!token.getExpiresAt().isAfter(now) || !user.isActive()) {
            throw new BadCredentialsException("Refresh token expirado ou usuário inativo");
        }

        token.setRevokedAt(now);
        return user;
    }

    /** Logout: revoga este token. Idempotente — token desconhecido ou já revogado é ignorado. */
    @Transactional
    public void revoke(String rawToken) {
        refreshTokenRepository.findByTokenHash(hash(rawToken)).ifPresent(token -> {
            if (token.getRevokedAt() == null) {
                token.setRevokedAt(clock.instant());
            }
        });
    }

    /** Revoga todos os refresh tokens ativos do usuário (ex: troca de senha). */
    @Transactional
    public void revokeAllFor(Long userId) {
        refreshTokenRepository.revokeAllActiveByUserId(userId, clock.instant());
    }

    // Faxina diária às 03:00: remove tokens já expirados (revogados ficam até expirar,
    // pois são necessários para detectar reuso).
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void deleteExpired() {
        int removed = refreshTokenRepository.deleteExpired(clock.instant());
        log.info("Limpeza de refresh tokens: {} expirados removidos", removed);
    }

    private static String hash(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível", e); // não ocorre em JVMs padrão
        }
    }
}
