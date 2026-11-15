package com.emergencias.emergency_management.service;

import com.emergencias.emergency_management.model.Incident;

public record IncidentSaveResult(Incident incident, boolean created) {
}
