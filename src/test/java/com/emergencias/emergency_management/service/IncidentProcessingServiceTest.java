package com.emergencias.emergency_management.service;

import com.emergencias.emergency_management.dto.DecisionResponse;
import com.emergencias.emergency_management.exception.EventPublishingException;
import com.emergencias.emergency_management.messaging.event.IncidentEvent;
import com.emergencias.emergency_management.messaging.producer.IncidentNotificationProducer;
import com.emergencias.emergency_management.model.Incident;
import com.emergencias.emergency_management.model.IncidentStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
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

    private static final DecisionResponse DECISION =
            DecisionResponse.builder().incidentId("incident-1").build();

    @Mock
    private IncidentGraphService incidentGraphService;

    @Mock
    private DecisionService decisionService;

    @Mock
    private IncidentNotificationProducer incidentNotificationProducer;

    @InjectMocks
    private IncidentProcessingService incidentProcessingService;

    private Incident givenIncident(boolean created, Boolean notified) {
        Incident incident = new Incident(
                "incident-1", "FOREST_FIRE", 2, null,
                IncidentStatus.ACTIVE, null);
        incident.setNotified(notified);
        when(incidentGraphService.save(EVENT))
                .thenReturn(new IncidentSaveResult(incident, created));
        return incident;
    }

    @Test
    void newIncidentIsNotifiedMarkedAndRecommended() {
        Incident incident = givenIncident(true, false);
        when(decisionService.recommend("incident-1")).thenReturn(DECISION);

        incidentProcessingService.process(EVENT);

        InOrder order =
                inOrder(incidentNotificationProducer, incidentGraphService);
        order.verify(incidentNotificationProducer).incidentCreated(incident);
        order.verify(incidentGraphService).markNotified("incident-1");
        verify(incidentNotificationProducer).incidentRecommendation(DECISION);
    }

    @Test
    void duplicateOfNotifiedIncidentIsIgnored() {
        givenIncident(false, true);

        incidentProcessingService.process(EVENT);

        verifyNoInteractions(incidentNotificationProducer, decisionService);
        verify(incidentGraphService, never()).markNotified(anyString());
    }

    @Test
    void duplicateOfIncidentWithoutNotificationFlagIsIgnored() {
        givenIncident(false, null);

        incidentProcessingService.process(EVENT);

        verifyNoInteractions(incidentNotificationProducer, decisionService);
        verify(incidentGraphService, never()).markNotified(anyString());
    }

    @Test
    void redeliveredIncidentPendingNotificationIsNotified() {
        Incident incident = givenIncident(false, false);
        when(decisionService.recommend("incident-1")).thenReturn(DECISION);

        incidentProcessingService.process(EVENT);

        verify(incidentNotificationProducer).incidentCreated(incident);
        verify(incidentGraphService).markNotified("incident-1");
        verify(incidentNotificationProducer).incidentRecommendation(DECISION);
    }

    @Test
    void failedNotificationIsNotMarkedSoThatItCanBeRetried() {
        Incident incident = givenIncident(true, false);
        doThrow(new EventPublishingException(
                "incident-created", new IllegalStateException("broker down")))
                .when(incidentNotificationProducer).incidentCreated(incident);

        assertThrows(
                EventPublishingException.class,
                () -> incidentProcessingService.process(EVENT));

        verify(incidentGraphService, never()).markNotified(anyString());
        verifyNoInteractions(decisionService);
    }

    @Test
    void recommendationFailureDoesNotUndoTheRegistration() {
        Incident incident = givenIncident(true, false);
        when(decisionService.recommend("incident-1"))
                .thenThrow(new IllegalStateException("Neo4j no disponible"));

        assertDoesNotThrow(() -> incidentProcessingService.process(EVENT));

        verify(incidentNotificationProducer).incidentCreated(incident);
        verify(incidentGraphService).markNotified("incident-1");
        verify(incidentNotificationProducer, never())
                .incidentRecommendation(any());
    }

    @Test
    void detectionAddedToAnOpenIncidentIsNotNotifiedAgain() {
        Incident incident = new Incident(
                "incident-1", "FOREST_FIRE", 2, null,
                IncidentStatus.ACTIVE, null);
        when(incidentGraphService.save(EVENT))
                .thenReturn(IncidentSaveResult.grouped(incident));

        incidentProcessingService.process(EVENT);

        verifyNoInteractions(incidentNotificationProducer, decisionService);
        verify(incidentGraphService, never()).markNotified(anyString());
    }
}
