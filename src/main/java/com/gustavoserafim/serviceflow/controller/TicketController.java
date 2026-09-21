package com.gustavoserafim.serviceflow.controller;

import com.gustavoserafim.serviceflow.dto.CommentRequest;
import com.gustavoserafim.serviceflow.dto.CommentResponse;
import com.gustavoserafim.serviceflow.dto.HistoryResponse;
import com.gustavoserafim.serviceflow.dto.PageResponse;
import com.gustavoserafim.serviceflow.dto.TicketAssignRequest;
import com.gustavoserafim.serviceflow.dto.TicketCreateRequest;
import com.gustavoserafim.serviceflow.dto.TicketFilter;
import com.gustavoserafim.serviceflow.dto.TicketResponse;
import com.gustavoserafim.serviceflow.dto.TicketStatusRequest;
import com.gustavoserafim.serviceflow.service.TicketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

/**
 * Rotas de chamados. As regras finas (quem vê o quê, quem muda qual status)
 * dependem dos DADOS do chamado, então ficam no TicketService. Aqui o
 * @PreAuthorize só barra o que depende exclusivamente da role.
 */
@RestController
@RequestMapping("/api/tickets")
@Tag(name = "Chamados", description = "Abertura, atribuição, fluxo de status, comentários, histórico e busca")
public class TicketController {

    private final TicketService ticketService;

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @PostMapping
    public ResponseEntity<TicketResponse> create(@Valid @RequestBody TicketCreateRequest request) {
        TicketResponse created = ticketService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    /**
     * Listagem com filtros e paginação, ex:
     *   GET /api/tickets?status=EM_ATENDIMENTO&priority=P1&slaStatus=ESTOURADO&page=0&size=20
     * TicketFilter e Pageable são preenchidos pelo Spring a partir da query string.
     */
    @GetMapping
    @Operation(summary = "Lista chamados com filtros e paginação",
            description = "Solicitantes só enxergam os próprios chamados. Ordenação fixa: mais recentes primeiro. "
                    + "Tamanho máximo de página: 100.")
    public PageResponse<TicketResponse> list(@ParameterObject TicketFilter filter,
                                             @ParameterObject Pageable pageable) {
        return ticketService.search(filter, pageable);
    }

    @GetMapping("/{id}")
    public TicketResponse get(@PathVariable Long id) {
        return ticketService.findById(id);
    }

    // PUT porque "a atribuição deste chamado passa a ser este técnico" é idempotente.
    @PutMapping("/{id}/assignment")
    @PreAuthorize("hasAnyRole('ADMIN', 'TECNICO')")
    @Operation(summary = "Atribui o chamado a um técnico",
            description = "TECNICO só pode atribuir a si mesmo; ADMIN a qualquer técnico ativo. "
                    + "Chamado ABERTO passa automaticamente para EM_ATENDIMENTO.")
    public TicketResponse assign(@PathVariable Long id, @Valid @RequestBody TicketAssignRequest request) {
        return ticketService.assign(id, request);
    }

    // PATCH: alteração parcial (só o status) do recurso.
    @PatchMapping("/{id}/status")
    @Operation(summary = "Muda o status do chamado",
            description = "Transições: ABERTO→CANCELADO; EM_ATENDIMENTO→RESOLVIDO/CANCELADO; "
                    + "RESOLVIDO→FECHADO/EM_ATENDIMENTO (reabertura). ABERTO→EM_ATENDIMENTO só via atribuição. "
                    + "Permissões: ADMIN todas; TECNICO responsável resolve/reabre; SOLICITANTE dono cancela (ABERTO), fecha ou reabre.")
    public TicketResponse changeStatus(@PathVariable Long id, @Valid @RequestBody TicketStatusRequest request) {
        return ticketService.changeStatus(id, request);
    }

    @GetMapping("/{id}/comments")
    public List<CommentResponse> listComments(@PathVariable Long id) {
        return ticketService.listComments(id);
    }

    @PostMapping("/{id}/comments")
    public ResponseEntity<CommentResponse> addComment(@PathVariable Long id,
                                                      @Valid @RequestBody CommentRequest request) {
        return ResponseEntity.status(201).body(ticketService.addComment(id, request));
    }

    @GetMapping("/{id}/history")
    public List<HistoryResponse> history(@PathVariable Long id) {
        return ticketService.listHistory(id);
    }
}
