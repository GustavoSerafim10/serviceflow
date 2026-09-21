package com.gustavoserafim.serviceflow.service;

import com.gustavoserafim.serviceflow.dto.SuggestionMetricsResponse;
import com.gustavoserafim.serviceflow.entity.Ticket;
import com.gustavoserafim.serviceflow.entity.TicketSuggestion;
import com.gustavoserafim.serviceflow.entity.User;
import com.gustavoserafim.serviceflow.repository.TicketSuggestionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

/**
 * Fecha o ciclo de feedback: liga uma sugestão ao chamado aberto a partir dela,
 * registra se o usuário aceitou ou trocou o que foi sugerido, calcula as
 * métricas de aceitação e limpa sugestões abandonadas.
 *
 * Regra de ouro: FEEDBACK É "MELHOR ESFORÇO". Nunca faz a abertura do chamado
 * falhar: id ausente, inexistente, de outro usuário ou já usado é ignorado.
 */
@Service
public class SuggestionFeedbackService {

    private static final Logger log = LoggerFactory.getLogger(SuggestionFeedbackService.class);
    private static final Duration ABANDONED_AFTER = Duration.ofDays(7);

    private final TicketSuggestionRepository suggestionRepository;
    private final Clock clock;

    public SuggestionFeedbackService(TicketSuggestionRepository suggestionRepository, Clock clock) {
        this.suggestionRepository = suggestionRepository;
        this.clock = clock;
    }

    /**
     * Chamado por TicketService.create, DENTRO da mesma transação que grava o
     * chamado: a comparação é feita pelo SERVIDOR, com o que foi realmente gravado.
     * Assim o cliente não consegue fabricar taxa de aceitação.
     */
    @Transactional
    public void linkAndEvaluate(Long suggestionId, User actor, Ticket ticket) {
        if (suggestionId == null) {
            return;
        }
        TicketSuggestion suggestion = suggestionRepository.findById(suggestionId).orElse(null);

        // Só o dono da sugestão pode usá-la, e uma única vez. Qualquer outra situação: ignora.
        if (suggestion == null
                || !suggestion.getUser().getId().equals(actor.getId())
                || suggestion.getTicket() != null) {
            log.debug("Sugestão {} ignorada no vínculo com o chamado (inexistente, de outro usuário ou já usada)", suggestionId);
            return;
        }

        suggestion.setTicket(ticket);
        suggestion.setLinkedAt(clock.instant());
        if (suggestion.getSuggestedCategory() != null) {
            suggestion.setCategoryAccepted(
                    suggestion.getSuggestedCategory().getId().equals(ticket.getCategory().getId()));
        }
        if (suggestion.getSuggestedPriority() != null) {
            suggestion.setPriorityAccepted(suggestion.getSuggestedPriority() == ticket.getPriority());
        }
    }

    @Transactional(readOnly = true)
    public SuggestionMetricsResponse metrics() {
        List<SuggestionMetricsResponse.ModelMetrics> models = suggestionRepository.statsByModel().stream()
                .map(SuggestionMetricsResponse.ModelMetrics::from)
                .toList();
        return new SuggestionMetricsResponse(models);
    }

    // Faxina diária às 03:30: sugestões oferecidas há mais de 7 dias que nunca viraram chamado.
    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void deleteAbandoned() {
        int removed = suggestionRepository.deleteUnlinkedOlderThan(clock.instant().minus(ABANDONED_AFTER));
        log.info("Limpeza de sugestões: {} abandonadas removidas", removed);
    }
}
