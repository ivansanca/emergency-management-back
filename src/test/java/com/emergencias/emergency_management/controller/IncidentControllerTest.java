package com.emergencias.emergency_management.controller;

import com.emergencias.emergency_management.dto.IncidentAcceptedResponse;
import com.emergencias.emergency_management.model.IncidentEvent;
import com.emergencias.emergency_management.producer.IncidentProducer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class IncidentControllerTest {

    @Mock
    private IncidentProducer incidentProducer;

    @InjectMocks
    private IncidentController incidentController;

    @Test
    void createGeneratesUuidWhenIdIsMissing() {
        IncidentEvent event = new IncidentEvent(
                null, "FOREST_FIRE", 3, "39014", "Forest fire");

        ResponseEntity<IncidentAcceptedResponse> response =
                incidentController.create(event);

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        String generatedId = response.getBody().getIncidentId();
        assertDoesNotThrow(() -> UUID.fromString(generatedId));
        assertEquals(generatedId, event.getId());
        verify(incidentProducer).publish(event);
    }

    @Test
    void createKeepsIdWhenProvided() {
        IncidentEvent event = new IncidentEvent(
                "FIRE-001", "FOREST_FIRE", 3, "39014", "Forest fire");

        ResponseEntity<IncidentAcceptedResponse> response =
                incidentController.create(event);

        assertEquals("FIRE-001", response.getBody().getIncidentId());
        verify(incidentProducer).publish(event);
    }
}
