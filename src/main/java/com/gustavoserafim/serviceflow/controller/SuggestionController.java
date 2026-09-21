package com.gustavoserafim.serviceflow.controller;

import com.gustavoserafim.serviceflow.dto.SuggestionRequest;
import com.gustavoserafim.serviceflow.dto.SuggestionResponse;
import com.gustavoserafim.serviceflow.service.SuggestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sugestão de categoria e prioridade ANTES de abrir o chamado: o formulário
 * chama este endpoint enquanto o usuário preenche título e descrição, exibe a
 * sugestão e o usuário aceita ou ignora. Abrir o chamado (POST /api/tickets)
 * continua totalmente independente do serviço de IA.
 */
@RestController
@RequestMapping("/api/tickets/suggestions")
@Tag(name = "Sugestões", description = "Sugestão automática de categoria e prioridade (serviço Python)")
public class SuggestionController {

    private final SuggestionService suggestionService;

    public SuggestionController(SuggestionService suggestionService) {
        this.suggestionService = suggestionService;
    }

    @PostMapping
    @Operation(summary = "Sugere categoria e prioridade para um chamado ainda não aberto",
            description = "Sempre responde 200. Se o serviço de IA estiver indisponível, devolve "
                    + "available=false e o cliente segue sem sugestão. A categoria sugerida já vem "
                    + "resolvida para uma categoria ativa do sistema (com id).")
    public SuggestionResponse suggest(@Valid @RequestBody SuggestionRequest request) {
        return suggestionService.suggest(request);
    }
}
