package com.emergencias.emergency_management.service;

import com.emergencias.emergency_management.dto.AssignmentResponse;
import com.emergencias.emergency_management.dto.CloseIncidentResponse;
import com.emergencias.emergency_management.dto.DetectionAddedResponse;
import com.emergencias.emergency_management.exception.ConflictException;
import com.emergencias.emergency_management.exception.InvalidRequestException;
import com.emergencias.emergency_management.exception.NotFoundException;
import com.emergencias.emergency_management.messaging.producer.IncidentNotificationProducer;
import com.emergencias.emergency_management.model.EmergencyResource;
import com.emergencias.emergency_management.model.Incident;
import com.emergencias.emergency_management.model.IncidentSource;
import com.emergencias.emergency_management.model.IncidentStatus;
import com.emergencias.emergency_management.repository.IncidentRepository;
import com.emergencias.emergency_management.repository.ResourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Decisions an operator takes on an incident: assigning resources, adding a
 * report to it and closing it. The system recommends; a person decides.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IncidentLifecycleService {

    private final IncidentRepository incidentRepository;
    private final ResourceRepository resourceRepository;
    private final DecisionService decisionService;
    private final IncidentNotificationProducer incidentNotificationProducer;

    @Transactional
    public AssignmentResponse assign(String incidentId, String resourceId) {

        Incident incident = findIncident(incidentId);

        if (incident.isClosed()) {
            throw new ConflictException(
                    "El incidente " + incidentId + " está cerrado");
        }

        EmergencyResource resource = resourceRepository.findById(resourceId)
                .orElseThrow(() -> new NotFoundException(
                        "No existe el recurso " + resourceId));

        if (!resource.isAvailable()) {
            throw new ConflictException(
                    "El recurso " + resourceId + " no está disponible");
        }

        String resourceType = resource.getResourceType() != null
                ? resource.getResourceType().getName()
                : resource.getType();

        List<String> eligibleTypes =
                decisionService.eligibleResourceTypes(incidentId);

        if (!eligibleTypes.contains(resourceType)) {
            throw new ConflictException(
                    "El tipo de recurso " + resourceType
                            + " no es compatible con el incidente "
                            + incidentId + ". Tipos admitidos: "
                            + eligibleTypes);
        }

        Instant assignedAt = resourceRepository
                .assignToIncident(resourceId, incidentId)
                .orElseThrow(() -> new ConflictException(
                        "El recurso " + resourceId
                                + " ha dejado de estar disponible"));

        log.info("Recurso {} asignado al incidente {}",
                resourceId, incidentId);

        AssignmentResponse response = AssignmentResponse.builder()
                .incidentId(incidentId)
                .resourceId(resourceId)
                .resourceName(resource.getName())
                .resourceType(resourceType)
                .assignedAt(assignedAt)
                .build();

        incidentNotificationProducer.resourceAssigned(response);

        return response;
    }

    /**
     * Adds a report of an operator to an open incident as one more detection,
     * of manual origin, when the operator confirms that it is the same
     * emergency. The incident keeps a record of every report.
     * <p>
     * Nothing is notified and the severity does not change, as when a
     * hotspot joins an incident: the incident was already notified and its
     * severity is set by the operator. The report keeps its id, so sending it
     * again does not add it twice. It takes the same lock as the registration
     * of incidents, so it cannot interleave with a grouping.
     */
    @Transactional
    public DetectionAddedResponse addReport(
            String incidentId,
            String reportId,
            Double latitude,
            Double longitude,
            Instant occurredAt) {

        requireValidPosition(latitude, longitude);

        String detectionId = reportId == null || reportId.isBlank()
                ? UUID.randomUUID().toString()
                : reportId;

        incidentRepository.lockRegistrations();

        Incident incident = findIncident(incidentId);

        Optional<String> holder =
                incidentRepository.findIncidentIdByDetectionId(detectionId);
        if (holder.isPresent()) {
            if (!holder.get().equals(incidentId)) {
                throw new ConflictException("El aviso " + detectionId
                        + " ya pertenece al incidente " + holder.get());
            }
            return DetectionAddedResponse.builder()
                    .incidentId(incidentId)
                    .detectionId(detectionId)
                    .detectionCount(incident.getDetectionCount() == null
                            ? 0 : incident.getDetectionCount())
                    .added(false)
                    .build();
        }

        if (incident.isClosed()) {
            throw new ConflictException(
                    "El incidente " + incidentId + " está cerrado");
        }

        Instant receivedAt = Instant.now();
        Long detectionCount = incidentRepository.addDetection(
                incidentId,
                detectionId,
                latitude,
                longitude,
                occurredAt != null ? occurredAt : receivedAt,
                receivedAt,
                IncidentSource.MANUAL.name());

        log.info("Aviso {} añadido al incidente {}", detectionId, incidentId);

        return DetectionAddedResponse.builder()
                .incidentId(incidentId)
                .detectionId(detectionId)
                .detectionCount(detectionCount == null ? 0 : detectionCount)
                .added(true)
                .build();
    }

    @Transactional
    public CloseIncidentResponse close(String incidentId) {

        Incident incident = findIncident(incidentId);

        if (incident.isClosed()) {
            throw new ConflictException(
                    "El incidente " + incidentId + " ya está cerrado");
        }

        long releasedResources = incidentRepository
                .closeAndReleaseResources(incidentId)
                .orElseThrow(() -> new ConflictException(
                        "El incidente " + incidentId + " ya está cerrado"));

        log.info("Incidente {} cerrado, {} recursos liberados",
                incidentId, releasedResources);

        CloseIncidentResponse response = CloseIncidentResponse.builder()
                .incidentId(incidentId)
                .status(IncidentStatus.CLOSED.name())
                .releasedResources(releasedResources)
                .build();

        incidentNotificationProducer.incidentClosed(response);

        return response;
    }

    /** Both coordinates or none, and inside Cantabria, as for a new incident. */
    private static void requireValidPosition(Double latitude, Double longitude) {
        if ((latitude == null) != (longitude == null)) {
            throw new InvalidRequestException(
                    "Hay que indicar las dos coordenadas o ninguna");
        }
        if (latitude != null
                && !IncidentEventValidator.insideCantabria(latitude, longitude)) {
            throw new InvalidRequestException(
                    "Las coordenadas están fuera de Cantabria");
        }
    }

    private Incident findIncident(String incidentId) {
        return incidentRepository.findById(incidentId)
                .orElseThrow(() -> new NotFoundException(
                        "No existe el incidente " + incidentId));
    }
}
