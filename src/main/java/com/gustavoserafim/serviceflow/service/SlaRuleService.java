package com.gustavoserafim.serviceflow.service;

import com.gustavoserafim.serviceflow.dto.SlaRuleResponse;
import com.gustavoserafim.serviceflow.dto.SlaRuleUpdateRequest;
import com.gustavoserafim.serviceflow.entity.Priority;
import com.gustavoserafim.serviceflow.entity.SlaRule;
import com.gustavoserafim.serviceflow.exception.ResourceNotFoundException;
import com.gustavoserafim.serviceflow.repository.SlaRuleRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Consulta e edição das regras de SLA. O método dueAtFor() é o que o
 * TicketService usa para calcular o prazo de cada novo chamado.
 */
@Service
public class SlaRuleService {

    private final SlaRuleRepository slaRuleRepository;
    private final BusinessCalendar businessCalendar;

    public SlaRuleService(SlaRuleRepository slaRuleRepository, BusinessCalendar businessCalendar) {
        this.slaRuleRepository = slaRuleRepository;
        this.businessCalendar = businessCalendar;
    }

    @Transactional(readOnly = true)
    public List<SlaRuleResponse> findAll() {
        return slaRuleRepository.findAll(Sort.by("priority")).stream()
                .map(SlaRuleResponse::from)
                .toList();
    }

    @Transactional
    public SlaRuleResponse update(Priority priority, SlaRuleUpdateRequest request) {
        SlaRule rule = getOrThrow(priority);
        rule.setResolutionMinutes(request.resolutionMinutes());
        rule.setBusinessHours(request.businessHours());
        // saveAndFlush força o UPDATE agora: o @PreUpdate (que atualiza updatedAt)
        // só roda no flush, e queremos o valor novo já na resposta.
        return SlaRuleResponse.from(slaRuleRepository.saveAndFlush(rule));
    }

    /**
     * Prazo final de um chamado aberto em "openedAt": em tempo corrido ou
     * apenas em horas úteis, conforme a regra da prioridade.
     */
    @Transactional(readOnly = true)
    public Instant dueAtFor(Priority priority, Instant openedAt) {
        SlaRule rule = getOrThrow(priority);
        return rule.isBusinessHours()
                ? businessCalendar.addBusinessMinutes(openedAt, rule.getResolutionMinutes())
                : SlaCalculator.dueAt(openedAt, rule.getResolutionMinutes());
    }

    private SlaRule getOrThrow(Priority priority) {
        return slaRuleRepository.findByPriority(priority)
                .orElseThrow(() -> new ResourceNotFoundException("Regra de SLA não encontrada para a prioridade " + priority));
    }
}
