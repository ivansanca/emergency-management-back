package com.emergencias.emergency_management.integration;

import com.emergencias.emergency_management.messaging.config.KafkaTopics;
import com.emergencias.emergency_management.messaging.event.IncidentEvent;
import com.emergencias.emergency_management.messaging.producer.IncidentProducer;
import com.emergencias.emergency_management.model.IncidentSource;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End to end: an event is registered in Neo4j and notified on the output
 * topics, or ends up in the dead letter topic of its input topic when it is
 * not valid. Operator reports go through {@code IncidentProducer} to
 * {@code emergency-events-manual}; automatic detections are written straight
 * to {@code emergency-events}, as the sensor service does.
 */
@IntegrationTest
class IncidentFlowIntegrationTest {

    @Autowired
    private Neo4jClient neo4jClient;

    @Autowired
    private IncidentProducer incidentProducer;

    @Autowired
    private KafkaContainer kafka;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    private TestGraph graph;
    private TopicReader reader;

    @BeforeEach
    void setUp() {
        graph = new TestGraph(neo4jClient);
        graph.reset();
        reader = new TopicReader(kafka.getBootstrapServers(),
                KafkaTopics.INCIDENT_CREATED,
                KafkaTopics.INCIDENT_RECOMMENDATION,
                KafkaTopics.EMERGENCY_EVENTS_DLT,
                KafkaTopics.EMERGENCY_EVENTS_MANUAL_DLT);
    }

    @AfterEach
    void tearDown() {
        reader.close();
    }

    private static String newId() {
        return "IT-" + UUID.randomUUID();
    }

    private void publish(String id, String zoneCode) {
        publish(id, zoneCode, 2);
    }

    private void publish(String id, String zoneCode, int severity) {
        incidentProducer.publish(IncidentEvent.builder()
                .id(id)
                .type("FOREST_FIRE")
                .severity(severity)
                .zoneCode(zoneCode)
                .description("Integration test")
                .build());
    }

    private void publishAutomatic(String id, String zoneCode) throws Exception {
        IncidentEvent event = IncidentEvent.builder()
                .id(id)
                .type("FOREST_FIRE")
                .severity(2)
                .zoneCode(zoneCode)
                .source(IncidentSource.FIRMS)
                .description("Integration test")
                .build();

        kafkaTemplate.send(KafkaTopics.EMERGENCY_EVENTS, id,
                jsonMapper.writeValueAsString(event)).get(10, TimeUnit.SECONDS);
    }

    private static String originalTopic(ConsumerRecord<String, String> record) {
        Header header = record.headers().lastHeader("kafka_dlt-original-topic");
        assertNotNull(header);
        return new String(header.value(), StandardCharsets.UTF_8);
    }

    private JsonNode json(ConsumerRecord<String, String> record) {
        return jsonMapper.readTree(record.value());
    }

    @Test
    void validEventIsRegisteredNotifiedAndRecommended() {
        String id = newId();

        publish(id, TestGraph.ORIGIN_ZONE);

        JsonNode created =
                json(reader.awaitFirst(KafkaTopics.INCIDENT_CREATED, id));
        assertEquals("INCIDENT_CREATED", created.get("eventType").asString());
        assertEquals(1, created.get("schemaVersion").asInt());
        assertEquals(TestGraph.ORIGIN_ZONE,
                created.get("payload").get("zoneCode").asString());

        JsonNode recommendation = json(
                reader.awaitFirst(KafkaTopics.INCIDENT_RECOMMENDATION, id));
        assertEquals(4,
                recommendation.get("payload").get("recommendations").size());

        assertTrue(graph.flag(
                "MATCH (i:Incident {id: $id}) RETURN i.notified",
                Map.of("id", id)));
    }

    @Test
    void duplicatedEventIsNotifiedOnlyOnce() {
        String id = newId();
        String marker = newId();

        publish(id, TestGraph.ORIGIN_ZONE);
        publish(id, TestGraph.ORIGIN_ZONE);
        publish(marker, TestGraph.ORIGIN_ZONE);

        reader.awaitFirst(KafkaTopics.INCIDENT_CREATED, marker);

        assertEquals(1,
                reader.recordsFor(KafkaTopics.INCIDENT_CREATED, id).size());
        assertEquals(1, graph.count(
                "MATCH (i:Incident {id: $id}) RETURN count(i)",
                Map.of("id", id)));
    }

    @Test
    void eventWithoutApplicableRuleEndsUpInTheDeadLetterTopic() {
        String id = newId();

        publish(id, TestGraph.ORIGIN_ZONE, 4);

        reader.awaitFirst(KafkaTopics.EMERGENCY_EVENTS_MANUAL_DLT, id);
        assertEquals(0, graph.count(
                "MATCH (i:Incident {id: $id}) RETURN count(i)",
                Map.of("id", id)));
    }

    @Test
    void invalidEventEndsUpInTheDeadLetterTopic() {
        String id = newId();

        publish(id, "99999");

        ConsumerRecord<String, String> rejected =
                reader.awaitFirst(KafkaTopics.EMERGENCY_EVENTS_MANUAL_DLT, id);
        assertEquals(KafkaTopics.EMERGENCY_EVENTS_MANUAL, originalTopic(rejected));
        assertEquals(0, graph.count(
                "MATCH (i:Incident {id: $id}) RETURN count(i)",
                Map.of("id", id)));
    }

    @Test
    void automaticEventIsRegisteredAndNotified() throws Exception {
        String id = newId();

        publishAutomatic(id, TestGraph.ORIGIN_ZONE);

        reader.awaitFirst(KafkaTopics.INCIDENT_CREATED, id);
        assertEquals(1, graph.count(
                "MATCH (i:Incident {id: $id}) RETURN count(i)",
                Map.of("id", id)));
    }

    @Test
    void invalidAutomaticEventEndsUpInItsOwnDeadLetterTopic() throws Exception {
        String id = newId();

        publishAutomatic(id, "99999");

        ConsumerRecord<String, String> rejected =
                reader.awaitFirst(KafkaTopics.EMERGENCY_EVENTS_DLT, id);
        assertEquals(KafkaTopics.EMERGENCY_EVENTS, originalTopic(rejected));
        assertTrue(reader.recordsFor(KafkaTopics.EMERGENCY_EVENTS_MANUAL_DLT, id).isEmpty());
    }
}
