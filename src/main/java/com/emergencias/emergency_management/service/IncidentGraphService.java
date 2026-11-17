package com.emergencias.emergency_management.service;

import com.emergencias.emergency_management.messaging.event.IncidentEvent;
import com.emergencias.emergency_management.model.Incident;
import com.emergencias.emergency_management.model.IncidentSource;
import com.emergencias.emergency_management.model.IncidentStatus;
import com.emergencias.emergency_management.model.Zone;
import com.emergencias.emergency_management.repository.IncidentRepository;
import com.emergencias.emergency_management.repository.ZoneRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class IncidentGraphService {

    private final IncidentRepository incidentRepository;
    private final ZoneRepository zoneRepository;

    public IncidentSaveResult save(IncidentEvent event) {
        IncidentEventValidator.validate(event);

        Zone zone = resolveZone(event);

        Incident incident = toIncident(event, zone, Instant.now());

        Optional<Incident> existing =
                incidentRepository.findById(event.getId());

        existing.map(Incident::getStatus)
                .filter(Objects::nonNull)
                .ifPresent(incident::setStatus);

        incidentRepository.save(incident);

        return new IncidentSaveResult(incident, existing.isEmpty());
    }

    private Incident toIncident(
            IncidentEvent event,
            Zone zone,
            Instant receivedAt) {

        Incident incident = new Incident(
                event.getId(),
                event.getType(),
                event.getSeverity(),
                event.getDescription(),
                IncidentStatus.ACTIVE,
                zone);

        incident.setLatitude(event.getLatitude());
        incident.setLongitude(event.getLongitude());
        incident.setReceivedAt(receivedAt);
        incident.setOccurredAt(event.getOccurredAt() != null
                ? event.getOccurredAt()
                : receivedAt);
        incident.setSource(event.getSource() != null
                ? event.getSource()
                : IncidentSource.MANUAL);

        return incident;
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
