package com.emergencias.emergency_management.api;

import com.emergencias.emergency_management.api.dto.IncidentAcceptedResponse;
import com.emergencias.emergency_management.exception.InvalidIncidentEventException;
import com.emergencias.emergency_management.messaging.event.IncidentEvent;
import com.emergencias.emergency_management.messaging.producer.IncidentProducer;
import com.emergencias.emergency_management.model.IncidentSource;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class IncidentControllerTest {

    @Mock
    private IncidentProducer incidentProducer;

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
                incidentController.create(event);

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
                incidentController.create(event);

        assertEquals("FIRE-001", response.getBody().getIncidentId());
        assertEquals(IncidentSource.MANUAL, event.getSource());
        verify(incidentProducer).publish(event);
    }

    @Test
    void createRejectsEventWithoutLocation() {
        IncidentEvent event = IncidentEvent.builder()
                .type("FOREST_FIRE")
                .severity(3)
                .build();

        assertThrows(
                InvalidIncidentEventException.class,
                () -> incidentController.create(event));

        verifyNoInteractions(incidentProducer);
    }
}
