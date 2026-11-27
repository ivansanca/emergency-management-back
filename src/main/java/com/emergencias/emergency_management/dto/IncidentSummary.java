package com.emergencias.emergency_management.dto;

import com.emergencias.emergency_management.model.IncidentSource;
import com.emergencias.emergency_management.model.IncidentStatus;

import java.time.Instant;

/**
 * An incident as the query API lists it: its current state and how many
 * detections and active assignments it has, without loading them.
 * <p>
 * {@code status} and {@code source} are enums, not strings: Spring Data Neo4j
 * maps a projection of {@code IncidentRepository} with the property types of
 * {@code Incident}, so it already converts those columns to the enums.
 *
 * @param lastDetectedAt latest detection, or the start of the incident when
 *                       it has only one
 * @param closedAt when an operator closed it; null while it is open
 * @param assignedResources resources assigned now (released ones excluded)
 */
public record IncidentSummary(
        String id,
        String type,
        int severity,
        IncidentStatus status,
        IncidentSource source,
        String description,
        String zoneCode,
        String zoneName,
        Double latitude,
        Double longitude,
        Instant occurredAt,
        Instant receivedAt,
        Instant lastDetectedAt,
        Instant closedAt,
        long detectionCount,
        long assignedResources
) {
}
