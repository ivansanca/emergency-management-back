package com.emergencias.emergency_management.service;

import com.emergencias.emergency_management.model.Incident;
import com.emergencias.emergency_management.model.IncidentEvent;
import com.emergencias.emergency_management.model.IncidentStatus;
import com.emergencias.emergency_management.model.Zone;
import com.emergencias.emergency_management.repository.IncidentRepository;
import com.emergencias.emergency_management.repository.ZoneRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
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

    @InjectMocks
    private IncidentGraphService incidentGraphService;

    @Test
    void saveMapsEventToIncidentAndPersistsIt() {
        when(zoneRepository.findById("39014"))
                .thenReturn(Optional.of(CABUERNIGA));
        IncidentEvent event = new IncidentEvent(
                "incident-1", "FOREST_FIRE", 4, "39014", "Forest fire");

        incidentGraphService.save(event);

        ArgumentCaptor<Incident> incidentCaptor =
                ArgumentCaptor.forClass(Incident.class);
        verify(incidentRepository).save(incidentCaptor.capture());
        Incident savedIncident = incidentCaptor.getValue();

        assertEquals("incident-1", savedIncident.getId());
        assertEquals("FOREST_FIRE", savedIncident.getType());
        assertEquals(4, savedIncident.getSeverity());
        assertEquals("Forest fire", savedIncident.getDescription());
        assertSame(CABUERNIGA, savedIncident.getZone());
        assertEquals(IncidentStatus.ACTIVE, savedIncident.getStatus());
    }

    @Test
    void saveKeepsStatusOfExistingIncident() {
        when(zoneRepository.findById("39014"))
                .thenReturn(Optional.of(CABUERNIGA));
        Incident existing = new Incident(
                "incident-1", "FOREST_FIRE", 4, "Forest fire",
                IncidentStatus.CLOSED, CABUERNIGA);
        when(incidentRepository.findById("incident-1"))
                .thenReturn(Optional.of(existing));

        incidentGraphService.save(new IncidentEvent(
                "incident-1", "FOREST_FIRE", 5, "39014", "Forest fire"));

        ArgumentCaptor<Incident> incidentCaptor =
                ArgumentCaptor.forClass(Incident.class);
        verify(incidentRepository).save(incidentCaptor.capture());
        assertEquals(IncidentStatus.CLOSED,
                incidentCaptor.getValue().getStatus());
        assertEquals(5, incidentCaptor.getValue().getSeverity());
    }

    @Test
    void saveRejectsUnknownZoneWithoutPersisting() {
        when(zoneRepository.findById("99999"))
                .thenReturn(Optional.empty());

        assertThrows(
                IllegalArgumentException.class,
                () -> incidentGraphService.save(new IncidentEvent(
                        "incident-1", "FOREST_FIRE", 3, "99999", null)));

        verify(incidentRepository, never()).save(any());
    }

    @Test
    void saveRejectsInvalidEventsWithoutPersisting() {
        List<IncidentEvent> invalidEvents = List.of(
                new IncidentEvent(null, "FOREST_FIRE", 3, "39014", null),
                new IncidentEvent(" ", "FOREST_FIRE", 3, "39014", null),
                new IncidentEvent("id", null, 3, "39014", null),
                new IncidentEvent("id", " ", 3, "39014", null),
                new IncidentEvent("id", "FOREST_FIRE", 3, null, null),
                new IncidentEvent("id", "FOREST_FIRE", 3, " ", null),
                new IncidentEvent("id", "FOREST_FIRE", 0, "39014", null),
                new IncidentEvent("id", "FOREST_FIRE", 6, "39014", null));

        assertThrows(
                IllegalArgumentException.class,
                () -> incidentGraphService.save(null));
        for (IncidentEvent event : invalidEvents) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> incidentGraphService.save(event));
        }

        verifyNoInteractions(incidentRepository, zoneRepository);
    }
}
