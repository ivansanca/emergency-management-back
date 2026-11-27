package com.emergencias.emergency_management.service;

import com.emergencias.emergency_management.config.GroupingProperties;
import com.emergencias.emergency_management.dto.NearbyIncident;
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
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * Registers incidents in the graph.
 * <p>
 * Every event is stored as a {@code Detection} of an incident. A new incident
 * starts with its first detection. An automatic detection (satellite,
 * sensor) close in space and time to an open incident of the same type is
 * added to it instead of opening another one: a fire is seen by several
 * satellites and passes, and without grouping each one would be a separate
 * incident. Manual reports always open their own incident, because the
 * operator knows what they are reporting.
 */
@Service
@RequiredArgsConstructor
public class IncidentGraphService {

    private final IncidentRepository incidentRepository;
    private final ZoneRepository zoneRepository;
    private final DecisionRuleRepository decisionRuleRepository;
    private final GroupingProperties groupingProperties;

    /**
     * Registers the event as a new incident or as one more detection of an
     * open incident.
     * <p>
     * An event whose id is already registered, as an incident or as a
     * detection, is ignored and its incident is returned untouched. Kafka may
     * deliver an event more than once and clients may resend it, so
     * registering has to be idempotent. Keeping the id of every detection also
     * stops a hotspot fetched again after its incident was closed from opening
     * a new one.
     * <p>
     * The incident and its first detection are written in one transaction.
     * Registrations are serialised with a lock in the graph: automatic and
     * manual events have separate consumers, and without the lock a report
     * and a hotspot of the same fire processed at once could open two
     * incidents. The lock is held for a few milliseconds, the length of one
     * registration.
     * <p>
     * Repeated events are recognised before taking the lock, so that they do
     * not wait for it: after a restart or an outage most events are
     * duplicates. They are checked again once the lock is taken, in case
     * another consumer registered the same event while this one waited;
     * otherwise saving the incident would overwrite the one already stored.
     */
    @Transactional
    public IncidentSaveResult save(IncidentEvent event) {
        IncidentEventValidator.validate(event);

        Optional<Incident> existing = findRegistered(event.getId());
        if (existing.isPresent()) {
            return IncidentSaveResult.existing(existing.get());
        }

        incidentRepository.lockRegistrations();

        existing = findRegistered(event.getId());
        if (existing.isPresent()) {
            return IncidentSaveResult.existing(existing.get());
        }

        requireApplicableRule(event);

        Instant receivedAt = Instant.now();
        Instant observedAt = event.getOccurredAt() != null
                ? event.getOccurredAt()
                : receivedAt;

        Optional<Incident> open = incidentToJoin(event, observedAt);
        if (open.isPresent()) {
            Incident incident = open.get();
            incident.setDetectionCount(
                    recordDetection(incident.getId(), event, observedAt, receivedAt));
            return IncidentSaveResult.grouped(incident);
        }

        Zone zone = resolveZone(event);

        Incident incident = toIncident(event, zone, observedAt, receivedAt);

        incidentRepository.save(incident);
        incident.setDetectionCount(
                recordDetection(incident.getId(), event, observedAt, receivedAt));

        return IncidentSaveResult.created(incident);
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

    /**
     * Open incident of the same type close to a new report of an operator,
     * within the grouping radius and time window, the nearest if there are
     * several. Reports are never grouped automatically: the API uses this to
     * ask the operator whether the report is the same emergency before
     * opening a second incident. Reports without coordinates, and types
     * without a radius, are not checked. Neither is a report sent again
     * after it was registered: otherwise a client retrying its request would
     * be told that its own incident is nearby.
     */
    @Transactional(readOnly = true)
    public Optional<NearbyIncident> findNearbyOpenIncident(IncidentEvent event) {
        if (!event.hasCoordinates()
                || (event.getId() != null && findRegistered(event.getId()).isPresent())) {
            return Optional.empty();
        }

        Instant observedAt = event.getOccurredAt() != null
                ? event.getOccurredAt()
                : Instant.now();

        return findIncidentNear(event, observedAt)
                .map(incident -> toNearby(incident, event));
    }

    /** Incident that holds the event, as its incident or as a detection. */
    private Optional<Incident> findRegistered(String eventId) {
        return incidentRepository.findById(eventId)
                .or(() -> incidentRepository.findIncidentIdByDetectionId(eventId)
                        .flatMap(incidentRepository::findById));
    }

    /**
     * Incident an automatic detection joins. Only automatic detections with
     * coordinates are grouped.
     */
    private Optional<Incident> incidentToJoin(IncidentEvent event, Instant observedAt) {
        if (!isAutomatic(event) || !event.hasCoordinates()) {
            return Optional.empty();
        }

        return findIncidentNear(event, observedAt);
    }

    /**
     * Open incident of the same type with a detection within the radius of
     * the type and the time window, the nearest if there are several.
     */
    private Optional<Incident> findIncidentNear(IncidentEvent event, Instant observedAt) {
        return groupingProperties.radiusFor(event.getType())
                .flatMap(radius -> incidentRepository.findIncidentToJoin(
                        event.getType(),
                        event.getLatitude(),
                        event.getLongitude(),
                        radius,
                        observedAt.minus(groupingProperties.getWindow()),
                        observedAt.plus(groupingProperties.getWindow())))
                .flatMap(incidentRepository::findById);
    }

    private NearbyIncident toNearby(Incident incident, IncidentEvent event) {
        double distance = incidentRepository.distanceToIncident(
                        incident.getId(), event.getLatitude(), event.getLongitude())
                .orElse(0.0);

        return NearbyIncident.builder()
                .incidentId(incident.getId())
                .type(incident.getType())
                .severity(incident.getSeverity())
                .source(incident.getSource())
                .detectionCount(incident.getDetectionCount() == null
                        ? 0 : incident.getDetectionCount())
                .lastDetectedAt(incident.getLastDetectedAt() != null
                        ? incident.getLastDetectedAt()
                        : incident.getOccurredAt())
                .distanceMeters(Math.round(distance))
                .build();
    }

    private static boolean isAutomatic(IncidentEvent event) {
        return event.getSource() != null && event.getSource() != IncidentSource.MANUAL;
    }

    private long recordDetection(
            String incidentId,
            IncidentEvent event,
            Instant observedAt,
            Instant receivedAt) {

        Long count = incidentRepository.addDetection(
                incidentId,
                event.getId(),
                event.getLatitude(),
                event.getLongitude(),
                observedAt,
                receivedAt,
                sourceOf(event).name());

        return count == null ? 0 : count;
    }

    private static IncidentSource sourceOf(IncidentEvent event) {
        return event.getSource() != null ? event.getSource() : IncidentSource.MANUAL;
    }

    private Incident toIncident(
            IncidentEvent event,
            Zone zone,
            Instant observedAt,
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
        incident.setOccurredAt(observedAt);
        incident.setSource(sourceOf(event));
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
