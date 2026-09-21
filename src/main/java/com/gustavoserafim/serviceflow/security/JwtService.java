package com.gustavoserafim.serviceflow.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/**
 * Gera e valida tokens JWT (os "access tokens", de vida curta).
 *
 * Um JWT são 3 partes em Base64 separadas por ponto: header.payload.assinatura.
 *  - payload: "claims" (dados). Aqui: subject (o e-mail), a versão do token
 *    do usuário ("tv"), emissão e expiração.
 *  - assinatura: HMAC-SHA de header+payload usando a chave secreta. A JJWT
 *    escolhe o algoritmo pelo tamanho da chave (256 bits -> HS256, 384 -> HS384,
 *    512 -> HS512); com a chave padrão de dev (62 bytes) sai HS384.
 *
 * ATENÇÃO: o payload é apenas codificado, NÃO criptografado — qualquer um
 * lê. Por isso nunca colocamos senha ou dados sensíveis nele. A assinatura
 * garante INTEGRIDADE: se alguém alterar o payload, a assinatura não bate e
 * o parse lança exceção. Como o servidor só precisa da chave para conferir,
 * não guarda sessão: a API é "stateless".
 */
@Service
public class JwtService {

    private static final String TOKEN_VERSION_CLAIM = "tv";

    private final SecretKey key;
    private final long expirationMinutes;

    /** Conteúdo útil de um token já validado. */
    public record TokenClaims(String subject, int tokenVersion) {
    }

    // Construtor recebe valores do application.yml via @Value. Fica fácil de
    // testar: o teste unitário chama new JwtService("...", 15) sem Spring.
    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.expiration-minutes}") long expirationMinutes) {
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        this.expirationMinutes = expirationMinutes;
    }

    public String generateToken(String subject, int tokenVersion) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(subject)
                .claim(TOKEN_VERSION_CLAIM, tokenVersion)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(Duration.ofMinutes(expirationMinutes))))
                .signWith(key)
                .compact();
    }

    /**
     * Valida assinatura e expiração e devolve o conteúdo do token.
     * Lança JwtException se o token for inválido, adulterado ou expirado.
     */
    public TokenClaims parse(String token) throws JwtException {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        Integer version = claims.get(TOKEN_VERSION_CLAIM, Integer.class);
        // Token sem versão (formato antigo) recebe -1, que nunca coincide com a do usuário.
        return new TokenClaims(claims.getSubject(), version == null ? -1 : version);
    }

    public long getExpirationSeconds() {
        return Duration.ofMinutes(expirationMinutes).toSeconds();
    }
}
