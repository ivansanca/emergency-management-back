package com.emergencias.emergency_management.service;

import com.emergencias.emergency_management.model.Incident;
import com.emergencias.emergency_management.model.IncidentEvent;
import com.emergencias.emergency_management.model.Zone;
import com.emergencias.emergency_management.repository.IncidentRepository;
import com.emergencias.emergency_management.repository.ZoneRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class IncidentGraphService {

    private final IncidentRepository incidentRepository;
    private final ZoneRepository zoneRepository;

    public void save(IncidentEvent event) {
        validate(event);

        Zone zone = zoneRepository.findById(event.getZoneCode())
                .orElseThrow(() -> new IllegalArgumentException(
                        "No existe la zona " + event.getZoneCode()));

        Incident incident = new Incident(event, zone);

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
                || event.getZoneCode() == null
                || event.getZoneCode().isBlank()
                || event.getSeverity() < 1
                || event.getSeverity() > 5) {

            throw new IllegalArgumentException(
                    "Los datos del incidente no son válidos");
        }
    }
}
