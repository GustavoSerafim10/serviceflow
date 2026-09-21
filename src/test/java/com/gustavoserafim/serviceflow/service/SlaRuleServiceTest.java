package com.gustavoserafim.serviceflow.service;

import com.gustavoserafim.serviceflow.dto.SlaRuleResponse;
import com.gustavoserafim.serviceflow.dto.SlaRuleUpdateRequest;
import com.gustavoserafim.serviceflow.entity.Priority;
import com.gustavoserafim.serviceflow.entity.SlaRule;
import com.gustavoserafim.serviceflow.exception.ResourceNotFoundException;
import com.gustavoserafim.serviceflow.repository.SlaRuleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SlaRuleServiceTest {

    @Mock
    private SlaRuleRepository slaRuleRepository;

    @InjectMocks
    private SlaRuleService slaRuleService;

    private SlaRule ruleOf(Priority priority, int minutes) {
        SlaRule rule = new SlaRule();
        rule.setPriority(priority);
        rule.setResolutionMinutes(minutes);
        return rule;
    }

    @Test
    void update_changesResolutionMinutes() {
        SlaRule rule = ruleOf(Priority.P1, 240);
        when(slaRuleRepository.findByPriority(Priority.P1)).thenReturn(Optional.of(rule));
        when(slaRuleRepository.saveAndFlush(rule)).thenReturn(rule);

        SlaRuleResponse response = slaRuleService.update(Priority.P1, new SlaRuleUpdateRequest(120));

        assertThat(response.resolutionMinutes()).isEqualTo(120);
        assertThat(rule.getResolutionMinutes()).isEqualTo(120);
    }

    @Test
    void minutesFor_returnsConfiguredMinutes() {
        when(slaRuleRepository.findByPriority(Priority.P3)).thenReturn(Optional.of(ruleOf(Priority.P3, 1440)));

        assertThat(slaRuleService.minutesFor(Priority.P3)).isEqualTo(1440);
    }

    @Test
    void minutesFor_withoutRule_throwsResourceNotFound() {
        when(slaRuleRepository.findByPriority(Priority.P2)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> slaRuleService.minutesFor(Priority.P2))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
