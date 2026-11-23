package com.emergencias.emergency_management.messaging.producer;

import com.emergencias.emergency_management.exception.EventPublishingException;
import com.emergencias.emergency_management.exception.EventRejectedException;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.kafka.common.InvalidRecordException;
import org.apache.kafka.common.errors.RecordBatchTooLargeException;
import org.apache.kafka.common.errors.RecordTooLargeException;
import org.apache.kafka.common.errors.SerializationException;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Sends a message and waits for the broker to confirm it. Used where the
 * caller needs to know that the message is safely stored in Kafka before
 * carrying on.
 * <p>
 * A failure is reported with one of two exception classes:
 * <ul>
 *   <li>{@link EventRejectedException} when Kafka rejects the message itself
 *   (too large, invalid, cannot be serialised): sending it again can never
 *   work.</li>
 *   <li>{@link EventPublishingException} for any other failure (broker down,
 *   topic missing, not authorised, timeout): the message is fine and will go
 *   through once the cause is fixed.</li>
 * </ul>
 * Before, every failure was an {@code EventPublishingException}, so a message
 * that could never be sent was retried forever and blocked its partition.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class SynchronousSend {

    static final long TIMEOUT_SECONDS = 5;

    private static final List<Class<? extends Throwable>> MESSAGE_FAILURES = List.of(
            RecordTooLargeException.class,
            RecordBatchTooLargeException.class,
            InvalidRecordException.class,
            SerializationException.class);

    static void sendAndWait(
            KafkaTemplate<String, String> kafkaTemplate,
            String topic,
            String key,
            String message) {

        try {
            kafkaTemplate.send(topic, key, message)
                    .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new EventPublishingException(topic, exception);
        } catch (ExecutionException | TimeoutException | RuntimeException exception) {
            // RuntimeException: send() throws instead of failing the future
            // when the broker cannot be reached within max.block.ms.
            throw failure(topic, exception);
        }
    }

    static RuntimeException failure(String topic, Throwable cause) {
        return isMessageFailure(cause)
                ? new EventRejectedException(topic, cause)
                : new EventPublishingException(topic, cause);
    }

    /** The whole cause chain is inspected because Spring wraps the Kafka error. */
    static boolean isMessageFailure(Throwable failure) {
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());

        for (Throwable cause = failure;
                cause != null && visited.add(cause);
                cause = cause.getCause()) {

            Throwable current = cause;
            if (MESSAGE_FAILURES.stream().anyMatch(type -> type.isInstance(current))) {
                return true;
            }
        }
        return false;
    }
}
