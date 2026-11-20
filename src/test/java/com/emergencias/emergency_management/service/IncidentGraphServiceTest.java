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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

    @InjectMocks
    private IncidentGraphService incidentGraphService;

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
}
