package com.emergencias.emergency_management.messaging.producer;

import com.emergencias.emergency_management.exception.EventPublishingException;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Sends a message and waits for the broker to confirm it. Used where the
 * caller needs to know that the message is safely stored in Kafka before
 * carrying on.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class SynchronousSend {

    static final long TIMEOUT_SECONDS = 5;

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
            throw new EventPublishingException(topic, exception);
        }
    }
}
