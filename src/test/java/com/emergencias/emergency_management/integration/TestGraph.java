package com.emergencias.emergency_management.integration;

import org.springframework.data.neo4j.core.Neo4jClient;

import java.util.Map;

/**
 * Small road network used by the integration tests.
 * <pre>
 *   Origen --10 km/10 min-- Paso --10 km/10 min-- Destino --closed-- Cortado
 *      \________________30 km/40 min_______________/
 *   Origen --80 km/70 min-- Lejano          Base aerea (no roads)
 * </pre>
 * The test rule asks for 3 rural engines, 1 water tender and 1 aerial unit
 * for forest fires of severity 2. Only two engines can be dispatched: one is
 * busy, one is behind a closed road and one is beyond the time limit.
 */
final class TestGraph {

    static final String INCIDENT_ID = "INC-1";
    static final String ORIGIN_ZONE = "00001";

    private final Neo4jClient neo4jClient;

    TestGraph(Neo4jClient neo4jClient) {
        this.neo4jClient = neo4jClient;
    }

    void reset() {
        run("MATCH (n) DETACH DELETE n", Map.of());
        run("""
            CREATE (origin:Zone {ineCode: '00001', name: 'Origen',
                                 latitude: 43.40, longitude: -4.00})
            CREATE (pass:Zone {ineCode: '00002', name: 'Paso',
                               latitude: 43.40, longitude: -3.90})
            CREATE (target:Zone {ineCode: '00003', name: 'Destino',
                                 latitude: 43.40, longitude: -3.80})
            CREATE (cut:Zone {ineCode: '00004', name: 'Cortado',
                              latitude: 43.30, longitude: -3.80})
            CREATE (far:Zone {ineCode: '00005', name: 'Lejano',
                              latitude: 43.10, longitude: -4.50})
            CREATE (base:Zone {ineCode: '00006', name: 'Base aerea',
                               latitude: 43.45, longitude: -3.82})

            CREATE (origin)-[:CONNECTS_TO {distanceKm: 10.0,
                travelTimeMinutes: 10.0, active: true}]->(pass)
            CREATE (pass)-[:CONNECTS_TO {distanceKm: 10.0,
                travelTimeMinutes: 10.0, active: true}]->(target)
            CREATE (origin)-[:CONNECTS_TO {distanceKm: 30.0,
                travelTimeMinutes: 40.0, active: true}]->(target)
            CREATE (target)-[:CONNECTS_TO {distanceKm: 5.0,
                travelTimeMinutes: 5.0, active: false}]->(cut)
            CREATE (origin)-[:CONNECTS_TO {distanceKm: 80.0,
                travelTimeMinutes: 70.0, active: true}]->(far)

            CREATE (rural:ResourceType {name: 'RURAL_ENGINE'})
            CREATE (tender:ResourceType {name: 'WATER_TENDER'})
            CREATE (aerial:ResourceType {name: 'AERIAL_UNIT', aerial: true})

            CREATE (rule:DecisionRule {id: 'RULE-TEST', name: 'Regla de prueba',
                description: 'Regla de prueba.', incidentType: 'FOREST_FIRE',
                minSeverity: 2, maxSeverity: 2, priority: 100, active: true})
            CREATE (rule)-[:ALLOWS_RESOURCE_TYPE {quantity: 3,
                group: 'ENGINES'}]->(rural)
            CREATE (rule)-[:ALLOWS_RESOURCE_TYPE {quantity: 1,
                group: 'WATER_TENDER'}]->(tender)
            CREATE (rule)-[:ALLOWS_RESOURCE_TYPE {quantity: 1,
                group: 'AERIAL_UNIT'}]->(aerial)

            CREATE (e1:Resource {id: 'ENGINE-ORIGIN', name: 'Autobomba Origen',
                                 type: 'RURAL_ENGINE', available: true})
            CREATE (e1)-[:LOCATED_IN]->(origin)
            CREATE (e1)-[:HAS_TYPE]->(rural)
            CREATE (e2:Resource {id: 'ENGINE-TARGET', name: 'Autobomba Destino',
                                 type: 'RURAL_ENGINE', available: true})
            CREATE (e2)-[:LOCATED_IN]->(target)
            CREATE (e2)-[:HAS_TYPE]->(rural)
            CREATE (e3:Resource {id: 'ENGINE-BUSY', name: 'Autobomba ocupada',
                                 type: 'RURAL_ENGINE', available: false})
            CREATE (e3)-[:LOCATED_IN]->(pass)
            CREATE (e3)-[:HAS_TYPE]->(rural)
            CREATE (e4:Resource {id: 'ENGINE-CUT', name: 'Autobomba Cortado',
                                 type: 'RURAL_ENGINE', available: true})
            CREATE (e4)-[:LOCATED_IN]->(cut)
            CREATE (e4)-[:HAS_TYPE]->(rural)
            CREATE (e5:Resource {id: 'ENGINE-FAR', name: 'Autobomba Lejano',
                                 type: 'RURAL_ENGINE', available: true})
            CREATE (e5)-[:LOCATED_IN]->(far)
            CREATE (e5)-[:HAS_TYPE]->(rural)
            CREATE (t1:Resource {id: 'TENDER-PASS', name: 'Nodriza Paso',
                                 type: 'WATER_TENDER', available: true})
            CREATE (t1)-[:LOCATED_IN]->(pass)
            CREATE (t1)-[:HAS_TYPE]->(tender)
            CREATE (h1:Resource {id: 'HELI', name: 'Helicoptero',
                                 type: 'AERIAL_UNIT', available: true})
            CREATE (h1)-[:LOCATED_IN]->(base)
            CREATE (h1)-[:HAS_TYPE]->(aerial)
            """, Map.of());
    }

    void incident(String incidentId, int severity) {
        run("""
            MATCH (z:Zone {ineCode: $zone})
            CREATE (:Incident {id: $id, type: 'FOREST_FIRE',
                               severity: $severity, status: 'ACTIVE',
                               source: 'MANUAL', occurredAt: datetime(),
                               receivedAt: datetime(), notified: true})
                   -[:OCCURRED_IN]->(z)
            """, Map.of("zone", ORIGIN_ZONE, "id", incidentId,
                "severity", severity));
    }

    long count(String cypher, Map<String, Object> parameters) {
        return neo4jClient.query(cypher)
                .bindAll(parameters)
                .fetchAs(Long.class)
                .one()
                .orElse(0L);
    }

    boolean flag(String cypher, Map<String, Object> parameters) {
        return neo4jClient.query(cypher)
                .bindAll(parameters)
                .fetchAs(Boolean.class)
                .one()
                .orElse(false);
    }

    private void run(String cypher, Map<String, Object> parameters) {
        neo4jClient.query(cypher).bindAll(parameters).run();
    }
}
