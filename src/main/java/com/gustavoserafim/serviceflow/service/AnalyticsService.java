package com.gustavoserafim.serviceflow.service;

import com.gustavoserafim.serviceflow.config.BusinessHoursProperties;
import com.gustavoserafim.serviceflow.dto.AnalyticsResponses.CategoryStat;
import com.gustavoserafim.serviceflow.dto.AnalyticsResponses.Current;
import com.gustavoserafim.serviceflow.dto.AnalyticsResponses.Period;
import com.gustavoserafim.serviceflow.dto.AnalyticsResponses.PriorityStat;
import com.gustavoserafim.serviceflow.dto.AnalyticsResponses.Report;
import com.gustavoserafim.serviceflow.dto.AnalyticsResponses.Summary;
import com.gustavoserafim.serviceflow.dto.AnalyticsResponses.TechnicianStat;
import com.gustavoserafim.serviceflow.dto.AnalyticsResponses.TimelinePoint;
import com.gustavoserafim.serviceflow.entity.Priority;
import com.gustavoserafim.serviceflow.entity.TicketStatus;
import com.gustavoserafim.serviceflow.exception.BusinessRuleException;
import com.gustavoserafim.serviceflow.repository.AnalyticsRepository;
import com.gustavoserafim.serviceflow.repository.AnalyticsRepository.PriorityRow;
import com.gustavoserafim.serviceflow.repository.AnalyticsRepository.StatusRow;
import com.gustavoserafim.serviceflow.repository.AnalyticsRepository.SummaryRow;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Traduz o pedido do cliente (datas) em instantes e transforma as linhas cruas do
 * banco em números de negócio (minutos, taxas). O trabalho pesado de agregar
 * fica no banco (AnalyticsRepository); aqui ficam as regras de período e de apresentação.
 */
@Service
@Transactional(readOnly = true)
public class AnalyticsService {

    static final int DEFAULT_DAYS = 30;
    static final int MAX_DAYS = 366;

    private final AnalyticsRepository repository;
    private final BusinessHoursProperties calendar;
    private final Clock clock;

    public AnalyticsService(AnalyticsRepository repository, BusinessHoursProperties calendar, Clock clock) {
        this.repository = repository;
        this.calendar = calendar;
        this.clock = clock;
    }

    public Summary summary(LocalDate from, LocalDate to) {
        Window w = window(from, to);
        SummaryRow row = repository.summary(w.start(), w.end());

        long resolved = count(row.getResolved());
        long within = count(row.getResolvedWithinSla());
        return new Summary(w.period(), count(row.getOpened()), resolved, within,
                rate(within, resolved),
                minutes(row.getAvgResolutionSeconds()),
                minutes(row.getMedianResolutionSeconds()),
                current());
    }

    public Report<CategoryStat> byCategory(LocalDate from, LocalDate to) {
        Window w = window(from, to);
        List<CategoryStat> items = repository.byCategory(w.start(), w.end()).stream()
                .map(r -> new CategoryStat(r.getCategoryId(), r.getCategoryName(),
                        count(r.getOpened()), count(r.getResolved()), count(r.getResolvedWithinSla()),
                        rate(count(r.getResolvedWithinSla()), count(r.getResolved())),
                        minutes(r.getAvgResolutionSeconds())))
                .toList();
        return new Report<>(w.period(), items);
    }

    /** Sempre devolve as 4 prioridades, em ordem P1..P4 (as sem movimento vêm zeradas): o eixo do gráfico é estável. */
    public Report<PriorityStat> byPriority(LocalDate from, LocalDate to) {
        Window w = window(from, to);
        Map<Priority, PriorityRow> rows = new EnumMap<>(Priority.class);
        repository.byPriority(w.start(), w.end()).forEach(r -> rows.put(Priority.valueOf(r.getPriority()), r));

        List<PriorityStat> items = java.util.Arrays.stream(Priority.values()).map(p -> {
            PriorityRow r = rows.get(p);
            if (r == null) {
                return new PriorityStat(p, 0, 0, 0, null, null);
            }
            return new PriorityStat(p, count(r.getOpened()), count(r.getResolved()), count(r.getResolvedWithinSla()),
                    rate(count(r.getResolvedWithinSla()), count(r.getResolved())),
                    minutes(r.getAvgResolutionSeconds()));
        }).toList();
        return new Report<>(w.period(), items);
    }

    public Report<TechnicianStat> byTechnician(LocalDate from, LocalDate to) {
        Window w = window(from, to);
        List<TechnicianStat> items = repository.byTechnician(w.start(), w.end()).stream()
                .map(r -> new TechnicianStat(r.getTechnicianId(), r.getTechnicianName(),
                        count(r.getResolved()), count(r.getResolvedWithinSla()),
                        rate(count(r.getResolvedWithinSla()), count(r.getResolved())),
                        minutes(r.getAvgResolutionSeconds()), count(r.getInProgressNow())))
                .toList();
        return new Report<>(w.period(), items);
    }

    public Report<TimelinePoint> timeline(LocalDate from, LocalDate to) {
        Window w = window(from, to);
        List<TimelinePoint> items = repository.timeline(w.start(), w.end(),
                        w.period().from(), w.period().to(), calendar.zone().getId()).stream()
                .map(r -> new TimelinePoint(LocalDate.parse(r.getDay()), count(r.getOpened()), count(r.getResolved())))
                .toList();
        return new Report<>(w.period(), items);
    }

    // ------------------------------------------------------------------ helpers

    private Current current() {
        Map<TicketStatus, Long> byStatus = new LinkedHashMap<>();
        for (TicketStatus status : TicketStatus.values()) {
            if (status != TicketStatus.CANCELADO) {
                byStatus.put(status, 0L);
            }
        }
        long total = 0;
        long breached = 0;
        for (StatusRow row : repository.currentByStatus(clock.instant())) {
            byStatus.put(TicketStatus.valueOf(row.getStatus()), count(row.getTotal()));
            total += count(row.getTotal());
            breached += count(row.getBreached());
        }
        return new Current(total, breached, byStatus);
    }

    /** Período pedido convertido em [início, fim) no fuso da empresa. */
    record Window(Period period, Instant start, Instant end) {
    }

    Window window(LocalDate from, LocalDate to) {
        ZoneId zone = calendar.zone();
        LocalDate effectiveTo = (to != null) ? to : LocalDate.now(clock.withZone(zone));
        LocalDate effectiveFrom = (from != null) ? from : effectiveTo.minusDays(DEFAULT_DAYS - 1L);

        if (effectiveFrom.isAfter(effectiveTo)) {
            throw new BusinessRuleException("A data inicial (from) não pode ser posterior à final (to)");
        }
        if (ChronoUnit.DAYS.between(effectiveFrom, effectiveTo) + 1 > MAX_DAYS) {
            throw new BusinessRuleException("O período máximo é de " + MAX_DAYS + " dias");
        }
        // "to" é inclusivo: o fim exclusivo é a meia-noite do dia seguinte.
        return new Window(new Period(effectiveFrom, effectiveTo),
                effectiveFrom.atStartOfDay(zone).toInstant(),
                effectiveTo.plusDays(1).atStartOfDay(zone).toInstant());
    }

    private static long count(Long value) {
        return value == null ? 0 : value;
    }

    /** Taxa 0..1 com 4 casas; null quando não há base (nada resolvido no período). */
    static Double rate(long part, long total) {
        return total == 0 ? null : Math.round(10000.0 * part / total) / 10000.0;
    }

    /** Segundos -> minutos com 1 casa decimal; null quando não há dados. */
    static Double minutes(Double seconds) {
        return seconds == null ? null : Math.round(seconds / 6.0) / 10.0;
    }
}
