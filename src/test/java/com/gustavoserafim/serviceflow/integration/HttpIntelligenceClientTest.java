package com.gustavoserafim.serviceflow.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Testa o cliente HTTP contra um servidor SIMULADO (MockRestServiceServer):
 * nenhuma rede real, mas passando pelo RestClient e pelo Jackson de verdade.
 */
class HttpIntelligenceClientTest {

    private static final String BASE = "http://intelligence.test";

    private static final String SUCCESS_JSON = """
            {"category":{"label":"Impressora","confidence":0.91,
                         "alternatives":[{"label":"Rede","confidence":0.03},{"label":"Hardware","confidence":0.02}]},
             "priority":{"label":"P3","confidence":0.66,"alternatives":[{"label":"P2","confidence":0.16}]},
             "modelVersion":"v1-abc",
             "campoNovoQueNaoConhecemos":true}
            """;

    private MockRestServiceServer server;
    private HttpIntelligenceClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new HttpIntelligenceClient(builder.build(), properties(true));
    }

    private static IntelligenceProperties properties(boolean enabled) {
        return new IntelligenceProperties(enabled, BASE, Duration.ofMillis(500), Duration.ofSeconds(2), 0.30);
    }

    @Test
    void suggest_sendsTitleAndDescriptionAndParsesTheResponse() {
        server.expect(requestTo(BASE + "/v1/suggestions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.title").value("Sem toner"))
                .andExpect(jsonPath("$.description").value("A impressora parou"))
                .andRespond(withSuccess(SUCCESS_JSON, MediaType.APPLICATION_JSON));

        Optional<IntelligenceResponse> result = client.suggest("Sem toner", "A impressora parou");

        assertThat(result).isPresent();
        assertThat(result.get().category().label()).isEqualTo("Impressora");
        assertThat(result.get().category().alternatives()).hasSize(2);
        assertThat(result.get().priority().label()).isEqualTo("P3");
        assertThat(result.get().modelVersion()).isEqualTo("v1-abc"); // e o campo desconhecido foi ignorado
        server.verify();
    }

    @Test
    void suggest_preservesAccentedText() {
        server.expect(requestTo(BASE + "/v1/suggestions"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Ninguém")))
                .andRespond(withSuccess(SUCCESS_JSON, MediaType.APPLICATION_JSON));

        assertThat(client.suggest("Conexão", "Ninguém consegue acessar")).isPresent();
        server.verify();
    }

    @Test
    void suggest_whenServerReturns500_returnsEmptyInsteadOfThrowing() {
        server.expect(requestTo(BASE + "/v1/suggestions")).andRespond(withServerError());

        assertThat(client.suggest("t", "d")).isEmpty();
    }

    @Test
    void suggest_whenResponseIsNotValidJson_returnsEmpty() {
        server.expect(requestTo(BASE + "/v1/suggestions"))
                .andRespond(withSuccess("isto não é json", MediaType.APPLICATION_JSON));

        assertThat(client.suggest("t", "d")).isEmpty();
    }

    @Test
    void suggest_whenDisabled_doesNotCallTheServer() {
        HttpIntelligenceClient disabled = new HttpIntelligenceClient(RestClient.builder().build(), properties(false));

        // Sem nenhuma expectativa registrada no servidor simulado, qualquer chamada HTTP falharia o teste.
        assertThat(disabled.suggest("t", "d")).isEmpty();
        server.verify();
    }
}
