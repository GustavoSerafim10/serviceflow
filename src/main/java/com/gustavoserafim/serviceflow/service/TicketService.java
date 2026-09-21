package com.gustavoserafim.serviceflow.service;

import com.gustavoserafim.serviceflow.dto.CommentRequest;
import com.gustavoserafim.serviceflow.dto.CommentResponse;
import com.gustavoserafim.serviceflow.dto.HistoryResponse;
import com.gustavoserafim.serviceflow.dto.PageResponse;
import com.gustavoserafim.serviceflow.dto.TicketAssignRequest;
import com.gustavoserafim.serviceflow.dto.TicketCreateRequest;
import com.gustavoserafim.serviceflow.dto.TicketFilter;
import com.gustavoserafim.serviceflow.dto.TicketResponse;
import com.gustavoserafim.serviceflow.dto.TicketStatusRequest;
import com.gustavoserafim.serviceflow.entity.Category;
import com.gustavoserafim.serviceflow.entity.HistoryAction;
import com.gustavoserafim.serviceflow.entity.Role;
import com.gustavoserafim.serviceflow.entity.Ticket;
import com.gustavoserafim.serviceflow.entity.TicketComment;
import com.gustavoserafim.serviceflow.entity.TicketHistory;
import com.gustavoserafim.serviceflow.entity.TicketStatus;
import com.gustavoserafim.serviceflow.entity.User;
import com.gustavoserafim.serviceflow.exception.BusinessRuleException;
import com.gustavoserafim.serviceflow.exception.ResourceNotFoundException;
import com.gustavoserafim.serviceflow.repository.CategoryRepository;
import com.gustavoserafim.serviceflow.repository.TicketCommentRepository;
import com.gustavoserafim.serviceflow.repository.TicketHistoryRepository;
import com.gustavoserafim.serviceflow.repository.TicketRepository;
import com.gustavoserafim.serviceflow.repository.TicketSpecifications;
import com.gustavoserafim.serviceflow.repository.UserRepository;
import com.gustavoserafim.serviceflow.security.CurrentUserProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Regras de negócio dos chamados. É o service mais importante do sistema.
 *
 * Ordem de verificação padrão nas operações sobre um chamado existente:
 *   1. o usuário PODE VER o chamado?   (senão 404 — não revelamos que existe)
 *   2. a operação é VÁLIDA agora?      (senão 422 — regra de negócio)
 *   3. este usuário PODE fazê-la?      (senão 403 — permissão)
 *
 * Toda ação relevante grava uma linha em ticket_history na MESMA transação:
 * ou a mudança e seu registro são gravados juntos, ou nenhum dos dois.
 */
@Service
public class TicketService {

    private static final int MAX_PAGE_SIZE = 100;

    private final TicketRepository ticketRepository;
    private final TicketCommentRepository commentRepository;
    private final TicketHistoryRepository historyRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final SlaRuleService slaRuleService;
    private final CurrentUserProvider currentUser;
    private final Clock clock;

    public TicketService(TicketRepository ticketRepository,
                         TicketCommentRepository commentRepository,
                         TicketHistoryRepository historyRepository,
                         CategoryRepository categoryRepository,
                         UserRepository userRepository,
                         SlaRuleService slaRuleService,
                         CurrentUserProvider currentUser,
                         Clock clock) {
        this.ticketRepository = ticketRepository;
        this.commentRepository = commentRepository;
        this.historyRepository = historyRepository;
        this.categoryRepository = categoryRepository;
        this.userRepository = userRepository;
        this.slaRuleService = slaRuleService;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ abrir

    @Transactional
    public TicketResponse create(TicketCreateRequest request) {
        User actor = currentUser.get();

        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new BusinessRuleException("Categoria não encontrada com id " + request.categoryId()));
        if (!category.isActive()) {
            throw new BusinessRuleException("A categoria '" + category.getName() + "' está inativa");
        }

        Instant now = clock.instant();

        Ticket ticket = new Ticket();
        ticket.setTitle(request.title().trim());
        ticket.setDescription(request.description().trim());
        ticket.setCategory(category);
        ticket.setRequester(actor);          // o solicitante é SEMPRE quem está logado
        ticket.setPriority(request.priority());
        ticket.setStatus(TicketStatus.ABERTO);
        ticket.setCreatedAt(now);
        // Cálculo automático do SLA: abertura + minutos da regra da prioridade.
        ticket.setSlaDueAt(SlaCalculator.dueAt(now, slaRuleService.minutesFor(request.priority())));

        ticketRepository.save(ticket);
        record(ticket, actor, HistoryAction.CREATED,
                "Chamado aberto com prioridade " + ticket.getPriority()
                        + " (prazo de SLA: " + ticket.getSlaDueAt() + ")");

        return toResponse(ticket);
    }

    // ---------------------------------------------------------------- consultar

    @Transactional(readOnly = true)
    public TicketResponse findById(Long id) {
        return toResponse(getVisible(id, currentUser.get()));
    }

    // ------------------------------------------------------------------ listar

    /**
     * Lista paginada com filtros. SOLICITANTE só recebe os próprios chamados
     * (a condição é acrescentada AQUI, no servidor, e não depende do que o
     * cliente enviar). Ordenação fixa: mais recentes primeiro — o "sort" do
     * cliente é ignorado de propósito: aceitar ordenação livre permitiria
     * ordenar por campos aninhados sensíveis (ex: requester.passwordHash) e
     * inferir dados a partir da ordem do resultado.
     */
    @Transactional(readOnly = true)
    public PageResponse<TicketResponse> search(TicketFilter filter, Pageable pageable) {
        User actor = currentUser.get();
        Instant now = clock.instant();

        List<Specification<Ticket>> specs = new ArrayList<>(TicketSpecifications.from(filter, now));
        if (actor.getRole() == Role.SOLICITANTE) {
            specs.add(TicketSpecifications.requesterIs(actor.getId()));
        }

        Pageable safePageable = PageRequest.of(
                pageable.getPageNumber(),
                Math.min(pageable.getPageSize(), MAX_PAGE_SIZE),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));

        Page<Ticket> page = ticketRepository.findAll(Specification.allOf(specs), safePageable);
        return PageResponse.from(page.map(ticket -> TicketResponse.from(ticket, now)));
    }

    // ----------------------------------------------------------------- atribuir

    @Transactional
    public TicketResponse assign(Long id, TicketAssignRequest request) {
        User actor = currentUser.get();
        Ticket ticket = getVisible(id, actor);

        if (ticket.getStatus() != TicketStatus.ABERTO && ticket.getStatus() != TicketStatus.EM_ATENDIMENTO) {
            throw new BusinessRuleException("Só é possível atribuir chamados abertos ou em atendimento");
        }

        User technician = userRepository.findById(request.technicianId())
                .orElseThrow(() -> new BusinessRuleException("Usuário não encontrado com id " + request.technicianId()));
        if (technician.getRole() != Role.TECNICO || !technician.isActive()) {
            throw new BusinessRuleException("O usuário informado não é um técnico ativo");
        }

        // Técnico só "puxa" chamado para si; atribuir a outra pessoa é do ADMIN.
        if (actor.getRole() == Role.TECNICO && !actor.getId().equals(technician.getId())) {
            throw new AccessDeniedException("Técnicos só podem atribuir chamados a si mesmos");
        }

        User previous = ticket.getAssignee();
        if (previous != null && previous.getId().equals(technician.getId())) {
            throw new BusinessRuleException("O chamado já está atribuído a este técnico");
        }

        ticket.setAssignee(technician);
        record(ticket, actor, HistoryAction.ASSIGNED, previous == null
                ? "Atribuído a " + technician.getName()
                : "Reatribuído de " + previous.getName() + " para " + technician.getName());

        // Atribuir um chamado ABERTO o coloca automaticamente em atendimento.
        if (ticket.getStatus() == TicketStatus.ABERTO) {
            ticket.setStatus(TicketStatus.EM_ATENDIMENTO);
            record(ticket, actor, HistoryAction.STATUS_CHANGED,
                    "Status: " + TicketStatus.ABERTO + " → " + TicketStatus.EM_ATENDIMENTO);
        }

        return toResponse(ticketRepository.saveAndFlush(ticket));
    }

    // ------------------------------------------------------------ mudar status

    @Transactional
    public TicketResponse changeStatus(Long id, TicketStatusRequest request) {
        User actor = currentUser.get();
        Ticket ticket = getVisible(id, actor);

        TicketStatus from = ticket.getStatus();
        TicketStatus to = request.status();

        if (!from.canTransitionTo(to)) {
            throw new BusinessRuleException("Transição de status inválida: " + from + " → " + to);
        }
        if (!isPermitted(actor, ticket, from, to)) {
            throw new AccessDeniedException("Você não tem permissão para mudar o status de " + from + " para " + to);
        }

        ticket.setStatus(to);
        if (to == TicketStatus.RESOLVIDO) {
            ticket.setResolvedAt(clock.instant());
        } else if (from == TicketStatus.RESOLVIDO && to == TicketStatus.EM_ATENDIMENTO) {
            ticket.setResolvedAt(null); // reaberto: o relógio do SLA volta a contar
        }

        record(ticket, actor, HistoryAction.STATUS_CHANGED, "Status: " + from + " → " + to);
        return toResponse(ticketRepository.saveAndFlush(ticket));
    }

    /**
     * Quem pode fazer cada transição (a validade da transição em si já foi checada):
     *  - ADMIN: qualquer transição válida;
     *  - TECNICO responsável: resolver o chamado e reabri-lo;
     *  - SOLICITANTE dono: cancelar (se ainda ABERTO), fechar ou reabrir um RESOLVIDO.
     */
    private boolean isPermitted(User actor, Ticket ticket, TicketStatus from, TicketStatus to) {
        return switch (actor.getRole()) {
            case ADMIN -> true;
            case TECNICO -> isAssignee(actor, ticket)
                    && ((from == TicketStatus.EM_ATENDIMENTO && to == TicketStatus.RESOLVIDO)
                    || (from == TicketStatus.RESOLVIDO && to == TicketStatus.EM_ATENDIMENTO));
            case SOLICITANTE -> isRequester(actor, ticket)
                    && ((from == TicketStatus.ABERTO && to == TicketStatus.CANCELADO)
                    || (from == TicketStatus.RESOLVIDO
                    && (to == TicketStatus.FECHADO || to == TicketStatus.EM_ATENDIMENTO)));
        };
    }

    // -------------------------------------------------------------- comentários

    @Transactional
    public CommentResponse addComment(Long id, CommentRequest request) {
        User actor = currentUser.get();
        Ticket ticket = getVisible(id, actor);

        if (ticket.getStatus().isTerminal()) {
            throw new BusinessRuleException("Não é possível comentar em um chamado " + ticket.getStatus());
        }

        TicketComment comment = new TicketComment();
        comment.setTicket(ticket);
        comment.setAuthor(actor);
        comment.setBody(request.body().trim());
        commentRepository.save(comment);

        record(ticket, actor, HistoryAction.COMMENT_ADDED, "Comentário adicionado");
        return CommentResponse.from(comment);
    }

    @Transactional(readOnly = true)
    public List<CommentResponse> listComments(Long id) {
        getVisible(id, currentUser.get()); // valida existência e visibilidade
        return commentRepository.findByTicketIdOrderByIdAsc(id).stream()
                .map(CommentResponse::from)
                .toList();
    }

    // ---------------------------------------------------------------- histórico

    @Transactional(readOnly = true)
    public List<HistoryResponse> listHistory(Long id) {
        getVisible(id, currentUser.get());
        return historyRepository.findByTicketIdOrderByIdAsc(id).stream()
                .map(HistoryResponse::from)
                .toList();
    }

    // ------------------------------------------------------------------ helpers

    /**
     * Busca o chamado respeitando a visibilidade: SOLICITANTE só enxerga os
     * seus; TECNICO e ADMIN enxergam todos. Para o solicitante, um chamado
     * alheio responde 404 (e não 403) para não confirmar que ele existe.
     */
    private Ticket getVisible(Long id, User actor) {
        Ticket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Chamado", id));
        if (actor.getRole() == Role.SOLICITANTE && !isRequester(actor, ticket)) {
            throw new ResourceNotFoundException("Chamado", id);
        }
        return ticket;
    }

    private boolean isRequester(User user, Ticket ticket) {
        return ticket.getRequester().getId().equals(user.getId());
    }

    private boolean isAssignee(User user, Ticket ticket) {
        return ticket.getAssignee() != null && ticket.getAssignee().getId().equals(user.getId());
    }

    private void record(Ticket ticket, User actor, HistoryAction action, String details) {
        TicketHistory entry = new TicketHistory();
        entry.setTicket(ticket);
        entry.setActor(actor);
        entry.setAction(action);
        entry.setDetails(details);
        historyRepository.save(entry);
    }

    private TicketResponse toResponse(Ticket ticket) {
        return TicketResponse.from(ticket, clock.instant());
    }
}
