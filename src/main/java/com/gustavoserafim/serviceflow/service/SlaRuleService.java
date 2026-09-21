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

import java.util.List;

/**
 * Consulta e edição das regras de SLA. O método minutesFor() é o que o
 * TicketService usa (Etapa 5) para calcular o prazo de cada novo chamado.
 */
@Service
public class SlaRuleService {

    private final SlaRuleRepository slaRuleRepository;

    public SlaRuleService(SlaRuleRepository slaRuleRepository) {
        this.slaRuleRepository = slaRuleRepository;
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
        // saveAndFlush força o UPDATE agora: o @PreUpdate (que atualiza updatedAt)
        // só roda no flush, e queremos o valor novo já na resposta.
        return SlaRuleResponse.from(slaRuleRepository.saveAndFlush(rule));
    }

    @Transactional(readOnly = true)
    public int minutesFor(Priority priority) {
        return getOrThrow(priority).getResolutionMinutes();
    }

    private SlaRule getOrThrow(Priority priority) {
        return slaRuleRepository.findByPriority(priority)
                .orElseThrow(() -> new ResourceNotFoundException("Regra de SLA não encontrada para a prioridade " + priority));
    }
}
