package com.emergencias.emergency_management.repository.projection;

import java.util.List;

public record ResourceCandidateRow(
        String id,
        String name,
        String type,
        boolean available,
        String zoneName,
        double distanceKm,
        double travelTimeMinutes,
        List<String> route
) {
}