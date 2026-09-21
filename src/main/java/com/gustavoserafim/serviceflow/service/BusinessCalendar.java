package com.gustavoserafim.serviceflow.service;

import com.gustavoserafim.serviceflow.config.BusinessHoursProperties;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Set;

/**
 * Soma "minutos úteis" a um instante, pulando noites, fins de semana e feriados.
 *
 * Exemplo (expediente 08:00–18:00, seg–sex): abrir na sexta às 17:00 com prazo
 * de 120 minutos úteis consome 1h na sexta (até 18:00) e 1h na segunda,
 * vencendo na segunda às 09:00.
 *
 * O cálculo é feito no fuso da empresa (não em UTC), pois "18:00" e "segunda-feira"
 * só têm sentido no horário local. O resultado volta como Instant (UTC), como todo
 * o resto do sistema.
 */
@Component
public class BusinessCalendar {

    private final ZoneId zone;
    private final LocalTime start;
    private final LocalTime end;
    private final Set<DayOfWeek> days;
    private final Set<LocalDate> holidays;

    public BusinessCalendar(BusinessHoursProperties properties) {
        this.zone = properties.zone();
        this.start = properties.start();
        this.end = properties.end();
        this.days = properties.days();
        this.holidays = Set.copyOf(properties.holidays());
    }

    public Instant addBusinessMinutes(Instant from, long minutes) {
        ZonedDateTime cursor = alignToBusinessTime(from.atZone(zone));
        Duration remaining = Duration.ofMinutes(minutes);

        // Consome o expediente dia a dia até o tempo restante caber no dia corrente.
        while (true) {
            ZonedDateTime endOfWindow = cursor.toLocalDate().atTime(end).atZone(zone);
            Duration availableToday = Duration.between(cursor, endOfWindow);

            if (remaining.compareTo(availableToday) <= 0) {
                return cursor.plus(remaining).toInstant();
            }
            remaining = remaining.minus(availableToday);
            cursor = nextBusinessStart(cursor.toLocalDate().plusDays(1));
        }
    }

    /** Se o instante já está dentro do expediente, mantém; senão, avança para o próximo início de expediente. */
    private ZonedDateTime alignToBusinessTime(ZonedDateTime time) {
        LocalDate date = time.toLocalDate();
        if (isBusinessDay(date)) {
            LocalTime clock = time.toLocalTime();
            if (clock.isBefore(start)) {
                return date.atTime(start).atZone(zone);
            }
            if (clock.isBefore(end)) {
                return time;
            }
        }
        return nextBusinessStart(date.plusDays(1));
    }

    private ZonedDateTime nextBusinessStart(LocalDate fromDate) {
        LocalDate date = fromDate;
        for (int i = 0; i < 366; i++) { // trava de segurança contra configuração sem dia útil
            if (isBusinessDay(date)) {
                return date.atTime(start).atZone(zone);
            }
            date = date.plusDays(1);
        }
        throw new IllegalStateException("Nenhum dia útil encontrado no próximo ano; revise app.sla.business-hours");
    }

    private boolean isBusinessDay(LocalDate date) {
        return days.contains(date.getDayOfWeek()) && !holidays.contains(date);
    }
}
