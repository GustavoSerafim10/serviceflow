package com.gustavoserafim.serviceflow.service;

import com.gustavoserafim.serviceflow.dto.SuggestionRequest;
import com.gustavoserafim.serviceflow.dto.SuggestionResponse;
import com.gustavoserafim.serviceflow.dto.SuggestionResponse.CategoryOption;
import com.gustavoserafim.serviceflow.dto.SuggestionResponse.CategorySuggestion;
import com.gustavoserafim.serviceflow.dto.SuggestionResponse.PriorityOption;
import com.gustavoserafim.serviceflow.dto.SuggestionResponse.PrioritySuggestion;
import com.gustavoserafim.serviceflow.entity.Category;
import com.gustavoserafim.serviceflow.entity.Priority;
import com.gustavoserafim.serviceflow.entity.TicketSuggestion;
import com.gustavoserafim.serviceflow.integration.IntelligenceClient;
import com.gustavoserafim.serviceflow.integration.IntelligenceProperties;
import com.gustavoserafim.serviceflow.integration.IntelligenceResponse;
import com.gustavoserafim.serviceflow.integration.IntelligenceResponse.Prediction;
import com.gustavoserafim.serviceflow.integration.IntelligenceResponse.Score;
import com.gustavoserafim.serviceflow.repository.CategoryRepository;
import com.gustavoserafim.serviceflow.repository.TicketSuggestionRepository;
import com.gustavoserafim.serviceflow.security.CurrentUserProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Traduz a resposta bruta do serviço Python (rótulos em texto) para o
 * vocabulário desta API: categorias reais e ativas (com id) e o enum Priority.
 *
 * Propositalmente SEM @Transactional no método principal: ele faz uma chamada
 * HTTP (até alguns segundos) e não devemos segurar uma conexão do banco
 * durante ela. As consultas ao repository abrem transações curtas por conta própria.
 */
@Service
public class SuggestionService {

    private final IntelligenceClient intelligenceClient;
    private final CategoryRepository categoryRepository;
    private final TicketSuggestionRepository suggestionRepository;
    private final CurrentUserProvider currentUser;
    private final double minConfidence;

    public SuggestionService(IntelligenceClient intelligenceClient,
                             CategoryRepository categoryRepository,
                             TicketSuggestionRepository suggestionRepository,
                             CurrentUserProvider currentUser,
                             IntelligenceProperties properties) {
        this.intelligenceClient = intelligenceClient;
        this.categoryRepository = categoryRepository;
        this.suggestionRepository = suggestionRepository;
        this.currentUser = currentUser;
        this.minConfidence = properties.minConfidence();
    }

    public SuggestionResponse suggest(SuggestionRequest request) {
        Optional<IntelligenceResponse> raw = intelligenceClient.suggest(request.title(), request.description());
        if (raw.isEmpty()) {
            return SuggestionResponse.unavailable();
        }

        IntelligenceResponse response = raw.get();
        CategorySuggestion category = mapCategory(response.category());
        PrioritySuggestion priority = mapPriority(response.priority());

        // Nada utilizável (ex: nenhuma categoria sugerida existe/está ativa e a prioridade é desconhecida).
        if (category == null && priority == null) {
            return SuggestionResponse.unavailable();
        }

        Long suggestionId = record(response.modelVersion(), category, priority);
        return new SuggestionResponse(true, suggestionId, response.modelVersion(), category, priority);
    }

    /**
     * Grava a sugestão OFERECIDA. Se o usuário abrir o chamado informando este id,
     * o servidor compara e registra aceitou/trocou (ver SuggestionFeedbackService).
     */
    private Long record(String modelVersion, CategorySuggestion category, PrioritySuggestion priority) {
        TicketSuggestion suggestion = new TicketSuggestion();
        suggestion.setUser(currentUser.get());
        suggestion.setModelVersion(modelVersion != null ? modelVersion : "desconhecida");
        if (category != null) {
            suggestion.setSuggestedCategory(categoryRepository.getReferenceById(category.id()));
            suggestion.setCategoryConfidence(category.confidence());
        }
        if (priority != null) {
            suggestion.setSuggestedPriority(priority.priority());
            suggestion.setPriorityConfidence(priority.confidence());
        }
        return suggestionRepository.save(suggestion).getId();
    }

    /**
     * Casa cada rótulo (o melhor e as alternativas) com uma categoria ATIVA pelo
     * nome. Rótulos sem correspondência são descartados; a primeira que sobrar
     * vira a sugestão principal. Assim, se o ADMIN desativou "Hardware", a IA
     * nunca sugere uma categoria que o usuário não poderia escolher. Se a
     * principal restante tiver confiança abaixo do piso (min-confidence),
     * nada é sugerido: um palpite de 7% só atrapalha.
     */
    private CategorySuggestion mapCategory(Prediction prediction) {
        if (prediction == null) {
            return null;
        }
        List<Score> candidates = new ArrayList<>();
        candidates.add(new Score(prediction.label(), prediction.confidence()));
        if (prediction.alternatives() != null) {
            candidates.addAll(prediction.alternatives());
        }

        List<CategoryOption> options = new ArrayList<>();
        for (Score candidate : candidates) {
            categoryRepository.findByNameIgnoreCase(candidate.label())
                    .filter(Category::isActive)
                    .ifPresent(c -> options.add(new CategoryOption(c.getId(), c.getName(), candidate.confidence())));
        }
        if (options.isEmpty()) {
            return null;
        }
        CategoryOption best = options.get(0);
        if (best.confidence() < minConfidence) {
            return null; // o melhor candidato disponível é fraco demais: melhor não sugerir nada
        }
        return new CategorySuggestion(best.id(), best.name(), best.confidence(),
                List.copyOf(options.subList(1, options.size())));
    }

    /** Converte "P1".."P4" no enum; rótulos desconhecidos são ignorados (o Python pode evoluir). */
    private PrioritySuggestion mapPriority(Prediction prediction) {
        if (prediction == null) {
            return null;
        }
        List<Score> candidates = new ArrayList<>();
        candidates.add(new Score(prediction.label(), prediction.confidence()));
        if (prediction.alternatives() != null) {
            candidates.addAll(prediction.alternatives());
        }

        List<PriorityOption> options = new ArrayList<>();
        for (Score candidate : candidates) {
            parsePriority(candidate.label())
                    .ifPresent(p -> options.add(new PriorityOption(p, candidate.confidence())));
        }
        if (options.isEmpty()) {
            return null;
        }
        PriorityOption best = options.get(0);
        if (best.confidence() < minConfidence) {
            return null;
        }
        return new PrioritySuggestion(best.priority(), best.confidence(),
                List.copyOf(options.subList(1, options.size())));
    }

    private Optional<Priority> parsePriority(String label) {
        try {
            return Optional.of(Priority.valueOf(label));
        } catch (IllegalArgumentException | NullPointerException ex) {
            return Optional.empty();
        }
    }
}
