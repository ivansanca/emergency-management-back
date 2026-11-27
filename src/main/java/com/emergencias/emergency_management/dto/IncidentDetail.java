package com.emergencias.emergency_management.dto;

import java.util.List;

/**
 * An incident with its detections, in the order they were observed, and its
 * assignments, current and past.
 */
public record IncidentDetail(
        IncidentSummary incident,
        List<DetectionSummary> detections,
        List<AssignmentSummary> assignments
) {
}
