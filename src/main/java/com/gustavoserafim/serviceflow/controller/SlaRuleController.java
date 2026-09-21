package com.gustavoserafim.serviceflow.controller;

import com.gustavoserafim.serviceflow.dto.SlaRuleResponse;
import com.gustavoserafim.serviceflow.dto.SlaRuleUpdateRequest;
import com.gustavoserafim.serviceflow.entity.Priority;
import com.gustavoserafim.serviceflow.service.SlaRuleService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Leitura: qualquer usuário autenticado (todos precisam saber o prazo).
 * Edição: só ADMIN. Repare que {priority} na URL é convertido direto para o
 * enum Priority pelo Spring: /api/sla-rules/P1 funciona e /api/sla-rules/X9
 * vira 400 automaticamente.
 */
@RestController
@RequestMapping("/api/sla-rules")
public class SlaRuleController {

    private final SlaRuleService slaRuleService;

    public SlaRuleController(SlaRuleService slaRuleService) {
        this.slaRuleService = slaRuleService;
    }

    @GetMapping
    public List<SlaRuleResponse> list() {
        return slaRuleService.findAll();
    }

    @PutMapping("/{priority}")
    @PreAuthorize("hasRole('ADMIN')")
    public SlaRuleResponse update(@PathVariable Priority priority, @Valid @RequestBody SlaRuleUpdateRequest request) {
        return slaRuleService.update(priority, request);
    }
}
