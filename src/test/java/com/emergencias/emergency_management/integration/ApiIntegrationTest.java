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
