package com.gustavoserafim.serviceflow.security;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Teste unitário puro (sem Spring): o JwtService recebe tudo pelo construtor,
 * então basta instanciá-lo. Cobre os três comportamentos que importam:
 * ida-e-volta, token expirado e token adulterado.
 */
class JwtServiceTest {

    // Base64 de uma string de 62 bytes (>= 256 bits, mínimo exigido pela JJWT).
    private static final String SECRET =
            "c2VydmljZWZsb3ctZGV2LW9ubHktc2VjcmV0LWNoYW5nZS1tZS1pbi1wcm9kdWN0aW9uLTAxMjM0NTY3ODk=";

    @Test
    void generateToken_thenExtractSubject_returnsSameSubject() {
        JwtService jwtService = new JwtService(SECRET, 60);

        String token = jwtService.generateToken("ana@empresa.com");

        assertThat(jwtService.extractSubject(token)).isEqualTo("ana@empresa.com");
    }

    @Test
    void extractSubject_withExpiredToken_throwsJwtException() {
        // Expiração negativa = token já nasce vencido.
        JwtService jwtService = new JwtService(SECRET, -1);
        String token = jwtService.generateToken("ana@empresa.com");

        assertThatThrownBy(() -> jwtService.extractSubject(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void extractSubject_withTamperedToken_throwsJwtException() {
        JwtService jwtService = new JwtService(SECRET, 60);
        String token = jwtService.generateToken("ana@empresa.com");

        // Troca o PRIMEIRO caractere da assinatura (a parte após o último ponto).
        // Não o último: em Base64 o último caractere pode conter bits de
        // preenchimento ignorados, e a troca poderia não alterar nada.
        int signatureStart = token.lastIndexOf('.') + 1;
        char first = token.charAt(signatureStart);
        String tampered = token.substring(0, signatureStart)
                + (first == 'A' ? 'B' : 'A')
                + token.substring(signatureStart + 1);

        assertThatThrownBy(() -> jwtService.extractSubject(tampered))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void extractSubject_withTokenSignedByAnotherKey_throwsJwtException() {
        String otherSecret = "b3V0cmEtY2hhdmUtc2VjcmV0YS1kaWZlcmVudGUtZG8tc2VydmljZWZsb3ctMDEyMzQ1Njc4OQ==";
        String token = new JwtService(otherSecret, 60).generateToken("ana@empresa.com");

        JwtService jwtService = new JwtService(SECRET, 60);

        assertThatThrownBy(() -> jwtService.extractSubject(token))
                .isInstanceOf(JwtException.class);
    }
}
