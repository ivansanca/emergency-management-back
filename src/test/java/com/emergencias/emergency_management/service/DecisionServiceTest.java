package com.emergencias.emergency_management.service;

import com.emergencias.emergency_management.config.DecisionProperties;
import com.emergencias.emergency_management.dto.DecisionResponse;
import com.emergencias.emergency_management.dto.ResourceRecommendation;
import com.emergencias.emergency_management.dto.ResourceShortfall;
import com.emergencias.emergency_management.exception.NotFoundException;
import com.emergencias.emergency_management.model.Incident;
import com.emergencias.emergency_management.model.IncidentStatus;
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

    private void givenIncident() {
        Incident incident = new Incident(
                "incident-1", "FOREST_FIRE", 2, "Description",
                IncidentStatus.ACTIVE, null);
        when(incidentRepository.findById("incident-1"))
                .thenReturn(Optional.of(incident));
    }

    private void givenLimits() {
        when(decisionProperties.getMaxDistanceKm()).thenReturn(60.0);
        when(decisionProperties.getMaxTravelTimeMinutes()).thenReturn(60.0);
    }

    private static ApplicableRuleRow row(
            String ruleId, int priority, String type, String group, int quantity) {
        return new ApplicableRuleRow(
                ruleId, "Rule " + ruleId, "Description.",
                priority, type, group, quantity);
    }

    private static ResourceCandidateRow ground(
            String id, String type, double km, double minutes) {
        return new ResourceCandidateRow(
                id, "Name " + id, type, true, "Zone " + id,
                km, minutes, List.of("Origin", "Zone " + id));
    }

    @Test
    void recommendThrowsNotFoundWhenIncidentDoesNotExist() {
        when(incidentRepository.findById("incident-1"))
                .thenReturn(Optional.empty());

        assertThrows(
                NotFoundException.class,
                () -> decisionService.recommend("incident-1"));

        verifyNoInteractions(decisionRuleRepository, resourceRepository);
    }

    @Test
    void recommendReturnsEmptyRecommendationsWhenThereAreNoApplicableRules() {
        givenIncident();
        when(decisionRuleRepository.findApplicableRules("incident-1"))
                .thenReturn(List.of());

        DecisionResponse response = decisionService.recommend("incident-1");

        assertEquals("incident-1", response.getIncidentId());
        assertEquals("FOREST_FIRE", response.getIncidentType());
        assertEquals(2, response.getSeverity());
        assertEquals(
                "No hay una regla activa para este tipo de incidente y gravedad.",
                response.getRuleApplied());
        assertEquals(List.of(), response.getRecommendations());
        assertEquals(List.of(), response.getShortfalls());
        verifyNoInteractions(resourceRepository, decisionProperties);
    }

    @Test
    void recommendUsesHighestPriorityRuleAndMapsGroundCandidates() {
        givenIncident();
        givenLimits();
        when(decisionRuleRepository.findApplicableRules("incident-1"))
                .thenReturn(List.of(
                        row("rule-z", 1, "TRUCK", "TRUCK", 1),
                        row("rule-a", 10, "OTHER", "OTHER", 1),
                        row("rule-b", 10, "RURAL_ENGINE", "RURAL_ENGINE", 1),
                        row("rule-b", 10, "WATER_TENDER", "WATER_TENDER", 1)));
        when(resourceRepository.findAvailableForIncident(
                "incident-1", List.of("RURAL_ENGINE", "WATER_TENDER"), 60.0, 60.0))
                .thenReturn(List.of(
                        ground("BRP-21", "RURAL_ENGINE", 24.53, 28.5),
                        ground("BCA-61", "WATER_TENDER", 40.549999999, 44.2)));
        when(resourceRepository.findAvailableAerialResources(
                List.of("RURAL_ENGINE", "WATER_TENDER")))
                .thenReturn(List.of());

        DecisionResponse response = decisionService.recommend("incident-1");

        assertEquals(
                "Rule rule-b: Description. Necesidades: "
                        + "1 x RURAL_ENGINE, 1 x WATER_TENDER",
                response.getRuleApplied());
        assertEquals(2, response.getRecommendations().size());

        ResourceRecommendation first = response.getRecommendations().get(0);
        assertEquals("BRP-21", first.getResourceId());
        assertEquals(24.5, first.getDistanceKm());
        assertEquals(
                "Ruta: Origin → Zone BRP-21. Distancia estimada: 24.5 km. "
                        + "Tiempo estimado: 29 min. Recurso disponible y "
                        + "compatible con la regla Rule rule-b.",
                first.getReason());

        ResourceRecommendation second = response.getRecommendations().get(1);
        assertEquals(40.5, second.getDistanceKm());
        assertEquals(List.of(), response.getShortfalls());
    }

    @Test
    void recommendPicksNearestPerRequirementAndReportsShortfalls() {
        givenIncident();
        givenLimits();
        when(decisionRuleRepository.findApplicableRules("incident-1"))
                .thenReturn(List.of(
                        row("rule-1", 100, "FOREST_ENGINE", "ENGINES", 2),
                        row("rule-1", 100, "RURAL_ENGINE", "ENGINES", 2),
                        row("rule-1", 100, "WATER_TENDER", "WATER_TENDER", 2)));
        List<String> types =
                List.of("FOREST_ENGINE", "RURAL_ENGINE", "WATER_TENDER");
        when(resourceRepository.findAvailableForIncident(
                "incident-1", types, 60.0, 60.0))
                .thenReturn(List.of(
                        ground("R1", "RURAL_ENGINE", 10, 10),
                        ground("F1", "FOREST_ENGINE", 20, 20),
                        ground("W1", "WATER_TENDER", 25, 25),
                        ground("R2", "RURAL_ENGINE", 30, 30)));
        when(resourceRepository.findAvailableAerialResources(types))
                .thenReturn(List.of());

        DecisionResponse response = decisionService.recommend("incident-1");

        assertEquals(
                List.of("R1", "F1", "W1"),
                response.getRecommendations().stream()
                        .map(ResourceRecommendation::getResourceId)
                        .toList());

        assertEquals(1, response.getShortfalls().size());
        ResourceShortfall shortfall = response.getShortfalls().getFirst();
        assertEquals("WATER_TENDER", shortfall.getRequirement());
        assertEquals(2, shortfall.getRequested());
        assertEquals(1, shortfall.getFound());
        assertTrue(response.getRuleApplied().contains(
                "2 x FOREST_ENGINE/RURAL_ENGINE"));
    }

    @Test
    void recommendIncludesAerialResourcesWithoutRoute() {
        givenIncident();
        givenLimits();
        when(decisionRuleRepository.findApplicableRules("incident-1"))
                .thenReturn(List.of(
                        row("rule-1", 100, "AERIAL_UNIT", "AERIAL_UNIT", 1)));
        when(resourceRepository.findAvailableForIncident(
                "incident-1", List.of("AERIAL_UNIT"), 60.0, 60.0))
                .thenReturn(List.of());
        when(resourceRepository.findAvailableAerialResources(
                List.of("AERIAL_UNIT")))
                .thenReturn(List.of(new ResourceCandidateRow(
                        "DELTA-ROMEO", "Helicóptero del 112", "AERIAL_UNIT",
                        true, "Santander", null, null, List.of())));

        DecisionResponse response = decisionService.recommend("incident-1");

        ResourceRecommendation helicopter =
                response.getRecommendations().getFirst();
        assertEquals("DELTA-ROMEO", helicopter.getResourceId());
        assertNull(helicopter.getDistanceKm());
        assertNull(helicopter.getTravelTimeMinutes());
        assertTrue(helicopter.getReason().startsWith(
                "Medio aéreo con base en Santander."));
        assertEquals(List.of(), response.getShortfalls());
        verify(resourceRepository).findAvailableAerialResources(
                List.of("AERIAL_UNIT"));
    }

    @Test
    void ruleWithoutDescriptionIsShownWithoutNull() {
        givenIncident();
        givenLimits();
        when(decisionRuleRepository.findApplicableRules("incident-1"))
                .thenReturn(List.of(new ApplicableRuleRow(
                        "rule-1", "Rule rule-1", null,
                        100, "AERIAL_UNIT", "AERIAL_UNIT", 1)));
        when(resourceRepository.findAvailableForIncident(
                "incident-1", List.of("AERIAL_UNIT"), 60.0, 60.0))
                .thenReturn(List.of());
        when(resourceRepository.findAvailableAerialResources(
                List.of("AERIAL_UNIT")))
                .thenReturn(List.of());

        DecisionResponse response = decisionService.recommend("incident-1");

        assertEquals("Rule rule-1. Necesidades: 1 x AERIAL_UNIT",
                response.getRuleApplied());
    }
}
