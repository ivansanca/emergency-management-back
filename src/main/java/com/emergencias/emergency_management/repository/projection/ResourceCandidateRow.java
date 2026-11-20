package com.emergencias.emergency_management.repository.projection;

import java.util.List;

/**
 * An available resource that could attend an incident, with the fastest route
 * found for it.
 */
public record ResourceCandidateRow(
        String id,
        String name,
        String type,
        boolean available,
        String zoneName,
        Double distanceKm,
        Double travelTimeMinutes,
        List<String> route
) {
}
