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
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The REST API over HTTP: status codes and error bodies in Problem Details
 * format.
 */
@IntegrationTest
class ApiIntegrationTest {

    private static final String PROBLEM_JSON = "application/problem+json";
    private static final Duration REGISTRATION_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration POLL_INTERVAL = Duration.ofMillis(100);

    /** Forest fire about 556 m north of the located test incident. */
    private static final String NEAR_REPORT = """
            {"type": "FOREST_FIRE", "severity": 2, "latitude": 43.405, "longitude": -4.00}
            """;

    private final HttpClient client = HttpClient.newHttpClient();

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private Neo4jClient neo4jClient;

    @Autowired
    private JsonMapper jsonMapper;

    @BeforeEach
    void setUp() {
        TestGraph graph = new TestGraph(neo4jClient);
        graph.reset();
        graph.incident(TestGraph.INCIDENT_ID, 2);
    }

    private HttpResponse<String> get(String path)
            throws IOException, InterruptedException {

        return client.send(
                HttpRequest.newBuilder(uri(path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String body)
            throws IOException, InterruptedException {

        return client.send(
                HttpRequest.newBuilder(uri(path))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    /** Gives the test incident a detection with a position, as if registered. */
    private void locateIncident() {
        neo4jClient.query("""
                MATCH (i:Incident {id: $id})
                SET i.detectionCount = 1
                CREATE (:Detection {id: $id, latitude: 43.40, longitude: -4.00,
                                    location: point({latitude: 43.40, longitude: -4.00}),
                                    observedAt: datetime(), receivedAt: datetime(),
                                    source: 'MANUAL'})
                       -[:DETECTION_OF]->(i)
                """).bind(TestGraph.INCIDENT_ID).to("id").run();
    }

    /**
     * Waits until an accepted report has been registered by the consumer.
     * The API answers 202 before that happens; without waiting, the
     * registration would still be running when the next test resets the
     * graph, and the two transactions can deadlock.
     */
    private void awaitRegistered(HttpResponse<String> accepted) throws InterruptedException {
        String incidentId = jsonMapper.readTree(accepted.body())
                .get("incidentId").asString();
        TestGraph graph = new TestGraph(neo4jClient);
        Instant deadline = Instant.now().plus(REGISTRATION_TIMEOUT);

        while (graph.count("MATCH (i:Incident {id: $id}) RETURN count(i)",
                Map.of("id", incidentId)) == 0) {
            assertTrue(Instant.now().isBefore(deadline),
                    "incident " + incidentId + " was not registered");
            Thread.sleep(POLL_INTERVAL.toMillis());
        }
    }

    private String detail(HttpResponse<String> response) {
        assertTrue(response.headers().firstValue("Content-Type")
                .orElse("").startsWith(PROBLEM_JSON));
        JsonNode body = jsonMapper.readTree(response.body());
        return body.get("detail").asString();
    }

    @Test
    void validIncidentIsAccepted() throws Exception {
        HttpResponse<String> response = post("/api/incidents", """
                {"type": "FOREST_FIRE", "severity": 2, "zoneCode": "00001"}
                """);

        assertEquals(202, response.statusCode());
        assertFalse(jsonMapper.readTree(response.body())
                .get("incidentId").asString().isBlank());
        awaitRegistered(response);
    }

    @Test
    void reportNearAnOpenIncidentAsksForConfirmation() throws Exception {
        locateIncident();

        HttpResponse<String> response = post("/api/incidents", NEAR_REPORT);

        assertEquals(409, response.statusCode());
        assertTrue(detail(response).contains("confirmNew=true"));
        assertTrue(detail(response).contains("1 detección)"));
        assertFalse(jsonMapper.readTree(response.body()).get("reportId").asString().isBlank());
        JsonNode nearby = jsonMapper.readTree(response.body()).get("nearbyIncident");
        assertEquals(TestGraph.INCIDENT_ID, nearby.get("incidentId").asString());
        assertEquals(1, nearby.get("detectionCount").asLong());
        long distance = nearby.get("distanceMeters").asLong();
        assertTrue(distance > 500 && distance < 600, "distance " + distance);
    }

    @Test
    void confirmedReportNearAnOpenIncidentIsAccepted() throws Exception {
        locateIncident();

        HttpResponse<String> response =
                post("/api/incidents?confirmNew=true", NEAR_REPORT);

        assertEquals(202, response.statusCode());
        awaitRegistered(response);
    }

    @Test
    void reportIsAddedToAnOpenIncidentOnlyOnce() throws Exception {
        String path = "/api/incidents/" + TestGraph.INCIDENT_ID + "/detections";
        String body = """
                {"id": "REPORT-1", "latitude": 43.405, "longitude": -4.00}
                """;

        HttpResponse<String> first = post(path, body);
        HttpResponse<String> second = post(path, body);

        assertEquals(201, first.statusCode());
        assertEquals(1, jsonMapper.readTree(first.body()).get("detectionCount").asLong());
        assertEquals(200, second.statusCode());
        assertFalse(jsonMapper.readTree(second.body()).get("added").asBoolean());
        assertEquals(1, new TestGraph(neo4jClient).count(
                "MATCH (:Detection {id: 'REPORT-1', source: 'MANUAL'})"
                        + "-[:DETECTION_OF]->(:Incident {id: $id}) RETURN count(*)",
                Map.of("id", TestGraph.INCIDENT_ID)));
    }

    @Test
    void reportForAClosedIncidentIsAConflict() throws Exception {
        neo4jClient.query("MATCH (i:Incident {id: $id}) SET i.status = 'CLOSED'")
                .bind(TestGraph.INCIDENT_ID).to("id").run();

        HttpResponse<String> response = post(
                "/api/incidents/" + TestGraph.INCIDENT_ID + "/detections", "{}");

        assertEquals(409, response.statusCode());
        assertTrue(detail(response).contains("está cerrado"));
    }

    @Test
    void incidentWithoutLocationIsRejectedWithItsReason() throws Exception {
        HttpResponse<String> response = post("/api/incidents", """
                {"type": "FOREST_FIRE", "severity": 1}
                """);

        assertEquals(400, response.statusCode());
        assertEquals("Hay que indicar el código de zona o las coordenadas",
                detail(response));
    }

    @Test
    void incidentWithoutSeverityIsRejected() throws Exception {
        HttpResponse<String> response = post("/api/incidents", """
                {"type": "FOREST_FIRE", "zoneCode": "00001"}
                """);

        assertEquals(400, response.statusCode());
    }

    @Test
    void incidentWithoutApplicableRuleIsRejected() throws Exception {
        HttpResponse<String> response = post("/api/incidents", """
                {"type": "FOREST_FIRE", "severity": 4, "zoneCode": "00001"}
                """);

        assertEquals(400, response.statusCode());
        assertEquals("No hay ninguna regla activa para FOREST_FIRE con gravedad 4",
                detail(response));
    }

    @Test
    void resourceIsRegisteredAvailableByDefault() throws Exception {
        HttpResponse<String> response = post("/api/resources", """
                {"id": "NEW-ENGINE", "name": "Autobomba nueva",
                 "type": "RURAL_ENGINE", "zoneCode": "00002"}
                """);

        assertEquals(201, response.statusCode());
        JsonNode body = jsonMapper.readTree(response.body());
        assertTrue(body.get("available").asBoolean());
        assertEquals("Paso", body.get("zoneName").asString());
    }

    @Test
    void resourceWithUnknownTypeIsRejected() throws Exception {
        HttpResponse<String> response = post("/api/resources", """
                {"id": "NEW-ENGINE", "name": "Autobomba nueva",
                 "type": "RURAL_ENIGNE", "zoneCode": "00002"}
                """);

        assertEquals(400, response.statusCode());
        assertEquals("No existe el tipo de recurso RURAL_ENIGNE",
                detail(response));
    }

    @Test
    void registeringAResourceKeepsTheAerialFlagOfItsType() throws Exception {
        post("/api/resources", """
                {"id": "NEW-HELI", "name": "Helicoptero nuevo",
                 "type": "AERIAL_UNIT", "zoneCode": "00006"}
                """);

        assertTrue(new TestGraph(neo4jClient).flag(
                "MATCH (t:ResourceType {name: 'AERIAL_UNIT'}) RETURN t.aerial",
                Map.of()));
    }

    @Test
    void severityOutOfRangeIsRejected() throws Exception {
        HttpResponse<String> response = post("/api/incidents", """
                {"type": "FOREST_FIRE", "severity": 5, "zoneCode": "00001"}
                """);

        assertEquals(400, response.statusCode());
    }

    @Test
    void unknownIncidentIsReportedAsNotFound() throws Exception {
        HttpResponse<String> response =
                get("/api/incidents/NO-EXISTE/recommendations");

        assertEquals(404, response.statusCode());
        assertEquals("No existe el incidente NO-EXISTE", detail(response));
    }

    @Test
    void secondAssignmentOfTheSameResourceIsAConflict() throws Exception {
        String path = "/api/incidents/" + TestGraph.INCIDENT_ID + "/assignments";
        String body = """
                {"resourceId": "ENGINE-ORIGIN"}
                """;

        assertEquals(201, post(path, body).statusCode());

        HttpResponse<String> second = post(path, body);
        assertEquals(409, second.statusCode());
        assertTrue(detail(second).contains("no está disponible"));
    }
}
