package com.gustavoserafim.serviceflow.service;

import com.gustavoserafim.serviceflow.config.BusinessHoursProperties;
import com.gustavoserafim.serviceflow.dto.AnalyticsResponses.PriorityStat;
import com.gustavoserafim.serviceflow.dto.AnalyticsResponses.Report;
import com.gustavoserafim.serviceflow.dto.AnalyticsResponses.Summary;
import com.gustavoserafim.serviceflow.entity.Priority;
import com.gustavoserafim.serviceflow.entity.TicketStatus;
import com.gustavoserafim.serviceflow.exception.BusinessRuleException;
import com.gustavoserafim.serviceflow.repository.AnalyticsRepository;
import com.gustavoserafim.serviceflow.repository.AnalyticsRepository.PriorityRow;
import com.gustavoserafim.serviceflow.repository.AnalyticsRepository.StatusRow;
import com.gustavoserafim.serviceflow.repository.AnalyticsRepository.SummaryRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AnalyticsServiceTest {

    // Sábado 2026-01-10 às 12:00 UTC = 09:00 em São Paulo (UTC-3)
    private static final Instant NOW = Instant.parse("2026-01-10T12:00:00Z");

    @Mock
    private AnalyticsRepository repository;

    private AnalyticsService service;

    @BeforeEach
    void setUp() {
        BusinessHoursProperties calendar = new BusinessHoursProperties(
                ZoneId.of("America/Sao_Paulo"), LocalTime.of(8, 0), LocalTime.of(18, 0),
                Set.of(DayOfWeek.MONDAY), List.of());
        service = new AnalyticsService(repository, calendar, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    // ------------------------------------------------------------- período

    @Test
    void defaultPeriod_isTheLast30DaysEndingToday_inTheCompanyZone() {
        AnalyticsService.Window w = service.window(null, null);

        assertThat(w.period().to()).isEqualTo(LocalDate.of(2026, 1, 10));
        assertThat(w.period().from()).isEqualTo(LocalDate.of(2025, 12, 12)); // 30 dias contando o de hoje
        // meia-noite LOCAL (UTC-3) = 03:00 UTC; o fim é exclusivo: meia-noite do dia seguinte
        assertThat(w.start()).isEqualTo(Instant.parse("2025-12-12T03:00:00Z"));
        assertThat(w.end()).isEqualTo(Instant.parse("2026-01-11T03:00:00Z"));
    }

    @Test
    void todayIsDecidedByTheCompanyZoneNotByUtc() {
        // 2026-01-10T01:00Z ainda é 09/01 às 22:00 em São Paulo
        BusinessHoursProperties calendar = new BusinessHoursProperties(
                ZoneId.of("America/Sao_Paulo"), LocalTime.of(8, 0), LocalTime.of(18, 0), Set.of(DayOfWeek.MONDAY), List.of());
        AnalyticsService lateNight = new AnalyticsService(repository, calendar,
                Clock.fixed(Instant.parse("2026-01-10T01:00:00Z"), ZoneOffset.UTC));

        assertThat(lateNight.window(null, null).period().to()).isEqualTo(LocalDate.of(2026, 1, 9));
    }

    @Test
    void explicitPeriod_isInclusiveOnBothEnds() {
        AnalyticsService.Window w = service.window(LocalDate.of(2021, 1, 1), LocalDate.of(2021, 1, 31));

        assertThat(w.start()).isEqualTo(Instant.parse("2021-01-01T03:00:00Z"));
        assertThat(w.end()).isEqualTo(Instant.parse("2021-02-01T03:00:00Z")); // o dia 31 inteiro está dentro
    }

    @Test
    void invalidPeriods_areRejected() {
        assertThatThrownBy(() -> service.window(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 1)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("posterior");
        assertThatThrownBy(() -> service.window(LocalDate.of(2024, 1, 1), LocalDate.of(2026, 1, 1)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("366");
    }

    @Test
    void maximumPeriodOf366DaysIsAccepted() {
        LocalDate to = LocalDate.of(2026, 1, 10);
        assertThat(service.window(to.minusDays(365), to).period().from()).isEqualTo(to.minusDays(365));
    }

    // ------------------------------------------------------------- números

    @Test
    void summary_convertsSecondsToMinutes_computesRates_andFillsAllStatuses() {
        SummaryRow row = mock(SummaryRow.class);
        when(row.getOpened()).thenReturn(10L);
        when(row.getResolved()).thenReturn(8L);
        when(row.getResolvedWithinSla()).thenReturn(6L);
        when(row.getAvgResolutionSeconds()).thenReturn(14850.0);   // 247,5 min
        when(row.getMedianResolutionSeconds()).thenReturn(16200.0); // 270 min
        when(repository.summary(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(row);

        StatusRow inProgress = mock(StatusRow.class);
        when(inProgress.getStatus()).thenReturn("EM_ATENDIMENTO");
        when(inProgress.getTotal()).thenReturn(5L);
        when(inProgress.getBreached()).thenReturn(2L);
        StatusRow open = mock(StatusRow.class);
        when(open.getStatus()).thenReturn("ABERTO");
        when(open.getTotal()).thenReturn(3L);
        when(open.getBreached()).thenReturn(1L);
        when(repository.currentByStatus(NOW)).thenReturn(List.of(inProgress, open));

        Summary s = service.summary(null, null);

        assertThat(s.opened()).isEqualTo(10);
        assertThat(s.resolved()).isEqualTo(8);
        assertThat(s.slaComplianceRate()).isEqualTo(0.75);
        assertThat(s.avgResolutionMinutes()).isEqualTo(247.5);
        assertThat(s.medianResolutionMinutes()).isEqualTo(270.0);
        assertThat(s.current().total()).isEqualTo(8);
        assertThat(s.current().breachedOpen()).isEqualTo(3);
        // todos os status (menos CANCELADO) presentes, os sem chamados com zero
        assertThat(s.current().byStatus()).containsEntry(TicketStatus.EM_ATENDIMENTO, 5L)
                .containsEntry(TicketStatus.RESOLVIDO, 0L).doesNotContainKey(TicketStatus.CANCELADO);
    }

    @Test
    void summary_withNothingResolved_returnsNullRatesInsteadOfAMisleadingZero() {
        SummaryRow row = mock(SummaryRow.class);
        when(row.getOpened()).thenReturn(3L);
        when(row.getResolved()).thenReturn(0L);
        when(row.getResolvedWithinSla()).thenReturn(0L);
        when(row.getAvgResolutionSeconds()).thenReturn(null);
        when(row.getMedianResolutionSeconds()).thenReturn(null);
        when(repository.summary(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(row);

        Summary s = service.summary(null, null);

        assertThat(s.slaComplianceRate()).isNull();
        assertThat(s.avgResolutionMinutes()).isNull();
        assertThat(s.medianResolutionMinutes()).isNull();
    }

    @Test
    void byPriority_alwaysReturnsP1ToP4InOrder_withMissingOnesZeroed() {
        PriorityRow p3 = mock(PriorityRow.class);
        when(p3.getPriority()).thenReturn("P3");
        when(p3.getOpened()).thenReturn(4L);
        when(p3.getResolved()).thenReturn(2L);
        when(p3.getResolvedWithinSla()).thenReturn(1L);
        when(p3.getAvgResolutionSeconds()).thenReturn(3600.0);
        when(repository.byPriority(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(p3));

        Report<PriorityStat> report = service.byPriority(null, null);

        assertThat(report.items()).extracting(PriorityStat::priority)
                .containsExactly(Priority.P1, Priority.P2, Priority.P3, Priority.P4);
        assertThat(report.items().get(0).opened()).isZero();
        assertThat(report.items().get(0).slaComplianceRate()).isNull();
        assertThat(report.items().get(2).slaComplianceRate()).isEqualTo(0.5);
        assertThat(report.items().get(2).avgResolutionMinutes()).isEqualTo(60.0);
    }

    @Test
    void helpers_roundAsDocumented() {
        assertThat(AnalyticsService.rate(1, 3)).isEqualTo(0.3333);
        assertThat(AnalyticsService.rate(0, 0)).isNull();
        assertThat(AnalyticsService.minutes(90.0)).isEqualTo(1.5);
        assertThat(AnalyticsService.minutes(null)).isNull();
    }
}
