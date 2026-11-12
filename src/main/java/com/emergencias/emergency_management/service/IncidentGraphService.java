package com.emergencias.emergency_management.service;

import com.emergencias.emergency_management.model.Incident;
import com.emergencias.emergency_management.model.IncidentEvent;
import com.emergencias.emergency_management.model.Zone;
import com.emergencias.emergency_management.repository.IncidentRepository;
import com.emergencias.emergency_management.repository.ZoneRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class IncidentGraphService {

    private final IncidentRepository incidentRepository;
    private final ZoneRepository zoneRepository;

    public void save(IncidentEvent event) {
        IncidentEventValidator.validate(event);

        Zone zone = resolveZone(event);

        Incident incident = new Incident(event, zone, Instant.now());

        incidentRepository.findById(event.getId())
                .map(Incident::getStatus)
                .filter(Objects::nonNull)
                .ifPresent(incident::setStatus);

        incidentRepository.save(incident);
    }

    private Zone resolveZone(IncidentEvent event) {

        if (event.hasZoneCode()) {
            return zoneRepository.findById(event.getZoneCode())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "No existe la zona " + event.getZoneCode()));
        }

        return zoneRepository
                .findNearest(event.getLatitude(), event.getLongitude())
                .orElseThrow(() -> new IllegalArgumentException(
                        "No hay ninguna zona con coordenadas"));
    }
}
