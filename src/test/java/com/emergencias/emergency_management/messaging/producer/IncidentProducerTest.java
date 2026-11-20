package com.emergencias.emergency_management.messaging.producer;

import com.emergencias.emergency_management.exception.EventPublishingException;
import com.emergencias.emergency_management.messaging.config.KafkaTopics;
import com.emergencias.emergency_management.messaging.event.IncidentEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IncidentProducerTest {

    private static final IncidentEvent EVENT = IncidentEvent.builder()
            .id("incident-1")
            .type("FOREST_FIRE")
            .severity(2)
            .zoneCode("39014")
            .build();

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private IncidentProducer producer;

    @BeforeEach
    void setUp() {
        producer = new IncidentProducer(
                kafkaTemplate, JsonMapper.builder().build());
    }

    @Test
    void publishSendsEventKeyedByIncidentIdAndWaitsForConfirmation() {
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        assertDoesNotThrow(() -> producer.publish(EVENT));

        verify(kafkaTemplate).send(
                eq(KafkaTopics.EMERGENCY_EVENTS),
                eq("incident-1"),
                contains("FOREST_FIRE"));
    }

    @Test
    void publishFailsWhenKafkaRejectsTheEvent() {
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(
                        new IllegalStateException("broker down")));

        assertThrows(
                EventPublishingException.class,
                () -> producer.publish(EVENT));
    }

    @Test
    void publishFailsWhenKafkaCannotBeReached() {
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenThrow(new IllegalStateException("no metadata"));

        assertThrows(
                EventPublishingException.class,
                () -> producer.publish(EVENT));
    }

    @Test
    void publishFailsAndKeepsInterruptionWhenThreadIsInterrupted() {
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(new CompletableFuture<>());
        Thread.currentThread().interrupt();

        assertThrows(
                EventPublishingException.class,
                () -> producer.publish(EVENT));

        assertTrue(Thread.interrupted());
    }
}
