package com.emergencias.emergency_management.integration;

import com.emergencias.emergency_management.messaging.event.IncidentEvent;
import com.emergencias.emergency_management.model.IncidentSource;
import com.emergencias.emergency_management.service.IncidentGraphService;
import com.emergencias.emergency_management.service.IncidentSaveResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.neo4j.core.Neo4jClient;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Grouping of automatic detections against a real Neo4j: distances and dates
 * are compared by the Cypher query, so they are not mocked.
 * <p>
 * One degree of latitude is about 111 km, so 0.009 degrees is about 1 km.
 */
@IntegrationTest
class DetectionGroupingIntegrationTest {

    private static final double LATITUDE = 43.40;
    private static final double LONGITUDE = -4.00;
    private static final double ONE_KM = 0.009;
    private static final Instant NOW = Instant.parse("2026-03-21T02:00:00Z");

    @Autowired
    private Neo4jClient neo4jClient;

    @Autowired
    private IncidentGraphService incidentGraphService;

    private TestGraph graph;

    @BeforeEach
    void setUp() {
        graph = new TestGraph(neo4jClient);
        graph.reset();
        // Satellites report forest fires at IGP 0; sensors, structure fires.
        neo4jClient.query("""
                CREATE (:DecisionRule {id: 'RULE-FOREST-0', name: 'Forestal IGP 0',
                    incidentType: 'FOREST_FIRE', minSeverity: 0, maxSeverity: 0,
                    priority: 100, active: true})
                CREATE (:DecisionRule {id: 'RULE-STRUCTURE-1', name: 'Estructural 1',
                    incidentType: 'STRUCTURE_FIRE', minSeverity: 1, maxSeverity: 1,
                    priority: 100, active: true})
                """).run();
    }

    @Test
    void hotspotOneKilometreAwayJoinsTheIncident() {
        IncidentSaveResult first = save(hotspot("H-1", 0, NOW));
        IncidentSaveResult second = save(hotspot("H-2", ONE_KM, NOW.plusSeconds(600)));

        assertTrue(first.created());
        assertTrue(second.grouped());
        assertEquals("H-1", second.incident().getId());
        assertEquals(Long.valueOf(2), second.incident().getDetectionCount());
        assertEquals(1, incidents());
        assertEquals(2, graph.count(
                "MATCH (:Detection)-[:DETECTION_OF]->(:Incident {id: 'H-1'}) RETURN count(*)",
                Map.of()));
    }

    @Test
    void hotspotThreeKilometresAwayOpensAnotherIncident() {
        save(hotspot("H-1", 0, NOW));
        IncidentSaveResult far = save(hotspot("H-2", 3 * ONE_KM, NOW));

        assertTrue(far.created());
        assertEquals(2, incidents());
    }

    @Test
    void distanceCountsFromAnyDetectionSoTheFireCanSpread() {
        save(hotspot("H-1", 0, NOW));
        save(hotspot("H-2", 1.8 * ONE_KM, NOW.plusSeconds(600)));

        // 3.6 km from the first detection, 1.8 km from the second one.
        IncidentSaveResult third = save(hotspot("H-3", 3.6 * ONE_KM, NOW.plusSeconds(1200)));

        assertTrue(third.grouped());
        assertEquals("H-1", third.incident().getId());
    }

    @Test
    void hotspotOutsideTheTimeWindowOpensAnotherIncident() {
        save(hotspot("H-1", 0, NOW));
        IncidentSaveResult later = save(hotspot("H-2", 0, NOW.plus(Duration.ofHours(30))));

        assertTrue(later.created());
    }

    @Test
    void closedIncidentIsNotJoined() {
        save(hotspot("H-1", 0, NOW));
        neo4jClient.query("MATCH (i:Incident {id: 'H-1'}) SET i.status = 'CLOSED'").run();

        assertTrue(save(hotspot("H-2", 0, NOW)).created());
    }

    @Test
    void repeatedHotspotIsIgnoredEvenAfterItsIncidentIsClosed() {
        save(hotspot("H-1", 0, NOW));
        save(hotspot("H-2", ONE_KM, NOW));
        neo4jClient.query("MATCH (i:Incident {id: 'H-1'}) SET i.status = 'CLOSED'").run();

        IncidentSaveResult again = save(hotspot("H-2", ONE_KM, NOW));

        assertFalse(again.created());
        assertFalse(again.grouped());
        assertEquals("H-1", again.incident().getId());
        assertEquals(1, incidents());
    }

    @Test
    void manualReportNearAHotspotOpensItsOwnIncident() {
        save(hotspot("H-1", 0, NOW));

        IncidentSaveResult manual = save(event("M-1", "FOREST_FIRE", 0,
                LATITUDE, LONGITUDE, NOW, IncidentSource.MANUAL));

        assertTrue(manual.created());
    }

    @Test
    void hotspotNearAManualReportJoinsIt() {
        save(event("M-1", "FOREST_FIRE", 0, LATITUDE, LONGITUDE, NOW, IncidentSource.MANUAL));

        IncidentSaveResult hotspot = save(hotspot("H-1", ONE_KM, NOW));

        assertTrue(hotspot.grouped());
        assertEquals("M-1", hotspot.incident().getId());
    }

    @Test
    void hotspotJoinsTheNearestOfTwoIncidents() {
        save(hotspot("H-1", 0, NOW));
        save(hotspot("H-2", 3 * ONE_KM, NOW));

        IncidentSaveResult between = save(hotspot("H-3", 1.9 * ONE_KM, NOW));

        assertTrue(between.grouped());
        assertEquals("H-2", between.incident().getId());
    }

    @Test
    void structureFiresOnlyGroupWithinAHundredMetres() {
        save(sensor("S-1", 0));

        assertTrue(save(sensor("S-2", 0.0005)).grouped());   // about 55 m
        assertTrue(save(sensor("S-3", 0.0040)).created());   // about 445 m
    }

    @Test
    void differentTypesAreNotGrouped() {
        save(hotspot("H-1", 0, NOW));

        assertTrue(save(sensor("S-1", 0)).created());
    }

    private IncidentSaveResult save(IncidentEvent event) {
        return incidentGraphService.save(event);
    }

    private long incidents() {
        return graph.count("MATCH (i:Incident) RETURN count(i)", Map.of());
    }

    private static IncidentEvent hotspot(String id, double northOffset, Instant observedAt) {
        return event(id, "FOREST_FIRE", 0, LATITUDE + northOffset, LONGITUDE,
                observedAt, IncidentSource.FIRMS);
    }

    private static IncidentEvent sensor(String id, double northOffset) {
        return event(id, "STRUCTURE_FIRE", 1, LATITUDE + northOffset, LONGITUDE,
                NOW, IncidentSource.SIMULATOR);
    }

    private static IncidentEvent event(String id, String type, int severity,
            double latitude, double longitude, Instant observedAt, IncidentSource source) {

        return IncidentEvent.builder()
                .schemaVersion(IncidentEvent.CURRENT_SCHEMA_VERSION)
                .id(id)
                .type(type)
                .severity(severity)
                .latitude(latitude)
                .longitude(longitude)
                .occurredAt(observedAt)
                .source(source)
                .description("Grouping test")
                .build();
    }
}
