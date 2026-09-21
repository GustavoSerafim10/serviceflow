package com.gustavoserafim.serviceflow.dto;

import com.gustavoserafim.serviceflow.entity.Priority;
import com.gustavoserafim.serviceflow.entity.SlaRule;

import java.time.Instant;

public record SlaRuleResponse(
        Priority priority,
        int resolutionMinutes,
        Instant updatedAt
) {

    public static SlaRuleResponse from(SlaRule rule) {
        return new SlaRuleResponse(rule.getPriority(), rule.getResolutionMinutes(), rule.getUpdatedAt());
    }
}
