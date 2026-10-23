package com.emergencias.emergency_management.repository.projection;

public record ApplicableRuleRow(
        String ruleId,
        String ruleName,
        String description,
        int priority,
        String resourceType
) {
}