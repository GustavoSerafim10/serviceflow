package com.gustavoserafim.serviceflow.service;

import com.gustavoserafim.serviceflow.dto.SuggestionMetricsResponse;
import com.gustavoserafim.serviceflow.entity.Category;
import com.gustavoserafim.serviceflow.entity.Priority;
import com.gustavoserafim.serviceflow.entity.Ticket;
import com.gustavoserafim.serviceflow.entity.TicketSuggestion;
import com.gustavoserafim.serviceflow.entity.User;
import com.gustavoserafim.serviceflow.repository.TicketSuggestionRepository;
import com.gustavoserafim.serviceflow.repository.TicketSuggestionRepository.ModelStats;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SuggestionFeedbackServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-10T10:00:00Z");

    @Mock
    private TicketSuggestionRepository repository;

    private SuggestionFeedbackService service;
    private User owner;
    private Ticket ticket;

    @BeforeEach
    void setUp() {
        service = new SuggestionFeedbackService(repository, Clock.fixed(NOW, ZoneOffset.UTC));
        owner = user(1L);

        Category chosen = new Category();
        chosen.setId(20L);
        ticket = new Ticket();
        ticket.setCategory(chosen);
        ticket.setPriority(Priority.P2);
    }

    private User user(long id) {
        User u = new User();
        u.setId(id);
        return u;
    }

    private TicketSuggestion suggestion(long categoryId, Priority priority) {
        Category suggested = new Category();
        suggested.setId(categoryId);
        TicketSuggestion s = new TicketSuggestion();
        s.setUser(owner);
        s.setSuggestedCategory(suggested);
        s.setSuggestedPriority(priority);
        when(repository.findById(5L)).thenReturn(Optional.of(s));
        return s;
    }

    @Test
    void linkAndEvaluate_whenUserKeepsTheSuggestions_marksBothAsAccepted() {
        TicketSuggestion s = suggestion(20L, Priority.P2); // igual ao que o chamado tem

        service.linkAndEvaluate(5L, owner, ticket);

        assertThat(s.getTicket()).isSameAs(ticket);
        assertThat(s.getLinkedAt()).isEqualTo(NOW);
        assertThat(s.getCategoryAccepted()).isTrue();
        assertThat(s.getPriorityAccepted()).isTrue();
    }

    @Test
    void linkAndEvaluate_whenUserChangesThem_marksAsNotAccepted() {
        TicketSuggestion s = suggestion(99L, Priority.P4); // o usuário escolheu outra categoria e outra prioridade

        service.linkAndEvaluate(5L, owner, ticket);

        assertThat(s.getCategoryAccepted()).isFalse();
        assertThat(s.getPriorityAccepted()).isFalse();
    }

    @Test
    void linkAndEvaluate_evaluatesOnlyTheFieldsThatWereSuggested() {
        TicketSuggestion s = suggestion(20L, null); // só houve sugestão de categoria
        s.setSuggestedPriority(null);

        service.linkAndEvaluate(5L, owner, ticket);

        assertThat(s.getCategoryAccepted()).isTrue();
        assertThat(s.getPriorityAccepted()).isNull(); // "não se aplica", não "trocou"
    }

    @Test
    void linkAndEvaluate_ignoresSuggestionsOfAnotherUser() {
        TicketSuggestion s = suggestion(20L, Priority.P2);
        s.setUser(user(2L));

        service.linkAndEvaluate(5L, owner, ticket);

        assertThat(s.getTicket()).isNull();
        assertThat(s.getCategoryAccepted()).isNull();
    }

    @Test
    void linkAndEvaluate_ignoresSuggestionsAlreadyUsedByAnotherTicket() {
        TicketSuggestion s = suggestion(20L, Priority.P2);
        Ticket other = new Ticket();
        s.setTicket(other);

        service.linkAndEvaluate(5L, owner, ticket);

        assertThat(s.getTicket()).isSameAs(other); // continua vinculada ao primeiro
    }

    @Test
    void linkAndEvaluate_ignoresUnknownIdAndNullId() {
        when(repository.findById(404L)).thenReturn(Optional.empty());

        service.linkAndEvaluate(404L, owner, ticket); // não lança
        service.linkAndEvaluate(null, owner, ticket); // não lança nem consulta o banco

        verify(repository).findById(404L);
    }

    @Test
    void metrics_computesAcceptanceRates_andHandlesDivisionByZero() {
        ModelStats withData = mock(ModelStats.class);
        when(withData.getModelVersion()).thenReturn("v1-a");
        when(withData.getOffered()).thenReturn(10L);
        when(withData.getUsedInTickets()).thenReturn(8L);
        when(withData.getCategoryEvaluated()).thenReturn(8L);
        when(withData.getCategoryAccepted()).thenReturn(6L);
        when(withData.getPriorityEvaluated()).thenReturn(3L);
        when(withData.getPriorityAccepted()).thenReturn(1L);

        ModelStats noEvaluations = mock(ModelStats.class);
        when(noEvaluations.getModelVersion()).thenReturn("v1-b");
        when(noEvaluations.getOffered()).thenReturn(4L);
        when(noEvaluations.getUsedInTickets()).thenReturn(0L);
        when(noEvaluations.getCategoryEvaluated()).thenReturn(0L);
        when(noEvaluations.getCategoryAccepted()).thenReturn(0L);
        when(noEvaluations.getPriorityEvaluated()).thenReturn(0L);
        when(noEvaluations.getPriorityAccepted()).thenReturn(0L);

        when(repository.statsByModel()).thenReturn(List.of(withData, noEvaluations));

        SuggestionMetricsResponse response = service.metrics();

        SuggestionMetricsResponse.ModelMetrics a = response.models().get(0);
        assertThat(a.categoryAcceptanceRate()).isEqualTo(0.75);
        assertThat(a.priorityAcceptanceRate()).isEqualTo(0.3333);
        SuggestionMetricsResponse.ModelMetrics b = response.models().get(1);
        assertThat(b.categoryAcceptanceRate()).isNull(); // sem avaliações: não inventa 0%
        assertThat(b.priorityAcceptanceRate()).isNull();
    }
}
