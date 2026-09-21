package com.gustavoserafim.serviceflow.service;

import com.gustavoserafim.serviceflow.config.BusinessHoursProperties;
import com.gustavoserafim.serviceflow.dto.SlaRuleResponse;
import com.gustavoserafim.serviceflow.dto.SlaRuleUpdateRequest;
import com.gustavoserafim.serviceflow.entity.Priority;
import com.gustavoserafim.serviceflow.entity.SlaRule;
import com.gustavoserafim.serviceflow.exception.ResourceNotFoundException;
import com.gustavoserafim.serviceflow.repository.SlaRuleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SlaRuleServiceTest {

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    @Mock
    private SlaRuleRepository slaRuleRepository;

    private SlaRuleService slaRuleService;

    @BeforeEach
    void setUp() {
        BusinessCalendar calendar = new BusinessCalendar(new BusinessHoursProperties(
                ZONE, LocalTime.of(8, 0), LocalTime.of(18, 0),
                Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                        DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
                List.of()));
        slaRuleService = new SlaRuleService(slaRuleRepository, calendar);
    }

    private SlaRule ruleOf(Priority priority, int minutes, boolean businessHours) {
        SlaRule rule = new SlaRule();
        rule.setPriority(priority);
        rule.setResolutionMinutes(minutes);
        rule.setBusinessHours(businessHours);
        return rule;
    }

    private Instant local(String isoDateTime) {
        return LocalDateTime.parse(isoDateTime).atZone(ZONE).toInstant();
    }

    @Test
    void update_changesMinutesAndBusinessHoursFlag() {
        SlaRule rule = ruleOf(Priority.P1, 240, false);
        when(slaRuleRepository.findByPriority(Priority.P1)).thenReturn(Optional.of(rule));
        when(slaRuleRepository.saveAndFlush(rule)).thenReturn(rule);

        SlaRuleResponse response = slaRuleService.update(Priority.P1, new SlaRuleUpdateRequest(120, true));

        assertThat(response.resolutionMinutes()).isEqualTo(120);
        assertThat(response.businessHours()).isTrue();
        assertThat(rule.getResolutionMinutes()).isEqualTo(120);
    }

    @Test
    void dueAtFor_continuousRule_countsCalendarTimeIncludingNightsAndWeekends() {
        when(slaRuleRepository.findByPriority(Priority.P1)).thenReturn(Optional.of(ruleOf(Priority.P1, 240, false)));

        // sábado 22:00 + 4h corridas = domingo 02:00 (não pula nada)
        assertThat(slaRuleService.dueAtFor(Priority.P1, local("2026-01-10T22:00:00")))
                .isEqualTo(local("2026-01-11T02:00:00"));
    }

    @Test
    void dueAtFor_businessHoursRule_countsOnlyWorkingTime() {
        when(slaRuleRepository.findByPriority(Priority.P3)).thenReturn(Optional.of(ruleOf(Priority.P3, 600, true)));

        // sexta 16:00 + 1 dia útil (10h): 2h na sexta + 8h na segunda -> segunda 16:00
        assertThat(slaRuleService.dueAtFor(Priority.P3, local("2026-01-09T16:00:00")))
                .isEqualTo(local("2026-01-12T16:00:00"));
    }

    @Test
    void dueAtFor_withoutRule_throwsResourceNotFound() {
        when(slaRuleRepository.findByPriority(Priority.P2)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> slaRuleService.dueAtFor(Priority.P2, Instant.now()))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
