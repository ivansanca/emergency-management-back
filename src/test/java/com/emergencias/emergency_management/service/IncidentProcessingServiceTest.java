package com.emergencias.emergency_management.service;

import com.emergencias.emergency_management.dto.DecisionResponse;
import com.emergencias.emergency_management.messaging.event.IncidentEvent;
import com.emergencias.emergency_management.messaging.producer.IncidentNotificationProducer;
import com.emergencias.emergency_management.model.Incident;
import com.emergencias.emergency_management.model.IncidentStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IncidentProcessingServiceTest {

    private static final IncidentEvent EVENT = IncidentEvent.builder()
            .id("incident-1")
            .type("FOREST_FIRE")
            .severity(2)
            .zoneCode("39014")
            .build();

    private static final Incident INCIDENT = new Incident(
            "incident-1", "FOREST_FIRE", 2, null, IncidentStatus.ACTIVE, null);

    @Mock
    private IncidentGraphService incidentGraphService;

    @Mock
    private DecisionService decisionService;

    @Mock
    private IncidentNotificationProducer incidentNotificationProducer;

    @InjectMocks
    private IncidentProcessingService incidentProcessingService;

    @Test
    void newIncidentIsNotifiedWithItsRecommendation() {
        DecisionResponse decision =
                DecisionResponse.builder().incidentId("incident-1").build();
        when(incidentGraphService.save(EVENT))
                .thenReturn(new IncidentSaveResult(INCIDENT, true));
        when(decisionService.recommend("incident-1")).thenReturn(decision);

        incidentProcessingService.process(EVENT);

        verify(incidentNotificationProducer).incidentCreated(INCIDENT);
        verify(incidentNotificationProducer).incidentRecommendation(decision);
    }

    @Test
    void existingIncidentIsUpdatedWithoutNotifying() {
        when(incidentGraphService.save(EVENT))
                .thenReturn(new IncidentSaveResult(INCIDENT, false));

        incidentProcessingService.process(EVENT);

        verifyNoInteractions(incidentNotificationProducer, decisionService);
    }

    @Test
    void recommendationFailureDoesNotUndoTheRegistration() {
        when(incidentGraphService.save(EVENT))
                .thenReturn(new IncidentSaveResult(INCIDENT, true));
        when(decisionService.recommend("incident-1"))
                .thenThrow(new IllegalStateException("Neo4j no disponible"));

        assertDoesNotThrow(() -> incidentProcessingService.process(EVENT));

        verify(incidentNotificationProducer).incidentCreated(INCIDENT);
        verify(incidentNotificationProducer, never())
                .incidentRecommendation(any());
    }
}
