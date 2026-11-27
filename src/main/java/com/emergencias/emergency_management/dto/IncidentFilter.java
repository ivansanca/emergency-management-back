package com.emergencias.emergency_management.dto;

import com.emergencias.emergency_management.model.IncidentSource;
import com.emergencias.emergency_management.model.IncidentStatus;

import java.time.Instant;

/**
 * Optional filters of the incident list; a missing one does not filter.
 * <p>
 * {@code from} and {@code to} select the incidents active at some point of
 * the interval: those that started before {@code to} and had not finished
 * before {@code from}. An open incident has not finished, so an operator
 * asking for the last six hours also sees a fire that started yesterday and
 * is still open, even if the satellite has not seen it again.
 */
public record IncidentFilter(
        IncidentStatus status,
        String type,
        IncidentSource source,
        String zoneCode,
        Instant from,
        Instant to
) {
}
