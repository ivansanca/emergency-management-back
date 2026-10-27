package com.emergencias.emergency_management.service;

import com.emergencias.emergency_management.config.DecisionProperties;
import com.emergencias.emergency_management.dto.DecisionResponse;
import com.emergencias.emergency_management.dto.ResourceRecommendation;
import com.emergencias.emergency_management.model.Incident;
import com.emergencias.emergency_management.repository.DecisionRuleRepository;
import com.emergencias.emergency_management.repository.IncidentRepository;
import com.emergencias.emergency_management.repository.ResourceRepository;
import com.emergencias.emergency_management.repository.projection.ApplicableRuleRow;
import com.emergencias.emergency_management.repository.projection.ResourceCandidateRow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DecisionServiceTest {

    @Mock
    private DecisionProperties decisionProperties;

    @Mock
    private IncidentRepository incidentRepository;

    @Mock
    private ResourceRepository resourceRepository;

    @Mock
    private DecisionRuleRepository decisionRuleRepository;

    @InjectMocks
    private DecisionService decisionService;

    @Test
    void recommendThrowsNotFoundWhenIncidentDoesNotExist() {
        when(incidentRepository.findById("incident-1"))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> decisionService.recommend("incident-1"));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        verifyNoInteractions(decisionRuleRepository, resourceRepository);
    }

    @Test
    void recommendReturnsEmptyRecommendationsWhenThereAreNoApplicableRules() {
        Incident incident = new Incident(
                "incident-1", "FIRE", 3, "Description", null);
        when(incidentRepository.findById("incident-1"))
                .thenReturn(Optional.of(incident));
        when(decisionRuleRepository.findApplicableRules("incident-1"))
                .thenReturn(List.of());

        DecisionResponse response = decisionService.recommend("incident-1");

        assertEquals("incident-1", response.getIncidentId());
        assertEquals("FIRE", response.getIncidentType());
        assertEquals(3, response.getSeverity());
        assertEquals(
                "No hay una regla activa para este tipo de incidente y gravedad.",
                response.getRuleApplied());
        assertEquals(List.of(), response.getRecommendations());
        verifyNoInteractions(resourceRepository, decisionProperties);
    }

    @Test
    void recommendSelectsHighestPriorityRuleAndMapsResourceCandidates() {
        Incident incident = new Incident(
                "incident-1", "FIRE", 3, "Description", null);
        when(incidentRepository.findById("incident-1"))
                .thenReturn(Optional.of(incident));
        when(decisionRuleRepository.findApplicableRules("incident-1"))
                .thenReturn(List.of(
                        new ApplicableRuleRow(
                                "rule-z", "Low priority", "Low", 1, "Truck"),
                        new ApplicableRuleRow(
                                "rule-a", "Other tie", "Other", 10, "Other"),
                        new ApplicableRuleRow(
                                "rule-b", "Selected rule", "Dispatch", 10, "Fire"),
                        new ApplicableRuleRow(
                                "rule-b", "Selected rule", "Dispatch", 10, "Ambulance"),
                        new ApplicableRuleRow(
                                "rule-b", "Selected rule", "Dispatch", 10, "Fire")));
        when(decisionProperties.getMaxDistanceKm()).thenReturn(25.0);
        when(decisionProperties.getMaxTravelTimeMinutes()).thenReturn(40.0);
        when(resourceRepository.findAvailableForIncident(
                "incident-1", List.of("Ambulance", "Fire"), 25.0, 40.0))
                .thenReturn(List.of(
                        new ResourceCandidateRow(
                                "resource-1",
                                "Fire engine",
                                "Fire",
                                true,
                                "North",
                                5.2,
                                12.0,
                                List.of("Origin", "Junction", "North")),
                        new ResourceCandidateRow(
                                "resource-2",
                                "Ambulance",
                                "Ambulance",
                                true,
                                "South",
                                2.0,
                                6.0,
                                null)));

        DecisionResponse response = decisionService.recommend("incident-1");

        assertEquals(
                "Selected rule: Dispatch Tipos admitidos: Ambulance, Fire",
                response.getRuleApplied());
        assertEquals(2, response.getRecommendations().size());

        ResourceRecommendation first = response.getRecommendations().get(0);
        assertEquals("resource-1", first.getResourceId());
        assertEquals("Fire engine", first.getName());
        assertEquals(List.of("Origin", "Junction", "North"), first.getRoute());
        assertEquals(
                "Ruta: Origin → Junction → North. Distancia estimada: 5.2 km. "
                        + "Tiempo estimado: 12 min. Recurso disponible y compatible "
                        + "con la regla Selected rule.",
                first.getReason());

        ResourceRecommendation second = response.getRecommendations().get(1);
        assertEquals("resource-2", second.getResourceId());
        assertNull(second.getRoute());
        assertTrue(second.getReason().contains("Ruta: South."));

        verify(resourceRepository).findAvailableForIncident(
                "incident-1", List.of("Ambulance", "Fire"), 25.0, 40.0);
    }
}
