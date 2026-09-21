package com.gustavoserafim.serviceflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gustavoserafim.serviceflow.entity.Priority;
import com.gustavoserafim.serviceflow.entity.TicketSuggestion;
import com.gustavoserafim.serviceflow.repository.CategoryRepository;
import com.gustavoserafim.serviceflow.repository.TicketSuggestionRepository;
import com.gustavoserafim.serviceflow.repository.UserRepository;
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

    @Autowired
    private TicketSuggestionRepository suggestionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryRepository categoryRepository;

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
        return JsonPath.read(response, "$.accessToken");
    }

    private String loginResponse(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private org.springframework.test.web.servlet.ResultActions refresh(String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"%s\"}".formatted(refreshToken)));
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
    void refreshToken_isRotatedAndItsReuseEndsAllSessions() throws Exception {
        String admin = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        Number userId = createUser(admin, "ref", "SOLICITANTE");
        String email = emailOf(admin, userId);

        String first = loginResponse(email, PASSWORD);
        String refresh1 = JsonPath.read(first, "$.refreshToken");

        // refresh válido: devolve um par NOVO
        String second = refresh(refresh1).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String refresh2 = JsonPath.read(second, "$.refreshToken");
        String access2 = JsonPath.read(second, "$.accessToken");
        assertThat(refresh2).isNotEqualTo(refresh1);
        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + access2))
                .andExpect(status().isOk());

        // reutilizar o token antigo = possível roubo: 401 e a sessão inteira cai
        refresh(refresh1).andExpect(status().isUnauthorized());
        refresh(refresh2).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + access2))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logout_revokesTheRefreshToken() throws Exception {
        String admin = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        String email = emailOf(admin, createUser(admin, "out", "SOLICITANTE"));
        String refreshToken = JsonPath.read(loginResponse(email, PASSWORD), "$.refreshToken");

        mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"%s\"}".formatted(refreshToken)))
                .andExpect(status().isNoContent());

        refresh(refreshToken).andExpect(status().isUnauthorized());
    }

    @Test
    void passwordChange_endsPreviousSessionsAndAcceptsNewPassword() throws Exception {
        String admin = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        String email = emailOf(admin, createUser(admin, "pwd", "SOLICITANTE"));
        String session = loginResponse(email, PASSWORD);
        String access = JsonPath.read(session, "$.accessToken");
        String refreshToken = JsonPath.read(session, "$.refreshToken");

        mockMvc.perform(post("/api/users/me/password")
                        .header("Authorization", "Bearer " + access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"%s\",\"newPassword\":\"outra-senha-1\"}".formatted(PASSWORD)))
                .andExpect(status().isNoContent());

        // tudo que existia antes da troca deixa de valer
        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + access))
                .andExpect(status().isUnauthorized());
        refresh(refreshToken).andExpect(status().isUnauthorized());

        // a senha antiga não entra mais; a nova, sim
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
                .andExpect(status().isUnauthorized());
        login(email, "outra-senha-1");
    }

    @Test
    void suggestions_whenIntelligenceServiceIsDown_degradesGracefully() throws Exception {
        String admin = login(ADMIN_EMAIL, ADMIN_PASSWORD);

        // exige autenticação como qualquer outra rota
        mockMvc.perform(post("/api/tickets/suggestions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Sem toner\",\"description\":\"Impressora parou\"}"))
                .andExpect(status().isUnauthorized());

        // serviço Python inacessível: 200 com available=false, nunca 5xx
        mockMvc.perform(post("/api/tickets/suggestions")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Sem toner\",\"description\":\"Impressora parou\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false));

        // validação idêntica à do chamado
        mockMvc.perform(post("/api/tickets/suggestions")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\",\"description\":\"x\"}"))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------ ciclo de feedback das sugestões

    private TicketSuggestion offerSuggestion(String ownerEmail, String modelVersion,
                                              Number categoryId, Priority priority) {
        TicketSuggestion suggestion = new TicketSuggestion();
        suggestion.setUser(userRepository.findByEmailIgnoreCase(ownerEmail).orElseThrow());
        suggestion.setModelVersion(modelVersion);
        suggestion.setSuggestedCategory(categoryRepository.getReferenceById(categoryId.longValue()));
        suggestion.setCategoryConfidence(0.8);
        suggestion.setSuggestedPriority(priority);
        suggestion.setPriorityConfidence(0.7);
        return suggestionRepository.save(suggestion);
    }

    private org.springframework.test.web.servlet.ResultActions openTicket(
            String token, Number categoryId, String priority, Long suggestionId) throws Exception {
        java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("title", "Chamado " + UUID.randomUUID());
        payload.put("description", "detalhe");
        payload.put("categoryId", categoryId);
        payload.put("priority", priority);
        if (suggestionId != null) {
            payload.put("suggestionId", suggestionId);
        }
        String json = new ObjectMapper().writeValueAsString(payload);
        return mockMvc.perform(post("/api/tickets")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void suggestionFeedback_recordsAcceptedAndChanged_ignoresForeignAndReused_andExposesMetrics() throws Exception {
        String admin = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        Number catA = createCategory(admin);
        Number catB = createCategory(admin);
        String ownerEmail = emailOf(admin, createUser(admin, "fb", "SOLICITANTE"));
        String strangerEmail = emailOf(admin, createUser(admin, "fb2", "SOLICITANTE"));
        String owner = login(ownerEmail, PASSWORD);
        String model = "it-" + UUID.randomUUID();

        TicketSuggestion kept = offerSuggestion(ownerEmail, model, catA, Priority.P2);
        TicketSuggestion changed = offerSuggestion(ownerEmail, model, catA, Priority.P1);
        TicketSuggestion foreign = offerSuggestion(strangerEmail, model, catA, Priority.P2);
        offerSuggestion(ownerEmail, model, catA, Priority.P3); // oferecida e abandonada (nunca vira chamado)

        // aceitou tudo
        openTicket(owner, catA, "P2", kept.getId()).andExpect(status().isCreated());
        // trocou categoria e prioridade
        openTicket(owner, catB, "P3", changed.getId()).andExpect(status().isCreated());
        // sugestão de OUTRO usuário e sugestão já usada: o chamado abre normalmente, o feedback é ignorado
        openTicket(owner, catA, "P2", foreign.getId()).andExpect(status().isCreated());
        openTicket(owner, catA, "P2", kept.getId()).andExpect(status().isCreated());
        // id inexistente também nunca falha a abertura
        openTicket(owner, catA, "P2", 999_999_999L).andExpect(status().isCreated());

        TicketSuggestion keptSaved = suggestionRepository.findById(kept.getId()).orElseThrow();
        assertThat(keptSaved.getCategoryAccepted()).isTrue();
        assertThat(keptSaved.getPriorityAccepted()).isTrue();
        TicketSuggestion changedSaved = suggestionRepository.findById(changed.getId()).orElseThrow();
        assertThat(changedSaved.getCategoryAccepted()).isFalse();
        assertThat(changedSaved.getPriorityAccepted()).isFalse();
        assertThat(suggestionRepository.findById(foreign.getId()).orElseThrow().getTicket()).isNull();

        // métricas (ADMIN): 4 oferecidas, 2 viraram chamado, categoria 1/2 aceita, prioridade 1/2 aceita
        String metrics = mockMvc.perform(get("/api/tickets/suggestions/metrics")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String base = "$.models[?(@.modelVersion == '" + model + "')]";
        assertThat((java.util.List<Object>) JsonPath.read(metrics, base + ".offered")).containsExactly(4);
        assertThat((java.util.List<Object>) JsonPath.read(metrics, base + ".usedInTickets")).containsExactly(2);
        assertThat((java.util.List<Object>) JsonPath.read(metrics, base + ".categoryAcceptanceRate")).containsExactly(0.5);
        assertThat((java.util.List<Object>) JsonPath.read(metrics, base + ".priorityAcceptanceRate")).containsExactly(0.5);

        // só ADMIN vê as métricas
        mockMvc.perform(get("/api/tickets/suggestions/metrics").header("Authorization", "Bearer " + owner))
                .andExpect(status().isForbidden());
    }

    @Test
    void trainingDataExport_isAdminOnly_neutralisesFormulas_andSkipsCancelledTickets() throws Exception {
        String admin = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        Number category = createCategory(admin);
        String requester = login(emailOf(admin, createUser(admin, "exp", "SOLICITANTE")), PASSWORD);
        String marker = UUID.randomUUID().toString();

        String kept = new ObjectMapper().writeValueAsString(java.util.Map.of(
                "title", "=cmd|' /C calc'!A0 " + marker, "description", "linha 1, com vírgula\nlinha 2",
                "categoryId", category, "priority", "P2"));
        mockMvc.perform(post("/api/tickets").header("Authorization", "Bearer " + requester)
                .contentType(MediaType.APPLICATION_JSON).content(kept)).andExpect(status().isCreated());

        String cancelledJson = new ObjectMapper().writeValueAsString(java.util.Map.of(
                "title", "cancelado " + marker, "description", "d", "categoryId", category, "priority", "P4"));
        String cancelled = mockMvc.perform(post("/api/tickets").header("Authorization", "Bearer " + requester)
                        .contentType(MediaType.APPLICATION_JSON).content(cancelledJson))
                .andReturn().getResponse().getContentAsString();
        Number cancelledId = JsonPath.read(cancelled, "$.id");
        mockMvc.perform(patch("/api/tickets/" + cancelledId + "/status")
                        .header("Authorization", "Bearer " + requester)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"CANCELADO\"}"))
                .andExpect(status().isOk());

        var result = mockMvc.perform(get("/api/tickets/suggestions/training-data")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string("Content-Disposition", org.hamcrest.Matchers.containsString("tickets-training.csv")))
                .andReturn().getResponse();
        String csv = result.getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        assertThat(csv).startsWith("title,description,category,priority\n");
        assertThat(csv).contains("'=cmd|' /C calc'!A0 " + marker);   // fórmula neutralizada com apóstrofo
        assertThat(csv).doesNotContain("\n=cmd");                      // nenhuma célula começa com "="
        assertThat(csv).contains("\"linha 1, com vírgula\nlinha 2\""); // vírgula e quebra de linha entre aspas
        assertThat(csv).doesNotContain("cancelado " + marker);         // cancelados ficam de fora

        mockMvc.perform(get("/api/tickets/suggestions/training-data").header("Authorization", "Bearer " + requester))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/tickets/suggestions/training-data")).andExpect(status().isUnauthorized());
    }

    @Test
    void defaultCategories_areAvailableOutOfTheBox() throws Exception {
        String admin = login(ADMIN_EMAIL, ADMIN_PASSWORD);

        mockMvc.perform(get("/api/categories").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'Impressora')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.name == 'Acesso e Senha')]").isNotEmpty());
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
