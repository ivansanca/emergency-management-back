package com.emergencias.emergency_management.messaging.config;

import com.emergencias.emergency_management.exception.InvalidIncidentEventException;
import com.emergencias.emergency_management.messaging.event.IncidentEvent;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.MessageListenerContainer;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class KafkaErrorHandlingConfigTest {

    private static final ConsumerRecord<String, String> RECORD =
            new ConsumerRecord<>(KafkaTopics.EMERGENCY_EVENTS, 0, 0L,
                    "incident-1", "{\"type\":\"FOREST_FIRE\"}");

    @Test
    void deadLetterDestinationIsDeadLetterTopic() {
        TopicPartition destination = KafkaErrorHandlingConfig
                .deadLetterDestination(RECORD, new IllegalArgumentException());

        assertEquals(KafkaTopics.EMERGENCY_EVENTS_DLT, destination.topic());
        assertEquals(-1, destination.partition());
    }

    @Test
    void invalidEventGoesToDeadLetterWithoutRetries() {
        List<Exception> recovered = new ArrayList<>();
        DefaultErrorHandler errorHandler = KafkaErrorHandlingConfig
                .errorHandler((record, exception) -> recovered.add(exception));
        InvalidIncidentEventException exception =
                new InvalidIncidentEventException("No existe la zona 99999");

        boolean handled = errorHandler.handleOne(
                exception, RECORD,
                mock(Consumer.class), mock(MessageListenerContainer.class));

        assertTrue(handled);
        assertEquals(1, recovered.size());
        assertSame(exception, recovered.get(0));
    }

    @Test
    void malformedJsonGoesToDeadLetterWithoutRetries() {
        Exception exception = assertThrows(
                Exception.class,
                () -> JsonMapper.builder().build()
                        .readValue("{no es json", IncidentEvent.class));
        List<Exception> recovered = new ArrayList<>();
        DefaultErrorHandler errorHandler = KafkaErrorHandlingConfig
                .errorHandler((record, thrown) -> recovered.add(thrown));

        boolean handled = errorHandler.handleOne(
                exception, RECORD,
                mock(Consumer.class), mock(MessageListenerContainer.class));

        assertTrue(handled);
        assertEquals(1, recovered.size());
    }
}
