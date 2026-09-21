package com.gustavoserafim.serviceflow.integration;

import java.util.Optional;

/**
 * Porta de saída para o serviço de inteligência. Uma INTERFACE: o
 * SuggestionService depende dela, não do HTTP. Nos testes basta simular esta
 * interface; a implementação real (HttpIntelligenceClient) fala com o Python.
 *
 * Optional.empty() significa "sem sugestão agora", seja qual for o motivo
 * (serviço desligado, fora do ar, lento, resposta inválida). Quem chama não
 * precisa tratar exceções de rede.
 */
public interface IntelligenceClient {

    Optional<IntelligenceResponse> suggest(String title, String description);
}
