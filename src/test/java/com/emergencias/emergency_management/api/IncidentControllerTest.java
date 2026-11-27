package com.emergencias.emergency_management.api;

import com.emergencias.emergency_management.dto.NearbyIncident;
import com.emergencias.emergency_management.exception.NearbyIncidentException;
import java.util.Optional;
import com.emergencias.emergency_management.api.dto.IncidentAcceptedResponse;
import com.emergencias.emergency_management.exception.InvalidIncidentEventException;
import com.emergencias.emergency_management.messaging.event.IncidentEvent;
import com.emergencias.emergency_management.messaging.producer.IncidentProducer;
import com.emergencias.emergency_management.model.IncidentSource;
import com.emergencias.emergency_management.service.IncidentGraphService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class IncidentControllerTest {

    @Mock
    private IncidentProducer incidentProducer;

    @Mock
    private IncidentGraphService incidentGraphService;

    @InjectMocks
    private IncidentController incidentController;

    @Test
    void createGeneratesUuidAndAppliesDefaults() {
        IncidentEvent event = IncidentEvent.builder()
                .type("FOREST_FIRE")
                .severity(3)
                .zoneCode("39014")
                .description("Forest fire")
                .build();

        ResponseEntity<IncidentAcceptedResponse> response =
                incidentController.create(event, false);

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        String generatedId = response.getBody().getIncidentId();
        assertDoesNotThrow(() -> UUID.fromString(generatedId));
        assertEquals(generatedId, event.getId());
        assertEquals(IncidentSource.MANUAL, event.getSource());
        assertEquals(IncidentEvent.CURRENT_SCHEMA_VERSION,
                event.getSchemaVersion());
        verify(incidentProducer).publish(event);
    }

    @Test
    void createKeepsIdAndAlwaysMarksSourceAsManual() {
        IncidentEvent event = IncidentEvent.builder()
                .id("FIRE-001")
                .type("FOREST_FIRE")
                .severity(3)
                .latitude(43.2265)
                .longitude(-4.3016)
                .source(IncidentSource.FIRMS)
                .build();

        ResponseEntity<IncidentAcceptedResponse> response =
                incidentController.create(event, false);

        assertEquals("FIRE-001", response.getBody().getIncidentId());
        assertEquals(IncidentSource.MANUAL, event.getSource());
        verify(incidentProducer).publish(event);
    }

    @Test
    void createAsksForConfirmationWhenAnOpenIncidentIsNear() {
        IncidentEvent event = nearReport();
        when(incidentGraphService.findNearbyOpenIncident(any()))
                .thenReturn(Optional.of(NearbyIncident.builder()
                        .incidentId("FIRMS-1")
                        .type("FOREST_FIRE")
                        .detectionCount(3)
                        .distanceMeters(800)
                        .build()));

        NearbyIncidentException exception = assertThrows(
                NearbyIncidentException.class,
                () -> incidentController.create(event, false));

        assertEquals("FIRMS-1", exception.getNearbyIncident().getIncidentId());
        assertEquals(event.getId(), exception.getReportId());
        assertFalse(exception.getReportId().isBlank());
        verifyNoInteractions(incidentProducer);
    }

    @Test
    void confirmedCreateIsPublishedWithoutLookingForNearbyIncidents() {
        IncidentEvent event = nearReport();

        ResponseEntity<IncidentAcceptedResponse> response =
                incidentController.create(event, true);

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        verify(incidentGraphService, never()).findNearbyOpenIncident(any());
        verify(incidentProducer).publish(event);
    }

    private static IncidentEvent nearReport() {
        return IncidentEvent.builder()
                .type("FOREST_FIRE")
                .severity(2)
                .latitude(43.2265)
                .longitude(-4.3016)
                .build();
    }

    @Test
    void createRejectsEventWithoutLocation() {
        IncidentEvent event = IncidentEvent.builder()
                .type("FOREST_FIRE")
                .severity(3)
                .build();

        assertThrows(
                InvalidIncidentEventException.class,
                () -> incidentController.create(event, false));

        verifyNoInteractions(incidentProducer);
    }

    @Test
    void createRejectsEventWithoutApplicableRule() {
        IncidentEvent event = IncidentEvent.builder()
                .type("FOREST_FIRE")
                .severity(4)
                .zoneCode("39014")
                .build();
        doThrow(new InvalidIncidentEventException("Sin regla"))
                .when(incidentGraphService).requireApplicableRule(event);

        assertThrows(
                InvalidIncidentEventException.class,
                () -> incidentController.create(event, false));

        verifyNoInteractions(incidentProducer);
    }
}
