package com.gustavoserafim.serviceflow.controller;

import com.gustavoserafim.serviceflow.dto.SuggestionMetricsResponse;
import com.gustavoserafim.serviceflow.dto.SuggestionRequest;
import com.gustavoserafim.serviceflow.dto.SuggestionResponse;
import com.gustavoserafim.serviceflow.service.SuggestionFeedbackService;
import com.gustavoserafim.serviceflow.service.SuggestionService;
import com.gustavoserafim.serviceflow.service.TrainingDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

/**
 * Sugestão de categoria e prioridade ANTES de abrir o chamado: o formulário
 * chama este endpoint enquanto o usuário preenche título e descrição, exibe a
 * sugestão e o usuário aceita ou ignora. Abrir o chamado (POST /api/tickets)
 * continua totalmente independente do serviço de IA.
 *
 * Também expõe, só para ADMIN, as métricas de aceitação e a exportação de
 * dados para retreino (o ciclo de feedback).
 */
@RestController
@RequestMapping("/api/tickets/suggestions")
@Tag(name = "Sugestões", description = "Sugestão automática de categoria e prioridade (serviço Python) e ciclo de feedback")
public class SuggestionController {

    private final SuggestionService suggestionService;
    private final SuggestionFeedbackService feedbackService;
    private final TrainingDataService trainingDataService;

    public SuggestionController(SuggestionService suggestionService,
                                SuggestionFeedbackService feedbackService,
                                TrainingDataService trainingDataService) {
        this.suggestionService = suggestionService;
        this.feedbackService = feedbackService;
        this.trainingDataService = trainingDataService;
    }

    @PostMapping
    @Operation(summary = "Sugere categoria e prioridade para um chamado ainda não aberto",
            description = "Sempre responde 200. Se o serviço de IA estiver indisponível, devolve "
                    + "available=false e o cliente segue sem sugestão. A categoria sugerida já vem "
                    + "resolvida para uma categoria ativa do sistema (com id). Envie o suggestionId "
                    + "retornado em POST /api/tickets para registrar se o usuário aceitou a sugestão.")
    public SuggestionResponse suggest(@Valid @RequestBody SuggestionRequest request) {
        return suggestionService.suggest(request);
    }

    @GetMapping("/metrics")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Taxa de aceitação das sugestões por versão do modelo (ADMIN)",
            description = "Aceitação = sugestões aceitas / sugestões que viraram chamado. É a medida REAL de "
                    + "qualidade do modelo, baseada no que os usuários fazem.")
    public SuggestionMetricsResponse metrics() {
        return feedbackService.metrics();
    }

    @GetMapping(value = "/training-data", produces = "text/csv;charset=UTF-8")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Exporta os chamados como CSV para retreinar o modelo (ADMIN)",
            description = "Formato title,description,category,priority, o mesmo lido por `python -m app.train`. "
                    + "Contém dados dos usuários: trate como confidencial. Chamados cancelados ficam de fora.")
    public ResponseEntity<byte[]> trainingData() {
        byte[] body = trainingDataService.exportCsv().getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("tickets-training.csv").build().toString())
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(body);
    }
}
