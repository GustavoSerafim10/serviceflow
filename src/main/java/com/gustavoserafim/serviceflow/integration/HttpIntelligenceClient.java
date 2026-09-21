package com.gustavoserafim.serviceflow.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.Optional;

/**
 * Implementação HTTP do cliente, usando o RestClient do Spring.
 *
 * Princípio: a sugestão é OPCIONAL, então esta classe nunca propaga falhas.
 * Qualquer problema (conexão recusada, timeout, HTTP 5xx, JSON inesperado)
 * é registrado no log e vira Optional.empty(). Assim, a indisponibilidade do
 * serviço Python nunca derruba nem atrasa muito o restante do sistema.
 */
@Component
public class HttpIntelligenceClient implements IntelligenceClient {

    private static final Logger log = LoggerFactory.getLogger(HttpIntelligenceClient.class);

    private final RestClient restClient;
    private final boolean enabled;

    public HttpIntelligenceClient(@Qualifier("intelligenceRestClient") RestClient restClient,
                                  IntelligenceProperties properties) {
        this.restClient = restClient;
        this.enabled = properties.enabled();
    }

    @Override
    public Optional<IntelligenceResponse> suggest(String title, String description) {
        if (!enabled) {
            return Optional.empty();
        }
        try {
            IntelligenceResponse body = restClient.post()
                    .uri("/v1/suggestions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("title", title, "description", description))
                    .retrieve()
                    .body(IntelligenceResponse.class);
            return Optional.ofNullable(body);
        } catch (RuntimeException ex) {
            // Só a mensagem (não o stack trace): serviço fora do ar é situação esperada, não bug.
            log.warn("Serviço de sugestão indisponível: {}", ex.getMessage());
            return Optional.empty();
        }
    }
}
