package com.gustavoserafim.serviceflow.integration;

import java.util.List;

/**
 * Espelho do JSON devolvido por POST /v1/suggestions do serviço Python.
 * Os nomes dos campos coincidem com o JSON (camelCase), então o Jackson
 * converte sem configuração. Campos desconhecidos são ignorados, de modo que o
 * serviço Python pode ganhar campos novos sem quebrar esta API.
 */
public record IntelligenceResponse(Prediction category, Prediction priority, String modelVersion) {

    public record Prediction(String label, double confidence, List<Score> alternatives) {
    }

    public record Score(String label, double confidence) {
    }
}
