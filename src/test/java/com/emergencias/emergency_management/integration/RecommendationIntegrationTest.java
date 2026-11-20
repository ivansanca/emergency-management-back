package com.emergencias.emergency_management.integration;

import com.emergencias.emergency_management.dto.DecisionResponse;
import com.emergencias.emergency_management.dto.ResourceRecommendation;
import com.emergencias.emergency_management.dto.ResourceShortfall;
import com.emergencias.emergency_management.service.DecisionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.neo4j.core.Neo4jClient;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the real recommendation queries over the test road network.
 */
@IntegrationTest
class RecommendationIntegrationTest {

    @Autowired
    private Neo4jClient neo4jClient;

    @Autowired
    private DecisionService decisionService;

    private TestGraph graph;

    @BeforeEach
    void setUp() {
        graph = new TestGraph(neo4jClient);
        graph.reset();
        graph.incident(TestGraph.INCIDENT_ID, 2);
    }

    private Map<String, ResourceRecommendation> recommend() {
        DecisionResponse response =
                decisionService.recommend(TestGraph.INCIDENT_ID);
        return response.getRecommendations().stream()
                .collect(Collectors.toMap(
                        ResourceRecommendation::getResourceId,
                        Function.identity()));
    }

    @Test
    void recommendsOnlyReachableAvailableResourcesWithinLimits() {
        assertEquals(
                Set.of(
                        "ENGINE-ORIGIN", "ENGINE-TARGET", "TENDER-PASS", "HELI"),
                recommend().keySet());
    }

    @Test
    void resourceInTheSameZoneIsAtDistanceZero() {
        ResourceRecommendation engine = recommend().get("ENGINE-ORIGIN");

        assertEquals(0.0, engine.getDistanceKm());
        assertEquals(0.0, engine.getTravelTimeMinutes());
        assertEquals(List.of("Origen"), engine.getRoute());
    }

    @Test
    void picksTheFastestRouteEvenIfItHasMoreHops() {
        ResourceRecommendation engine = recommend().get("ENGINE-TARGET");

        assertEquals(20.0, engine.getDistanceKm());
        assertEquals(20.0, engine.getTravelTimeMinutes());
        assertEquals(List.of("Origen", "Paso", "Destino"), engine.getRoute());
    }

    @Test
    void aerialResourceIsDispatchedWithoutRoute() {
        ResourceRecommendation helicopter = recommend().get("HELI");

        assertNull(helicopter.getDistanceKm());
        assertNull(helicopter.getTravelTimeMinutes());
        assertTrue(helicopter.getRoute().isEmpty());
    }

    @Test
    void reportsRequirementsThatCannotBeCovered() {
        DecisionResponse response =
                decisionService.recommend(TestGraph.INCIDENT_ID);

        assertEquals(1, response.getShortfalls().size());
        ResourceShortfall shortfall = response.getShortfalls().getFirst();
        assertEquals("RURAL_ENGINE", shortfall.getRequirement());
        assertEquals(3, shortfall.getRequested());
        assertEquals(2, shortfall.getFound());
        assertTrue(response.getRuleApplied().startsWith("Regla de prueba"));
    }

    @Test
    void incidentWithoutRuleGetsNoRecommendation() {
        graph.incident("INC-NO-RULE", 3);

        DecisionResponse response = decisionService.recommend("INC-NO-RULE");

        assertTrue(response.getRecommendations().isEmpty());
        assertTrue(response.getShortfalls().isEmpty());
    }
}
