package com.gustavoserafim.serviceflow.service;

import com.gustavoserafim.serviceflow.dto.TicketAssignRequest;
import com.gustavoserafim.serviceflow.dto.TicketCreateRequest;
import com.gustavoserafim.serviceflow.dto.TicketResponse;
import com.gustavoserafim.serviceflow.dto.TicketStatusRequest;
import com.gustavoserafim.serviceflow.entity.Category;
import com.gustavoserafim.serviceflow.entity.HistoryAction;
import com.gustavoserafim.serviceflow.entity.Priority;
import com.gustavoserafim.serviceflow.entity.Role;
import com.gustavoserafim.serviceflow.entity.SlaStatus;
import com.gustavoserafim.serviceflow.entity.Ticket;
import com.gustavoserafim.serviceflow.entity.TicketHistory;
import com.gustavoserafim.serviceflow.entity.TicketStatus;
import com.gustavoserafim.serviceflow.entity.User;
import com.gustavoserafim.serviceflow.exception.BusinessRuleException;
import com.gustavoserafim.serviceflow.exception.ResourceNotFoundException;
import com.gustavoserafim.serviceflow.repository.CategoryRepository;
import com.gustavoserafim.serviceflow.repository.TicketCommentRepository;
import com.gustavoserafim.serviceflow.repository.TicketHistoryRepository;
import com.gustavoserafim.serviceflow.repository.TicketRepository;
import com.gustavoserafim.serviceflow.repository.UserRepository;
import com.gustavoserafim.serviceflow.security.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Testa as regras do TicketService com dependências simuladas (mocks) e um
 * Clock FIXO: o "agora" do teste é sempre 2026-01-10T10:00:00Z, então os
 * prazos de SLA são determinísticos.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT) // o @BeforeEach configura stubs que nem todo teste usa
class TicketServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-10T10:00:00Z");

    @Mock private TicketRepository ticketRepository;
    @Mock private TicketCommentRepository commentRepository;
    @Mock private TicketHistoryRepository historyRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private UserRepository userRepository;
    @Mock private SlaRuleService slaRuleService;
    @Mock private CurrentUserProvider currentUser;

    private TicketService service;

    private User admin;
    private User tech;
    private User otherTech;
    private User requester;
    private User otherRequester;
    private Category category;

    @BeforeEach
    void setUp() {
        service = new TicketService(ticketRepository, commentRepository, historyRepository,
                categoryRepository, userRepository, slaRuleService, currentUser,
                Clock.fixed(NOW, ZoneOffset.UTC));

        admin = user(1L, "Admin", Role.ADMIN);
        tech = user(2L, "Tecnico", Role.TECNICO);
        otherTech = user(3L, "Outro Tecnico", Role.TECNICO);
        requester = user(4L, "Ana", Role.SOLICITANTE);
        otherRequester = user(5L, "Bia", Role.SOLICITANTE);

        category = new Category();
        category.setId(10L);
        category.setName("Rede");

        when(ticketRepository.saveAndFlush(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private User user(Long id, String name, Role role) {
        User u = new User();
        u.setId(id);
        u.setName(name);
        u.setRole(role);
        return u;
    }

    private Ticket ticket(TicketStatus status, User assignee) {
        Ticket t = new Ticket();
        t.setId(100L);
        t.setTitle("Sem rede");
        t.setDescription("Sem conexão");
        t.setCategory(category);
        t.setRequester(requester);
        t.setAssignee(assignee);
        t.setPriority(Priority.P2);
        t.setStatus(status);
        t.setCreatedAt(NOW);
        t.setSlaDueAt(NOW.plusSeconds(3600));
        when(ticketRepository.findById(100L)).thenReturn(Optional.of(t));
        return t;
    }

    // --------------------------------------------------------------- abertura

    @Test
    void create_setsRequesterStatusAndSlaDeadlineFromRule() {
        when(currentUser.get()).thenReturn(requester);
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(category));
        when(slaRuleService.minutesFor(Priority.P1)).thenReturn(240);

        TicketResponse response = service.create(
                new TicketCreateRequest("  Sem rede  ", "Sem conexão", 10L, Priority.P1));

        assertThat(response.title()).isEqualTo("Sem rede");
        assertThat(response.status()).isEqualTo(TicketStatus.ABERTO);
        assertThat(response.requesterId()).isEqualTo(requester.getId());
        assertThat(response.slaDueAt()).isEqualTo(NOW.plusSeconds(240 * 60));
        assertThat(response.slaStatus()).isEqualTo(SlaStatus.DENTRO_DO_PRAZO);

        ArgumentCaptor<TicketHistory> captor = ArgumentCaptor.forClass(TicketHistory.class);
        verify(historyRepository).save(captor.capture());
        assertThat(captor.getValue().getAction()).isEqualTo(HistoryAction.CREATED);
    }

    @Test
    void create_withInactiveCategory_throwsBusinessRule() {
        category.setActive(false);
        when(currentUser.get()).thenReturn(requester);
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(category));

        assertThatThrownBy(() -> service.create(new TicketCreateRequest("t", "d", 10L, Priority.P3)))
                .isInstanceOf(BusinessRuleException.class);
        verify(ticketRepository, never()).save(any());
    }

    // ------------------------------------------------------------- visibilidade

    @Test
    void findById_requesterCannotSeeAnotherRequestersTicket_returnsNotFound() {
        ticket(TicketStatus.ABERTO, null);
        when(currentUser.get()).thenReturn(otherRequester);

        assertThatThrownBy(() -> service.findById(100L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void findById_technicianSeesAnyTicket() {
        ticket(TicketStatus.ABERTO, null);
        when(currentUser.get()).thenReturn(tech);

        assertThat(service.findById(100L).id()).isEqualTo(100L);
    }

    // ---------------------------------------------------------------- atribuição

    @Test
    void assign_openTicket_movesToInServiceAndRecordsHistory() {
        Ticket t = ticket(TicketStatus.ABERTO, null);
        when(currentUser.get()).thenReturn(tech);
        when(userRepository.findById(2L)).thenReturn(Optional.of(tech));

        TicketResponse response = service.assign(100L, new TicketAssignRequest(2L));

        assertThat(t.getStatus()).isEqualTo(TicketStatus.EM_ATENDIMENTO);
        assertThat(response.assigneeId()).isEqualTo(2L);
        verify(historyRepository, org.mockito.Mockito.times(2)).save(any(TicketHistory.class)); // ASSIGNED + STATUS_CHANGED
    }

    @Test
    void assign_technicianCannotAssignToAnotherTechnician() {
        ticket(TicketStatus.ABERTO, null);
        when(currentUser.get()).thenReturn(tech);
        when(userRepository.findById(3L)).thenReturn(Optional.of(otherTech));

        assertThatThrownBy(() -> service.assign(100L, new TicketAssignRequest(3L)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void assign_adminCanAssignToAnyTechnician() {
        Ticket t = ticket(TicketStatus.ABERTO, null);
        when(currentUser.get()).thenReturn(admin);
        when(userRepository.findById(3L)).thenReturn(Optional.of(otherTech));

        service.assign(100L, new TicketAssignRequest(3L));

        assertThat(t.getAssignee()).isEqualTo(otherTech);
    }

    @Test
    void assign_toNonTechnician_throwsBusinessRule() {
        ticket(TicketStatus.ABERTO, null);
        when(currentUser.get()).thenReturn(admin);
        when(userRepository.findById(4L)).thenReturn(Optional.of(requester));

        assertThatThrownBy(() -> service.assign(100L, new TicketAssignRequest(4L)))
                .isInstanceOf(BusinessRuleException.class);
    }

    // -------------------------------------------------------------------- status

    @Test
    void changeStatus_assigneeResolves_setsResolvedAt() {
        Ticket t = ticket(TicketStatus.EM_ATENDIMENTO, tech);
        when(currentUser.get()).thenReturn(tech);

        service.changeStatus(100L, new TicketStatusRequest(TicketStatus.RESOLVIDO));

        assertThat(t.getStatus()).isEqualTo(TicketStatus.RESOLVIDO);
        assertThat(t.getResolvedAt()).isEqualTo(NOW);
    }

    @Test
    void changeStatus_reopeningResolvedTicket_clearsResolvedAt() {
        Ticket t = ticket(TicketStatus.RESOLVIDO, tech);
        t.setResolvedAt(NOW.minusSeconds(60));
        when(currentUser.get()).thenReturn(requester);

        service.changeStatus(100L, new TicketStatusRequest(TicketStatus.EM_ATENDIMENTO));

        assertThat(t.getStatus()).isEqualTo(TicketStatus.EM_ATENDIMENTO);
        assertThat(t.getResolvedAt()).isNull();
    }

    @Test
    void changeStatus_technicianWhoIsNotTheAssignee_isDenied() {
        ticket(TicketStatus.EM_ATENDIMENTO, tech);
        when(currentUser.get()).thenReturn(otherTech);

        assertThatThrownBy(() -> service.changeStatus(100L, new TicketStatusRequest(TicketStatus.RESOLVIDO)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void changeStatus_requesterCannotResolve_isDenied() {
        ticket(TicketStatus.EM_ATENDIMENTO, tech);
        when(currentUser.get()).thenReturn(requester);

        assertThatThrownBy(() -> service.changeStatus(100L, new TicketStatusRequest(TicketStatus.RESOLVIDO)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void changeStatus_requesterCancelsOwnOpenTicket() {
        Ticket t = ticket(TicketStatus.ABERTO, null);
        when(currentUser.get()).thenReturn(requester);

        service.changeStatus(100L, new TicketStatusRequest(TicketStatus.CANCELADO));

        assertThat(t.getStatus()).isEqualTo(TicketStatus.CANCELADO);
    }

    @Test
    void changeStatus_invalidTransition_throwsBusinessRuleEvenForAdmin() {
        ticket(TicketStatus.ABERTO, null);
        when(currentUser.get()).thenReturn(admin);

        assertThatThrownBy(() -> service.changeStatus(100L, new TicketStatusRequest(TicketStatus.RESOLVIDO)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void changeStatus_lateResolution_reportsBreachedSla() {
        // prazo era NOW+1h; resolvemos "tarde" simulando o prazo já vencido
        Ticket t = ticket(TicketStatus.EM_ATENDIMENTO, tech);
        t.setSlaDueAt(NOW.minusSeconds(1));
        when(currentUser.get()).thenReturn(tech);

        TicketResponse response = service.changeStatus(100L, new TicketStatusRequest(TicketStatus.RESOLVIDO));

        assertThat(response.slaStatus()).isEqualTo(SlaStatus.ESTOURADO);
    }
}
