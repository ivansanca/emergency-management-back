package com.emergencias.emergency_management.service;

import com.emergencias.emergency_management.model.Incident;

/**
 * Outcome of registering an event: the incident, whether it has just been
 * created, and whether the event was added as one more detection of an open
 * incident instead.
 */
public record IncidentSaveResult(Incident incident, boolean created, boolean grouped) {

    public IncidentSaveResult(Incident incident, boolean created) {
        this(incident, created, false);
    }

    public static IncidentSaveResult created(Incident incident) {
        return new IncidentSaveResult(incident, true, false);
    }

    public static IncidentSaveResult existing(Incident incident) {
        return new IncidentSaveResult(incident, false, false);
    }

    public static IncidentSaveResult grouped(Incident incident) {
        return new IncidentSaveResult(incident, false, true);
    }
}
