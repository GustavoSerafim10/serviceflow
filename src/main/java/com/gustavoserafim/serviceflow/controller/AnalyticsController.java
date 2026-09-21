package com.gustavoserafim.serviceflow.controller;

import com.gustavoserafim.serviceflow.dto.AnalyticsResponses.CategoryStat;
import com.gustavoserafim.serviceflow.dto.AnalyticsResponses.PriorityStat;
import com.gustavoserafim.serviceflow.dto.AnalyticsResponses.Report;
import com.gustavoserafim.serviceflow.dto.AnalyticsResponses.Summary;
import com.gustavoserafim.serviceflow.dto.AnalyticsResponses.TechnicianStat;
import com.gustavoserafim.serviceflow.dto.AnalyticsResponses.TimelinePoint;
import com.gustavoserafim.serviceflow.service.AnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Indicadores operacionais (base do dashboard). Somente ADMIN e TECNICO: são
 * números globais de toda a operação, que um solicitante não deve ver.
 *
 * Todos aceitam ?from=AAAA-MM-DD&to=AAAA-MM-DD (inclusivos, no fuso da empresa).
 * Sem parâmetros: últimos 30 dias. Máximo: 366 dias.
 */
@RestController
@RequestMapping("/api/analytics")
@PreAuthorize("hasAnyRole('ADMIN', 'TECNICO')")
@Tag(name = "Analytics", description = "Indicadores de SLA, MTTR, volume e desempenho (ADMIN e TECNICO)")
public class AnalyticsController {

    private static final String FROM_DESC = "Data inicial, inclusiva (AAAA-MM-DD). Padrão: 29 dias antes de 'to'.";
    private static final String TO_DESC = "Data final, inclusiva (AAAA-MM-DD). Padrão: hoje.";

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/summary")
    @Operation(summary = "Visão geral: abertos, resolvidos, conformidade de SLA, MTTR e retrato atual",
            description = "'opened' conta chamados abertos no período; 'resolved', 'slaComplianceRate' e o MTTR "
                    + "consideram os chamados RESOLVIDOS no período. 'current' é o retrato de agora "
                    + "(por status e quantos abertos já estouraram o SLA). Cancelados nunca entram.")
    public Summary summary(
            @Parameter(description = FROM_DESC) @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = TO_DESC) @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return analyticsService.summary(from, to);
    }

    @GetMapping("/by-category")
    @Operation(summary = "Volume, conformidade de SLA e MTTR por categoria")
    public Report<CategoryStat> byCategory(
            @Parameter(description = FROM_DESC) @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = TO_DESC) @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return analyticsService.byCategory(from, to);
    }

    @GetMapping("/by-priority")
    @Operation(summary = "Volume, conformidade de SLA e MTTR por prioridade (sempre P1 a P4)")
    public Report<PriorityStat> byPriority(
            @Parameter(description = FROM_DESC) @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = TO_DESC) @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return analyticsService.byPriority(from, to);
    }

    @GetMapping("/by-technician")
    @Operation(summary = "Desempenho por técnico responsável",
            description = "Resolvidos no período, conformidade de SLA, MTTR e chamados em atendimento agora. "
                    + "Chamados reatribuídos contam para o técnico atualmente responsável.")
    public Report<TechnicianStat> byTechnician(
            @Parameter(description = FROM_DESC) @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = TO_DESC) @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return analyticsService.byTechnician(from, to);
    }

    @GetMapping("/timeline")
    @Operation(summary = "Série diária de chamados abertos e resolvidos",
            description = "Todos os dias do período aparecem, inclusive os sem movimento (com zeros).")
    public Report<TimelinePoint> timeline(
            @Parameter(description = FROM_DESC) @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = TO_DESC) @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return analyticsService.timeline(from, to);
    }
}
