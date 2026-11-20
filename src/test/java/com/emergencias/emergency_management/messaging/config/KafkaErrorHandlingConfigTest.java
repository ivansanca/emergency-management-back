package com.emergencias.emergency_management.messaging.config;

import com.emergencias.emergency_management.exception.EventPublishingException;
import com.emergencias.emergency_management.exception.InvalidIncidentEventException;
import com.emergencias.emergency_management.messaging.event.IncidentEvent;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.exceptions.ServiceUnavailableException;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.ListenerExecutionFailedException;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.util.backoff.BackOff;
import org.springframework.util.backoff.BackOffExecution;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
    void infrastructureFailuresAreRetriedWithoutLimit() {
        BackOff backOff = KafkaErrorHandlingConfig.backOffFor(
                new ListenerExecutionFailedException("listener failed",
                        new TransactionSystemException("no session",
                                new ServiceUnavailableException("Neo4j down"))));

        BackOffExecution execution = backOff.start();
        assertEquals(1000L, execution.nextBackOff());
        assertEquals(2000L, execution.nextBackOff());
        long last = 0;
        for (int attempt = 0; attempt < 50; attempt++) {
            last = execution.nextBackOff();
        }
        assertEquals(KafkaErrorHandlingConfig.MAX_RETRY_INTERVAL_MS, last);
    }

    @Test
    void kafkaNotConfirmingIsRetriedWithoutLimit() {
        assertNotNull(KafkaErrorHandlingConfig.backOffFor(
                new EventPublishingException("incident-created",
                        new IllegalStateException("broker down"))));
    }

    @Test
    void otherFailuresUseTheLimitedDefaultBackOff() {
        assertNull(KafkaErrorHandlingConfig.backOffFor(
                new ListenerExecutionFailedException("listener failed",
                        new NullPointerException("bug"))));
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
