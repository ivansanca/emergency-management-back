package com.emergencias.emergency_management.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.neo4j.core.Neo4jClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The query API over HTTP, against the test graph and three incidents:
 * <ul>
 *   <li>{@code INC-A}: active forest fire seen by satellite in Origen, with
 *   two detections and one resource assigned;</li>
 *   <li>{@code INC-B}: active structure fire reported in Destino;</li>
 *   <li>{@code INC-C}: closed forest fire in Origen, closed on the same day,
 *   whose resource was released.</li>
 * </ul>
 */
@IntegrationTest
class QueryApiIntegrationTest {

    private final HttpClient client = HttpClient.newHttpClient();

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private Neo4jClient neo4jClient;

    @Autowired
    private JsonMapper jsonMapper;

    @BeforeEach
    void setUp() {
        new TestGraph(neo4jClient).reset();
        neo4jClient.query("""
                MATCH (origin:Zone {ineCode: '00001'}), (target:Zone {ineCode: '00003'})
                MATCH (engine:Resource {id: 'ENGINE-ORIGIN'}), (tender:Resource {id: 'TENDER-PASS'})

                CREATE (a:Incident {id: 'INC-A', type: 'FOREST_FIRE', severity: 0,
                    status: 'ACTIVE', source: 'FIRMS', latitude: 43.40, longitude: -4.00,
                    occurredAt: datetime('2026-03-21T02:00:00Z'),
                    receivedAt: datetime('2026-03-21T03:00:00Z'),
                    lastDetectedAt: datetime('2026-03-21T14:00:00Z'),
                    detectionCount: 2, notified: true})-[:OCCURRED_IN]->(origin)
                CREATE (:Detection {id: 'INC-A', latitude: 43.40, longitude: -4.00,
                    observedAt: datetime('2026-03-21T02:00:00Z'),
                    receivedAt: datetime('2026-03-21T03:00:00Z'), source: 'FIRMS'})
                    -[:DETECTION_OF]->(a)
                CREATE (:Detection {id: 'INC-A-2', latitude: 43.41, longitude: -4.00,
                    observedAt: datetime('2026-03-21T14:00:00Z'),
                    receivedAt: datetime('2026-03-21T15:00:00Z'), source: 'FIRMS'})
                    -[:DETECTION_OF]->(a)
                SET engine.available = false
                CREATE (engine)-[:ASSIGNED_TO {assignedAt: datetime('2026-03-21T04:00:00Z')}]->(a)

                CREATE (:Incident {id: 'INC-B', type: 'STRUCTURE_FIRE', severity: 2,
                    status: 'ACTIVE', source: 'MANUAL',
                    occurredAt: datetime('2026-03-20T10:00:00Z'),
                    receivedAt: datetime('2026-03-20T10:00:00Z'),
                    detectionCount: 1, notified: true})-[:OCCURRED_IN]->(target)

                CREATE (c:Incident {id: 'INC-C', type: 'FOREST_FIRE', severity: 2,
                    status: 'CLOSED', source: 'MANUAL',
                    occurredAt: datetime('2026-03-10T10:00:00Z'),
                    receivedAt: datetime('2026-03-10T10:00:00Z'),
                    closedAt: datetime('2026-03-10T20:00:00Z'),
                    detectionCount: 1, notified: true})-[:OCCURRED_IN]->(origin)
                CREATE (tender)-[:ASSIGNED_TO {assignedAt: datetime('2026-03-10T11:00:00Z'),
                    releasedAt: datetime('2026-03-10T20:00:00Z')}]->(c)
                """).run();
    }

    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        return client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode getJson(String path) throws IOException, InterruptedException {
        HttpResponse<String> response = get(path);
        assertEquals(200, response.statusCode(), response.body());
        return jsonMapper.readTree(response.body());
    }

    private static List<String> values(JsonNode array, String field) {
        List<String> values = new ArrayList<>();
        array.forEach(node -> values.add(node.get(field).asString()));
        return values;
    }

    @Test
    void incidentsAreListedMostRecentlyActiveFirst() throws Exception {
        JsonNode page = getJson("/api/incidents");

        assertEquals(List.of("INC-A", "INC-B", "INC-C"), values(page.get("items"), "id"));
        assertEquals(3, page.get("totalElements").asLong());
        assertEquals(1, page.get("totalPages").asInt());
    }

    @Test
    void summaryCountsDetectionsAndCurrentAssignments() throws Exception {
        JsonNode items = getJson("/api/incidents?status=ACTIVE&type=FOREST_FIRE").get("items");

        assertEquals(List.of("INC-A"), values(items, "id"));
        JsonNode incident = items.get(0);
        assertEquals("Origen", incident.get("zoneName").asString());
        assertEquals("FIRMS", incident.get("source").asString());
        assertEquals(2, incident.get("detectionCount").asLong());
        assertEquals(1, incident.get("assignedResources").asLong());
        assertTrue(incident.get("lastDetectedAt").asString().startsWith("2026-03-21T14:00"));
    }

    @Test
    void filtersCanBeCombined() throws Exception {
        assertEquals(List.of("INC-B"), values(getJson(
                "/api/incidents?source=MANUAL&status=ACTIVE").get("items"), "id"));
        assertEquals(List.of("INC-A", "INC-C"), values(getJson(
                "/api/incidents?zoneCode=00001").get("items"), "id"));
        assertEquals(List.of(), values(getJson(
                "/api/incidents?zoneCode=00002").get("items"), "id"));
    }

    @Test
    void intervalSelectsTheIncidentsActiveInIt() throws Exception {
        assertEquals(List.of("INC-A", "INC-B"), values(getJson(
                "/api/incidents?from=2026-03-15T00:00:00Z").get("items"), "id"));
        assertEquals(List.of("INC-C"), values(getJson(
                "/api/incidents?to=2026-03-15T00:00:00Z").get("items"), "id"));
        // Both started before the interval: INC-A was detected within it and
        // INC-B, never detected again, is still open.
        assertEquals(List.of("INC-A", "INC-B"), values(getJson(
                "/api/incidents?from=2026-03-21T12:00:00Z&to=2026-03-21T16:00:00Z")
                .get("items"), "id"));
        // INC-C was last detected at 10:00 but stayed active until it was closed.
        assertEquals(List.of("INC-C"), values(getJson(
                "/api/incidents?from=2026-03-10T15:00:00Z&to=2026-03-10T23:00:00Z")
                .get("items"), "id"));
    }

    @Test
    void pagesSplitTheList() throws Exception {
        JsonNode second = getJson("/api/incidents?page=1&size=2");

        assertEquals(List.of("INC-C"), values(second.get("items"), "id"));
        assertEquals(3, second.get("totalElements").asLong());
        assertEquals(2, second.get("totalPages").asInt());
    }

    @Test
    void invalidQueriesAreRejected() throws Exception {
        assertEquals(400, get("/api/incidents?size=0").statusCode());
        assertEquals(400, get("/api/incidents?size=101").statusCode());
        assertEquals(400, get("/api/incidents?status=OPEN").statusCode());
        assertEquals(400, get("/api/incidents?from=ayer").statusCode());
        assertEquals(400, get(
                "/api/incidents?from=2026-03-22T00:00:00Z&to=2026-03-21T00:00:00Z").statusCode());
    }

    @Test
    void detailHasTheDetectionsInOrderAndTheAssignments() throws Exception {
        JsonNode detail = getJson("/api/incidents/INC-A");

        assertEquals("INC-A", detail.get("incident").get("id").asString());
        assertEquals(List.of("INC-A", "INC-A-2"), values(detail.get("detections"), "id"));
        JsonNode assignment = detail.get("assignments").get(0);
        assertEquals("ENGINE-ORIGIN", assignment.get("resourceId").asString());
        assertEquals("RURAL_ENGINE", assignment.get("resourceType").asString());
        assertTrue(assignment.get("releasedAt").isNull());
    }

    @Test
    void closedIncidentKeepsItsReleasedAssignments() throws Exception {
        JsonNode detail = getJson("/api/incidents/INC-C");

        assertEquals("CLOSED", detail.get("incident").get("status").asString());
        assertTrue(detail.get("incident").get("closedAt").asString()
                .startsWith("2026-03-10T20:00"));
        assertEquals(0, detail.get("incident").get("assignedResources").asLong());
        assertTrue(detail.get("assignments").get(0).get("releasedAt").asString()
                .startsWith("2026-03-10T20:00"));
    }

    @Test
    void detailOfAnUnknownIncidentIsNotFound() throws Exception {
        assertEquals(404, get("/api/incidents/NO-EXISTE").statusCode());
    }

    @Test
    void resourcesCanBeFilteredAndShowTheirIncident() throws Exception {
        JsonNode busy = getJson("/api/resources?available=false");
        assertEquals(List.of("ENGINE-ORIGIN", "ENGINE-BUSY"), values(busy, "id"));
        assertEquals("INC-A", busy.get(0).get("assignedIncidentId").asString());
        assertTrue(busy.get(1).get("assignedIncidentId").isNull());

        assertEquals(List.of("ENGINE-BUSY", "TENDER-PASS"),
                values(getJson("/api/resources?zoneCode=00002"), "id"));

        JsonNode aerial = getJson("/api/resources?type=AERIAL_UNIT");
        assertEquals(List.of("HELI"), values(aerial, "id"));
        assertTrue(aerial.get(0).get("aerial").asBoolean());
    }

    @Test
    void zonesAreListedByName() throws Exception {
        assertEquals(List.of("Base aerea", "Cortado", "Destino", "Lejano", "Origen", "Paso"),
                values(getJson("/api/zones"), "name"));
    }
}
