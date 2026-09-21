package com.gustavoserafim.serviceflow.service;

import com.gustavoserafim.serviceflow.dto.SuggestionRequest;
import com.gustavoserafim.serviceflow.dto.SuggestionResponse;
import com.gustavoserafim.serviceflow.entity.Category;
import com.gustavoserafim.serviceflow.entity.Priority;
import com.gustavoserafim.serviceflow.entity.TicketSuggestion;
import com.gustavoserafim.serviceflow.entity.User;
import com.gustavoserafim.serviceflow.integration.IntelligenceClient;
import com.gustavoserafim.serviceflow.integration.IntelligenceProperties;
import com.gustavoserafim.serviceflow.integration.IntelligenceResponse;
import com.gustavoserafim.serviceflow.integration.IntelligenceResponse.Prediction;
import com.gustavoserafim.serviceflow.integration.IntelligenceResponse.Score;
import com.gustavoserafim.serviceflow.repository.CategoryRepository;
import com.gustavoserafim.serviceflow.repository.TicketSuggestionRepository;
import com.gustavoserafim.serviceflow.security.CurrentUserProvider;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SuggestionServiceTest {

    private static final SuggestionRequest REQUEST = new SuggestionRequest("Sem toner", "A impressora parou");

    @Mock
    private IntelligenceClient intelligenceClient;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private TicketSuggestionRepository suggestionRepository;

    @Mock
    private CurrentUserProvider currentUser;

    private SuggestionService service() {
        User user = new User();
        user.setId(9L);
        when(currentUser.get()).thenReturn(user);
        // simula o INSERT: devolve a própria entidade já com id
        when(suggestionRepository.save(any(TicketSuggestion.class))).thenAnswer(inv -> {
            TicketSuggestion s = inv.getArgument(0);
            s.setId(55L);
            return s;
        });
        when(categoryRepository.getReferenceById(anyLong())).thenAnswer(inv -> category(inv.getArgument(0), "ref", true));
        return new SuggestionService(intelligenceClient, categoryRepository, suggestionRepository, currentUser,
                new IntelligenceProperties(true, "http://x", Duration.ofMillis(500), Duration.ofSeconds(2), 0.30));
    }

    private Category category(long id, String name, boolean active) {
        Category c = new Category();
        c.setId(id);
        c.setName(name);
        c.setActive(active);
        return c;
    }

    private IntelligenceResponse response(Prediction category, Prediction priority) {
        return new IntelligenceResponse(category, priority, "v1-test");
    }

    private Prediction prediction(String label, double confidence, Score... alternatives) {
        return new Prediction(label, confidence, List.of(alternatives));
    }

    @Test
    void suggest_mapsLabelsToRealCategoriesAndPriority() {
        when(categoryRepository.findByNameIgnoreCase("Impressora")).thenReturn(Optional.of(category(6, "Impressora", true)));
        when(categoryRepository.findByNameIgnoreCase("Hardware")).thenReturn(Optional.of(category(2, "Hardware", true)));
        when(intelligenceClient.suggest(anyString(), anyString())).thenReturn(Optional.of(response(
                prediction("Impressora", 0.91, new Score("Hardware", 0.05), new Score("Rede", 0.02)),
                prediction("P3", 0.66, new Score("P2", 0.16)))));

        SuggestionResponse result = service().suggest(REQUEST);

        assertThat(result.available()).isTrue();
        assertThat(result.suggestionId()).isEqualTo(55L);
        assertThat(result.modelVersion()).isEqualTo("v1-test");

        // a sugestão oferecida foi gravada para o ciclo de feedback
        ArgumentCaptor<TicketSuggestion> saved = ArgumentCaptor.forClass(TicketSuggestion.class);
        verify(suggestionRepository).save(saved.capture());
        assertThat(saved.getValue().getUser().getId()).isEqualTo(9L);
        assertThat(saved.getValue().getModelVersion()).isEqualTo("v1-test");
        assertThat(saved.getValue().getSuggestedCategory().getId()).isEqualTo(6L);
        assertThat(saved.getValue().getCategoryConfidence()).isEqualTo(0.91);
        assertThat(saved.getValue().getSuggestedPriority()).isEqualTo(Priority.P3);
        assertThat(saved.getValue().getPriorityConfidence()).isEqualTo(0.66);

        assertThat(result.category().id()).isEqualTo(6L);
        assertThat(result.category().name()).isEqualTo("Impressora");
        assertThat(result.category().confidence()).isEqualTo(0.91);
        // "Rede" não existe no sistema: descartada; "Hardware" permanece como alternativa
        assertThat(result.category().alternatives()).hasSize(1);
        assertThat(result.category().alternatives().get(0).id()).isEqualTo(2L);
        assertThat(result.priority().priority()).isEqualTo(Priority.P3);
        assertThat(result.priority().alternatives()).hasSize(1);
    }

    @Test
    void suggest_skipsInactiveCategoriesAndPromotesTheNextCandidate() {
        when(categoryRepository.findByNameIgnoreCase("Hardware")).thenReturn(Optional.of(category(2, "Hardware", false))); // desativada
        when(categoryRepository.findByNameIgnoreCase("Software")).thenReturn(Optional.of(category(3, "Software", true)));
        when(intelligenceClient.suggest(anyString(), anyString())).thenReturn(Optional.of(response(
                prediction("Hardware", 0.60, new Score("Software", 0.30)),
                prediction("P2", 0.5))));

        SuggestionResponse result = service().suggest(REQUEST);

        assertThat(result.category().name()).isEqualTo("Software"); // promovida: a IA nunca sugere o que o usuário não pode escolher
        assertThat(result.category().confidence()).isEqualTo(0.30);
        assertThat(result.category().alternatives()).isEmpty();
    }

    @Test
    void suggest_dropsAMainSuggestionBelowTheConfidenceFloor() {
        // "Hardware" (60%) está desativada; a próxima candidata só tem 7,6%: fraca demais para ser oferecida
        when(categoryRepository.findByNameIgnoreCase("Hardware")).thenReturn(Optional.of(category(2, "Hardware", false)));
        when(categoryRepository.findByNameIgnoreCase("Rede")).thenReturn(Optional.of(category(1, "Rede", true)));
        when(intelligenceClient.suggest(anyString(), anyString())).thenReturn(Optional.of(response(
                prediction("Hardware", 0.60, new Score("Rede", 0.076)),
                prediction("P1", 0.20)))); // prioridade também abaixo do piso

        assertThat(service().suggest(REQUEST).available()).isFalse(); // nada utilizável sobrou
        verify(suggestionRepository, never()).save(any()); // sem sugestão oferecida, nada a registrar
    }

    @Test
    void suggest_whenNoCategoryMatches_stillReturnsThePriority() {
        when(categoryRepository.findByNameIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(intelligenceClient.suggest(anyString(), anyString())).thenReturn(Optional.of(response(
                prediction("Categoria Inexistente", 0.9), prediction("P1", 0.8))));

        SuggestionResponse result = service().suggest(REQUEST);

        assertThat(result.available()).isTrue();
        assertThat(result.category()).isNull();
        assertThat(result.priority().priority()).isEqualTo(Priority.P1);
    }

    @Test
    void suggest_ignoresUnknownPriorityLabels() {
        when(categoryRepository.findByNameIgnoreCase("Rede")).thenReturn(Optional.of(category(1, "Rede", true)));
        when(intelligenceClient.suggest(anyString(), anyString())).thenReturn(Optional.of(response(
                prediction("Rede", 0.7), prediction("P9", 0.9, new Score("P2", 0.40)))));

        SuggestionResponse result = service().suggest(REQUEST);

        assertThat(result.priority().priority()).isEqualTo(Priority.P2); // "P9" descartado
    }

    @Test
    void suggest_whenNothingIsUsable_returnsUnavailable() {
        when(categoryRepository.findByNameIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(intelligenceClient.suggest(anyString(), anyString())).thenReturn(Optional.of(response(
                prediction("Nada", 0.9), prediction("P9", 0.9))));

        assertThat(service().suggest(REQUEST).available()).isFalse();
    }

    @Test
    void suggest_whenServiceIsDown_returnsUnavailableWithoutError() {
        when(intelligenceClient.suggest(anyString(), anyString())).thenReturn(Optional.empty());

        SuggestionResponse result = service().suggest(REQUEST);

        assertThat(result.available()).isFalse();
        assertThat(result.suggestionId()).isNull();
        assertThat(result.category()).isNull();
        assertThat(result.priority()).isNull();
        verify(suggestionRepository, never()).save(any());
    }
}
