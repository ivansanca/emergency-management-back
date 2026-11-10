package com.emergencias.emergency_management.service;

import com.emergencias.emergency_management.model.Incident;
import com.emergencias.emergency_management.model.IncidentEvent;
import com.emergencias.emergency_management.repository.IncidentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class IncidentGraphService {

    private final IncidentRepository incidentRepository;

    public void save(IncidentEvent event) {
        validate(event);

        Incident incident = new Incident(event);

        incidentRepository.findById(event.getId())
                .map(Incident::getStatus)
                .filter(Objects::nonNull)
                .ifPresent(incident::setStatus);

        incidentRepository.save(incident);
    }

    private void validate(IncidentEvent event) {

        if (event == null
                || event.getId() == null
                || event.getId().isBlank()
                || event.getType() == null
                || event.getType().isBlank()
                || event.getZone() == null
                || event.getZone().isBlank()
                || event.getSeverity() < 1
                || event.getSeverity() > 5) {

            throw new IllegalArgumentException(
                    "Los datos del incidente no son válidos");
        }
    }
}
