package com.emergencias.emergency_management.messaging.producer;

import com.emergencias.emergency_management.dto.CloseIncidentResponse;
import com.emergencias.emergency_management.messaging.config.KafkaTopics;
import com.emergencias.emergency_management.model.Incident;
import com.emergencias.emergency_management.model.IncidentStatus;
import com.emergencias.emergency_management.model.Zone;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IncidentNotificationProducerTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private IncidentNotificationProducer producer;

    @BeforeEach
    void setUp() {
        producer = new IncidentNotificationProducer(kafkaTemplate, jsonMapper);
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));
    }

    private JsonNode sentMessage(String topic) {
        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq(topic), eq("incident-1"), message.capture());
        return jsonMapper.readTree(message.getValue());
    }

    @Test
    void incidentCreatedIsPublishedWithEnvelopeAndZone() {
        Incident incident = new Incident(
                "incident-1", "FOREST_FIRE", 2, "Forest fire",
                IncidentStatus.ACTIVE,
                new Zone("39014", "Cabuérniga", 43.2264717, -4.3015564));

        producer.incidentCreated(incident);

        JsonNode message = sentMessage(KafkaTopics.INCIDENT_CREATED);
        assertEquals("INCIDENT_CREATED", message.get("eventType").asString());
        assertEquals(1, message.get("schemaVersion").asInt());
        assertEquals("incident-1", message.get("incidentId").asString());
        assertFalse(message.get("eventId").asString().isBlank());
        assertFalse(message.get("emittedAt").isNull());
        assertEquals("39014",
                message.get("payload").get("zoneCode").asString());
        assertEquals("Cabuérniga",
                message.get("payload").get("zoneName").asString());
    }

    @Test
    void incidentClosedIsPublishedToClosedTopic() {
        producer.incidentClosed(CloseIncidentResponse.builder()
                .incidentId("incident-1")
                .status("CLOSED")
                .releasedResources(3)
                .build());

        JsonNode message = sentMessage(KafkaTopics.INCIDENT_CLOSED);
        assertEquals("INCIDENT_CLOSED", message.get("eventType").asString());
        assertEquals(3, message.get("payload").get("releasedResources").asInt());
    }
}
