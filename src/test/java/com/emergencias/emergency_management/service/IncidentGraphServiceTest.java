package com.emergencias.emergency_management.service;

import com.emergencias.emergency_management.model.Incident;
import com.emergencias.emergency_management.model.IncidentEvent;
import com.emergencias.emergency_management.model.IncidentStatus;
import com.emergencias.emergency_management.repository.IncidentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IncidentGraphServiceTest {

    @Mock
    private IncidentRepository incidentRepository;

    @InjectMocks
    private IncidentGraphService incidentGraphService;

    @Test
    void saveMapsEventToIncidentAndPersistsIt() {
        IncidentEvent event = new IncidentEvent(
                "incident-1", "FIRE", 4, "North", "Building fire");

        incidentGraphService.save(event);

        ArgumentCaptor<Incident> incidentCaptor =
                ArgumentCaptor.forClass(Incident.class);
        verify(incidentRepository).save(incidentCaptor.capture());
        Incident savedIncident = incidentCaptor.getValue();

        assertEquals("incident-1", savedIncident.getId());
        assertEquals("FIRE", savedIncident.getType());
        assertEquals(4, savedIncident.getSeverity());
        assertEquals("Building fire", savedIncident.getDescription());
        assertEquals("North", savedIncident.getZone().getName());
        assertEquals(IncidentStatus.ACTIVE, savedIncident.getStatus());
    }

    @Test
    void saveKeepsStatusOfExistingIncident() {
        Incident existing = new Incident(
                "incident-1", "FIRE", 4, "Building fire",
                IncidentStatus.CLOSED, null);
        when(incidentRepository.findById("incident-1"))
                .thenReturn(Optional.of(existing));

        incidentGraphService.save(new IncidentEvent(
                "incident-1", "FIRE", 5, "North", "Building fire"));

        ArgumentCaptor<Incident> incidentCaptor =
                ArgumentCaptor.forClass(Incident.class);
        verify(incidentRepository).save(incidentCaptor.capture());
        assertEquals(IncidentStatus.CLOSED,
                incidentCaptor.getValue().getStatus());
        assertEquals(5, incidentCaptor.getValue().getSeverity());
    }

    @Test
    void saveRejectsInvalidEventsWithoutPersisting() {
        List<IncidentEvent> invalidEvents = List.of(
                new IncidentEvent(null, "FIRE", 3, "North", null),
                new IncidentEvent(" ", "FIRE", 3, "North", null),
                new IncidentEvent("id", null, 3, "North", null),
                new IncidentEvent("id", " ", 3, "North", null),
                new IncidentEvent("id", "FIRE", 3, null, null),
                new IncidentEvent("id", "FIRE", 3, " ", null),
                new IncidentEvent("id", "FIRE", 0, "North", null),
                new IncidentEvent("id", "FIRE", 6, "North", null));

        assertThrows(
                IllegalArgumentException.class,
                () -> incidentGraphService.save(null));
        for (IncidentEvent event : invalidEvents) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> incidentGraphService.save(event));
        }

        verifyNoInteractions(incidentRepository);
    }
}
