package com.emergencias.emergency_management.dto;

/**
 * A resource as the query API lists it, with the incident it is assigned to
 * when it is not available.
 */
public record ResourceSummary(
        String id,
        String name,
        String type,
        boolean aerial,
        boolean available,
        String zoneCode,
        String zoneName,
        String assignedIncidentId
) {
}
