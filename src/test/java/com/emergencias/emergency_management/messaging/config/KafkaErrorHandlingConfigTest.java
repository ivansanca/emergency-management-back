package com.emergencias.emergency_management.messaging.config;

import com.emergencias.emergency_management.exception.EventPublishingException;
import com.emergencias.emergency_management.exception.EventRejectedException;
import com.emergencias.emergency_management.exception.InvalidIncidentEventException;
import com.emergencias.emergency_management.messaging.event.IncidentEvent;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.RecordTooLargeException;
import org.apache.kafka.common.errors.TopicAuthorizationException;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.exceptions.ServiceUnavailableException;
import org.springframework.kafka.listener.BackOffHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.ListenerExecutionFailedException;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.util.backoff.BackOff;
import org.springframework.util.backoff.BackOffExecution;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    /** Skips the waits between attempts: these tests only count them. */
    private static final BackOffHandler NO_WAIT = new BackOffHandler() {
        @Override
        public void onNextBackOff(
                MessageListenerContainer container, Exception exception, long nextBackOff) {
            // No wait.
        }
    };

    private final List<Exception> deadLetters = new ArrayList<>();
    private final DefaultErrorHandler noWaitHandler =
            KafkaErrorHandlingConfig.errorHandler(
                    (record, exception) -> deadLetters.add(exception), NO_WAIT);

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
    void eventRejectedByKafkaGoesToDeadLetterWithoutRetries() {
        assertTrue(handle(rejected()));
        assertEquals(1, deadLetters.size());
    }

    @Test
    void notAuthorisedIsRetriedWithoutLimit() {
        for (int attempt = 0; attempt < 100; attempt++) {
            assertFalse(handle(temporary(
                    new TopicAuthorizationException("emergency-events"))));
        }
        assertTrue(deadLetters.isEmpty());
    }

    @Test
    void rejectionAfterTemporaryFailuresStopsRetrying() {
        // Spring Kafka keeps the first decision while the exception type
        // stays the same; a rejection must still end the retries.
        for (int attempt = 0; attempt < 5; attempt++) {
            assertFalse(handle(temporary(new IllegalStateException("broker down"))));
        }

        assertTrue(handle(rejected()));
        assertEquals(1, deadLetters.size());
    }

    @Test
    void failedAttemptIsDescribedByItsRootCause() {
        assertEquals("TopicAuthorizationException: Not authorized",
                KafkaErrorHandlingConfig.describe(temporary(
                        new TopicAuthorizationException("Not authorized"))));
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

    private boolean handle(Exception failure) {
        return noWaitHandler.handleOne(failure, RECORD,
                mock(Consumer.class), mock(MessageListenerContainer.class));
    }

    private static Exception temporary(Exception cause) {
        return new ListenerExecutionFailedException("listener failed",
                new EventPublishingException("incident-created",
                        new ExecutionException(cause)));
    }

    private static Exception rejected() {
        return new ListenerExecutionFailedException("listener failed",
                new EventRejectedException("incident-created",
                        new ExecutionException(new RecordTooLargeException("too large"))));
    }
}
