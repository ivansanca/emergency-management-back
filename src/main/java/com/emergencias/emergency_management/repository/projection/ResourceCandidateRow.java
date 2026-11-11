package com.emergencias.emergency_management.repository.projection;

import java.util.List;

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
