package com.gustavoserafim.serviceflow.service;

import com.gustavoserafim.serviceflow.config.BusinessHoursProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Expediente 08:00–18:00, segunda a sexta, fuso America/Sao_Paulo (UTC-3, sem
 * horário de verão). Datas de referência em janeiro/2026:
 *   05 = segunda · 06 = terça · 07 = quarta · 09 = sexta · 10 = sábado · 11 = domingo · 12 = segunda
 * Os testes falam em horário LOCAL (mais legível) e convertem para Instant.
 */
class BusinessCalendarTest {

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    private final BusinessCalendar calendar = calendarWithHolidays(List.of());

    private static BusinessCalendar calendarWithHolidays(List<LocalDate> holidays) {
        return new BusinessCalendar(new BusinessHoursProperties(
                ZONE, LocalTime.of(8, 0), LocalTime.of(18, 0),
                Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                        DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
                holidays));
    }

    private static Instant local(String isoDateTime) {
        return LocalDateTime.parse(isoDateTime).atZone(ZONE).toInstant();
    }

    @Test
    void withinTheSameWorkday_justAddsTheMinutes() {
        assertThat(calendar.addBusinessMinutes(local("2026-01-05T10:00:00"), 60))
                .isEqualTo(local("2026-01-05T11:00:00"));
    }

    @Test
    void reachingExactlyTheEndOfTheWorkday_staysOnThatDay() {
        assertThat(calendar.addBusinessMinutes(local("2026-01-05T08:00:00"), 600)) // 10h = expediente inteiro
                .isEqualTo(local("2026-01-05T18:00:00"));
    }

    @Test
    void overflowingTheWorkday_continuesNextMorning() {
        // segunda 17:30 + 60 min: 30 min na segunda, 30 min na terça
        assertThat(calendar.addBusinessMinutes(local("2026-01-05T17:30:00"), 60))
                .isEqualTo(local("2026-01-06T08:30:00"));
    }

    @Test
    void openedBeforeTheWorkday_startsCountingAtOpening() {
        assertThat(calendar.addBusinessMinutes(local("2026-01-05T07:00:00"), 30))
                .isEqualTo(local("2026-01-05T08:30:00"));
    }

    @Test
    void openedAfterTheWorkday_startsCountingNextBusinessMorning() {
        assertThat(calendar.addBusinessMinutes(local("2026-01-05T19:00:00"), 30))
                .isEqualTo(local("2026-01-06T08:30:00"));
    }

    @Test
    void openedExactlyAtClosingTime_isTreatedAsAfterHours() {
        assertThat(calendar.addBusinessMinutes(local("2026-01-05T18:00:00"), 30))
                .isEqualTo(local("2026-01-06T08:30:00"));
    }

    @Test
    void fridayEveningSpillsOverTheWeekend() {
        // sexta 17:00 + 120 min: 1h na sexta + 1h na segunda
        assertThat(calendar.addBusinessMinutes(local("2026-01-09T17:00:00"), 120))
                .isEqualTo(local("2026-01-12T09:00:00"));
    }

    @Test
    void openedOnSaturday_startsOnMonday() {
        assertThat(calendar.addBusinessMinutes(local("2026-01-10T12:00:00"), 30))
                .isEqualTo(local("2026-01-12T08:30:00"));
    }

    @Test
    void multiDayDeadline_consumesEachWorkdayInTurn() {
        // segunda 09:00 + 20h úteis: 9h (seg) + 10h (ter) + 1h (qua) -> quarta 09:00
        assertThat(calendar.addBusinessMinutes(local("2026-01-05T09:00:00"), 1200))
                .isEqualTo(local("2026-01-07T09:00:00"));
    }

    @Test
    void holidaysAreSkippedLikeWeekends() {
        BusinessCalendar withHoliday = calendarWithHolidays(List.of(LocalDate.of(2026, 1, 6))); // terça
        // segunda 17:00 + 120 min: 1h na segunda; terça é feriado; 1h na quarta
        assertThat(withHoliday.addBusinessMinutes(local("2026-01-05T17:00:00"), 120))
                .isEqualTo(local("2026-01-07T09:00:00"));
    }

    @Test
    void neverReturnsAnInstantOutsideTheWorkday() {
        // varre chegadas de hora em hora durante 2 semanas: o prazo sempre cai em dia útil, dentro do expediente
        LocalDateTime cursor = LocalDateTime.parse("2026-01-05T00:00:00");
        Executable[] checks = new Executable[14 * 24];
        for (int i = 0; i < checks.length; i++) {
            LocalDateTime opened = cursor.plusHours(i);
            checks[i] = () -> {
                LocalDateTime due = calendar.addBusinessMinutes(opened.atZone(ZONE).toInstant(), 45)
                        .atZone(ZONE).toLocalDateTime();
                assertThat(due.getDayOfWeek()).isNotIn(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);
                assertThat(due.toLocalTime()).isBetween(LocalTime.of(8, 0), LocalTime.of(18, 0));
            };
        }
        org.junit.jupiter.api.Assertions.assertAll(checks);
    }

    @Test
    void invalidConfiguration_isRejected() {
        assertThatThrownBy(() -> new BusinessHoursProperties(
                ZONE, LocalTime.of(18, 0), LocalTime.of(8, 0), Set.of(DayOfWeek.MONDAY), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
