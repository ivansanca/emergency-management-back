package com.emergencias.emergency_management.service;

import com.emergencias.emergency_management.dto.NearbyIncident;
import com.emergencias.emergency_management.config.GroupingProperties;
import com.emergencias.emergency_management.exception.InvalidIncidentEventException;
import com.emergencias.emergency_management.messaging.event.IncidentEvent;
import com.emergencias.emergency_management.model.Incident;
import com.emergencias.emergency_management.model.IncidentSource;
import com.emergencias.emergency_management.model.IncidentStatus;
import com.emergencias.emergency_management.model.Zone;
import com.emergencias.emergency_management.repository.DecisionRuleRepository;
import com.emergencias.emergency_management.repository.IncidentRepository;
import com.emergencias.emergency_management.repository.ZoneRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IncidentGraphServiceTest {

    private static final Zone CABUERNIGA =
            new Zone("39014", "Cabuérniga", 43.2264717, -4.3015564);

    @Mock
    private IncidentRepository incidentRepository;

    @Mock
    private ZoneRepository zoneRepository;

    @Mock
    private DecisionRuleRepository decisionRuleRepository;

    @Spy
    private GroupingProperties groupingProperties = new GroupingProperties();

    @InjectMocks
    private IncidentGraphService incidentGraphService;

    @BeforeEach
    void groupForestFiresWithinTwoKilometres() {
        groupingProperties.getRadiusMeters().put("FOREST_FIRE", 2000.0);
    }

    private void givenRuleFor(String type, int severity) {
        when(decisionRuleRepository.existsActiveRule(type, severity))
                .thenReturn(true);
    }

    private Incident savedIncident() {
        ArgumentCaptor<Incident> incidentCaptor =
                ArgumentCaptor.forClass(Incident.class);
        verify(incidentRepository).save(incidentCaptor.capture());
        return incidentCaptor.getValue();
    }

    @Test
    void saveMapsEventToIncidentAndPersistsIt() {
        givenRuleFor("FOREST_FIRE", 3);
        when(zoneRepository.findById("39014"))
                .thenReturn(Optional.of(CABUERNIGA));
        Instant occurredAt = Instant.parse("2026-08-14T15:30:00Z");

        IncidentSaveResult result =
                incidentGraphService.save(IncidentEvent.builder()
                        .id("incident-1")
                        .type("FOREST_FIRE")
                        .severity(3)
                        .zoneCode("39014")
                        .occurredAt(occurredAt)
                        .source(IncidentSource.FIRMS)
                        .description("Forest fire")
                        .build());

        Incident incident = savedIncident();
        assertEquals("incident-1", incident.getId());
        assertEquals("FOREST_FIRE", incident.getType());
        assertEquals(3, incident.getSeverity());
        assertEquals("Forest fire", incident.getDescription());
        assertSame(CABUERNIGA, incident.getZone());
        assertEquals(IncidentStatus.ACTIVE, incident.getStatus());
        assertEquals(occurredAt, incident.getOccurredAt());
        assertEquals(IncidentSource.FIRMS, incident.getSource());
        assertNotNull(incident.getReceivedAt());
        assertEquals(Boolean.FALSE, incident.getNotified());
        assertTrue(result.created());
        assertSame(incident, result.incident());
    }

    @Test
    void saveResolvesNearestZoneFromCoordinates() {
        givenRuleFor("FOREST_FIRE", 2);
        when(zoneRepository.findNearest(43.2265, -4.3016))
                .thenReturn(Optional.of(CABUERNIGA));

        incidentGraphService.save(IncidentEvent.builder()
                .id("incident-1")
                .type("FOREST_FIRE")
                .severity(2)
                .latitude(43.2265)
                .longitude(-4.3016)
                .build());

        Incident incident = savedIncident();
        assertSame(CABUERNIGA, incident.getZone());
        assertEquals(43.2265, incident.getLatitude());
        assertEquals(-4.3016, incident.getLongitude());
    }

    @Test
    void saveUsesZoneCodeBeforeCoordinates() {
        givenRuleFor("FOREST_FIRE", 2);
        when(zoneRepository.findById("39014"))
                .thenReturn(Optional.of(CABUERNIGA));

        incidentGraphService.save(IncidentEvent.builder()
                .id("incident-1")
                .type("FOREST_FIRE")
                .severity(2)
                .zoneCode("39014")
                .latitude(43.3000)
                .longitude(-4.0000)
                .build());

        Incident incident = savedIncident();
        assertSame(CABUERNIGA, incident.getZone());
        assertEquals(43.3000, incident.getLatitude());
        verify(zoneRepository, never()).findNearest(anyDouble(), anyDouble());
    }

    @Test
    void saveAppliesDefaultsWhenOptionalFieldsAreMissing() {
        givenRuleFor("FOREST_FIRE", 1);
        when(zoneRepository.findById("39014"))
                .thenReturn(Optional.of(CABUERNIGA));

        incidentGraphService.save(IncidentEvent.builder()
                .id("incident-1")
                .type("FOREST_FIRE")
                .severity(1)
                .zoneCode("39014")
                .build());

        Incident incident = savedIncident();
        assertEquals(IncidentSource.MANUAL, incident.getSource());
        assertNotNull(incident.getReceivedAt());
        assertEquals(incident.getReceivedAt(), incident.getOccurredAt());
    }

    @Test
    void saveIgnoresEventOfExistingIncident() {
        Incident existing = new Incident(
                "incident-1", "FOREST_FIRE", 2, "Forest fire",
                IncidentStatus.CLOSED, CABUERNIGA);
        when(incidentRepository.findById("incident-1"))
                .thenReturn(Optional.of(existing));

        IncidentSaveResult result =
                incidentGraphService.save(IncidentEvent.builder()
                        .id("incident-1")
                        .type("FOREST_FIRE")
                        .severity(3)
                        .zoneCode("39014")
                        .build());

        assertFalse(result.created());
        assertSame(existing, result.incident());
        assertEquals(2, existing.getSeverity());
        verify(incidentRepository, never()).save(any());
        verify(incidentRepository, never()).lockRegistrations();
        verifyNoInteractions(zoneRepository);
    }

    @Test
    void saveRejectsUnknownZoneWithoutPersisting() {
        givenRuleFor("FOREST_FIRE", 3);
        when(zoneRepository.findById("99999"))
                .thenReturn(Optional.empty());

        assertThrows(
                InvalidIncidentEventException.class,
                () -> incidentGraphService.save(IncidentEvent.builder()
                        .id("incident-1")
                        .type("FOREST_FIRE")
                        .severity(3)
                        .zoneCode("99999")
                        .build()));

        verify(incidentRepository, never()).save(any());
    }

    @Test
    void saveRejectsInvalidEventWithoutPersisting() {
        assertThrows(
                InvalidIncidentEventException.class,
                () -> incidentGraphService.save(IncidentEvent.builder()
                        .id("incident-1")
                        .type("FOREST_FIRE")
                        .severity(3)
                        .build()));

        verifyNoInteractions(incidentRepository, zoneRepository);
    }

    @Test
    void saveRejectsEventWithoutApplicableRuleWithoutPersisting() {
        assertThrows(
                InvalidIncidentEventException.class,
                () -> incidentGraphService.save(IncidentEvent.builder()
                        .id("incident-1")
                        .type("FOREST_FIRE")
                        .severity(4)
                        .zoneCode("39014")
                        .build()));

        verify(incidentRepository, never()).save(any());
        verifyNoInteractions(zoneRepository);
    }

    @Test
    void markNotifiedUpdatesTheIncident() {
        incidentGraphService.markNotified("incident-1");

        verify(incidentRepository).markNotified("incident-1");
    }

    /** findById answers for this incident only: other ids are new events. */
    private void givenOnlyIncident(Incident incident) {
        when(incidentRepository.findById(anyString())).thenAnswer(call ->
                incident.getId().equals(call.getArgument(0))
                        ? Optional.of(incident)
                        : Optional.empty());
    }

    private static IncidentEvent hotspot(String id) {
        return IncidentEvent.builder()
                .id(id)
                .type("FOREST_FIRE")
                .severity(0)
                .latitude(43.2265)
                .longitude(-4.3016)
                .occurredAt(Instant.parse("2026-03-21T02:00:00Z"))
                .source(IncidentSource.FIRMS)
                .build();
    }

    @Test
    void newIncidentStoresItsFirstDetection() {
        givenRuleFor("FOREST_FIRE", 0);
        when(zoneRepository.findNearest(43.2265, -4.3016))
                .thenReturn(Optional.of(CABUERNIGA));
        when(incidentRepository.addDetection(eq("H-1"), eq("H-1"), eq(43.2265),
                eq(-4.3016), any(), any(), eq("FIRMS"))).thenReturn(1L);

        IncidentSaveResult result = incidentGraphService.save(hotspot("H-1"));

        assertTrue(result.created());
        assertEquals(Long.valueOf(1), result.incident().getDetectionCount());
    }

    @Test
    void registrationTakesTheLockBeforeLookingForAnIncidentToJoin() {
        givenRuleFor("FOREST_FIRE", 0);
        when(zoneRepository.findNearest(43.2265, -4.3016))
                .thenReturn(Optional.of(CABUERNIGA));

        incidentGraphService.save(hotspot("H-1"));

        InOrder order = inOrder(incidentRepository);
        order.verify(incidentRepository).lockRegistrations();
        order.verify(incidentRepository).findIncidentToJoin(anyString(), anyDouble(),
                anyDouble(), anyDouble(), any(), any());
        order.verify(incidentRepository).save(any());
    }

    @Test
    void eventRegisteredWhileWaitingForTheLockIsIgnored() {
        Incident registered = new Incident("H-1", "FOREST_FIRE", 0, null,
                IncidentStatus.ACTIVE, CABUERNIGA);
        when(incidentRepository.findById("H-1"))
                .thenReturn(Optional.empty(), Optional.of(registered));

        IncidentSaveResult result = incidentGraphService.save(hotspot("H-1"));

        assertFalse(result.created());
        assertFalse(result.grouped());
        assertSame(registered, result.incident());
        verify(incidentRepository).lockRegistrations();
        verify(incidentRepository, never()).save(any());
    }

    @Test
    void nearbyOpenIncidentIsReportedWithItsDistance() {
        Incident open = new Incident("FIRMS-1", "FOREST_FIRE", 0, null,
                IncidentStatus.ACTIVE, CABUERNIGA);
        open.setSource(IncidentSource.FIRMS);
        open.setDetectionCount(3L);
        Instant observedAt = Instant.parse("2026-03-21T02:00:00Z");
        when(incidentRepository.findIncidentToJoin("FOREST_FIRE", 43.2265, -4.3016, 2000.0,
                observedAt.minus(Duration.ofHours(24)), observedAt.plus(Duration.ofHours(24))))
                .thenReturn(Optional.of("FIRMS-1"));
        givenOnlyIncident(open);
        when(incidentRepository.distanceToIncident("FIRMS-1", 43.2265, -4.3016))
                .thenReturn(Optional.of(812.4));

        NearbyIncident nearby = incidentGraphService
                .findNearbyOpenIncident(manualReport(observedAt))
                .orElseThrow();

        assertEquals("FIRMS-1", nearby.getIncidentId());
        assertEquals(812, nearby.getDistanceMeters());
        assertEquals(3, nearby.getDetectionCount());
        assertEquals(IncidentSource.FIRMS, nearby.getSource());
    }

    @Test
    void reportWithoutCoordinatesIsNotCheckedForNearbyIncidents() {
        IncidentEvent report = IncidentEvent.builder()
                .id("M-1")
                .type("FOREST_FIRE")
                .severity(2)
                .zoneCode("39014")
                .build();

        assertTrue(incidentGraphService.findNearbyOpenIncident(report).isEmpty());
        verifyNoInteractions(incidentRepository);
    }

    @Test
    void reportSentAgainIsNotCheckedForNearbyIncidents() {
        Incident registered = new Incident("M-1", "FOREST_FIRE", 2, null,
                IncidentStatus.ACTIVE, CABUERNIGA);
        givenOnlyIncident(registered);

        assertTrue(incidentGraphService
                .findNearbyOpenIncident(manualReport(Instant.now()))
                .isEmpty());
        verify(incidentRepository, never()).findIncidentToJoin(
                anyString(), anyDouble(), anyDouble(), anyDouble(), any(), any());
    }

    private static IncidentEvent manualReport(Instant occurredAt) {
        return IncidentEvent.builder()
                .id("M-1")
                .type("FOREST_FIRE")
                .severity(2)
                .latitude(43.2265)
                .longitude(-4.3016)
                .occurredAt(occurredAt)
                .source(IncidentSource.MANUAL)
                .build();
    }

    @Test
    void hotspotNearAnOpenIncidentIsAddedToIt() {
        givenRuleFor("FOREST_FIRE", 0);
        Incident open = new Incident("H-1", "FOREST_FIRE", 0, null,
                IncidentStatus.ACTIVE, CABUERNIGA);
        Instant observedAt = Instant.parse("2026-03-21T02:00:00Z");
        when(incidentRepository.findIncidentToJoin("FOREST_FIRE", 43.2265, -4.3016, 2000.0,
                observedAt.minus(Duration.ofHours(24)), observedAt.plus(Duration.ofHours(24))))
                .thenReturn(Optional.of("H-1"));
        givenOnlyIncident(open);
        when(incidentRepository.addDetection(eq("H-1"), eq("H-2"), any(), any(),
                any(), any(), anyString())).thenReturn(2L);

        IncidentSaveResult result = incidentGraphService.save(hotspot("H-2"));

        assertTrue(result.grouped());
        assertFalse(result.created());
        assertSame(open, result.incident());
        assertEquals(Long.valueOf(2), open.getDetectionCount());
        verify(incidentRepository, never()).save(any());
        verifyNoInteractions(zoneRepository);
    }

    @Test
    void detectionAlreadyAddedToAnIncidentIsIgnored() {
        Incident holder = new Incident("H-1", "FOREST_FIRE", 0, null,
                IncidentStatus.CLOSED, CABUERNIGA);
        when(incidentRepository.findIncidentIdByDetectionId("H-2"))
                .thenReturn(Optional.of("H-1"));
        givenOnlyIncident(holder);

        IncidentSaveResult result = incidentGraphService.save(hotspot("H-2"));

        assertFalse(result.created());
        assertFalse(result.grouped());
        assertSame(holder, result.incident());
        verify(incidentRepository, never()).addDetection(
                any(), any(), any(), any(), any(), any(), any());
        verify(incidentRepository, never()).lockRegistrations();
    }

    @Test
    void manualReportIsNeverGrouped() {
        givenRuleFor("FOREST_FIRE", 0);
        when(zoneRepository.findNearest(43.2265, -4.3016))
                .thenReturn(Optional.of(CABUERNIGA));
        IncidentEvent manual = hotspot("M-1");
        manual.setSource(IncidentSource.MANUAL);

        assertTrue(incidentGraphService.save(manual).created());

        verify(incidentRepository, never()).findIncidentToJoin(
                any(), anyDouble(), anyDouble(), anyDouble(), any(), any());
    }

    @Test
    void typeWithoutRadiusIsNeverGrouped() {
        groupingProperties.getRadiusMeters().clear();
        givenRuleFor("FOREST_FIRE", 0);
        when(zoneRepository.findNearest(43.2265, -4.3016))
                .thenReturn(Optional.of(CABUERNIGA));

        assertTrue(incidentGraphService.save(hotspot("H-1")).created());

        verify(incidentRepository, never()).findIncidentToJoin(
                any(), anyDouble(), anyDouble(), anyDouble(), any(), any());
    }
}
