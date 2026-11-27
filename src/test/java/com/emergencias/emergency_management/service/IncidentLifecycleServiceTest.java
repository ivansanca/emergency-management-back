package com.emergencias.emergency_management.service;

import com.emergencias.emergency_management.dto.DetectionAddedResponse;
import com.emergencias.emergency_management.exception.InvalidRequestException;
import com.emergencias.emergency_management.exception.NotFoundException;
import com.emergencias.emergency_management.dto.AssignmentResponse;
import com.emergencias.emergency_management.dto.CloseIncidentResponse;
import com.emergencias.emergency_management.exception.ConflictException;
import com.emergencias.emergency_management.messaging.producer.IncidentNotificationProducer;
import com.emergencias.emergency_management.model.EmergencyResource;
import com.emergencias.emergency_management.model.Incident;
import com.emergencias.emergency_management.model.IncidentStatus;
import com.emergencias.emergency_management.model.ResourceType;
import com.emergencias.emergency_management.model.Zone;
import com.emergencias.emergency_management.repository.IncidentRepository;
import com.emergencias.emergency_management.repository.ResourceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verifyNoInteractions;
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
                "resource-1", "Fire engine", new ResourceType(type, false),
                available,
                new Zone("39022", "Cillorigo de Liébana", 43.17608, -4.60108));
    }

    private static final Instant REPORTED_AT = Instant.parse("2026-11-27T10:00:00Z");

    private DetectionAddedResponse addReport(String reportId, Double latitude, Double longitude) {
        return incidentLifecycleService.addReport(
                "incident-1", reportId, latitude, longitude, REPORTED_AT);
    }

    @Test
    void reportIsAddedToTheIncidentAsAManualDetection() {
        when(incidentRepository.findById("incident-1"))
                .thenReturn(Optional.of(incident(IncidentStatus.ACTIVE)));
        when(incidentRepository.findIncidentIdByDetectionId("report-1"))
                .thenReturn(Optional.empty());
        when(incidentRepository.addDetection(eq("incident-1"), eq("report-1"),
                eq(43.2265), eq(-4.3016), eq(REPORTED_AT), any(), eq("MANUAL")))
                .thenReturn(4L);

        DetectionAddedResponse response = addReport("report-1", 43.2265, -4.3016);

        assertTrue(response.isAdded());
        assertEquals("report-1", response.getDetectionId());
        assertEquals(4, response.getDetectionCount());
        verify(incidentRepository).lockRegistrations();
        verifyNoInteractions(incidentNotificationProducer);
    }

    @Test
    void reportWithoutIdOrPositionGetsAnIdAndNoPosition() {
        when(incidentRepository.findById("incident-1"))
                .thenReturn(Optional.of(incident(IncidentStatus.ACTIVE)));
        when(incidentRepository.findIncidentIdByDetectionId(anyString()))
                .thenReturn(Optional.empty());
        when(incidentRepository.addDetection(eq("incident-1"), anyString(),
                isNull(), isNull(), any(), any(), eq("MANUAL")))
                .thenReturn(2L);

        DetectionAddedResponse response = addReport(null, null, null);

        assertTrue(response.isAdded());
        assertFalse(response.getDetectionId().isBlank());
    }

    @Test
    void repeatedReportIsNotAddedTwice() {
        Incident incident = incident(IncidentStatus.ACTIVE);
        incident.setDetectionCount(4L);
        when(incidentRepository.findById("incident-1"))
                .thenReturn(Optional.of(incident));
        when(incidentRepository.findIncidentIdByDetectionId("report-1"))
                .thenReturn(Optional.of("incident-1"));

        DetectionAddedResponse response = addReport("report-1", 43.2265, -4.3016);

        assertFalse(response.isAdded());
        assertEquals(4, response.getDetectionCount());
        verify(incidentRepository, never()).addDetection(
                any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void reportThatBelongsToAnotherIncidentIsAConflict() {
        when(incidentRepository.findById("incident-1"))
                .thenReturn(Optional.of(incident(IncidentStatus.ACTIVE)));
        when(incidentRepository.findIncidentIdByDetectionId("report-1"))
                .thenReturn(Optional.of("incident-2"));

        assertThrows(ConflictException.class,
                () -> addReport("report-1", 43.2265, -4.3016));
    }

    @Test
    void reportForAClosedIncidentIsAConflict() {
        when(incidentRepository.findById("incident-1"))
                .thenReturn(Optional.of(incident(IncidentStatus.CLOSED)));
        when(incidentRepository.findIncidentIdByDetectionId("report-1"))
                .thenReturn(Optional.empty());

        assertThrows(ConflictException.class,
                () -> addReport("report-1", 43.2265, -4.3016));
        verify(incidentRepository, never()).addDetection(
                any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void reportForAnUnknownIncidentIsNotFound() {
        when(incidentRepository.findById("incident-1")).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class,
                () -> addReport("report-1", 43.2265, -4.3016));
    }

    @Test
    void reportWithAnInvalidPositionIsRejected() {
        assertThrows(InvalidRequestException.class,
                () -> addReport("report-1", 43.2265, null));
        assertThrows(InvalidRequestException.class,
                () -> addReport("report-1", 40.4168, -3.7038));   // Madrid

        verifyNoInteractions(incidentRepository);
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
                .thenReturn(Optional.of(Instant.parse("2026-10-31T17:00:00Z")));

        AssignmentResponse response =
                incidentLifecycleService.assign("incident-1", "resource-1");

        assertEquals("incident-1", response.getIncidentId());
        assertEquals("resource-1", response.getResourceId());
        assertEquals("FOREST_ENGINE", response.getResourceType());
        assertEquals(Instant.parse("2026-10-31T17:00:00Z"),
                response.getAssignedAt());
        verify(incidentNotificationProducer).resourceAssigned(response);
    }

    @Test
    void assignRejectsClosedIncident() {
        when(incidentRepository.findById("incident-1"))
                .thenReturn(Optional.of(incident(IncidentStatus.CLOSED)));

        assertThrows(
                ConflictException.class,
                () -> incidentLifecycleService.assign(
                        "incident-1", "resource-1"));

        verify(resourceRepository, never())
                .assignToIncident(anyString(), anyString());
    }

    @Test
    void assignRejectsUnavailableResource() {
        when(incidentRepository.findById("incident-1"))
                .thenReturn(Optional.of(incident(IncidentStatus.ACTIVE)));
        when(resourceRepository.findById("resource-1"))
                .thenReturn(Optional.of(resource("FOREST_ENGINE", false)));

        assertThrows(
                ConflictException.class,
                () -> incidentLifecycleService.assign(
                        "incident-1", "resource-1"));

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

        assertThrows(
                ConflictException.class,
                () -> incidentLifecycleService.assign(
                        "incident-1", "resource-1"));

        verify(resourceRepository, never())
                .assignToIncident(anyString(), anyString());
    }

    @Test
    void closeMarksIncidentClosedAndReleasesResources() {
        when(incidentRepository.findById("incident-1"))
                .thenReturn(Optional.of(incident(IncidentStatus.ACTIVE)));
        when(incidentRepository.closeAndReleaseResources("incident-1"))
                .thenReturn(Optional.of(2L));

        CloseIncidentResponse response =
                incidentLifecycleService.close("incident-1");

        assertEquals("CLOSED", response.getStatus());
        assertEquals(2, response.getReleasedResources());
        verify(incidentNotificationProducer).incidentClosed(response);
    }

    @Test
    void closeRejectsIncidentClosedMeanwhile() {
        when(incidentRepository.findById("incident-1"))
                .thenReturn(Optional.of(incident(IncidentStatus.ACTIVE)));
        when(incidentRepository.closeAndReleaseResources("incident-1"))
                .thenReturn(Optional.empty());

        assertThrows(
                ConflictException.class,
                () -> incidentLifecycleService.close("incident-1"));

        verify(incidentNotificationProducer, never()).incidentClosed(any());
    }

    @Test
    void closeRejectsAlreadyClosedIncident() {
        when(incidentRepository.findById("incident-1"))
                .thenReturn(Optional.of(incident(IncidentStatus.CLOSED)));

        assertThrows(
                ConflictException.class,
                () -> incidentLifecycleService.close("incident-1"));

        verify(incidentRepository, never())
                .closeAndReleaseResources(anyString());
        verify(incidentNotificationProducer, never()).incidentClosed(any());
    }
}
