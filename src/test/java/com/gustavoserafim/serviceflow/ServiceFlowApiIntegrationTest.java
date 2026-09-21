package com.gustavoserafim.serviceflow;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cenários de ponta a ponta pela API HTTP, com segurança e banco reais.
 * Cada teste cria seus próprios dados com sufixos aleatórios, então não
 * depende de ordem de execução nem do estado do banco.
 * O ADMIN inicial vem do AdminBootstrap (credenciais padrão do application.yml).
 */
class ServiceFlowApiIntegrationTest extends AbstractIntegrationTest {

    private static final String ADMIN_EMAIL = "admin@serviceflow.local";
    private static final String ADMIN_PASSWORD = "Admin@12345";
    private static final String PASSWORD = "senha1234";

    @Autowired
    private MockMvc mockMvc;

    // ------------------------------------------------------------------ helpers

    private String body(MockHttpServletRequestBuilder request, String token, String json) throws Exception {
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return mockMvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(json))
                .andReturn().getResponse().getContentAsString();
    }

    private String login(String email, String password) throws Exception {
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.token");
    }

    private Number createUser(String adminToken, String prefix, String role) throws Exception {
        String email = prefix + "-" + UUID.randomUUID() + "@teste.com";
        String response = mockMvc.perform(post("/api/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"%s\",\"email\":\"%s\",\"password\":\"%s\",\"role\":\"%s\"}"
                                .formatted(prefix, email, PASSWORD, role)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        // devolve o id; o e-mail é recuperável pela resposta
        return JsonPath.read(response, "$.id");
    }

    private String emailOf(String adminToken, Number userId) throws Exception {
        String response = mockMvc.perform(get("/api/users/" + userId)
                        .header("Authorization", "Bearer " + adminToken))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.email");
    }

    private Number createCategory(String adminToken) throws Exception {
        String response = mockMvc.perform(post("/api/categories")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cat-%s\"}".formatted(UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.id");
    }

    // ------------------------------------------------------------------ cenários

    @Test
    void ticketLifecycle_fromOpeningToClosing_recordsCompleteHistory() throws Exception {
        String admin = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        Number categoryId = createCategory(admin);
        Number techId = createUser(admin, "tec", "TECNICO");
        Number reqId = createUser(admin, "sol", "SOLICITANTE");
        String tech = login(emailOf(admin, techId), PASSWORD);
        String requester = login(emailOf(admin, reqId), PASSWORD);

        // solicitante abre o chamado: nasce ABERTO, com prazo de SLA calculado
        String created = body(post("/api/tickets"), requester,
                "{\"title\":\"Sem rede\",\"description\":\"Sem conexão\",\"categoryId\":%s,\"priority\":\"P1\"}"
                        .formatted(categoryId));
        Number ticketId = JsonPath.read(created, "$.id");
        assertThat((String) JsonPath.read(created, "$.status")).isEqualTo("ABERTO");
        assertThat((String) JsonPath.read(created, "$.slaStatus")).isEqualTo("DENTRO_DO_PRAZO");
        assertThat((String) JsonPath.read(created, "$.slaDueAt")).isNotBlank();

        // técnico assume, resolve; solicitante fecha
        mockMvc.perform(put("/api/tickets/" + ticketId + "/assignment")
                        .header("Authorization", "Bearer " + tech)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"technicianId\":" + techId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EM_ATENDIMENTO"));

        mockMvc.perform(patch("/api/tickets/" + ticketId + "/status")
                        .header("Authorization", "Bearer " + tech)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"RESOLVIDO\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/tickets/" + ticketId + "/status")
                        .header("Authorization", "Bearer " + requester)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"FECHADO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FECHADO"));

        // histórico: CREATED, ASSIGNED, ABERTO→EM_ATENDIMENTO, →RESOLVIDO, →FECHADO
        mockMvc.perform(get("/api/tickets/" + ticketId + "/history")
                        .header("Authorization", "Bearer " + requester))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[0].action").value("CREATED"))
                .andExpect(jsonPath("$[4].details").value("Status: RESOLVIDO → FECHADO"));

        // chamado encerrado não aceita comentários
        mockMvc.perform(post("/api/tickets/" + ticketId + "/comments")
                        .header("Authorization", "Bearer " + requester)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"obrigado\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void security_requestsWithoutTokenOrWithoutRole_areRejected() throws Exception {
        String admin = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        Number reqId = createUser(admin, "sol", "SOLICITANTE");
        String requester = login(emailOf(admin, reqId), PASSWORD);

        mockMvc.perform(get("/api/tickets")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/tickets").header("Authorization", "Bearer token-invalido"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/users").header("Authorization", "Bearer " + requester))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/categories")
                        .header("Authorization", "Bearer " + requester)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Nao pode\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void visibility_requesterCannotSeeOrListAnotherRequestersTickets() throws Exception {
        String admin = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        Number categoryId = createCategory(admin);
        String owner = login(emailOf(admin, createUser(admin, "dono", "SOLICITANTE")), PASSWORD);
        String stranger = login(emailOf(admin, createUser(admin, "outro", "SOLICITANTE")), PASSWORD);

        String created = body(post("/api/tickets"), owner,
                "{\"title\":\"Meu chamado\",\"description\":\"detalhe\",\"categoryId\":%s,\"priority\":\"P3\"}"
                        .formatted(categoryId));
        Number ticketId = JsonPath.read(created, "$.id");

        mockMvc.perform(get("/api/tickets/" + ticketId).header("Authorization", "Bearer " + stranger))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/tickets").header("Authorization", "Bearer " + stranger))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/api/tickets").header("Authorization", "Bearer " + owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void validation_blankCategoryName_returnsProblemDetailWithFieldErrors() throws Exception {
        String admin = login(ADMIN_EMAIL, ADMIN_PASSWORD);

        mockMvc.perform(post("/api/categories")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Erro de validação"))
                .andExpect(jsonPath("$.errors.name").value("O nome é obrigatório"));
    }

    @Test
    void login_withWrongPassword_returnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"errada\"}".formatted(ADMIN_EMAIL)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Credenciais inválidas"));
    }
}
