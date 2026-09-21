package com.gustavoserafim.serviceflow.integration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuração do serviço de sugestão (app.intelligence.* no application.yml).
 *
 * Os timeouts são curtos de propósito: a sugestão é um EXTRA; se o serviço
 * Python estiver lento ou fora do ar, preferimos desistir rápido a deixar a
 * tela do usuário esperando.
 */
@ConfigurationProperties(prefix = "app.intelligence")
public record IntelligenceProperties(
        boolean enabled,
        String baseUrl,
        Duration connectTimeout,
        Duration readTimeout,
        double minConfidence   // abaixo disso a sugestão PRINCIPAL não é oferecida (0 a 1)
) {

    public IntelligenceProperties {
        baseUrl = (baseUrl != null && !baseUrl.isBlank()) ? baseUrl : "http://localhost:8000";
        connectTimeout = (connectTimeout != null) ? connectTimeout : Duration.ofMillis(500);
        readTimeout = (readTimeout != null) ? readTimeout : Duration.ofSeconds(2);
    }
}
