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
 * Gera e valida tokens JWT.
 *
 * Um JWT são 3 partes em Base64 separadas por ponto: header.payload.assinatura.
 *  - payload: "claims" (dados). Aqui: subject (o e-mail), emissão e expiração.
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

    private final SecretKey key;
    private final long expirationMinutes;

    // Construtor recebe valores do application.yml via @Value. Fica fácil de
    // testar: o teste unitário chama new JwtService("...", 60) sem Spring.
    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.expiration-minutes}") long expirationMinutes) {
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        this.expirationMinutes = expirationMinutes;
    }

    public String generateToken(String subject) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(subject)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(Duration.ofMinutes(expirationMinutes))))
                .signWith(key)
                .compact();
    }

    /**
     * Valida assinatura e expiração e devolve o subject (e-mail).
     * Lança JwtException se o token for inválido, adulterado ou expirado.
     */
    public String extractSubject(String token) throws JwtException {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claims.getSubject();
    }

    public long getExpirationSeconds() {
        return Duration.ofMinutes(expirationMinutes).toSeconds();
    }
}
