package com.gustavoserafim.serviceflow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

/**
 * Calendário comercial usado nos SLAs em "horas úteis", lido de
 * app.sla.business-hours.* no application.yml.
 *
 * @ConfigurationProperties agrupa várias propriedades num objeto tipado (mais
 * seguro que vários @Value soltos). Em um record, o Spring preenche pelo
 * construtor; o construtor compacto abaixo aplica padrões e valida.
 */
@ConfigurationProperties(prefix = "app.sla.business-hours")
public record BusinessHoursProperties(
        ZoneId zone,
        LocalTime start,
        LocalTime end,
        Set<DayOfWeek> days,
        List<LocalDate> holidays
) {

    public BusinessHoursProperties {
        zone = (zone != null) ? zone : ZoneId.of("America/Sao_Paulo");
        start = (start != null) ? start : LocalTime.of(8, 0);
        end = (end != null) ? end : LocalTime.of(18, 0);
        days = (days != null && !days.isEmpty())
                ? Set.copyOf(days)
                : Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                        DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);
        holidays = (holidays != null) ? List.copyOf(holidays) : List.of();

        if (!start.isBefore(end)) {
            throw new IllegalArgumentException("app.sla.business-hours: 'start' deve ser anterior a 'end'");
        }
    }
}
