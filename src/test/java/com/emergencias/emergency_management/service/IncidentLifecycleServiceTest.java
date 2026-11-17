package com.emergencias.emergency_management.service;

import com.emergencias.emergency_management.dto.AssignmentResponse;
import com.emergencias.emergency_management.dto.CloseIncidentResponse;
import com.emergencias.emergency_management.messaging.producer.IncidentNotificationProducer;
import com.emergencias.emergency_management.model.EmergencyResource;
import com.emergencias.emergency_management.model.Incident;
import com.emergencias.emergency_management.model.IncidentStatus;
import com.emergencias.emergency_management.model.Zone;
import com.emergencias.emergency_management.repository.IncidentRepository;
import com.emergencias.emergency_management.repository.ResourceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IncidentLifecycleServiceTest {

    @Mock
    private IncidentRepository incidentRepository;

    @Mock
    private ResourceRepository resourceRepository;

    @Mock
    private DecisionService decisionService;

    @Mock
    private IncidentNotificationProducer incidentNotificationProducer;

    @InjectMocks
    private IncidentLifecycleService incidentLifecycleService;

    private static Incident incident(IncidentStatus status) {
        return new Incident(
                "incident-1", "FIRE", 3, "Forest fire", status, null);
    }

    private static EmergencyResource resource(String type, boolean available) {
        return new EmergencyResource(
                "resource-1", "Fire engine", type, available,
                new Zone("39022", "Cillorigo de Liébana", 43.17608, -4.60108));
    }

    @Test
    void assignLinksAvailableCompatibleResource() {
        when(incidentRepository.findById("incident-1"))
                .thenReturn(Optional.of(incident(IncidentStatus.ACTIVE)));
        when(resourceRepository.findById("resource-1"))
                .thenReturn(Optional.of(resource("FOREST_ENGINE", true)));
        when(decisionService.eligibleResourceTypes("incident-1"))
                .thenReturn(List.of("FOREST_ENGINE", "WATER_TENDER"));
        when(resourceRepository.assignToIncident("resource-1", "incident-1"))
                .thenReturn(Optional.of("2026-10-31T17:00:00Z"));

        AssignmentResponse response =
                incidentLifecycleService.assign("incident-1", "resource-1");

        assertEquals("incident-1", response.getIncidentId());
        assertEquals("resource-1", response.getResourceId());
        assertEquals("FOREST_ENGINE", response.getResourceType());
        assertEquals("2026-10-31T17:00:00Z", response.getAssignedAt());
        verify(incidentNotificationProducer).resourceAssigned(response);
    }

    @Test
    void assignRejectsClosedIncident() {
        when(incidentRepository.findById("incident-1"))
                .thenReturn(Optional.of(incident(IncidentStatus.CLOSED)));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> incidentLifecycleService.assign(
                        "incident-1", "resource-1"));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verify(resourceRepository, never())
                .assignToIncident(anyString(), anyString());
    }

    @Test
    void assignRejectsUnavailableResource() {
        when(incidentRepository.findById("incident-1"))
                .thenReturn(Optional.of(incident(IncidentStatus.ACTIVE)));
        when(resourceRepository.findById("resource-1"))
                .thenReturn(Optional.of(resource("FOREST_ENGINE", false)));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> incidentLifecycleService.assign(
                        "incident-1", "resource-1"));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verify(resourceRepository, never())
                .assignToIncident(anyString(), anyString());
    }

    @Test
    void assignRejectsIncompatibleResourceType() {
        when(incidentRepository.findById("incident-1"))
                .thenReturn(Optional.of(incident(IncidentStatus.ACTIVE)));
        when(resourceRepository.findById("resource-1"))
                .thenReturn(Optional.of(resource("AMBULANCE", true)));
        when(decisionService.eligibleResourceTypes("incident-1"))
                .thenReturn(List.of("FOREST_ENGINE"));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> incidentLifecycleService.assign(
                        "incident-1", "resource-1"));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verify(resourceRepository, never())
                .assignToIncident(anyString(), anyString());
    }

    @Test
    void closeMarksIncidentClosedAndReleasesResources() {
        when(incidentRepository.findById("incident-1"))
                .thenReturn(Optional.of(incident(IncidentStatus.ACTIVE)));
        when(incidentRepository.closeAndReleaseResources("incident-1"))
                .thenReturn(2L);

        CloseIncidentResponse response =
                incidentLifecycleService.close("incident-1");

        assertEquals("CLOSED", response.getStatus());
        assertEquals(2, response.getReleasedResources());
        verify(incidentNotificationProducer).incidentClosed(response);
    }

    @Test
    void closeRejectsAlreadyClosedIncident() {
        when(incidentRepository.findById("incident-1"))
                .thenReturn(Optional.of(incident(IncidentStatus.CLOSED)));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> incidentLifecycleService.close("incident-1"));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verify(incidentRepository, never())
                .closeAndReleaseResources(anyString());
        verify(incidentNotificationProducer, never()).incidentClosed(any());
    }
}
