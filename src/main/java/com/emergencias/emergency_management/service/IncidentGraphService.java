package com.emergencias.emergency_management.service;

import com.emergencias.emergency_management.exception.InvalidIncidentEventException;
import com.emergencias.emergency_management.messaging.event.IncidentEvent;
import com.emergencias.emergency_management.model.Incident;
import com.emergencias.emergency_management.model.IncidentSource;
import com.emergencias.emergency_management.model.IncidentStatus;
import com.emergencias.emergency_management.model.Zone;
import com.emergencias.emergency_management.repository.DecisionRuleRepository;
import com.emergencias.emergency_management.repository.IncidentRepository;
import com.emergencias.emergency_management.repository.ZoneRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

/**
 * Registers incidents in the graph.
 */
@Service
@RequiredArgsConstructor
public class IncidentGraphService {

    private final IncidentRepository incidentRepository;
    private final ZoneRepository zoneRepository;
    private final DecisionRuleRepository decisionRuleRepository;

    /**
     * Registers the incident described by the event.
     * <p>
     * An event whose id is already registered is ignored and the existing
     * incident is returned untouched. Kafka may deliver an event more than
     * once and clients may resend it, so registering has to be idempotent.
     */
    public IncidentSaveResult save(IncidentEvent event) {
        IncidentEventValidator.validate(event);

        Optional<Incident> existing =
                incidentRepository.findById(event.getId());

        if (existing.isPresent()) {
            return new IncidentSaveResult(existing.get(), false);
        }

        requireApplicableRule(event);

        Zone zone = resolveZone(event);

        Incident incident = toIncident(event, zone, Instant.now());

        incidentRepository.save(incident);

        return new IncidentSaveResult(incident, true);
    }

    /**
     * Rejects incidents that no active rule covers, such as an unknown type
     * or a severity outside the scale of its type. Accepting them would
     * register incidents that silently never get a recommendation. Valid
     * types and severities come from the rules in the graph, so new kinds of
     * emergency only need new rules.
     */
    public void requireApplicableRule(IncidentEvent event) {
        if (!decisionRuleRepository.existsActiveRule(
                event.getType(), event.getSeverity())) {

            throw new InvalidIncidentEventException(
                    "No hay ninguna regla activa para " + event.getType()
                            + " con gravedad " + event.getSeverity());
        }
    }

    public void markNotified(String incidentId) {
        incidentRepository.markNotified(incidentId);
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
        incident.setNotified(false);

        return incident;
    }

    private Zone resolveZone(IncidentEvent event) {

        if (event.hasZoneCode()) {
            return zoneRepository.findById(event.getZoneCode())
                    .orElseThrow(() -> new InvalidIncidentEventException(
                            "No existe la zona " + event.getZoneCode()));
        }

        return zoneRepository
                .findNearest(event.getLatitude(), event.getLongitude())
                .orElseThrow(() -> new InvalidIncidentEventException(
                        "No hay ninguna zona con coordenadas"));
    }
}
