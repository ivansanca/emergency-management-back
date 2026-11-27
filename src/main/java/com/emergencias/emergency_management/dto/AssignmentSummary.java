package com.emergencias.emergency_management.dto;

import java.time.Instant;

/**
 * A resource assigned to an incident. Assignments are kept when the incident
 * is closed, with the time the resource was released, as history.
 */
public record AssignmentSummary(
        String resourceId,
        String resourceName,
        String resourceType,
        Instant assignedAt,
        Instant releasedAt
) {
}
