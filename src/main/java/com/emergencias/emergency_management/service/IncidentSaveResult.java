package com.emergencias.emergency_management.service;

import com.emergencias.emergency_management.model.Incident;

/**
 * Outcome of registering an event: the incident and whether it has just been
 * created or already existed.
 */
public record IncidentSaveResult(Incident incident, boolean created) {
}
