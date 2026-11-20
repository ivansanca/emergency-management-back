package com.emergencias.emergency_management.repository.projection;

/**
 * One resource type required by a rule that applies to an incident.
 */
public record ApplicableRuleRow(
        String ruleId,
        String ruleName,
        String description,
        int priority,
        String resourceType,
        String requirementGroup,
        int quantity
) {
}
