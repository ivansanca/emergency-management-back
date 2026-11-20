package com.emergencias.emergency_management.service;

import com.emergencias.emergency_management.dto.AssignmentResponse;
import com.emergencias.emergency_management.dto.CloseIncidentResponse;
import com.emergencias.emergency_management.exception.ConflictException;
import com.emergencias.emergency_management.exception.NotFoundException;
import com.emergencias.emergency_management.messaging.producer.IncidentNotificationProducer;
import com.emergencias.emergency_management.model.EmergencyResource;
import com.emergencias.emergency_management.model.Incident;
import com.emergencias.emergency_management.model.IncidentStatus;
import com.emergencias.emergency_management.repository.IncidentRepository;
import com.emergencias.emergency_management.repository.ResourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Decisions an operator takes on an incident: assigning resources and closing
 * it. The system recommends; a person decides.
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

        String assignedAt = resourceRepository
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

    @Transactional
    public CloseIncidentResponse close(String incidentId) {

        Incident incident = findIncident(incidentId);

        if (incident.isClosed()) {
            throw new ConflictException(
                    "El incidente " + incidentId + " ya está cerrado");
        }

        Long released =
                incidentRepository.closeAndReleaseResources(incidentId);

        long releasedResources = released == null ? 0 : released;

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

    private Incident findIncident(String incidentId) {
        return incidentRepository.findById(incidentId)
                .orElseThrow(() -> new NotFoundException(
                        "No existe el incidente " + incidentId));
    }
}
