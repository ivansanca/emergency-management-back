package com.emergencias.emergency_management.dto;

import com.emergencias.emergency_management.model.IncidentSource;

import java.time.Instant;

/**
 * One of the events that reported an incident: a satellite hotspot, a sensor
 * reading or a report of an operator. Detections without a position have no
 * coordinates.
 * <p>
 * {@code source} is the enum, as in {@link IncidentSummary}: the projection
 * is mapped with the property types of {@code Incident}.
 */
public record DetectionSummary(
        String id,
        Double latitude,
        Double longitude,
        Instant observedAt,
        Instant receivedAt,
        IncidentSource source
) {
}
